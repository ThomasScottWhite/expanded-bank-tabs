package com.comfy.expandedbanktabs;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class ExpandedBankTabsTest
{
	private ExpandedBankTabsTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ExpandedBankTabsPlugin.class);
		RuneLite.main(args);
	}
}
