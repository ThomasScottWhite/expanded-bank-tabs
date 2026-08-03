package com.expandedbanktagsviewer;

/** Pure geometry calculations for the expanded bank view. */
final class ExpandedViewLayout
{
	static final int TAB_WIDTH = 39;
	static final int TAB_HEIGHT = 39;
	static final int PANEL_TOP = 41;
	static final int TILE_WIDTH = 39;
	static final int TILE_HEIGHT = 40;
	static final int GROUP_HEADER_HEIGHT = 24;
	static final int GROUP_SPACING = 8;

	private static final int BANK_BOTTOM_OFFSET = 39;
	private static final int TILE_GAP = 1;
	private static final int PANEL_LEFT_GAP = 2;
	private static final int CONTENT_INSET = 12;

	private ExpandedViewLayout()
	{
	}

	static int panelX()
	{
		return TAB_WIDTH + PANEL_LEFT_GAP;
	}

	static int panelWidth(int parentWidth)
	{
		return Math.max(1, parentWidth - panelX());
	}

	static int panelHeight(int parentHeight)
	{
		return Math.max(1, parentHeight - BANK_BOTTOM_OFFSET - PANEL_TOP);
	}

	static int firstGroupY()
	{
		return PANEL_TOP + 8;
	}

	static int contentStartX(int panelX)
	{
		return panelX + CONTENT_INSET;
	}

	static int columns(int panelWidth)
	{
		return Math.max(1, (panelWidth - CONTENT_INSET + TILE_GAP) / (TILE_WIDTH + TILE_GAP));
	}

	static int tileX(int startX, int index, int columns)
	{
		return startX + (index % columns) * (TILE_WIDTH + TILE_GAP);
	}

	static int tileY(int startY, int index, int columns)
	{
		return startY + (index / columns) * (TILE_HEIGHT + TILE_GAP);
	}

	static int groupBottom(int contentY, int tileCount, int columns)
	{
		int rows = (tileCount + columns - 1) / columns;
		return contentY + rows * (TILE_HEIGHT + TILE_GAP);
	}

	static int maxScroll(int panelY, int panelHeight, int contentBottom)
	{
		return Math.max(0, contentBottom - (panelY + panelHeight) + 8);
	}
}
