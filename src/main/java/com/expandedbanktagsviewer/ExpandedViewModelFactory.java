package com.expandedbanktagsviewer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.util.Text;

/**
 * Produces a deterministic snapshot for one expanded-view render. Keeping this
 * separate from widgets prevents rendering code from mutating group storage and
 * avoids repeatedly scanning the full tab list for every group entry.
 */
@Singleton
final class ExpandedViewModelFactory
{
	private static final String UNGROUPED_NAME = "Ungrouped bank tabs (default)";

	private final BankTagsAdapter bankTags;
	private final BankTabGroupManager groupManager;

	@Inject
	ExpandedViewModelFactory(BankTagsAdapter bankTags, BankTabGroupManager groupManager)
	{
		this.bankTags = bankTags;
		this.groupManager = groupManager;
	}

	List<ExpandedViewGroup> create()
	{
		List<BankTab> allTabs = bankTags.loadTabs();
		Map<String, BankTab> tabsByTag = new HashMap<>();
		for (BankTab tab : allTabs)
		{
			tabsByTag.put(tab.getTag(), tab);
		}

		List<ExpandedViewGroup> result = new ArrayList<>();
		List<BankTabGroup> customGroups = groupManager.getGroups();
		Set<String> assignedTags = new HashSet<>();
		for (BankTabGroup group : customGroups)
		{
			for (String tag : group.getTabs())
			{
				assignedTags.add(Text.standardize(tag));
			}
		}
		int ungroupedIndex = groupManager.getUngroupedIndex();
		for (int i = 0; i <= customGroups.size(); ++i)
		{
			if (i == ungroupedIndex)
			{
				result.add(new ExpandedViewGroup(UNGROUPED_NAME, null,
					orderedUngrouped(allTabs, tabsByTag, assignedTags), false));
			}

			if (i < customGroups.size())
			{
				BankTabGroup group = customGroups.get(i);
				result.add(new ExpandedViewGroup(group.getName(), group.getId(),
					orderedTabs(group.getTabs(), tabsByTag), group.isCollapsed()));
			}
		}
		return result;
	}

	private List<BankTab> orderedUngrouped(List<BankTab> allTabs, Map<String, BankTab> tabsByTag,
		Set<String> assignedTags)
	{
		List<String> availableTags = new ArrayList<>();
		for (BankTab tab : allTabs)
		{
			if (!assignedTags.contains(tab.getTag()))
			{
				availableTags.add(tab.getTag());
			}
		}
		return orderedTabs(groupManager.orderUngrouped(availableTags), tabsByTag);
	}

	private static List<BankTab> orderedTabs(List<String> orderedTags, Map<String, BankTab> tabsByTag)
	{
		List<BankTab> result = new ArrayList<>();
		for (String tag : orderedTags)
		{
			BankTab tab = tabsByTag.get(Text.standardize(tag));
			if (tab != null)
			{
				result.add(tab);
			}
		}
		return result;
	}
}
