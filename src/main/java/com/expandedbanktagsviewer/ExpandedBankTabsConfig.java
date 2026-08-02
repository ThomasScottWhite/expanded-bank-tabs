package com.expandedbanktagsviewer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("expandedbanktabs")
public interface ExpandedBankTabsConfig extends Config
{
	@ConfigItem(
		keyName = "enabled",
		name = "Enable expanded bank tabs",
		description = "Show the expanded bank tabs button while a bank is open.",
		position = 1
	)
	default boolean enabled()
	{
		return true;
	}
}
