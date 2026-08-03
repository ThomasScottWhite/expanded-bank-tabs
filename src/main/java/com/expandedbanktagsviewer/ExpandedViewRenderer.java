package com.expandedbanktagsviewer;

import java.awt.Color;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Constants;
import net.runelite.api.FontID;
import net.runelite.api.ScriptEvent;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetConfig;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.plugins.banktags.tabs.TabSprites;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.util.ColorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates one complete generation of expanded-view widgets. */
@Singleton
final class ExpandedViewRenderer
{
	private static final Logger log = LoggerFactory.getLogger(ExpandedViewRenderer.class);
	static final int MENU_VIEW = 1;
	static final int MENU_CHANGE_ICON = 2;
	static final int MENU_LAYOUT = 3;
	static final int MENU_EXPORT = 4;
	static final int MENU_RENAME = 5;
	static final int MENU_DELETE = 6;
	static final int GROUP_MENU_RENAME = 1;
	static final int GROUP_MENU_DELETE = 2;
	static final int GROUP_MENU_COLLAPSE = 3;

	static final String CHANGE_ICON = "Change icon";

	private static final String VIEW_TAB = "View tag tab";
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

	private final BankTagsAdapter bankTags;
	private final BankTabGroupManager groupManager;

	@Inject
	ExpandedViewRenderer(BankTagsAdapter bankTags, BankTabGroupManager groupManager)
	{
		this.bankTags = bankTags;
		this.groupManager = groupManager;
	}

	RenderResult render(Widget layer, Widget bankContent, List<ExpandedViewGroup> groups,
		ExpandedWidgetRegistry registry, Listener listener)
	{
		int panelX = ExpandedViewLayout.panelX();
		int width = ExpandedViewLayout.panelWidth(bankContent.getWidth());
		log.debug("Rendering expanded widgets: layer={}, bankSize={}x{}, panelX={}, panelWidth={}, groups={}",
			widgetIdentity(layer), bankContent.getWidth(), bankContent.getHeight(), panelX, width, groups.size());
		Widget panel = layer.createChild(-1, WidgetType.RECTANGLE);
		panel.setOriginalX(panelX);
		panel.setOriginalY(ExpandedViewLayout.PANEL_TOP);
		panel.setOriginalWidth(width);
		panel.setOriginalHeight(ExpandedViewLayout.panelHeight(bankContent.getHeight()));
		panel.setFilled(false);
		panel.setOpacity(255);
		panel.setNoClickThrough(true);
		panel.setNoScrollThrough(true);
		panel.setHasListener(true);
		panel.setOnOpListener((JavaScriptCallback) event -> { });
		panel.setOnScrollWheelListener((JavaScriptCallback) event -> listener.onScroll(event.getMouseY()));
		panel.revalidate();
		registry.registerOwned(panel);

		int y = ExpandedViewLayout.firstGroupY();
		boolean renderedGroup = false;
		for (ExpandedViewGroup group : groups)
		{
			log.trace("Rendering group: id={}, name='{}', tabs={}, collapsed={}, y={}",
				group.getId(), group.getName(), group.getTabs().size(), group.isCollapsed(), y);
			if (renderedGroup)
			{
				y += ExpandedViewLayout.GROUP_SPACING;
			}
			y = renderGroup(layer, group, panelX, width, y, registry, listener);
			renderedGroup = true;
		}

		int createY = y + 8;
		Widget createGroup = createText(layer, CREATE_GROUP, panelX + 10, createY,
			Math.max(1, width - 20), 26, JagexColors.MENU_TARGET);
		createGroup.setFontId(FontID.BOLD_12);
		createGroup.setXTextAlignment(WidgetTextAlignment.CENTER);
		createGroup.setYTextAlignment(WidgetTextAlignment.CENTER);
		createGroup.setAction(1, CREATE_GROUP);
		createGroup.setHasListener(true);
		createGroup.setNoClickThrough(true);
		createGroup.setOnOpListener((JavaScriptCallback) event -> listener.onCreateGroup());
		registerScrollable(createGroup, createY, registry, listener);
		createGroup.revalidate();
		int contentBottom = createY + createGroup.getOriginalHeight();
		log.debug("Expanded widget render complete: panel={}, contentBottom={}, trackedWidgets={}",
			widgetIdentity(panel), contentBottom, registry.getOwnedWidgetCount());
		return new RenderResult(panel, contentBottom);
	}

