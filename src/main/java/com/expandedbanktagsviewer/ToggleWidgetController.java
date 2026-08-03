package com.expandedbanktagsviewer;

import java.awt.Rectangle;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Point;
import net.runelite.api.ScriptEvent;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.banktags.tabs.TabSprites;

/** Owns the chest toggle and the hidden Bank Tags new-tab button for one bank session. */
@Singleton
final class ToggleWidgetController
{
	static final String TOGGLE_OPTION = "Toggle expanded bank tabs";
	static final String NEW_TAB_OPTION = "New tag tab";

	private static final int TOGGLE_OP = 1;
	private static final int NEW_TAB_OP = 2;

	private final Client client;
	private final ClientThread clientThread;
	private Widget originalNewTab;
	private Widget layerParent;
	private Widget layer;
	private Widget background;
	private Widget icon;
	private Widget hitbox;
	private boolean originalHidden;
	private boolean originalStateCaptured;
	private boolean expanded;
	private boolean enabled;
	private boolean mouseDown;
	private int lastToggleTick = -1;
	private int lastNewTabTick = -1;
	private Listener listener;

	@Inject
	ToggleWidgetController(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;
	}

	void maintain(Widget bankContent, boolean enabled, boolean expanded, Listener listener)
	{
		this.enabled = enabled;
		this.expanded = expanded;
		this.listener = listener;
		if (bankContent == null)
		{
			return;
		}

		Widget liveNewTab = getLiveNewTabWidget(bankContent);
		if (liveNewTab == null)
		{
			removeOverlay();
			return;
		}
		if (originalNewTab != liveNewTab)
		{
			restoreOriginal();
			originalNewTab = liveNewTab;
			originalHidden = originalNewTab.isHidden();
			originalStateCaptured = true;
		}

		originalNewTab.setHidden(enabled && originalStateCaptured ? true : originalHidden);
		originalNewTab.revalidate();
		if (!enabled)
		{
			removeOverlay();
			return;
		}

		Widget newLayerParent = findLayerParent(bankContent);
		if (newLayerParent == null)
		{
			removeOverlay();
			return;
		}
		if (layerParent != newLayerParent)
		{
			removeOverlay();
			layerParent = newLayerParent;
		}

		ensureOverlay();
		position();
		update();
	}

	void pollFallbackClick()
	{
		boolean currentMouseDown = client.getMouseCurrentButton() == 1;
		Point mouse = client.getMouseCanvasPosition();
		Rectangle bounds = hitbox == null ? null : hitbox.getBounds();
		if (currentMouseDown && !mouseDown && mouse != null && bounds != null
			&& bounds.contains(mouse.getX(), mouse.getY()))
		{
			handleToggleClick();
		}
		mouseDown = currentMouseDown;
	}

	boolean owns(Widget widget)
	{
		return widget != null && (widget == layer || widget == background || widget == icon || widget == hitbox);
	}

	boolean handleMenuOption(String option)
	{
		if (TOGGLE_OPTION.equals(option))
		{
			clientThread.invokeLater(this::handleToggleClick);
			return true;
		}
		if (NEW_TAB_OPTION.equals(option))
		{
			clientThread.invokeLater(this::handleNewTabClick);
			return true;
		}
		return false;
	}

	Widget getLayerParent()
	{
		return layerParent;
	}

	void updateState(boolean enabled, boolean expanded)
	{
		this.enabled = enabled;
		this.expanded = expanded;
		if (originalNewTab != null && originalStateCaptured)
		{
			originalNewTab.setHidden(enabled);
			originalNewTab.revalidate();
		}
		update();
	}

	void dispose()
	{
		restoreOriginal();
		removeOverlay();
		listener = null;
		mouseDown = false;
	}

	private void ensureOverlay()
	{
		if (layerParent == null || originalNewTab == null)
		{
			return;
		}
		Widget[] layerChildren = layerParent.getChildren();
		boolean layerAttached = layer != null && layerChildren != null
			&& Arrays.stream(layerChildren).anyMatch(child -> child == layer);
		Widget[] children = layer == null ? null : layer.getChildren();
		boolean childrenAttached = background != null && icon != null && hitbox != null && children != null
			&& Arrays.stream(children).anyMatch(child -> child == background)
			&& Arrays.stream(children).anyMatch(child -> child == icon)
			&& Arrays.stream(children).anyMatch(child -> child == hitbox);
		if (layerAttached && childrenAttached)
		{
			configure();
			return;
		}

		Widget stableParent = layerParent;
		removeOverlay();
		layerParent = stableParent;
		layer = layerParent.createChild(-1, WidgetType.LAYER);
		layer.setOriginalWidth(ExpandedViewLayout.TAB_WIDTH);
		layer.setOriginalHeight(ExpandedViewLayout.TAB_HEIGHT);
		layer.setNoClickThrough(true);

		background = layer.createChild(-1, WidgetType.GRAPHIC);
		background.setOriginalWidth(ExpandedViewLayout.TAB_WIDTH);
		background.setOriginalHeight(ExpandedViewLayout.TAB_HEIGHT);
		background.setSpriteId(TabSprites.TAB_BACKGROUND.getSpriteId());
		background.setItemId(-1);
		background.setItemQuantity(-1);
		background.setBorderType(0);
		background.setName("");
		background.setHasListener(true);
		background.setNoClickThrough(false);
		background.setOnMouseOverListener((JavaScriptCallback) event -> setActiveSprite());
		background.setOnMouseLeaveListener((JavaScriptCallback) event -> update());

		icon = layer.createChild(-1, WidgetType.GRAPHIC);
		icon.setOriginalX(3);
		icon.setOriginalY(4);
		icon.setOriginalWidth(Constants.ITEM_SPRITE_WIDTH);
		icon.setOriginalHeight(Constants.ITEM_SPRITE_HEIGHT);
		icon.setSpriteId(-1);
		icon.setItemId(ItemID.BCS_CHEST);
		icon.setItemQuantity(-1);
		icon.setBorderType(1);
		icon.setClickMask(0);
		icon.setName("");
		icon.setNoClickThrough(false);
		icon.revalidate();

		hitbox = layer.createChild(-1, WidgetType.GRAPHIC);
		hitbox.setOriginalWidth(ExpandedViewLayout.TAB_WIDTH);
		hitbox.setOriginalHeight(ExpandedViewLayout.TAB_HEIGHT);
		hitbox.setSpriteId(-1);
		hitbox.setItemId(-1);
		hitbox.setItemQuantity(-1);
		hitbox.setName("");
		hitbox.setAction(TOGGLE_OP, TOGGLE_OPTION);
		hitbox.setAction(NEW_TAB_OP, NEW_TAB_OPTION);
		hitbox.setHasListener(true);
		hitbox.setNoClickThrough(true);
		hitbox.setOnOpListener((JavaScriptCallback) this::handleOperation);
		hitbox.setOnMouseOverListener((JavaScriptCallback) event -> setActiveSprite());
		hitbox.setOnMouseLeaveListener((JavaScriptCallback) event -> update());
		hitbox.revalidate();
		layer.revalidate();
	}

