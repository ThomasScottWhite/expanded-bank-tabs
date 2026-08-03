package com.expandedbanktagsviewer;

import com.google.inject.Provides;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetDrag;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Expanded Bank Tags Viewer",
	description = "Organize existing Bank Tags tabs into an expanded, grouped view.",
	tags = {"bank", "bank tags", "bank tabs", "organization"}
)
@PluginDependency(BankTagsPlugin.class)
public class ExpandedBankTabsPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(ExpandedBankTabsPlugin.class);
	private static final String BANKTAGS_GROUP = "banktags";
	private static final String EXPANDED_TITLE = "Expanded Bank Tags Viewer";

	private final ExpandedWidgetRegistry widgetRegistry = new ExpandedWidgetRegistry();

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private BankTagsAdapter bankTags;

	@Inject
	private BankTabGroupManager groupManager;

	@Inject
	private ExpandedViewModelFactory viewModelFactory;

	@Inject
	private ExpandedViewRenderer viewRenderer;

	@Inject
	private BankTagsSidebarRefresher sidebarRefresher;

	@Inject
	private ExpandedBankTabActions actions;

	@Inject
	private ToggleWidgetController toggleController;

	@Inject
	private ExpandedScrollbarController scrollbarController;

	@Inject
	private ExpandedWidgetSession widgetSession;

	@Inject
	private DragDropController dragDropController;

	@Inject
	private ExpandedBankTabsConfig config;

	private Widget parent;
	private Widget panel;
	private boolean expandedViewVisible;
	private int expandedScrollOffset;
	private int expandedContentBottom;
	private int renderedBankWidth;
	private int renderedBankHeight;
	private boolean expandedRebuildQueued;
	private boolean postDragRebuildQueued;
	private boolean rebuildDeferralLogged;
	private int renderGeneration;
	private final ExpandedBankTabActions.Host actionsHost = new ExpandedBankTabActions.Host()
	{
		@Override
		public void closeExpandedView()
		{
			setExpandedViewVisible(false);
		}

		@Override
		public void rebuildExpandedView()
		{
			ExpandedBankTabsPlugin.this.rebuildExpandedView();
		}

		@Override
		public void prepareForSidebarRefresh()
		{
			removeOwnedWidgets();
			parent = null;
		}

		@Override
		public void refreshPluginWidgets()
		{
			refresh();
		}
	};
	private final ToggleWidgetController.Listener toggleListener = new ToggleWidgetController.Listener()
	{
		@Override
		public void toggleExpandedView()
		{
			ExpandedBankTabsPlugin.this.toggleExpandedView();
		}

		@Override
		public void createBankTab()
		{
			actions.createBankTab(null, actionsHost);
		}
	};
	private final ExpandedScrollbarController.Listener scrollbarListener = new ExpandedScrollbarController.Listener()
	{
		@Override
		public void scrollBy(int direction)
		{
			scrollExpandedView(direction);
		}

		@Override
		public int getMaxScroll()
		{
			return panel == null ? 0 : ExpandedViewLayout.maxScroll(panel.getOriginalY(),
				panel.getOriginalHeight(), expandedContentBottom);
		}

		@Override
		public void setScrollOffset(int offset)
		{
			expandedScrollOffset = offset;
			applyExpandedScroll();
		}
	};

	@Override
	protected void startUp()
	{
		log.debug("Starting Expanded Bank Tabs; enabled={}", config.enabled());
		groupManager.reload();
	}

	@Override
	protected void shutDown()
	{
		log.debug("Stopping Expanded Bank Tabs; visible={}, generation={}",
			expandedViewVisible, renderGeneration);
		sidebarRefresher.reset();
		setExpandedViewVisible(false);
		restoreBankItems();
		removeOwnedWidgets();
		parent = null;
	}

	@Provides
	ExpandedBankTabsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ExpandedBankTabsConfig.class);
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		log.trace("Bank UI script completed: id={}, visible={}, panelPresent={}",
			event.getScriptId(), expandedViewVisible, panel != null);
		switch (event.getScriptId())
		{
			case ScriptID.BANKMAIN_INIT:
			case ScriptID.BANKMAIN_FINISHBUILDING:
			case ScriptID.BANKMAIN_SIZE_CHECK:
				clientThread.invokeLater(this::refresh);
				break;
			case ScriptID.BANKMAIN_POPUP_TAB_DRAW:
				// Bank Tags emits this while its sidebar is being repositioned,
				// including during sidebar scrolling. Its layout pass hides every
				// child after its own tab range, which also includes our expanded
				// widgets. Restore only those widgets synchronously so they are visible
				// again before the next frame is rendered; rebuilding here causes a
				// visible flash and can interrupt a drag.
				restoreExpandedViewAfterBankTagsLayout();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (!config.enabled())
		{
			return;
		}

		Widget liveParent = client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER);
		if (liveParent == null)
		{
			return;
		}

		if (parent != liveParent)
		{
			log.debug("Bank content parent changed on client tick; old={}, new={}",
				widgetIdentity(parent), widgetIdentity(liveParent));
			refresh();
			return;
		}

		toggleController.maintain(parent, config.enabled(), expandedViewVisible, toggleListener);
		if (expandedViewVisible && config.enabled())
		{
			// Reattaching the layer deletes the previous render generation. Keep the
			// widget that received mouse-down alive until RuneLite has resolved the
			// press as either a click or a drag.
			if (canRebuildExpandedViewNow())
			{
				ensureExpandedLayer();
			}
			if (panel == null)
			{
				log.debug("Expanded view is visible without a panel; requesting rebuild");
				requestExpandedViewRebuild();
			}
		}
		maintainScrollbar();
		scrollbarController.pollDrag();
		toggleController.pollFallbackClick();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (BANKTAGS_GROUP.equals(event.getGroup()) || "expandedbanktabs".equals(event.getGroup()))
		{
			log.debug("Relevant config changed: group={}, key={}; reloading model",
				event.getGroup(), event.getKey());
			groupManager.reload();
			clientThread.invokeLater(() ->
			{
				refresh();
				requestExpandedViewRebuild();
			});
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN && event.isUnload())
		{
			log.debug("Bank interface unloaded; clearing generation {}", renderGeneration);
			restoreBankItems();
			removeExpandedWidgets();
			disposeExpandedLayer();
			scrollbarController.dispose();
			toggleController.dispose();
			parent = null;
			expandedViewVisible = false;
			expandedScrollOffset = 0;
			widgetSession.forgetTitle();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String option = event.getMenuOption();
		Widget widget = event.getWidget();
		if (toggleController.owns(widget))
		{
			if (toggleController.handleMenuOption(option))
			{
				event.consume();
				return;
			}
		}

		if (!expandedViewVisible)
		{
			return;
		}

		if (option.startsWith("View tab") || option.equals("View all items") || option.equals("View tag tab"))
		{
			if (widget != null && widgetRegistry.findTab(widget) != null)
			{
				// Let the expanded tab's own callback close the page and open the
				// selected Bank Tag. Closing here first destroys that callback source.
				return;
			}
			// Do not remove the dynamic layer while RuneLite is still traversing
			// the widget tree for this menu action. The next client tick is safe.
			clientThread.invokeLater(() -> setExpandedViewVisible(false));
		}
	}

	@Subscribe
	public void onWidgetDrag(WidgetDrag event)
	{
		// RuneLite emits WidgetDrag every client frame while mouse1 is held. Log
		// only the release event, which is when this plugin resolves the drop.
		if (client.getMouseCurrentButton() == 0)
		{
			log.debug("WidgetDrag released: visible={}, dragging={}, source={}, target={}",
				expandedViewVisible, client.isDraggingWidget(), widgetIdentity(client.getDraggedWidget()),
				widgetIdentity(client.getDraggedOnWidget()));
		}
		dragDropController.handle(event, expandedViewVisible, widgetRegistry, parent,
			expandedScrollOffset, scrollbarController, this::requestPostDragExpandedViewRebuild);
	}

	private void refresh()
	{
		Widget newParent = client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER);
		if (newParent == null)
		{
			return;
		}

		if (parent != newParent)
		{
			log.debug("Refreshing against a new bank content parent; old={}, new={}",
				widgetIdentity(parent), widgetIdentity(newParent));
			restoreBankItems();
			removeOwnedWidgets();
			parent = newParent;
		}

		toggleController.maintain(parent, config.enabled(), expandedViewVisible, toggleListener);
		maintainScrollbar();
		if (expandedViewVisible && config.enabled())
		{
			// Bank Tags can recreate or unhide the normal bank item children during
			// its own refresh without changing our panel or bank geometry. Reassert
			// this non-structural state on every relevant UI refresh.
			hideBankItems();
			updateExpandedTitle();
			boolean panelMissing = panel == null;
			boolean geometryChanged = parent.getWidth() != renderedBankWidth
				|| parent.getHeight() != renderedBankHeight;
			if (panelMissing || geometryChanged)
			{
				log.debug("Refresh invalidated expanded view: panelMissing={}, geometryChanged={}, "
					+ "renderedSize={}x{}, currentSize={}x{}", panelMissing, geometryChanged,
					renderedBankWidth, renderedBankHeight, parent.getWidth(), parent.getHeight());
				requestExpandedViewRebuild();
			}
			else
			{
				log.trace("Refresh kept generation {}: panel present and geometry unchanged", renderGeneration);
			}
		}
		else
		{
			restoreBankItems();
			updateToggle();
		}
	}

	private void restoreExpandedViewAfterBankTagsLayout()
	{
		if (!expandedViewVisible || !config.enabled())
		{
			return;
		}
		hideBankItems();
		widgetSession.restoreLayerAfterBankTagsLayout(panel, this::applyExpandedScroll);
	}

	private boolean ensureExpandedLayer()
	{
		return widgetSession.ensureLayer(parent, toggleController.getLayerParent(), expandedViewVisible,
			config.enabled(), this::clearExpandedWidgetReferences);
	}

	private void hideExpandedLayer()
	{
		widgetSession.hideLayer();
	}

	private void disposeExpandedLayer()
	{
		widgetSession.disposeLayer();
	}

	private void maintainScrollbar()
	{
		scrollbarController.maintain(toggleController.getLayerParent(),
			expandedViewVisible && config.enabled(), scrollbarListener);
	}

	private void toggleExpandedView()
	{
		if (!config.enabled())
		{
			return;
		}
		setExpandedViewVisible(!expandedViewVisible);
		// Bank Tags may finish rebuilding its sidebar after the toggle callback
		// returns. Reapply the chest action after that rebuild as well.
		clientThread.invokeLater(this::refresh);
	}

	private void setExpandedViewVisible(boolean visible)
	{
		if (visible == expandedViewVisible && (!visible || panel != null))
		{
			return;
		}

		log.debug("Expanded view visibility changing: {} -> {}", expandedViewVisible, visible);
		if (visible)
		{
			// Treat this like changing to a separate bank menu. This deselects the
			// currently active Bank Tags tab before the expanded page is shown.
			if (bankTags.getActiveTag() != null)
			{
				bankTags.closeTag();
			}

			widgetSession.showExpandedTitle(EXPANDED_TITLE);
		}
		else
		{
			restoreBankTitle();
		}

		expandedViewVisible = visible;
		expandedScrollOffset = 0;
		if (visible)
		{
			rebuildExpandedView();
		}
		else
		{
			restoreBankItems();
			removeExpandedWidgets();
			hideExpandedLayer();
			scrollbarController.hide();
			updateToggle();
		}
	}

	private void rebuildExpandedView()
	{
		if (parent == null || !expandedViewVisible || !config.enabled())
		{
			log.debug("Skipping rebuild: parentPresent={}, visible={}, enabled={}",
				parent != null, expandedViewVisible, config.enabled());
			return;
		}

		int nextGeneration = renderGeneration + 1;
		log.debug("Beginning rebuild generation {}; oldPanel={}, scrollOffset={}",
			nextGeneration, widgetIdentity(panel), expandedScrollOffset);
		removeExpandedWidgets();
		if (!ensureExpandedLayer())
		{
			log.debug("Generation {} aborted because no render layer could be attached", nextGeneration);
			return;
		}

		hideBankItems();
		updateExpandedTitle();
		updateToggle();
		List<ExpandedViewGroup> viewModel = viewModelFactory.create();
		int tabCount = viewModel.stream().mapToInt(group -> group.getTabs().size()).sum();
		log.debug("Rendering generation {} with {} groups and {} tabs", nextGeneration,
			viewModel.size(), tabCount);
		ExpandedViewRenderer.RenderResult renderResult = viewRenderer.render(widgetSession.getLayer(), parent,
			viewModel, widgetRegistry, new ExpandedViewRenderer.Listener()
			{
				@Override
				public void onScroll(int direction)
				{
					scrollExpandedView(direction);
				}

				@Override
				public void onCreateGroup()
				{
					actions.createGroup(actionsHost);
				}

				@Override
				public void onCreateTab(String groupId)
				{
					actions.createBankTab(groupId, actionsHost);
				}

				@Override
				public void onTabOperation(ScriptEvent event, String tag)
				{
					actions.handleTabOperation(event, tag, actionsHost);
				}

				@Override
				public void onGroupOperation(ScriptEvent event)
				{
					actions.handleGroupOperation(event,
						widgetRegistry.getGroupHeaderId(event.getSource()), actionsHost);
				}
			});
		panel = renderResult.getPanel();
		expandedContentBottom = renderResult.getContentBottom();
		renderedBankWidth = parent.getWidth();
		renderedBankHeight = parent.getHeight();
		renderGeneration = nextGeneration;
		applyExpandedScroll();
		maintainScrollbar();
		log.debug("Completed generation {}; panel={}, contentBottom={}, trackedWidgets={}",
			renderGeneration, widgetIdentity(panel), expandedContentBottom, widgetRegistry.getOwnedWidgetCount());
	}

	private void requestExpandedViewRebuild()
	{
		if (!expandedViewVisible || !config.enabled() || expandedRebuildQueued || postDragRebuildQueued)
		{
			log.trace("Ignoring rebuild request: visible={}, enabled={}, alreadyQueued={}, postDragQueued={}",
				expandedViewVisible, config.enabled(), expandedRebuildQueued, postDragRebuildQueued);
			return;
		}

		expandedRebuildQueued = true;
		rebuildDeferralLogged = false;
		log.debug("Queued guarded rebuild after generation {}", renderGeneration);
		clientThread.invokeLater(() ->
		{
			// A completed drop can supersede this guarded request with a guaranteed
			// tick-end rebuild. Remove this queue entry once that happens.
			if (!expandedRebuildQueued)
			{
				log.debug("Removing guarded rebuild because a newer request superseded it");
				return true;
			}

			if (!expandedViewVisible || !config.enabled())
			{
				expandedRebuildQueued = false;
				return true;
			}

			// The state may have changed since this rebuild was queued. Returning
			// false keeps the same request queued for a later client cycle without
			// destroying the widget currently being considered for a drag.
			if (!canRebuildExpandedViewNow())
			{
				if (!rebuildDeferralLogged)
				{
					rebuildDeferralLogged = true;
					log.debug("Deferring rebuild: menuOpen={}, mouseButton={}, dragging={}",
						client.isMenuOpen(), client.getMouseCurrentButton(), client.isDraggingWidget());
				}
				return false;
			}

			expandedRebuildQueued = false;
			rebuildDeferralLogged = false;
			log.debug("Executing guarded rebuild after generation {}", renderGeneration);
			rebuildExpandedView();
			return true;
		});
	}

	private void requestPostDragExpandedViewRebuild()
	{
		if (!expandedViewVisible || !config.enabled() || postDragRebuildQueued)
		{
			log.debug("Ignoring post-drag rebuild request: visible={}, enabled={}, alreadyQueued={}",
				expandedViewVisible, config.enabled(), postDragRebuildQueued);
			return;
		}

		postDragRebuildQueued = true;
		// The completed-drop render is authoritative and runs at tick end. Cancel
		// any older guarded request now so config-change events from the same save
		// cannot produce a second generation first.
		expandedRebuildQueued = false;
		rebuildDeferralLogged = false;
		log.debug("Queued tick-end rebuild for completed drag after generation {}", renderGeneration);
		clientThread.invokeAtTickEnd(() ->
		{
			log.debug("Executing completed-drag callback: visible={}, enabled={}, generation={}",
				expandedViewVisible, config.enabled(), renderGeneration);
			postDragRebuildQueued = false;
			// Cancel any older guarded request. Its queued callback will observe the
			// cleared flag and remove itself without producing a duplicate render.
			expandedRebuildQueued = false;
			if (expandedViewVisible && config.enabled())
			{
				rebuildExpandedView();
			}
		});
	}

	private boolean canRebuildExpandedViewNow()
	{
		return !client.isMenuOpen()
			&& client.getMouseCurrentButton() == 0;
	}

	private void scrollExpandedView(int direction)
	{
		if (panel == null || direction == 0)
		{
			return;
		}
		int maxScroll = ExpandedViewLayout.maxScroll(panel.getOriginalY(),
			panel.getOriginalHeight(), expandedContentBottom);
		expandedScrollOffset = Math.max(0, Math.min(maxScroll, expandedScrollOffset + direction * 24));
		applyExpandedScroll();
	}

	private void applyExpandedScroll()
	{
		if (panel == null)
		{
			return;
		}

		// The group list now starts near the top of the panel because the create
		// control was moved below the groups. Keep that first header in the
		// viewport instead of using the old top-control padding.
		int viewportTop = panel.getOriginalY() + 8;
		int viewportBottom = panel.getOriginalY() + panel.getOriginalHeight();
		int maxScroll = ExpandedViewLayout.maxScroll(panel.getOriginalY(),
			panel.getOriginalHeight(), expandedContentBottom);
		expandedScrollOffset = Math.max(0, Math.min(maxScroll, expandedScrollOffset));
		widgetRegistry.forEachScrollable((widget, baseY) ->
		{
			int y = baseY - expandedScrollOffset;
			widget.setOriginalY(y);
			// Widgets are siblings of the panel rather than children of a clipping
			// layer, so hide them before they can partially render over the bank
			// footer or scrollbar.
			widget.setHidden(y < viewportTop || y + widget.getOriginalHeight() > viewportBottom);
			widget.revalidate();
		});
	}

	private void updateToggle()
	{
		toggleController.updateState(config.enabled(), expandedViewVisible);
	}

	private void hideBankItems()
	{
		widgetSession.hideBankItems();
	}

	private void restoreBankItems()
	{
		widgetSession.restoreBankItems();
	}

	private void removeExpandedWidgets()
	{
		log.debug("Retiring generation {} with {} tracked widgets", renderGeneration,
			widgetRegistry.getOwnedWidgetCount());
		// Retire the complete generation before asking RuneLite to remove its
		// dynamic children. This prevents deferred children from remaining visible
		// or interactive while the replacement generation is being constructed.
		widgetRegistry.retireOwnedWidgets();
		widgetSession.clearLayerChildren();
		clearExpandedWidgetReferences();
	}

	private void clearExpandedWidgetReferences()
	{
		panel = null;
		widgetRegistry.clear();
		expandedContentBottom = 0;
		renderedBankWidth = 0;
		renderedBankHeight = 0;
	}

	private void removeOwnedWidgets()
	{
		removeExpandedWidgets();
		disposeExpandedLayer();
		scrollbarController.dispose();
		toggleController.dispose();
		clearExpandedWidgetReferences();
	}

	private void restoreBankTitle()
	{
		widgetSession.restoreTitle();
	}

	private void updateExpandedTitle()
	{
		if (!expandedViewVisible)
		{
			return;
		}
		widgetSession.showExpandedTitle(EXPANDED_TITLE);
	}

	private static String widgetIdentity(Widget widget)
	{
		return widget == null ? "null"
			: widget.getId() + "@" + Integer.toHexString(System.identityHashCode(widget));
	}

}
