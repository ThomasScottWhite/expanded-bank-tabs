package com.expandedbanktagsviewer;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns all widget-to-domain mappings for the current expanded-view render.
 * Clearing this registry invalidates every old drag, drop, tab, and scroll
 * mapping together so render generations cannot overlap.
 */
final class ExpandedWidgetRegistry
{
	private static final Logger log = LoggerFactory.getLogger(ExpandedWidgetRegistry.class);
	private final Map<Widget, String> tabWidgets = new IdentityHashMap<>();
	private final Map<Widget, String> groupHeaders = new IdentityHashMap<>();
	private final Map<Widget, String> groupDropTargets = new IdentityHashMap<>();
	private final Map<Widget, Integer> scrollBaseY = new IdentityHashMap<>();
	private final List<GroupDropArea> groupDropAreas = new ArrayList<>();
	private final Set<Widget> ownedWidgets = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

	void registerOwned(Widget widget)
	{
		ownedWidgets.add(widget);
	}

	void registerTab(Widget widget, String tag)
	{
		registerOwned(widget);
		tabWidgets.put(widget, tag);
	}

	String findTab(Widget widget)
	{
		return findMappedAncestor(widget, tabWidgets);
	}

	void registerGroupHeader(Widget widget, String groupId)
	{
		registerOwned(widget);
		groupHeaders.put(widget, groupId);
	}

	Widget findGroupHeader(Widget widget)
	{
		return findMappedWidget(widget, groupHeaders);
	}

	String getGroupHeaderId(Widget widget)
	{
		return groupHeaders.get(widget);
	}

	void registerGroupDropTarget(Widget widget, String groupId)
	{
		registerOwned(widget);
		groupDropTargets.put(widget, groupId);
		widget.setClickMask(widget.getClickMask() | WidgetConfig.DRAG_ON);
	}

	void registerGroupDropArea(String groupId, int x, int width, int top, int bottom)
	{
		groupDropAreas.add(new GroupDropArea(groupId, x, width, top, Math.max(1, bottom - top)));
	}

	DropTarget findDropTarget(Widget target, Point mouse, Rectangle parentBounds, int scrollOffset,
		Function<String, String> groupForTab)
	{
		String tabTag = findTab(target);
		if (tabTag != null)
		{
			log.trace("Drop target resolved from widget mapping: tab='{}'", tabTag);
			return DropTarget.forTab(groupForTab.apply(tabTag), tabTag);
		}

		for (Widget current = target; current != null; current = current.getParent())
		{
			if (groupDropTargets.containsKey(current))
			{
				log.trace("Drop target resolved from group widget mapping: group={}",
					groupDropTargets.get(current));
				return DropTarget.forGroup(groupDropTargets.get(current));
			}
		}

		if (mouse == null || parentBounds == null)
		{
			log.trace("Drop target unresolved because pointer or parent bounds are unavailable");
			return DropTarget.none();
		}

		int localX = mouse.getX() - parentBounds.x;
		int localY = mouse.getY() - parentBounds.y + scrollOffset;
		for (Map.Entry<Widget, String> entry : tabWidgets.entrySet())
		{
			Rectangle bounds = entry.getKey().getBounds();
			if (bounds != null && bounds.contains(mouse.getX(), mouse.getY()))
			{
				String matchedTag = entry.getValue();
				log.trace("Drop target resolved by tab bounds fallback: tab='{}'", matchedTag);
				return DropTarget.forTab(groupForTab.apply(matchedTag), matchedTag);
			}
		}
		for (GroupDropArea area : groupDropAreas)
		{
			if (area.contains(localX, localY))
			{
				log.trace("Drop target resolved by group-area fallback: group={}, local=({}, {})",
					area.groupId, localX, localY);
				return DropTarget.forGroup(area.groupId);
			}
		}
		log.trace("No drop target matched pointer local=({}, {})", localX, localY);
		return DropTarget.none();
	}

	void registerScrollable(Widget widget, int baseY)
	{
		registerOwned(widget);
		scrollBaseY.put(widget, baseY);
	}

	void forEachScrollable(BiConsumer<Widget, Integer> consumer)
	{
		scrollBaseY.forEach(consumer);
	}

	int getOwnedWidgetCount()
	{
		return ownedWidgets.size();
	}

	void retireOwnedWidgets()
	{
		log.debug("Retiring {} owned expanded-view widgets", ownedWidgets.size());
		for (Widget widget : ownedWidgets)
		{
			if (widget != null)
			{
				widget.clearActions();
				widget.setHidden(true);
				widget.setOriginalWidth(0);
				widget.setOriginalHeight(0);
				widget.revalidate();
			}
		}
	}

	void clear()
	{
		log.trace("Clearing widget registry: tabs={}, headers={}, dropTargets={}, areas={}, scrollables={}, owned={}",
			tabWidgets.size(), groupHeaders.size(), groupDropTargets.size(), groupDropAreas.size(),
			scrollBaseY.size(), ownedWidgets.size());
		tabWidgets.clear();
		groupHeaders.clear();
		groupDropTargets.clear();
		groupDropAreas.clear();
		scrollBaseY.clear();
		ownedWidgets.clear();
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

	static final class DropTarget
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

		static DropTarget none()
		{
			return NONE;
		}

		static DropTarget forGroup(String groupId)
		{
			return new DropTarget(true, groupId, null);
		}

		static DropTarget forTab(String groupId, String tabTag)
		{
			return new DropTarget(true, groupId, tabTag);
		}

		boolean isMatched()
		{
			return matched;
		}

		String getGroupId()
		{
			return groupId;
		}

		String getTabTag()
		{
			return tabTag;
		}
	}

	private static final class GroupDropArea
	{
		private final String groupId;
		private final int x;
		private final int width;
		private final int y;
		private final int height;

		private GroupDropArea(String groupId, int x, int width, int y, int height)
		{
			this.groupId = groupId;
			this.x = x;
			this.width = width;
			this.y = y;
			this.height = height;
		}

		private boolean contains(int localX, int localY)
		{
			return localX >= x && localX < x + width && localY >= y && localY < y + height;
		}
	}
}
