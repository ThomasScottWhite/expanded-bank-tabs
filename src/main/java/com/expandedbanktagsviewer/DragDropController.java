package com.expandedbanktagsviewer;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.events.WidgetDrag;
import net.runelite.api.widgets.Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Resolves expanded-view tab/group drops without changing Bank Tags sidebar order. */
@Singleton
final class DragDropController
{
	private static final Logger log = LoggerFactory.getLogger(DragDropController.class);
	private final Client client;
	private final BankTabGroupManager groupManager;

	@Inject
	DragDropController(Client client, BankTabGroupManager groupManager)
	{
		this.client = client;
		this.groupManager = groupManager;
	}

	void handle(WidgetDrag event, boolean visible, ExpandedWidgetRegistry registry,
		Widget parent, int scrollOffset, ExpandedScrollbarController scrollbar, Runnable rebuild)
	{
		if (!visible)
		{
			log.debug("Ignoring WidgetDrag because expanded view is hidden");
			return;
		}

		Widget dragged = client.getDraggedWidget();
		Widget target = client.getDraggedOnWidget();
		if (client.getMouseCurrentButton() != 0)
		{
			return;
		}
		if (dragged == null)
		{
			log.debug("Ignoring released WidgetDrag because no source widget is available");
			return;
		}
		if (scrollbar.handleDrag(dragged))
		{
			log.debug("WidgetDrag was handled by the expanded scrollbar");
			return;
		}

		ExpandedWidgetRegistry.DropTarget dropTarget = registry.findDropTarget(target,
			client.getMouseCanvasPosition(), parent == null ? null : parent.getBounds(), scrollOffset,
			groupManager::getGroupId);
		log.debug("Resolved drop: source={}, targetWidget={}, matched={}, targetGroup={}, targetTab={}, scrollOffset={}",
			widgetIdentity(dragged), widgetIdentity(target), dropTarget.isMatched(),
			dropTarget.getGroupId(), dropTarget.getTabTag(), scrollOffset);
		Widget sourceGroupWidget = registry.findGroupHeader(dragged);
		if (sourceGroupWidget != null && dropTarget.isMatched())
		{
			String sourceGroupId = registry.getGroupHeaderId(sourceGroupWidget);
			log.debug("Applying group drop: sourceGroup={} destinationGroup={}",
				sourceGroupId, dropTarget.getGroupId());
			groupManager.move(sourceGroupId, dropTarget.getGroupId());
			client.setDraggedOnWidget(null);
			rebuild.run();
			return;
		}

		String tag = registry.findTab(dragged);
		if (tag == null || !dropTarget.isMatched())
		{
			log.debug("Drop produced no tab mutation: sourceTag={}, matched={}",
				tag, dropTarget.isMatched());
			return;
		}

		boolean changed = false;
		if (dropTarget.getTabTag() != null)
		{
			log.debug("Moving tab '{}' relative to tab '{}'", tag, dropTarget.getTabTag());
			changed = groupManager.moveTab(tag, dropTarget.getTabTag());
		}
		else
		{
			String destination = dropTarget.getGroupId();
			if (!equalsNullable(groupManager.getGroupId(tag), destination))
			{
				log.debug("Assigning tab '{}' to group {}", tag, destination);
				groupManager.assign(tag, destination);
				changed = true;
			}
		}
		client.setDraggedOnWidget(null);
		if (changed)
		{
			log.debug("Tab drop changed persisted order; requesting post-drag render");
			rebuild.run();
		}
		else
		{
			log.debug("Tab drop was a no-op; no render requested");
		}
	}

	private static String widgetIdentity(Widget widget)
	{
		return widget == null ? "null"
			: widget.getId() + "@" + Integer.toHexString(System.identityHashCode(widget));
	}

	private static boolean equalsNullable(Object first, Object second)
	{
		return first == null ? second == null : first.equals(second);
	}
}
