package com.expandedbanktagsviewer;

import java.awt.Rectangle;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetConfig;
import net.runelite.api.widgets.WidgetType;

/** Owns the invisible drag/scroll input surface over the bank scrollbar. */
@Singleton
final class ExpandedScrollbarController
{
	private final Client client;
	private Widget input;
	private Listener listener;

	@Inject
	ExpandedScrollbarController(Client client)
	{
		this.client = client;
	}

	void maintain(Widget layerParent, boolean visible, Listener listener)
	{
		this.listener = listener;
		if (!visible || layerParent == null)
		{
			hide();
			return;
		}

		Widget bankScrollbar = client.getWidget(InterfaceID.Bankmain.SCROLLBAR);
		if (bankScrollbar == null)
		{
			hide();
			return;
		}

		Widget[] children = layerParent.getChildren();
		boolean attached = input != null && children != null
			&& Arrays.stream(children).anyMatch(child -> child == input);
		if (!attached)
		{
			dispose();
			this.listener = listener;
			input = layerParent.createChild(-1, WidgetType.RECTANGLE);
			input.setFilled(false);
			input.setOpacity(0);
			input.setHasListener(true);
			input.setNoClickThrough(true);
			input.setNoScrollThrough(true);
			input.setClickMask(WidgetConfig.DRAG | WidgetConfig.DRAG_ON);
			input.setDragDeadTime(1);
			input.setDragDeadZone(1);
			input.setOnScrollWheelListener((JavaScriptCallback) event ->
			{
				if (this.listener != null)
				{
					this.listener.scrollBy(event.getMouseY());
				}
			});
		}

		Point relative = WidgetGeometry.relativeLocation(bankScrollbar, layerParent);
		if (relative != null)
		{
		position(relative.getX(), relative.getY(), bankScrollbar.getWidth(), bankScrollbar.getHeight());
		}
		else
		{
			Point scrollbarLocation = bankScrollbar.getCanvasLocation();
			Point parentLocation = layerParent.getCanvasLocation();
			if (scrollbarLocation == null || parentLocation == null)
			{
				return;
			}
			position(scrollbarLocation.getX() - parentLocation.getX(),
				scrollbarLocation.getY() - parentLocation.getY(),
				bankScrollbar.getWidth(), bankScrollbar.getHeight());
		}
	}

	private void position(int x, int y, int width, int height)
	{
		width = Math.max(1, width);
		height = Math.max(1, height);
		if (input.getOriginalX() == x && input.getOriginalY() == y
			&& input.getOriginalWidth() == width && input.getOriginalHeight() == height
			&& !input.isHidden())
		{
			return;
		}
		input.setOriginalX(x);
		input.setOriginalY(y);
		input.setOriginalWidth(width);
		input.setOriginalHeight(height);
		input.setHidden(false);
		input.revalidate();
	}

	boolean handleDrag(Widget dragged)
	{
		if (input == null || dragged != input)
		{
			return false;
		}
		updateFromMouse();
		return true;
	}

	void pollDrag()
	{
		updateFromMouse();
	}

	void hide()
	{
		if (input != null)
		{
			if (!input.isHidden())
			{
				input.setHidden(true);
				input.revalidate();
			}
		}
	}

	void dispose()
	{
		hide();
		input = null;
		listener = null;
	}

	private void updateFromMouse()
	{
		if (input == null || listener == null || client.getMouseCurrentButton() != 1)
		{
			return;
		}
		Rectangle bounds = input.getBounds();
		Point mouse = client.getMouseCanvasPosition();
		if (bounds == null || mouse == null || !bounds.contains(mouse.getX(), mouse.getY()))
		{
			return;
		}

		int maxScroll = listener.getMaxScroll();
		if (maxScroll == 0)
		{
			return;
		}
		int padding = Math.min(16, Math.max(1, bounds.height / 4));
		int trackTop = bounds.y + padding;
		int trackLength = Math.max(1, bounds.y + bounds.height - padding - trackTop);
		int position = Math.max(0, Math.min(trackLength, mouse.getY() - trackTop));
		listener.setScrollOffset(Math.round((float) position / trackLength * maxScroll));
	}

	interface Listener
	{
		void scrollBy(int direction);
		int getMaxScroll();
		void setScrollOffset(int offset);
	}
}
