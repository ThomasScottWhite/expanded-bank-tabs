package com.expandedbanktagsviewer;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.tabs.TabManager;
import net.runelite.client.plugins.banktags.tabs.TagTab;
import net.runelite.client.util.Text;

/**
 * Isolates Expanded Bank Tags Viewer from Bank Tags storage and manager details.
 * Rendering code should consume {@link BankTab} values and never read Bank Tags
 * configuration directly.
 */
@Singleton
final class BankTagsAdapter
{
	private static final String CONFIG_GROUP = "banktags";
	private static final String TABS_KEY = "tagtabs";
	private static final String ICON_PREFIX = "icon_";
	private static final String LAYOUT_PREFIX = "layout_";
	private static final String ITEM_PREFIX = "item_";

	private final ConfigManager configManager;
	private final BankTagsService bankTagsService;
	private final TabManager tabManager;

	@Inject
	BankTagsAdapter(ConfigManager configManager, BankTagsService bankTagsService, TabManager tabManager)
	{
		this.configManager = configManager;
		this.bankTagsService = bankTagsService;
		this.tabManager = tabManager;
	}

	List<BankTab> loadTabs()
	{
		String value = configManager.getConfiguration(CONFIG_GROUP, TABS_KEY);
		if (value == null || value.isEmpty())
		{
			return Collections.emptyList();
		}

		List<BankTab> tabs = new ArrayList<>();
		for (String tag : Text.fromCSV(value))
		{
			String normalized = Text.standardize(tag);
			if (!normalized.isEmpty())
			{
				tabs.add(new BankTab(normalized, iconFor(normalized)));
			}
		}
		return tabs;
	}

	String getActiveTag()
	{
		return bankTagsService == null ? null : bankTagsService.getActiveTag();
	}

	void openTag(String tag)
	{
		if (bankTagsService != null)
		{
			bankTagsService.openBankTag(tag, BankTagsService.OPTION_ALLOW_MODIFICATIONS);
		}
	}

	void closeTag()
	{
		if (bankTagsService != null)
		{
			bankTagsService.closeBankTag();
		}
	}

	String createTab(String name)
	{
		String tag = normalize(name);
		if (tag.isEmpty() || tabManager.find(tag) != null)
		{
			return null;
		}

		TagTab newTab = new TagTab();
		newTab.setTag(tag);
		newTab.setIconItemId(ItemID.SPADE);
		tabManager.add(newTab);
		tabManager.save();
		return tag;
	}

	void setIcon(String tag, int itemId)
	{
		String normalized = normalize(tag);
		TagTab tab = tabManager.find(normalized);
		if (tab != null)
		{
			tab.setIconItemId(itemId);
			tabManager.save();
			return;
		}

		configManager.setConfiguration(CONFIG_GROUP, ICON_PREFIX + normalized, itemId);
	}

	boolean hasLayout(String tag)
	{
		return configManager.getConfiguration(CONFIG_GROUP, LAYOUT_PREFIX + normalize(tag)) != null;
	}

	void toggleLayout(String tag)
	{
		String normalized = normalize(tag);
		String key = LAYOUT_PREFIX + normalized;
		if (configManager.getConfiguration(CONFIG_GROUP, key) == null)
		{
			configManager.setConfiguration(CONFIG_GROUP, key, "");
		}
		else
		{
			configManager.unsetConfiguration(CONFIG_GROUP, key);
		}

		if (normalized.equals(getActiveTag()))
		{
			openTag(normalized);
		}
	}

	String renameTab(String oldTag, String newTag)
	{
		String oldValue = normalize(oldTag);
		String newValue = normalize(newTag);
		if (newValue.isEmpty() || oldValue.equals(newValue)
			|| loadTabs().stream().anyMatch(tab -> tab.getTag().equals(newValue)))
		{
			return null;
		}

		List<String> tabs = loadTabs().stream().map(BankTab::getTag).collect(Collectors.toList());
		Collections.replaceAll(tabs, oldValue, newValue);
		configManager.setConfiguration(CONFIG_GROUP, TABS_KEY, Text.toCSV(tabs));
		moveConfig(ICON_PREFIX + oldValue, ICON_PREFIX + newValue);
		moveConfig(LAYOUT_PREFIX + oldValue, LAYOUT_PREFIX + newValue);
		rewriteItemTags(oldValue, newValue);
		rewriteItemTags(oldValue + "*", newValue + "*");

		if (oldValue.equals(getActiveTag()))
		{
			openTag(newValue);
		}
		return newValue;
	}

	void deleteTab(String tag)
	{
		String normalized = normalize(tag);
		List<String> tabs = loadTabs().stream().map(BankTab::getTag)
			.filter(value -> !value.equals(normalized)).collect(Collectors.toList());
		configManager.setConfiguration(CONFIG_GROUP, TABS_KEY, Text.toCSV(tabs));
		configManager.unsetConfiguration(CONFIG_GROUP, ICON_PREFIX + normalized);
		configManager.unsetConfiguration(CONFIG_GROUP, LAYOUT_PREFIX + normalized);
		rewriteItemTags(normalized, null);
		rewriteItemTags(normalized + "*", null);

		if (normalized.equals(getActiveTag()))
		{
			closeTag();
		}
	}

	void exportTab(String tag)
	{
		String normalized = normalize(tag);
		List<String> data = new ArrayList<>();
		data.add("banktags");
		data.add("1");
		data.add(normalized);
		data.add(String.valueOf(iconFor(normalized)));
		for (String key : configManager.getConfigurationKeys(CONFIG_GROUP + "." + ITEM_PREFIX))
		{
			String[] split = key.split("\\.", 2);
			if (split.length != 2)
			{
				continue;
			}
			String values = configManager.getConfiguration(CONFIG_GROUP, split[1]);
			if (values != null && Text.fromCSV(values).stream().map(Text::standardize)
				.anyMatch(normalized::equals))
			{
				data.add(split[1].substring(ITEM_PREFIX.length()));
			}
		}
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(Text.toCSV(data)), null);
	}

	private int iconFor(String tag)
	{
		String value = configManager.getConfiguration(CONFIG_GROUP, ICON_PREFIX + normalize(tag));
		try
		{
			return value == null ? ItemID.SPADE : Integer.parseInt(value);
		}
		catch (NumberFormatException ex)
		{
			return ItemID.SPADE;
		}
	}

	private void rewriteItemTags(String oldTag, String newTag)
	{
		for (String key : configManager.getConfigurationKeys(CONFIG_GROUP + "." + ITEM_PREFIX))
		{
			String[] split = key.split("\\.", 2);
			if (split.length != 2)
			{
				continue;
			}
			String stored = configManager.getConfiguration(CONFIG_GROUP, split[1]);
			if (stored == null)
			{
				continue;
			}

			List<String> tags = new ArrayList<>(Text.fromCSV(stored));
			boolean changed;
			if (newTag == null)
			{
				changed = tags.removeIf(oldTag::equals);
			}
			else
			{
				changed = Collections.replaceAll(tags, oldTag, newTag);
			}
			if (changed)
			{
				configManager.setConfiguration(CONFIG_GROUP, split[1], Text.toCSV(tags));
			}
		}
	}

	private void moveConfig(String oldKey, String newKey)
	{
		String value = configManager.getConfiguration(CONFIG_GROUP, oldKey);
		if (value != null)
		{
			configManager.setConfiguration(CONFIG_GROUP, newKey, value);
			configManager.unsetConfiguration(CONFIG_GROUP, oldKey);
		}
	}

	private static String normalize(String tag)
	{
		return tag == null ? "" : Text.standardize(tag.trim());
	}
}