	private int renderGroup(Widget layer, ExpandedViewGroup viewGroup, int panelX, int width, int y,
		ExpandedWidgetRegistry registry, Listener listener)
	{
		String groupId = viewGroup.getId();
		int groupTop = y;
		Widget header = createText(layer, viewGroup.getName(), panelX + 10, y, Math.max(1, width - 20),
			ExpandedViewLayout.GROUP_HEADER_HEIGHT, JagexColors.DARK_ORANGE_INTERFACE_TEXT);
		header.setFontId(FontID.BOLD_12);
		header.setXTextAlignment(WidgetTextAlignment.CENTER);
		header.setYTextAlignment(WidgetTextAlignment.CENTER);
		registry.registerGroupDropTarget(header, groupId);
		registry.registerGroupHeader(header, groupId);
		addDragOptions(header);
		header.setHasListener(true);
		if (groupId != null)
		{
			BankTabGroup group = groupManager.find(groupId);
			header.setAction(GROUP_MENU_RENAME, RENAME_GROUP);
			header.setAction(GROUP_MENU_DELETE, DELETE_GROUP);
			header.setAction(GROUP_MENU_COLLAPSE,
				group != null && group.isCollapsed() ? EXPAND_GROUP : COLLAPSE_GROUP);
			header.setNoClickThrough(true);
			header.setOnOpListener((JavaScriptCallback) listener::onGroupOperation);
		}
		header.revalidate();
		registerScrollable(header, y, registry, listener);
		y += ExpandedViewLayout.GROUP_HEADER_HEIGHT + 4;

		if (viewGroup.isCollapsed())
		{
			registry.registerGroupDropArea(groupId, panelX, width, groupTop, y);
			return y;
		}

		int startX = ExpandedViewLayout.contentStartX(panelX);
		int columns = ExpandedViewLayout.columns(width);
		List<BankTab> tabs = viewGroup.getTabs();
		for (int i = 0; i < tabs.size(); ++i)
		{
			createTabTile(layer, tabs.get(i), groupId,
				ExpandedViewLayout.tileX(startX, i, columns),
				ExpandedViewLayout.tileY(y, i, columns), registry, listener);
		}

		int addIndex = tabs.size();
		createAddTabTile(layer, groupId,
			ExpandedViewLayout.tileX(startX, addIndex, columns),
			ExpandedViewLayout.tileY(y, addIndex, columns), registry, listener);
		int groupBottom = ExpandedViewLayout.groupBottom(y, addIndex + 1, columns);
		registry.registerGroupDropArea(groupId, panelX, width, groupTop, groupBottom);
		return groupBottom;
	}

	private void createTabTile(Widget layer, BankTab tab, String groupId, int x, int y,
		ExpandedWidgetRegistry registry, Listener listener)
	{
		String target = ColorUtil.wrapWithColorTag(tab.getTag(), JagexColors.MENU_TARGET);
		int backgroundSprite = tab.getTag().equals(bankTags.getActiveTag())
			? TabSprites.TAB_BACKGROUND_ACTIVE.getSpriteId() : TabSprites.TAB_BACKGROUND.getSpriteId();
		Widget background = createGraphic(layer, target, backgroundSprite, -1,
			ExpandedViewLayout.TILE_WIDTH, ExpandedViewLayout.TILE_HEIGHT, x, y);
		addTabActions(background, tab.getTag(), listener);
		addDragOptions(background);
		registry.registerTab(background, tab.getTag());
		registry.registerGroupDropTarget(background, groupId);
		registerScrollable(background, y, registry, listener);

		Widget icon = createGraphic(layer, target, -1, tab.getIconItemId(),
			Constants.ITEM_SPRITE_WIDTH, Constants.ITEM_SPRITE_HEIGHT, x + 3, y + 4);
		registry.registerTab(icon, tab.getTag());
		icon.setClickMask(0);
		icon.setNoClickThrough(false);
		registerScrollable(icon, y + 4, registry, listener);

		background.setOnMouseOverListener((JavaScriptCallback) event ->
		{
			background.setSpriteId(TabSprites.TAB_BACKGROUND_ACTIVE.getSpriteId());
			background.revalidate();
		});
		background.setOnMouseLeaveListener((JavaScriptCallback) event ->
		{
			background.setSpriteId(tab.getTag().equals(bankTags.getActiveTag())
				? TabSprites.TAB_BACKGROUND_ACTIVE.getSpriteId() : TabSprites.TAB_BACKGROUND.getSpriteId());
			background.revalidate();
		});
	}

