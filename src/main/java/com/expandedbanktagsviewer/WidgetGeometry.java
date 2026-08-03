package com.expandedbanktagsviewer;

import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

final class WidgetGeometry
{
	private WidgetGeometry()
	{
	}

	static Point relativeLocation(Widget widget, Widget ancestor)
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

	static Widget findLayerAncestor(Widget start)
	{
		for (Widget current = start; current != null; current = current.getParent())
		{
			if (current.getType() == WidgetType.LAYER)
			{
				return current;
			}
		}
		return null;
	}
}
