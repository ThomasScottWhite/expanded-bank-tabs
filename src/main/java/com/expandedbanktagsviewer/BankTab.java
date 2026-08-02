package com.expandedbanktagsviewer;

final class BankTab
{
	private final String tag;
	private final int iconItemId;

	BankTab(String tag, int iconItemId)
	{
		this.tag = tag;
		this.iconItemId = iconItemId;
	}

	String getTag()
	{
		return tag;
	}

	int getIconItemId()
	{
		return iconItemId;
	}
}
