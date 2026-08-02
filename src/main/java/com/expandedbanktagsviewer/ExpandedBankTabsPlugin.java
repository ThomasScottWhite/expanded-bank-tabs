package com.expandedbanktagsviewer;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.FontID;
import net.runelite.api.Point;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.SoundEffectID;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetDrag;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetConfig;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.chatbox.ChatboxItemSearch;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.tabs.TabManager;
import net.runelite.client.plugins.banktags.tabs.TabSprites;
import net.runelite.client.plugins.banktags.tabs.TagTab;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;
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
	private static final String TAG_TABS_KEY = "tagtabs";
	private static final String TAG_ICON_PREFIX = "icon_";
	private static final String TAG_LAYOUT_PREFIX = "layout_";
	private static final String TOGGLE = "Toggle expanded bank tabs";
	private static final String VIEW_TAB = "View tag tab";
	private static final String CHANGE_ICON = "Change icon";
	private static final String ENABLE_LAYOUT = "Enable layout";
	private static final String DISABLE_LAYOUT = "Disable layout";
	private static final String EXPORT_TAB = "Export tag tab";
	private static final String RENAME_TAB = "Rename tag tab";
	private static final String DELETE_TAB = "Delete tag tab";
	private static final String NEW_TAB = "New tag tab";
	private static final String CREATE_GROUP = "+ Create group";
	private static final String RENAME_GROUP = "Rename bank tab group";
	private static final String DELETE_GROUP = "Delete bank tab group";
	private static final String COLLAPSE_GROUP = "Collapse bank tab group";
	private static final String EXPAND_GROUP = "Expand bank tab group";
	private static final String EXPANDED_TITLE = "Expanded Bank Tags Viewer";

	private static final int TAB_WIDTH = 39;
	private static final int TAB_HEIGHT = 39;
	// Bank Tags reserves the first 41 pixels of the item container for the
	// sidebar's top controls. Start the expanded content below that same row so
	// the first group header is not covered by the bank-tab strip.
	private static final int PANEL_TOP = 41;
	private static final int BANK_BOTTOM_OFFSET = 39;
	private static final int TILE_WIDTH = 39;
	private static final int TILE_HEIGHT = 40;
	private static final int TILE_GAP = 1;
	private static final int GROUP_HEADER_HEIGHT = 24;
	private static final int GROUP_SPACING = 8;
	private static final int MENU_VIEW = 1;
	private static final int MENU_CHANGE_ICON = 2;
	private static final int MENU_LAYOUT = 3;
	private static final int MENU_EXPORT = 4;
	private static final int MENU_RENAME = 5;
	private static final int MENU_DELETE = 6;
	private static final int GROUP_MENU_RENAME = 1;
	private static final int GROUP_MENU_DELETE = 2;
	private static final int GROUP_MENU_COLLAPSE = 3;

	private final Map<Widget, String> tabWidgets = new IdentityHashMap<>();
	private final Map<Widget, String> groupHeaders = new IdentityHashMap<>();
	private final Map<Widget, String> groupDropTargets = new IdentityHashMap<>();
	private final Map<Widget, Integer> scrollBaseY = new IdentityHashMap<>();
	private final Map<Widget, Boolean> hiddenBankItems = new IdentityHashMap<>();
	private final List<GroupDropArea> groupDropAreas = new ArrayList<>();
	private final Set<Widget> expandedLayers = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<Widget> addTabWidgets = Collections.newSetFromMap(new IdentityHashMap<>());

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private BankTagsService bankTagsService;

	@Inject
	private BankTagsPlugin bankTagsPlugin;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private TabManager tabManager;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	@Inject
	private ChatboxItemSearch itemSearch;

	@Inject
	private BankTabGroupManager groupManager;

	@Inject
	private ExpandedBankTabsConfig config;

	private Widget parent;
	private Widget expandedLayer;
	private Widget expandedLayerParent;
	private Widget panel;
	private Widget originalNewTab;
	private Widget toggleLayerParent;
	private Widget toggleLayer;
	private Widget toggle;
	private Widget toggleIcon;
	private Widget toggleHitbox;
	private Widget expandedScrollbar;
	private boolean originalNewTabHidden;
	private boolean originalNewTabStateCaptured;
	private boolean expandedViewVisible;
	private int lastToggleHandledTick = -1;
	private boolean toggleMouseDown;
	private int expandedScrollOffset;
	private int expandedContentBottom;
	private String previousBankTitle;
	private boolean bankTagsRefreshPending;

	@Override
	protected void startUp()
	{
		groupManager.reload();
	}

	@Override
	protected void shutDown()
	{
		bankTagsRefreshPending = false;
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
			refresh();
			return;
		}

		ensureToggle();
		positionToggleOverlay();
		if (expandedViewVisible && config.enabled())
		{
			ensureExpandedLayer();
			if (panel == null)
			{
				rebuildExpandedView();
			}
		}
		ensureExpandedScrollbar();
		updateScrollFromMouse();
		boolean mouseDown = client.getMouseCurrentButton() == 1;
		Point mouse = client.getMouseCanvasPosition();
		Rectangle toggleBounds = toggleHitbox == null ? null : toggleHitbox.getBounds();
		if (mouseDown && !toggleMouseDown && mouse != null && toggleBounds != null
			&& toggleBounds.contains(mouse.getX(), mouse.getY()))
		{
			// Fallback for client builds which draw the dynamically-created widget
			// and show its menu but do not dispatch its op callback.
			handleToggleClick();
		}
		toggleMouseDown = mouseDown;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (BANKTAGS_GROUP.equals(event.getGroup()) || "expandedbanktabs".equals(event.getGroup()))
		{
			groupManager.reload();
			clientThread.invokeLater(this::refresh);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN && event.isUnload())
		{
			restoreBankItems();
			removeExpandedWidgets();
			removeExpandedLayer();
			removeToggleOverlay();
			restoreOriginalNewTab();
			parent = null;
			expandedViewVisible = false;
			expandedScrollOffset = 0;
			previousBankTitle = null;
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String option = event.getMenuOption();
		Widget widget = event.getWidget();
		if ((widget == toggle || widget == toggleIcon || widget == toggleHitbox || widget == toggleLayer)
			&& (TOGGLE.equals(option) || NEW_TAB.equals(option)))
		{
			// Handle the overlay through the normal RuneLite menu event. The
			// underlying Bank Tags + widget is hidden, so neither its New tag action
			// nor its callback can be selected accidentally.
			event.consume();
			clientThread.invokeLater(this::handleToggleClick);
			return;
		}

		if (!expandedViewVisible)
		{
			return;
		}

		if (option.startsWith("View tab") || option.equals("View all items") || option.equals("View tag tab"))
		{
			if (widget != null && findMappedAncestor(widget, tabWidgets) != null)
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
		if (!expandedViewVisible)
		{
			return;
		}

		Widget dragged = client.getDraggedWidget();
		Widget target = client.getDraggedOnWidget();
		if (dragged == null || client.getMouseCurrentButton() != 0)
		{
			return;
		}

		if (expandedScrollbar != null && dragged == expandedScrollbar)
		{
			updateScrollFromMouse();
			return;
		}

		DropTarget dropTarget = findDropTarget(target);
		Widget sourceGroupWidget = findMappedWidget(dragged, groupHeaders);
		if (sourceGroupWidget != null && dropTarget.matched)
		{
			groupManager.move(groupHeaders.get(sourceGroupWidget), dropTarget.groupId);
			client.setDraggedOnWidget(null);
			clientThread.invokeLater(this::rebuildExpandedView);
			return;
		}

		String tag = findMappedAncestor(dragged, tabWidgets);
		if (tag != null && dropTarget.matched)
		{
			boolean changed = false;
			if (dropTarget.tabTag != null)
			{
				changed = groupManager.moveTab(tag, dropTarget.tabTag);
			}
			else
			{
				String destination = dropTarget.groupId;
				if (!equalsNullable(groupManager.getGroupId(tag), destination))
				{
					groupManager.assign(tag, destination);
					changed = true;
				}
			}
			client.setDraggedOnWidget(null);
			if (changed)
			{
				clientThread.invokeLater(this::rebuildExpandedView);
			}
		}
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
			restoreBankItems();
			removeOwnedWidgets();
			parent = newParent;
		}

		ensureToggle();
		positionToggleOverlay();
		ensureExpandedScrollbar();
		if (expandedViewVisible && config.enabled())
		{
			updateExpandedTitle();
			// Do not replace the dynamic children while RuneLite is deciding
			// whether the current mouse press is a click or a drag. Rebuilding
			// here destroys the original drag source and makes its normal op
			// action (rename/view) win instead.
			if (!client.isMenuOpen() && !client.isDraggingWidget() && client.getMouseCurrentButton() == 0)
			{
				rebuildExpandedView();
			}
		}
		else
		{
			restoreBankItems();
			updateToggle();
		}
	}

	private void ensureToggle()
	{
		if (parent == null)
		{
			return;
		}

		Widget liveNewTab = getLiveNewTabWidget();
		if (liveNewTab == null)
		{
			removeToggleOverlay();
			return;
		}

		if (originalNewTab != liveNewTab)
		{
			restoreOriginalNewTab();
			originalNewTab = liveNewTab;
			originalNewTabHidden = originalNewTab.isHidden();
			originalNewTabStateCaptured = true;
		}

		// Keep Bank Tags' real + button and its listener intact. It is hidden while
		// this plugin is enabled so its create-new-tag action cannot leak through
		// after Bank Tags rebuilds the sidebar.
		originalNewTab.setHidden(config.enabled() && originalNewTabStateCaptured
			? true : originalNewTabHidden);
		originalNewTab.revalidate();

		if (!config.enabled())
		{
			removeToggleOverlay();
			return;
		}

		Widget newLayerParent = findToggleLayerParent();
		if (newLayerParent == null)
		{
			removeToggleOverlay();
			return;
		}
		if (toggleLayerParent != newLayerParent)
		{
			removeToggleOverlay();
			toggleLayerParent = newLayerParent;
		}

		ensureToggleOverlay();
		updateToggle();
	}

	private void restoreExpandedViewAfterBankTagsLayout()
	{
		if (!expandedViewVisible || !config.enabled() || expandedLayer == null)
		{
			return;
		}

		// The expanded widgets live below this layer, outside the direct child
		// range that Bank Tags lays out for its sidebar. Only the layer itself
		// needs to be made visible after a sidebar draw.
		expandedLayer.setHidden(false);
		expandedLayer.revalidate();
		if (panel != null)
		{
			panel.setHidden(false);
			panel.revalidate();
			applyExpandedScroll();
		}
	}

	private Widget getLiveNewTabWidget()
	{
		Widget[] children = parent.getChildren();
		if (children == null || children.length <= 3)
		{
			return null;
		}
		return children[3];
	}

	private Widget findToggleLayerParent()
	{
		Widget layer = findLayerAncestor(client.getWidget(InterfaceID.Bankmain.UNIVERSE));
		if (layer != null && layer != parent)
		{
			return layer;
		}

		Widget current = parent == null ? null : parent.getParent();
		while (current != null)
		{
			if (current.getType() == WidgetType.LAYER)
			{
				return current;
			}
			current = current.getParent();
		}

		// This fallback preserves the previous behavior on client layouts that do
		// not expose a separate ancestor layer, although the normal Bank interface
		// path uses the Bankmain universe layer above the sidebar container.
		return layer;
	}

	private Widget findLayerAncestor(Widget start)
	{
		Widget current = start;
		while (current != null)
		{
			if (current.getType() == WidgetType.LAYER)
			{
				return current;
			}
			current = current.getParent();
		}
		return null;
	}

	private boolean ensureExpandedLayer()
	{
		if (!expandedViewVisible || !config.enabled() || parent == null || toggleLayerParent == null)
		{
			return false;
		}

		Widget[] layerChildren = expandedLayerParent == null ? null : expandedLayerParent.getChildren();
		boolean attached = expandedLayer != null && expandedLayerParent == toggleLayerParent && layerChildren != null
			&& Arrays.stream(layerChildren).anyMatch(child -> child == expandedLayer);
		if (!attached)
		{
			removeExpandedLayer();
			clearExpandedWidgetReferences();
			expandedLayer = toggleLayerParent.createChild(-1, WidgetType.LAYER);
			expandedLayerParent = toggleLayerParent;
			expandedLayers.add(expandedLayer);
		}

		Point relativeLocation = getRelativeLocation(parent, toggleLayerParent);
		if (relativeLocation != null)
		{
			expandedLayer.setOriginalX(relativeLocation.getX());
			expandedLayer.setOriginalY(relativeLocation.getY());
		}
		else
		{
			Point parentLocation = parent.getCanvasLocation();
			Point layerLocation = toggleLayerParent.getCanvasLocation();
			if (parentLocation == null || layerLocation == null)
			{
				return false;
			}
			expandedLayer.setOriginalX(parentLocation.getX() - layerLocation.getX());
			expandedLayer.setOriginalY(parentLocation.getY() - layerLocation.getY());
		}

		expandedLayer.setOriginalWidth(Math.max(1, parent.getWidth()));
		expandedLayer.setOriginalHeight(Math.max(1, parent.getHeight()));
		expandedLayer.setNoClickThrough(false);
		expandedLayer.setHidden(false);
		expandedLayer.revalidate();
		return true;
	}

	private void removeExpandedLayer()
	{
		if (expandedLayer != null)
		{
			// Dynamic widget child arrays must not be replaced while the client is
			// traversing the interface. Hide the layer and leave it attached until
			// the bank interface itself is unloaded.
			expandedLayer.setHidden(true);
			expandedLayer.revalidate();
		}
		expandedLayer = null;
		expandedLayerParent = null;
	}

	private void ensureToggleOverlay()
	{
		if (toggleLayerParent == null || originalNewTab == null)
		{
			return;
		}

		Widget[] layerChildren = toggleLayerParent.getChildren();
		boolean layerAttached = toggleLayer != null && layerChildren != null
			&& Arrays.stream(layerChildren).anyMatch(child -> child == toggleLayer);
		Widget[] toggleChildren = toggleLayer == null ? null : toggleLayer.getChildren();
		boolean childrenAttached = toggle != null && toggleIcon != null && toggleHitbox != null && toggleChildren != null
			&& Arrays.stream(toggleChildren).anyMatch(child -> child == toggle)
			&& Arrays.stream(toggleChildren).anyMatch(child -> child == toggleIcon)
			&& Arrays.stream(toggleChildren).anyMatch(child -> child == toggleHitbox);
		if (layerAttached && childrenAttached)
		{
			configureToggle();
			return;
		}

		// Cleanup also clears the stored parent. Preserve the stable layer before
		// removing the old overlay so a replacement can be created safely.
		Widget layerParent = toggleLayerParent;
		removeToggleOverlay();
		if (layerParent == null)
		{
			return;
		}
		toggleLayerParent = layerParent;
		toggleLayer = toggleLayerParent.createChild(-1, WidgetType.LAYER);
		toggleLayer.setOriginalWidth(TAB_WIDTH);
		toggleLayer.setOriginalHeight(TAB_HEIGHT);
		toggleLayer.setNoClickThrough(true);

		toggle = toggleLayer.createChild(-1, WidgetType.GRAPHIC);
		toggle.setOriginalX(0);
		toggle.setOriginalY(0);
		toggle.setOriginalWidth(TAB_WIDTH);
		toggle.setOriginalHeight(TAB_HEIGHT);
		toggle.setSpriteId(TabSprites.TAB_BACKGROUND.getSpriteId());
		toggle.setItemId(-1);
		toggle.setItemQuantity(-1);
		toggle.setBorderType(0);
		toggle.setName("");
		toggle.setHasListener(true);
		toggle.setNoClickThrough(false);
		toggle.setOnMouseOverListener((JavaScriptCallback) event ->
		{
			toggle.setSpriteId(TabSprites.TAB_BACKGROUND_ACTIVE.getSpriteId());
			toggle.revalidate();
		});
		toggle.setOnMouseLeaveListener((JavaScriptCallback) event -> updateToggle());

		toggleIcon = toggleLayer.createChild(-1, WidgetType.GRAPHIC);
		toggleIcon.setOriginalX(3);
		toggleIcon.setOriginalY(4);
		toggleIcon.setOriginalWidth(Constants.ITEM_SPRITE_WIDTH);
		toggleIcon.setOriginalHeight(Constants.ITEM_SPRITE_HEIGHT);
		toggleIcon.setSpriteId(-1);
		toggleIcon.setItemId(ItemID.BCS_CHEST);
		toggleIcon.setItemQuantity(-1);
		toggleIcon.setBorderType(1);
		toggleIcon.setClickMask(0);
		toggleIcon.setName("");
		// The background is the only interactive child. Letting the item layer
		// click through prevents it from obscuring the full-slot hitbox below.
		toggleIcon.setNoClickThrough(false);
		toggleIcon.revalidate();

		// Keep input separate from both visual widgets. This prevents the item
		// layer from passing a click through to the hidden Bank Tags + widget,
		// while still exposing only one toggle menu action.
		toggleHitbox = toggleLayer.createChild(-1, WidgetType.GRAPHIC);
		toggleHitbox.setOriginalX(0);
		toggleHitbox.setOriginalY(0);
		toggleHitbox.setOriginalWidth(TAB_WIDTH);
		toggleHitbox.setOriginalHeight(TAB_HEIGHT);
		toggleHitbox.setSpriteId(-1);
		toggleHitbox.setItemId(-1);
		toggleHitbox.setItemQuantity(-1);
		toggleHitbox.setName("");
		toggleHitbox.setAction(1, TOGGLE);
		toggleHitbox.setHasListener(true);
		toggleHitbox.setNoClickThrough(true);
		toggleHitbox.setOnOpListener((JavaScriptCallback) event -> clientThread.invokeLater(this::handleToggleClick));
		toggleHitbox.setOnMouseOverListener((JavaScriptCallback) event ->
		{
			toggle.setSpriteId(TabSprites.TAB_BACKGROUND_ACTIVE.getSpriteId());
			toggle.revalidate();
		});
		toggleHitbox.setOnMouseLeaveListener((JavaScriptCallback) event -> updateToggle());
		toggleHitbox.revalidate();
		toggleLayer.revalidate();
		positionToggleOverlay();
	}

	private void configureToggle()
	{
		if (toggle == null || toggleIcon == null || toggleHitbox == null)
		{
			return;
		}
		toggle.setName("");
		toggle.setSpriteId(expandedViewVisible
			? TabSprites.TAB_BACKGROUND_ACTIVE.getSpriteId()
			: TabSprites.TAB_BACKGROUND.getSpriteId());
		toggle.setHasListener(true);
		toggle.setNoClickThrough(false);
		toggleHitbox.setAction(1, TOGGLE);
		toggleHitbox.setHasListener(true);
		toggleHitbox.setNoClickThrough(true);
		toggle.setItemId(-1);
		toggle.setItemQuantity(-1);
		toggle.setBorderType(0);
		toggleIcon.setItemId(ItemID.BCS_CHEST);
		toggleIcon.setItemQuantity(-1);
		toggleIcon.setClickMask(0);
		toggleIcon.setNoClickThrough(false);
		toggle.revalidate();
		toggleIcon.revalidate();
		toggleHitbox.revalidate();
	}

	private void positionToggleOverlay()
	{
		if (toggleLayer == null || toggleLayerParent == null || originalNewTab == null)
		{
			return;
		}

		// Use widget-relative coordinates first. Canvas coordinates can be stale
		// while Bank Tags is rebuilding its sidebar, especially for a hidden widget.
		Point relativeLocation = getRelativeLocation(originalNewTab, toggleLayerParent);
		if (relativeLocation != null)
		{
			toggleLayer.setOriginalX(relativeLocation.getX());
			toggleLayer.setOriginalY(relativeLocation.getY());
		}
		else
		{
			Point buttonLocation = originalNewTab.getCanvasLocation();
			Point layerLocation = toggleLayerParent.getCanvasLocation();
			if (buttonLocation == null || layerLocation == null)
			{
				return;
			}
			toggleLayer.setOriginalX(buttonLocation.getX() - layerLocation.getX());
			toggleLayer.setOriginalY(buttonLocation.getY() - layerLocation.getY());
		}

		toggleLayer.setOriginalWidth(TAB_WIDTH);
		toggleLayer.setOriginalHeight(TAB_HEIGHT);
		toggleLayer.revalidate();
	}

	private Point getRelativeLocation(Widget widget, Widget ancestor)
	{
		int x = 0;
		int y = 0;
		Widget current = widget;
		while (current != null && current != ancestor)
		{
			x += current.getRelativeX();
			y += current.getRelativeY();
			current = current.getParent();
		}
		return current == ancestor ? new Point(x, y) : null;
	}

	private void restoreOriginalNewTab()
	{
		if (originalNewTab != null && originalNewTabStateCaptured)
		{
			originalNewTab.setHidden(originalNewTabHidden);
			originalNewTab.revalidate();
		}
		originalNewTab = null;
		originalNewTabStateCaptured = false;
	}

	private void removeToggleOverlay()
	{
		removeExpandedScrollbar();
		if (toggleLayer != null)
		{
			toggleLayer.setHidden(true);
			toggleLayer.revalidate();
		}
		toggleLayer = null;
		toggle = null;
		toggleIcon = null;
		toggleHitbox = null;
		toggleLayerParent = null;
	}

	private void ensureExpandedScrollbar()
	{
		if (!expandedViewVisible || !config.enabled() || toggleLayerParent == null)
		{
			removeExpandedScrollbar();
			return;
		}

		Widget bankScrollbar = client.getWidget(InterfaceID.Bankmain.SCROLLBAR);
		if (bankScrollbar == null)
		{
			removeExpandedScrollbar();
			return;
		}

		Widget[] layerChildren = toggleLayerParent.getChildren();
		boolean attached = expandedScrollbar != null && layerChildren != null
			&& Arrays.stream(layerChildren).anyMatch(child -> child == expandedScrollbar);
		if (!attached)
		{
			removeExpandedScrollbar();
			expandedScrollbar = toggleLayerParent.createChild(-1, WidgetType.RECTANGLE);
			expandedScrollbar.setFilled(false);
			expandedScrollbar.setOpacity(0);
			expandedScrollbar.setHasListener(true);
			expandedScrollbar.setNoClickThrough(true);
			expandedScrollbar.setNoScrollThrough(true);
			expandedScrollbar.setClickMask(WidgetConfig.DRAG | WidgetConfig.DRAG_ON);
			expandedScrollbar.setDragDeadTime(1);
			expandedScrollbar.setDragDeadZone(1);
			expandedScrollbar.setOnScrollWheelListener((JavaScriptCallback) event -> scrollExpandedView(event.getMouseY()));
		}

		Point relativeLocation = getRelativeLocation(bankScrollbar, toggleLayerParent);
		if (relativeLocation != null)
		{
			expandedScrollbar.setOriginalX(relativeLocation.getX());
			expandedScrollbar.setOriginalY(relativeLocation.getY());
		}
		else
		{
			Point scrollbarLocation = bankScrollbar.getCanvasLocation();
			Point layerLocation = toggleLayerParent.getCanvasLocation();
			if (scrollbarLocation == null || layerLocation == null)
			{
				return;
			}
			expandedScrollbar.setOriginalX(scrollbarLocation.getX() - layerLocation.getX());
			expandedScrollbar.setOriginalY(scrollbarLocation.getY() - layerLocation.getY());
		}
		expandedScrollbar.setOriginalWidth(Math.max(1, bankScrollbar.getWidth()));
		expandedScrollbar.setOriginalHeight(Math.max(1, bankScrollbar.getHeight()));
		expandedScrollbar.setHidden(false);
		expandedScrollbar.revalidate();
	}

	private void removeExpandedScrollbar()
	{
		if (expandedScrollbar != null)
		{
			expandedScrollbar.setHidden(true);
			expandedScrollbar.revalidate();
		}
		expandedScrollbar = null;
	}

	private void updateScrollFromMouse()
	{
		if (!expandedViewVisible || expandedScrollbar == null || client.getMouseCurrentButton() != 1)
		{
			return;
		}

		Rectangle bounds = expandedScrollbar.getBounds();
		Point mouse = client.getMouseCanvasPosition();
		if (bounds == null || mouse == null || !bounds.contains(mouse.getX(), mouse.getY()))
		{
			return;
		}

		int viewportBottom = panel == null ? 0 : panel.getOriginalY() + panel.getOriginalHeight();
		int maxScroll = Math.max(0, expandedContentBottom - viewportBottom + 8);
		if (maxScroll == 0)
		{
			return;
		}

		int padding = Math.min(16, Math.max(1, bounds.height / 4));
		int trackTop = bounds.y + padding;
		int trackBottom = bounds.y + bounds.height - padding;
		int trackLength = Math.max(1, trackBottom - trackTop);
		int position = Math.max(0, Math.min(trackLength, mouse.getY() - trackTop));
		expandedScrollOffset = Math.round((float) position / trackLength * maxScroll);
		applyExpandedScroll();
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

	private void handleToggleClick()
	{
		int tick = client.getTickCount();
		if (lastToggleHandledTick == tick)
		{
			return;
		}
		lastToggleHandledTick = tick;
		toggleExpandedView();
	}

	private void setExpandedViewVisible(boolean visible)
	{
		if (visible == expandedViewVisible && (!visible || panel != null))
		{
			return;
		}

		if (visible)
		{
			// Treat this like changing to a separate bank menu. This deselects the
			// currently active Bank Tags tab before the expanded page is shown.
			if (bankTagsService != null && bankTagsService.getActiveTag() != null)
			{
				bankTagsService.closeBankTag();
			}

			Widget bankTitle = client.getWidget(InterfaceID.Bankmain.TITLE);
			if (bankTitle != null)
			{
				previousBankTitle = bankTitle.getText();
				bankTitle.setText(EXPANDED_TITLE);
				bankTitle.revalidate();
			}
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
			removeExpandedLayer();
			removeExpandedScrollbar();
			updateToggle();
		}
	}

	private void rebuildExpandedView()
	{
		if (parent == null || !expandedViewVisible || !config.enabled())
		{
			return;
		}

		removeExpandedWidgets();
		if (!ensureExpandedLayer())
		{
			return;
		}

		hideBankItems();
		updateExpandedTitle();
		updateToggle();

		int panelX = TAB_WIDTH + 2;
		int width = Math.max(1, parent.getWidth() - panelX);
		int height = Math.max(1, parent.getHeight() - BANK_BOTTOM_OFFSET - PANEL_TOP);
		panel = expandedLayer.createChild(-1, WidgetType.RECTANGLE);
		panel.setOriginalX(panelX);
		panel.setOriginalY(PANEL_TOP);
		panel.setOriginalWidth(width);
		panel.setOriginalHeight(height);
		panel.setFilled(false);
		panel.setOpacity(255);
		panel.setNoClickThrough(true);
		panel.setNoScrollThrough(true);
		panel.setHasListener(true);
		panel.setOnOpListener((JavaScriptCallback) event -> { });
		panel.setOnScrollWheelListener((JavaScriptCallback) event -> scrollExpandedView(event.getMouseY()));
		panel.revalidate();

		List<BankTab> tabs = loadTabs();
		// The create control now lives below the groups, so reclaim the space that
		// was previously reserved for it at the top of the panel.
		int y = PANEL_TOP + 8;
		List<BankTabGroup> groups = groupManager.getGroups();
		int ungroupedIndex = groupManager.getUngroupedIndex();
		boolean renderedGroup = false;
		for (int i = 0; i <= groups.size(); ++i)
		{
			if (i == ungroupedIndex)
			{
				if (renderedGroup)
				{
					y += GROUP_SPACING;
				}
				y = renderGroup("Ungrouped bank tabs (default)", null, ungroupedTabs(tabs), false,
					panelX, width, y);
				renderedGroup = true;
			}
			if (i < groups.size())
			{
				if (renderedGroup)
				{
					y += GROUP_SPACING;
				}
				BankTabGroup group = groups.get(i);
				y = renderGroup(group.getName(), group.getId(), tabsForGroup(tabs, group),
					group.isCollapsed(), panelX, width, y);
				renderedGroup = true;
			}
		}

		int createY = y + 8;
		Widget createGroup = createText(CREATE_GROUP, panelX + 10, createY,
			Math.max(1, width - 20), 26, JagexColors.MENU_TARGET);
		createGroup.setFontId(FontID.BOLD_12);
		createGroup.setXTextAlignment(WidgetTextAlignment.CENTER);
		createGroup.setYTextAlignment(WidgetTextAlignment.CENTER);
		createGroup.setAction(1, CREATE_GROUP);
		createGroup.setHasListener(true);
		createGroup.setNoClickThrough(true);
		createGroup.setOnOpListener((JavaScriptCallback) event -> createGroup());
		registerScrollable(createGroup, createY);
		createGroup.revalidate();
		expandedContentBottom = createY + createGroup.getOriginalHeight();
		applyExpandedScroll();
		ensureExpandedScrollbar();
	}

	private int renderGroup(String name, String groupId, List<BankTab> tabs, boolean collapsed,
		int panelX, int width, int y)
	{
		int groupTop = y;
		Widget header = createText(name, panelX + 10, y, Math.max(1, width - 20), GROUP_HEADER_HEIGHT,
			JagexColors.DARK_ORANGE_INTERFACE_TEXT);
		header.setFontId(FontID.BOLD_12);
		header.setXTextAlignment(WidgetTextAlignment.CENTER);
		header.setYTextAlignment(WidgetTextAlignment.CENTER);
		registerGroupDropTarget(header, groupId);
		groupHeaders.put(header, groupId);
		addDragOptions(header);
		header.setHasListener(true);
		if (groupId != null)
		{
			BankTabGroup group = groupManager.find(groupId);
			header.setAction(GROUP_MENU_RENAME, RENAME_GROUP);
			header.setAction(GROUP_MENU_DELETE, DELETE_GROUP);
			header.setAction(GROUP_MENU_COLLAPSE, group != null && group.isCollapsed() ? EXPAND_GROUP : COLLAPSE_GROUP);
			header.setHasListener(true);
			header.setNoClickThrough(true);
			header.setOnOpListener((JavaScriptCallback) this::handleGroupOp);
		}
		header.revalidate();
		registerScrollable(header, y);
		y += GROUP_HEADER_HEIGHT + 4;

		if (collapsed)
		{
			registerGroupDropArea(groupId, panelX, width, groupTop, y);
			return y;
		}

		int contentWidth = Math.max(1, width - 12);
		int startX = panelX + 12;
		int columns = Math.max(1, (width - 12 + TILE_GAP) / (TILE_WIDTH + TILE_GAP));
		if (tabs.isEmpty())
		{
			int addY = y;
			createAddTabTile(groupId, startX, addY);
			int groupBottom = addY + TILE_HEIGHT;
			registerGroupDropArea(groupId, panelX, width, groupTop, groupBottom);
			return groupBottom;
		}

		for (int i = 0; i < tabs.size(); ++i)
		{
			BankTab tab = tabs.get(i);
			int x = startX + (i % columns) * (TILE_WIDTH + TILE_GAP);
			int tileY = y + (i / columns) * (TILE_HEIGHT + TILE_GAP);
			createTabTile(tab, groupId, x, tileY);
		}

		int addIndex = tabs.size();
		int addX = startX + (addIndex % columns) * (TILE_WIDTH + TILE_GAP);
		int addY = y + (addIndex / columns) * (TILE_HEIGHT + TILE_GAP);
		createAddTabTile(groupId, addX, addY);

		int rows = (addIndex + 1 + columns - 1) / columns;
		int groupBottom = y + rows * (TILE_HEIGHT + TILE_GAP);
		registerGroupDropArea(groupId, panelX, width, groupTop, groupBottom);
		return groupBottom;
	}

	private void createTabTile(BankTab tab, String groupId, int x, int y)
	{
		String target = ColorUtil.wrapWithColorTag(tab.getTag(), JagexColors.MENU_TARGET);
		int backgroundSprite = tab.getTag().equals(bankTagsService == null ? null : bankTagsService.getActiveTag())
			? TabSprites.TAB_BACKGROUND_ACTIVE.getSpriteId()
			: TabSprites.TAB_BACKGROUND.getSpriteId();
		Widget background = createGraphic(parent, target, backgroundSprite, -1,
			TILE_WIDTH, TILE_HEIGHT, x, y);
		addTabActions(background, tab.getTag());
		addDragOptions(background);
		tabWidgets.put(background, tab.getTag());
		registerGroupDropTarget(background, groupId);
		registerScrollable(background, y);

		// Keep the item icon visual-only. The background tile remains the one
		// interactive widget, which prevents duplicate right-click menus.
		Widget icon = createGraphic(parent, target, -1, tab.getIconItemId(),
			Constants.ITEM_SPRITE_WIDTH, Constants.ITEM_SPRITE_HEIGHT, x + 3, y + 4);
		tabWidgets.put(icon, tab.getTag());
		icon.setClickMask(0);
		icon.setNoClickThrough(false);
		// The icon is a sibling of the tile background, so it must be registered
		// independently or it will remain at its original position while the
		// background scrolls away.
		registerScrollable(icon, y + 4);

		JavaScriptCallback onMouseOver = event ->
		{
			background.setSpriteId(TabSprites.TAB_BACKGROUND_ACTIVE.getSpriteId());
			background.revalidate();
		};
		JavaScriptCallback onMouseLeave = event ->
		{
			background.setSpriteId(tab.getTag().equals(bankTagsService == null ? null : bankTagsService.getActiveTag())
				? TabSprites.TAB_BACKGROUND_ACTIVE.getSpriteId() : TabSprites.TAB_BACKGROUND.getSpriteId());
			background.revalidate();
		};
		background.setOnMouseOverListener(onMouseOver);
		background.setOnMouseLeaveListener(onMouseLeave);
		icon.setOnMouseOverListener(onMouseOver);
		icon.setOnMouseLeaveListener(onMouseLeave);
	}

	private void createAddTabTile(String groupId, int x, int y)
	{
		// The action is the menu option; leave the widget target empty so RuneLite
		// does not render the option twice as "New tag tab New tag tab".
		Widget addTab = createGraphic(parent, "", TabSprites.NEW_TAB.getSpriteId(), -1,
			TILE_WIDTH, TILE_HEIGHT, x, y);
		addTabWidgets.add(addTab);
		addTab.setAction(1, NEW_TAB);
		addTab.setHasListener(true);
		addTab.setNoClickThrough(true);
		addTab.setOnOpListener((JavaScriptCallback) event -> createBankTab(groupId));
		registerGroupDropTarget(addTab, groupId);
		registerScrollable(addTab, y);
		addTab.revalidate();
	}

	private void addTabActions(Widget widget, String tag)
	{
		widget.setAction(MENU_VIEW, VIEW_TAB);
		widget.setAction(MENU_CHANGE_ICON, CHANGE_ICON);
		widget.setAction(MENU_LAYOUT, hasLayout(tag) ? DISABLE_LAYOUT : ENABLE_LAYOUT);
		widget.setAction(MENU_EXPORT, EXPORT_TAB);
		widget.setAction(MENU_RENAME, RENAME_TAB);
		widget.setAction(MENU_DELETE, DELETE_TAB);
		widget.setHasListener(true);
		widget.setOnOpListener((JavaScriptCallback) event -> handleTabOp(event, tag));
	}

	private void addDragOptions(Widget widget)
	{
		widget.setClickMask(widget.getClickMask() | WidgetConfig.DRAG | WidgetConfig.DRAG_ON);
		widget.setDragDeadTime(5);
		widget.setDragDeadZone(5);
		widget.setItemQuantity(10000);
		widget.setItemQuantityMode(ItemQuantityMode.NEVER);
	}

	private void handleTabOp(ScriptEvent event, String tag)
	{
		switch (event.getOp() - 1)
		{
			case MENU_VIEW:
				// The callback runs while the client is traversing the clicked widget.
				// Defer both the layer removal and Bank Tags transition until that
				// traversal has completed, otherwise the widget array can be resized
				// underneath the client and crash.
				clientThread.invokeLater(() -> openBankTagFromExpandedView(tag));
				break;
			case MENU_CHANGE_ICON:
				itemSearch.tooltipText(CHANGE_ICON + " (" + tag + ")")
					.onItemSelected(itemId -> setTabIcon(tag, itemId))
					.build();
				break;
			case MENU_LAYOUT:
				clientThread.invokeLater(() -> toggleLayout(tag));
				break;
			case MENU_EXPORT:
				exportTab(tag);
				break;
			case MENU_RENAME:
				renameTab(tag);
				break;
			case MENU_DELETE:
				clientThread.invokeLater(() -> deleteTab(tag));
				break;
			default:
				break;
		}
	}

	private void openBankTagFromExpandedView(String tag)
	{
		setExpandedViewVisible(false);
		client.setVarbit(VarbitID.BANK_CURRENTTAB, 0);
		if (bankTagsService != null)
		{
			bankTagsService.openBankTag(tag, BankTagsService.OPTION_ALLOW_MODIFICATIONS);
		}
		client.playSoundEffect(SoundEffectID.UI_BOOP);
	}

	private void handleGroupOp(ScriptEvent event)
	{
		String groupId = groupHeaders.get(event.getSource());
		BankTabGroup group = groupManager.find(groupId);
		if (group == null)
		{
			return;
		}

		switch (event.getOp() - 1)
		{
			case GROUP_MENU_RENAME:
				renameGroup(group);
				break;
			case GROUP_MENU_DELETE:
				chatboxPanelManager.openTextMenuInput("Delete bank tab group " + group.getName())
					.option("1. Delete group", () -> clientThread.invoke(() ->
					{
						groupManager.delete(group.getId());
						rebuildExpandedView();
					}))
					.option("2. Cancel", () -> { })
					.build();
				break;
			case GROUP_MENU_COLLAPSE:
				groupManager.toggleCollapsed(group.getId());
				clientThread.invokeLater(this::rebuildExpandedView);
				break;
			default:
				break;
		}
	}

	private void createGroup()
	{
		chatboxPanelManager.openTextInput("Group name")
			.onDone((Consumer<String>) name -> clientThread.invoke(() ->
			{
				if (groupManager.create(name) != null)
				{
					rebuildExpandedView();
				}
			}))
			.build();
	}

	private void createBankTab(String groupId)
	{
		chatboxPanelManager.openTextInput("Tag name")
			.addCharValidator(c -> "</>:".indexOf(c) == -1)
			.onDone((Consumer<String>) name -> clientThread.invoke(() -> createBankTabNow(name, groupId)))
			.build();
	}

	private void createBankTabNow(String name, String groupId)
	{
		String tag = name == null ? "" : Text.standardize(name.trim());
		if (tag.isEmpty() || tabManager.find(tag) != null)
		{
			return;
		}

		TagTab newTab = new TagTab();
		newTab.setTag(tag);
		newTab.setIconItemId(ItemID.SPADE);
		tabManager.add(newTab);
		tabManager.save();
		if (groupId != null)
		{
			groupManager.assign(tag, groupId);
		}
		refreshBankTagsSidebar();
		clientThread.invokeLater(this::refresh);
	}

	private void refreshBankTagsSidebar()
	{
		// TabManager.save updates the shared Bank Tags configuration, but the
		// sidebar is a live widget tree and only rebuilds on bank initialization.
		// Use the public plugin lifecycle to request that rebuild instead of
		// reaching into Bank Tags' internal TabInterface implementation.
		removeOwnedWidgets();
		parent = null;

		if (bankTagsRefreshPending || !pluginManager.isPluginActive(bankTagsPlugin))
		{
			clientThread.invokeLater(this::refresh);
			return;
		}

		bankTagsRefreshPending = true;
		SwingUtilities.invokeLater(this::restartBankTagsPlugin);
	}

	private void restartBankTagsPlugin()
	{
		try
		{
			pluginManager.stopPlugin(bankTagsPlugin);
		}
		catch (PluginInstantiationException ex)
		{
			log.warn("Unable to restart Bank Tags after changing its tabs", ex);
			bankTagsRefreshPending = false;
			clientThread.invokeLater(this::refresh);
			return;
		}

		// BankTagsPlugin performs its deinitialization on the client thread during
		// shutdown. Start it again only after that work has completed so its
		// TabInterface can safely rebuild the sidebar from the saved configuration.
		clientThread.invokeLater(() -> SwingUtilities.invokeLater(() ->
		{
			try
			{
				if (pluginManager.isPluginEnabled(bankTagsPlugin))
				{
					pluginManager.startPlugin(bankTagsPlugin);
				}
			}
			catch (PluginInstantiationException ex)
			{
				log.warn("Unable to start Bank Tags after changing its tabs", ex);
			}
			finally
			{
				bankTagsRefreshPending = false;
				clientThread.invokeLater(this::refresh);
			}
		}));
	}

	private void renameGroup(BankTabGroup group)
	{
		chatboxPanelManager.openTextInput("Enter new name for bank tab group \"" + group.getName() + "\":")
			.onDone((Consumer<String>) name -> clientThread.invoke(() ->
			{
				if (groupManager.rename(group.getId(), name))
				{
					rebuildExpandedView();
				}
			}))
			.build();
	}

	private void setTabIcon(String tag, int itemId)
	{
		TagTab tab = tabManager.find(tag);
		if (tab != null)
		{
			tab.setIconItemId(itemId);
			tabManager.save();
		}
		else
		{
			configManager.setConfiguration(BANKTAGS_GROUP, TAG_ICON_PREFIX + Text.standardize(tag), itemId);
		}

		refreshBankTagsSidebar();
		clientThread.invokeLater(this::refresh);
	}

	private void toggleLayout(String tag)
	{
		String key = TAG_LAYOUT_PREFIX + Text.standardize(tag);
		if (configManager.getConfiguration(BANKTAGS_GROUP, key) == null)
		{
			configManager.setConfiguration(BANKTAGS_GROUP, key, "");
		}
		else
		{
			configManager.unsetConfiguration(BANKTAGS_GROUP, key);
		}

		if (bankTagsService != null && tag.equals(bankTagsService.getActiveTag()))
		{
			bankTagsService.openBankTag(tag, BankTagsService.OPTION_ALLOW_MODIFICATIONS);
		}
		rebuildExpandedView();
	}

	private void renameTab(String oldTag)
	{
		chatboxPanelManager.openTextInput("Enter new tag name for tag \"" + oldTag + "\":")
			.onDone((Consumer<String>) newTag -> clientThread.invoke(() -> renameTabNow(oldTag, newTag)))
			.build();
	}

	private void renameTabNow(String oldTag, String newTag)
	{
		String oldValue = Text.standardize(oldTag);
		String newValue = newTag == null ? "" : Text.standardize(newTag.trim());
		if (newValue.isEmpty() || oldValue.equals(newValue) || loadTabs().stream().anyMatch(tab -> tab.getTag().equals(newValue)))
		{
			return;
		}

		List<String> tabs = loadTabs().stream().map(BankTab::getTag).collect(Collectors.toList());
		Collections.replaceAll(tabs, oldValue, newValue);
		configManager.setConfiguration(BANKTAGS_GROUP, TAG_TABS_KEY, Text.toCSV(tabs));
		moveConfig(BANKTAGS_GROUP, TAG_ICON_PREFIX + oldValue, TAG_ICON_PREFIX + newValue);
		moveConfig(BANKTAGS_GROUP, TAG_LAYOUT_PREFIX + oldValue, TAG_LAYOUT_PREFIX + newValue);
		rewriteItemTags(oldValue, newValue, false);
		rewriteItemTags(oldValue + "*", newValue + "*", false);
		groupManager.renameTab(oldValue, newValue);

		if (bankTagsService != null && oldValue.equals(bankTagsService.getActiveTag()))
		{
			bankTagsService.openBankTag(newValue, BankTagsService.OPTION_ALLOW_MODIFICATIONS);
		}
		rebuildExpandedView();
	}

	private void deleteTab(String tag)
	{
		List<String> tabs = loadTabs().stream().map(BankTab::getTag)
			.filter(value -> !value.equals(Text.standardize(tag))).collect(Collectors.toList());
		configManager.setConfiguration(BANKTAGS_GROUP, TAG_TABS_KEY, Text.toCSV(tabs));
		configManager.unsetConfiguration(BANKTAGS_GROUP, TAG_ICON_PREFIX + Text.standardize(tag));
		configManager.unsetConfiguration(BANKTAGS_GROUP, TAG_LAYOUT_PREFIX + Text.standardize(tag));
		rewriteItemTags(Text.standardize(tag), null, true);
		rewriteItemTags(Text.standardize(tag) + "*", null, true);
		groupManager.removeTab(tag);
		if (bankTagsService != null && Text.standardize(tag).equals(bankTagsService.getActiveTag()))
		{
			bankTagsService.closeBankTag();
		}
		rebuildExpandedView();
	}

	private void exportTab(String tag)
	{
		List<String> data = new ArrayList<>();
		data.add("banktags");
		data.add("1");
		data.add(tag);
		data.add(String.valueOf(iconFor(tag)));
		for (String key : configManager.getConfigurationKeys(BANKTAGS_GROUP + ".item_"))
		{
			String[] split = key.split("\\.", 2);
			if (split.length != 2)
			{
				continue;
			}
			String values = configManager.getConfiguration(BANKTAGS_GROUP, split[1]);
			if (values != null && Text.fromCSV(values).contains(tag))
			{
				String id = split[1].substring("item_".length());
				data.add(id);
			}
		}
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(Text.toCSV(data)), null);
	}

	private void rewriteItemTags(String oldTag, String newTag, boolean remove)
	{
		for (String key : configManager.getConfigurationKeys(BANKTAGS_GROUP + ".item_"))
		{
			String[] split = key.split("\\.", 2);
			if (split.length != 2)
			{
				continue;
			}
			String stored = configManager.getConfiguration(BANKTAGS_GROUP, split[1]);
			if (stored == null)
			{
				continue;
			}
			List<String> tags = Text.fromCSV(stored);
			boolean changed = false;
			for (int i = 0; i < tags.size(); ++i)
			{
				if (tags.get(i).equals(oldTag))
				{
					tags.set(i, remove ? null : newTag);
					changed = true;
				}
			}
			if (changed)
			{
				configManager.setConfiguration(BANKTAGS_GROUP, split[1], Text.toCSV(tags));
			}
		}
	}

	private void moveConfig(String group, String oldKey, String newKey)
	{
		String value = configManager.getConfiguration(group, oldKey);
		if (value != null)
		{
			configManager.setConfiguration(group, newKey, value);
			configManager.unsetConfiguration(group, oldKey);
		}
	}

	private boolean hasLayout(String tag)
	{
		return configManager.getConfiguration(BANKTAGS_GROUP, TAG_LAYOUT_PREFIX + Text.standardize(tag)) != null;
	}

	private List<BankTab> loadTabs()
	{
		String value = configManager.getConfiguration(BANKTAGS_GROUP, TAG_TABS_KEY);
		if (value == null || value.isEmpty())
		{
			return Collections.emptyList();
		}

		List<BankTab> tabs = new ArrayList<>();
		for (String tag : Text.fromCSV(value))
		{
			String normalized = Text.standardize(tag);
			if (!normalized.isEmpty())
			{
				tabs.add(new BankTab(normalized, iconFor(normalized)));
			}
		}
		return tabs;
	}

	private int iconFor(String tag)
	{
		String value = configManager.getConfiguration(BANKTAGS_GROUP, TAG_ICON_PREFIX + Text.standardize(tag));
		try
		{
			return value == null ? ItemID.SPADE : Integer.parseInt(value);
		}
		catch (NumberFormatException ex)
		{
			return ItemID.SPADE;
		}
	}

	private List<BankTab> tabsForGroup(List<BankTab> tabs, BankTabGroup group)
	{
		List<BankTab> result = new ArrayList<>();
		for (String tag : group.getTabs())
		{
			tabs.stream().filter(tab -> tab.getTag().equals(Text.standardize(tag))).findFirst().ifPresent(result::add);
		}
		return result;
	}

	private List<BankTab> ungroupedTabs(List<BankTab> tabs)
	{
		List<BankTab> available = tabs.stream()
			.filter(tab -> groupManager.getGroupId(tab.getTag()) == null)
			.collect(Collectors.toList());
		List<String> availableTags = available.stream().map(BankTab::getTag).collect(Collectors.toList());
		List<BankTab> ordered = new ArrayList<>();
		for (String tag : groupManager.orderUngrouped(availableTags))
		{
			available.stream().filter(tab -> tab.getTag().equals(tag)).findFirst().ifPresent(ordered::add);
		}
		return ordered;
	}

	private Widget createGraphic(Widget container, String name, int spriteId, int itemId,
		int width, int height, int x, int y)
	{
		Widget widget = expandedLayer.createChild(-1, WidgetType.GRAPHIC);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setOriginalWidth(width);
		widget.setOriginalHeight(height);
		widget.setSpriteId(spriteId);
		if (itemId > -1)
		{
			widget.setItemId(itemId);
			widget.setItemQuantity(-1);
			widget.setBorderType(1);
		}
		widget.setName(name);
		widget.revalidate();
		return widget;
	}

	private Widget createText(String text, int x, int y, int width, int height, Color color)
	{
		Widget widget = expandedLayer.createChild(-1, WidgetType.TEXT);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setOriginalWidth(width);
		widget.setOriginalHeight(height);
		widget.setFontId(FontID.PLAIN_11);
		widget.setText(text);
		widget.setTextColor(color.getRGB() & 0xFFFFFF);
		widget.setTextShadowed(true);
		widget.setXTextAlignment(WidgetTextAlignment.LEFT);
		widget.setYTextAlignment(WidgetTextAlignment.CENTER);
		return widget;
	}

	private void registerGroupDropTarget(Widget widget, String groupId)
	{
		groupDropTargets.put(widget, groupId);
		widget.setClickMask(widget.getClickMask() | WidgetConfig.DRAG_ON);
	}

	private void registerGroupDropArea(String groupId, int x, int width, int top, int bottom)
	{
		groupDropAreas.add(new GroupDropArea(groupId, x, width, top, Math.max(1, bottom - top)));
	}

	private DropTarget findDropTarget(Widget target)
	{
		String tabTag = findMappedAncestor(target, tabWidgets);
		if (tabTag != null)
		{
			return new DropTarget(true, groupManager.getGroupId(tabTag), tabTag);
		}

		for (Widget current = target; current != null; current = current.getParent())
		{
			if (groupDropTargets.containsKey(current))
			{
				return new DropTarget(true, groupDropTargets.get(current), null);
			}
		}

		Point mouse = client.getMouseCanvasPosition();
		Rectangle parentBounds = parent == null ? null : parent.getBounds();
		if (mouse == null || parentBounds == null)
		{
			return DropTarget.NONE;
		}

		int localX = mouse.getX() - parentBounds.x;
		int localY = mouse.getY() - parentBounds.y + expandedScrollOffset;
		for (Map.Entry<Widget, String> entry : tabWidgets.entrySet())
		{
			Rectangle bounds = entry.getKey().getBounds();
			if (bounds != null && bounds.contains(mouse.getX(), mouse.getY()))
			{
				String targetTag = entry.getValue();
				return new DropTarget(true, groupManager.getGroupId(targetTag), targetTag);
			}
		}
		for (GroupDropArea area : groupDropAreas)
		{
			if (localX >= area.x && localX < area.x + area.width
				&& localY >= area.y && localY < area.y + area.height)
			{
				return new DropTarget(true, area.groupId, null);
			}
		}
		return DropTarget.NONE;
	}

	private static String findMappedAncestor(Widget widget, Map<Widget, String> mappings)
	{
		for (Widget current = widget; current != null; current = current.getParent())
		{
			String value = mappings.get(current);
			if (value != null)
			{
				return value;
			}
		}
		return null;
	}

	private static Widget findMappedWidget(Widget widget, Map<Widget, String> mappings)
	{
		for (Widget current = widget; current != null; current = current.getParent())
		{
			if (mappings.containsKey(current))
			{
				return current;
			}
		}
		return null;
	}

	private void registerScrollable(Widget widget, int baseY)
	{
		scrollBaseY.put(widget, baseY);
		widget.setOnScrollWheelListener((JavaScriptCallback) event -> scrollExpandedView(event.getMouseY()));
	}

	private void scrollExpandedView(int direction)
	{
		if (panel == null || direction == 0)
		{
			return;
		}
		int viewportBottom = panel.getOriginalY() + panel.getOriginalHeight();
		int maxScroll = Math.max(0, expandedContentBottom - viewportBottom + 8);
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
		int maxScroll = Math.max(0, expandedContentBottom - viewportBottom + 8);
		expandedScrollOffset = Math.max(0, Math.min(maxScroll, expandedScrollOffset));
		for (Map.Entry<Widget, Integer> entry : scrollBaseY.entrySet())
		{
			Widget widget = entry.getKey();
			int y = entry.getValue() - expandedScrollOffset;
			widget.setOriginalY(y);
			// Widgets are siblings of the panel rather than children of a clipping
			// layer, so hide them before they can partially render over the bank
			// footer or scrollbar.
			widget.setHidden(y < viewportTop || y + widget.getOriginalHeight() > viewportBottom);
			widget.revalidate();
		}
	}

	private void updateToggle()
	{
		if (originalNewTab != null && originalNewTabStateCaptured)
		{
			originalNewTab.setHidden(config.enabled());
			originalNewTab.revalidate();
		}
		if (toggle == null)
		{
			return;
		}
		toggle.setHidden(!config.enabled());
		toggleIcon.setHidden(!config.enabled());
		toggleHitbox.setHidden(!config.enabled());
		toggleLayer.setHidden(!config.enabled());
		toggle.setSpriteId(expandedViewVisible
			? TabSprites.TAB_BACKGROUND_ACTIVE.getSpriteId()
			: TabSprites.TAB_BACKGROUND.getSpriteId());
		toggle.revalidate();
	}

	private void hideBankItems()
	{
		Widget bankItems = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (bankItems == null || bankItems.getChildren() == null)
		{
			return;
		}
		for (Widget child : bankItems.getChildren())
		{
			if (child != null)
			{
				hiddenBankItems.putIfAbsent(child, child.isHidden());
				child.setHidden(true);
			}
		}
	}

	private void restoreBankItems()
	{
		for (Map.Entry<Widget, Boolean> entry : hiddenBankItems.entrySet())
		{
			if (entry.getKey() != null)
			{
				entry.getKey().setHidden(entry.getValue());
			}
		}
		hiddenBankItems.clear();
	}

	private void removeExpandedWidgets()
	{
		// Hide tracked add-tab widgets first so stale menu hitboxes cannot survive
		// a rebuild, then remove dynamic children from every layer created by this
		// plugin. The layer registry matters because Bank Tags can rebuild its
		// parent and leave an older plugin layer detached from the current field.
		for (Widget addTab : addTabWidgets)
		{
			if (addTab != null)
			{
				addTab.setHidden(true);
			}
		}
		for (Widget layer : expandedLayers)
		{
			if (layer != null)
			{
				layer.deleteAllChildren();
				layer.setHidden(true);
				layer.revalidate();
			}
		}
		clearExpandedWidgetReferences();
	}

	private void clearExpandedWidgetReferences()
	{
		panel = null;
		tabWidgets.clear();
		groupHeaders.clear();
		groupDropTargets.clear();
		groupDropAreas.clear();
		scrollBaseY.clear();
	}

	private void removeOwnedWidgets()
	{
		removeExpandedWidgets();
		removeExpandedLayer();
		removeToggleOverlay();
		restoreOriginalNewTab();
		clearExpandedWidgetReferences();
		expandedLayers.clear();
		addTabWidgets.clear();
	}

	private void restoreBankTitle()
	{
		if (previousBankTitle == null)
		{
			return;
		}
		Widget bankTitle = client.getWidget(InterfaceID.Bankmain.TITLE);
		if (bankTitle != null)
		{
			bankTitle.setText(previousBankTitle);
			bankTitle.revalidate();
		}
		previousBankTitle = null;
	}

	private void updateExpandedTitle()
	{
		if (!expandedViewVisible)
		{
			return;
		}
		Widget bankTitle = client.getWidget(InterfaceID.Bankmain.TITLE);
		if (bankTitle != null)
		{
			bankTitle.setText(EXPANDED_TITLE);
			bankTitle.revalidate();
		}
	}

	private static boolean equalsNullable(Object first, Object second)
	{
		return first == null ? second == null : first.equals(second);
	}

	private static final class DropTarget
	{
		private static final DropTarget NONE = new DropTarget(false, null, null);

		private final boolean matched;
		private final String groupId;
		private final String tabTag;

		private DropTarget(boolean matched, String groupId, String tabTag)
		{
			this.matched = matched;
			this.groupId = groupId;
			this.tabTag = tabTag;
		}
	}

	private static final class GroupDropArea
	{
		private final String groupId;
		private final int x;
		private final int y;
		private final int width;
		private final int height;

		private GroupDropArea(String groupId, int x, int width, int y, int height)
		{
			this.groupId = groupId;
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
	}
}