	private void configure()
	{
		background.setName("");
		background.setHasListener(true);
		background.setNoClickThrough(false);
		background.setItemId(-1);
		background.setItemQuantity(-1);
		background.setBorderType(0);
		icon.setItemId(ItemID.BCS_CHEST);
		icon.setItemQuantity(-1);
		icon.setClickMask(0);
		icon.setNoClickThrough(false);
		hitbox.setAction(TOGGLE_OP, TOGGLE_OPTION);
		hitbox.setAction(NEW_TAB_OP, NEW_TAB_OPTION);
		hitbox.setHasListener(true);
		hitbox.setNoClickThrough(true);
		update();
	}

	private void update()
	{
		if (background == null)
		{
			return;
		}
		boolean hidden = !enabled;
		layer.setHidden(hidden);
		background.setHidden(hidden);
		icon.setHidden(hidden);
		hitbox.setHidden(hidden);
		background.setSpriteId(expanded
			? TabSprites.TAB_BACKGROUND_ACTIVE.getSpriteId() : TabSprites.TAB_BACKGROUND.getSpriteId());
		background.revalidate();
		icon.revalidate();
		hitbox.revalidate();
		layer.revalidate();
	}

	private void setActiveSprite()
	{
		if (background != null)
		{
			background.setSpriteId(TabSprites.TAB_BACKGROUND_ACTIVE.getSpriteId());
			background.revalidate();
		}
	}

	private void position()
	{
		if (layer == null || layerParent == null || originalNewTab == null)
		{
			return;
		}
		Point relative = WidgetGeometry.relativeLocation(originalNewTab, layerParent);
		if (relative != null)
		{
			layer.setOriginalX(relative.getX());
			layer.setOriginalY(relative.getY());
		}
		else
		{
			Point buttonLocation = originalNewTab.getCanvasLocation();
			Point parentLocation = layerParent.getCanvasLocation();
			if (buttonLocation == null || parentLocation == null)
			{
				return;
			}
			layer.setOriginalX(buttonLocation.getX() - parentLocation.getX());
			layer.setOriginalY(buttonLocation.getY() - parentLocation.getY());
		}
		layer.setOriginalWidth(ExpandedViewLayout.TAB_WIDTH);
		layer.setOriginalHeight(ExpandedViewLayout.TAB_HEIGHT);
		layer.revalidate();
	}

	private Widget getLiveNewTabWidget(Widget bankContent)
	{
		Widget[] children = bankContent.getChildren();
		return children == null || children.length <= 3 ? null : children[3];
	}

	private Widget findLayerParent(Widget bankContent)
	{
		Widget found = WidgetGeometry.findLayerAncestor(client.getWidget(InterfaceID.Bankmain.UNIVERSE));
		if (found != null && found != bankContent)
		{
			return found;
		}
		return WidgetGeometry.findLayerAncestor(bankContent.getParent());
	}

	private void restoreOriginal()
	{
		if (originalNewTab != null && originalStateCaptured)
		{
			originalNewTab.setHidden(originalHidden);
			originalNewTab.revalidate();
		}
		originalNewTab = null;
		originalStateCaptured = false;
	}

	private void removeOverlay()
	{
		if (layer != null)
		{
			layer.setHidden(true);
			layer.revalidate();
		}
		layer = null;
		background = null;
		icon = null;
		hitbox = null;
		layerParent = null;
	}

	private void handleOperation(ScriptEvent event)
	{
		switch (event.getOp() - 1)
		{
			case TOGGLE_OP:
				handleToggleClick();
				break;
			case NEW_TAB_OP:
				handleNewTabClick();
				break;
			default:
				break;
		}
	}

	private void handleToggleClick()
	{
		int tick = client.getTickCount();
		if (lastToggleTick != tick && listener != null)
		{
			lastToggleTick = tick;
			listener.toggleExpandedView();
		}
	}

	private void handleNewTabClick()
	{
		int tick = client.getTickCount();
		if (lastNewTabTick != tick && listener != null)
		{
			lastNewTabTick = tick;
			listener.createBankTab();
		}
	}

	interface Listener
	{
		void toggleExpandedView();
		void createBankTab();
	}
}
