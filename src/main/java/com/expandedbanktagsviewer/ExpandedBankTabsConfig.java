package com.expandedbanktagsviewer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("expandedbanktabs")
public interface ExpandedBankTabsConfig extends Config
{
	@ConfigItem(
		keyName = "enabled",
		name = "Enable Expanded Bank Tags Viewer",
		description = "Show the expanded bank tabs button while a bank is open.",
		position = 1
	)
	default boolean enabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "expandedViewOpen",
		name = "Expanded view open",
		description = "Whether the expanded bank tabs view was open during the previous bank session.",
		hidden = true
	)
	default boolean expandedViewOpen()
	{
		return false;
	}
}