	private void createAddTabTile(Widget layer, String groupId, int x, int y,
		ExpandedWidgetRegistry registry, Listener listener)
	{
		Widget addTab = createGraphic(layer, "", TabSprites.NEW_TAB.getSpriteId(), -1,
			ExpandedViewLayout.TILE_WIDTH, ExpandedViewLayout.TILE_HEIGHT, x, y);
		addTab.setAction(1, NEW_TAB);
		addTab.setHasListener(true);
		addTab.setNoClickThrough(true);
		addTab.setOnOpListener((JavaScriptCallback) event -> listener.onCreateTab(groupId));
		registry.registerGroupDropTarget(addTab, groupId);
		registerScrollable(addTab, y, registry, listener);
		addTab.revalidate();
	}

	private void addTabActions(Widget widget, String tag, Listener listener)
	{
		widget.setAction(MENU_VIEW, VIEW_TAB);
		widget.setAction(MENU_CHANGE_ICON, CHANGE_ICON);
		widget.setAction(MENU_LAYOUT, bankTags.hasLayout(tag) ? DISABLE_LAYOUT : ENABLE_LAYOUT);
		widget.setAction(MENU_EXPORT, EXPORT_TAB);
		widget.setAction(MENU_RENAME, RENAME_TAB);
		widget.setAction(MENU_DELETE, DELETE_TAB);
		widget.setHasListener(true);
		widget.setOnOpListener((JavaScriptCallback) event -> listener.onTabOperation(event, tag));
	}

	private static void addDragOptions(Widget widget)
	{
		widget.setClickMask(widget.getClickMask() | WidgetConfig.DRAG | WidgetConfig.DRAG_ON);
		widget.setDragDeadTime(5);
		widget.setDragDeadZone(5);
		widget.setItemQuantity(10000);
		widget.setItemQuantityMode(ItemQuantityMode.NEVER);
	}

	private static Widget createGraphic(Widget layer, String name, int spriteId, int itemId,
		int width, int height, int x, int y)
	{
		Widget widget = layer.createChild(-1, WidgetType.GRAPHIC);
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

	private static Widget createText(Widget layer, String text, int x, int y, int width, int height, Color color)
	{
		Widget widget = layer.createChild(-1, WidgetType.TEXT);
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

	private static void registerScrollable(Widget widget, int baseY,
		ExpandedWidgetRegistry registry, Listener listener)
	{
		registry.registerScrollable(widget, baseY);
		widget.setOnScrollWheelListener((JavaScriptCallback) event -> listener.onScroll(event.getMouseY()));
	}

	private static String widgetIdentity(Widget widget)
	{
		return widget == null ? "null"
			: widget.getId() + "@" + Integer.toHexString(System.identityHashCode(widget));
	}

	interface Listener
	{
		void onScroll(int direction);
		void onCreateGroup();
		void onCreateTab(String groupId);
		void onTabOperation(ScriptEvent event, String tag);
		void onGroupOperation(ScriptEvent event);
	}

	static final class RenderResult
	{
		private final Widget panel;
		private final int contentBottom;

		private RenderResult(Widget panel, int contentBottom)
		{
			this.panel = panel;
			this.contentBottom = contentBottom;
		}

		Widget getPanel()
		{
			return panel;
		}

		int getContentBottom()
		{
			return contentBottom;
		}
	}
}
