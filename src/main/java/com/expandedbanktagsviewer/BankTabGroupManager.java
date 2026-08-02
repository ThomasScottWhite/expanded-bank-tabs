package com.expandedbanktagsviewer;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

@Singleton
final class BankTabGroupManager
{
	private static final String CONFIG_GROUP = "expandedbanktabs";
	private static final String CONFIG_KEY = "groups";
	private static final String LEGACY_CONFIG_GROUP = "banktags";
	private static final String LEGACY_CONFIG_KEY = "expandedGroups";
	private static final int STORAGE_VERSION = 3;

	private final ConfigManager configManager;
	private final Gson gson;
	private final List<BankTabGroup> groups = new ArrayList<>();
	private final List<String> ungroupedTabs = new ArrayList<>();
	private int ungroupedIndex;

	@Inject
	BankTabGroupManager(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	void reload()
	{
		groups.clear();
		ungroupedTabs.clear();
		ungroupedIndex = 0;
		String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY);
		if (json == null || json.isEmpty())
		{
			// Preserve groups made with the earlier source-fork implementation.
			json = configManager.getConfiguration(LEGACY_CONFIG_GROUP, LEGACY_CONFIG_KEY);
		}

		if (json == null || json.isEmpty())
		{
			return;
		}

		try
		{
			Storage storage = gson.fromJson(json, Storage.class);
			if (storage == null || (storage.version != 1 && storage.version != 2 && storage.version != STORAGE_VERSION)
				|| storage.groups == null)
			{
				return;
			}

			Set<String> assigned = new HashSet<>();
			for (BankTabGroup group : storage.groups)
			{
				if (group == null || isBlank(group.getId()) || isBlank(group.getName()))
				{
					continue;
				}

				List<String> tabs = new ArrayList<>();
				List<String> storedTabs = group.getTabs() == null ? Collections.emptyList() : group.getTabs();
				for (String tab : storedTabs)
				{
					String normalized = normalize(tab);
					if (normalized != null && assigned.add(normalized))
					{
						tabs.add(normalized);
					}
				}
				group.setTabs(tabs);
				groups.add(group);
			}

			if (storage.version == STORAGE_VERSION && storage.ungroupedTabs != null)
			{
				for (String tab : storage.ungroupedTabs)
				{
					String normalized = normalize(tab);
					if (normalized != null && !assigned.contains(normalized) && !ungroupedTabs.contains(normalized))
					{
						ungroupedTabs.add(normalized);
					}
				}
			}

			ungroupedIndex = storage.version == STORAGE_VERSION && storage.ungroupedIndex != null
				? storage.ungroupedIndex : groups.size();
			ungroupedIndex = Math.max(0, Math.min(groups.size(), ungroupedIndex));
		}
		catch (JsonParseException | IllegalStateException ex)
		{
			groups.clear();
		}
	}

	List<BankTabGroup> getGroups()
	{
		return Collections.unmodifiableList(groups);
	}

	BankTabGroup find(String id)
	{
		return groups.stream().filter(group -> group.getId().equals(id)).findFirst().orElse(null);
	}

	BankTabGroup create(String name)
	{
		String trimmed = name == null ? "" : name.trim();
		if (trimmed.isEmpty() || hasName(trimmed, null))
		{
			return null;
		}

		int oldSize = groups.size();
		BankTabGroup group = new BankTabGroup(UUID.randomUUID().toString(), trimmed);
		groups.add(group);
		if (ungroupedIndex == oldSize)
		{
			// Keep the default section at the bottom when a new custom group is
			// created while it is currently last.
			ungroupedIndex++;
		}
		save();
		return group;
	}

	boolean rename(String id, String name)
	{
		BankTabGroup group = find(id);
		String trimmed = name == null ? "" : name.trim();
		if (group == null || trimmed.isEmpty() || hasName(trimmed, id))
		{
			return false;
		}

		group.setName(trimmed);
		save();
		return true;
	}

	boolean delete(String id)
	{
		BankTabGroup group = find(id);
		if (group == null)
		{
			return false;
		}

		int removedIndex = groups.indexOf(group);
		groups.remove(group);
		if (removedIndex < ungroupedIndex)
		{
			ungroupedIndex--;
		}
		save();
		return true;
	}

	void toggleCollapsed(String id)
	{
		BankTabGroup group = find(id);
		if (group != null)
		{
			group.setCollapsed(!group.isCollapsed());
			save();
		}
	}

	void assign(String tag, String groupId)
	{
		String normalized = normalize(tag);
		if (normalized == null)
		{
			return;
		}

		for (BankTabGroup group : groups)
		{
			group.getTabs().removeIf(tab -> normalized.equals(normalize(tab)));
		}
		ungroupedTabs.removeIf(tab -> normalized.equals(normalize(tab)));

		if (groupId != null)
		{
			BankTabGroup target = find(groupId);
			if (target != null)
			{
				target.getTabs().add(normalized);
			}
		}
		else
		{
			ungroupedTabs.add(normalized);
		}
		save();
	}

	boolean moveTab(String sourceTag, String targetTag)
	{
		String source = normalize(sourceTag);
		String target = normalize(targetTag);
		if (source == null || target == null || source.equals(target))
		{
			return false;
		}

		String targetGroupId = getGroupId(target);
		BankTabGroup targetGroup = targetGroupId == null ? null : find(targetGroupId);
		if (targetGroupId != null && targetGroup == null)
		{
			return false;
		}

		removeFromGroups(source);
		if (targetGroupId == null)
		{
			if (!ungroupedTabs.contains(target))
			{
				ungroupedTabs.add(target);
			}
			insertBefore(ungroupedTabs, source, target);
		}
		else
		{
			int targetIndex = targetGroup.getTabs().indexOf(target);
			if (targetIndex < 0)
			{
				targetGroup.getTabs().add(source);
			}
			else
			{
				targetGroup.getTabs().add(targetIndex, source);
			}
		}
		save();
		return true;
	}

	List<String> orderUngrouped(List<String> availableTags)
	{
		List<String> ordered = new ArrayList<>();
		Set<String> available = new HashSet<>();
		for (String tag : availableTags)
		{
			String normalized = normalize(tag);
			if (normalized != null)
			{
				available.add(normalized);
			}
		}

		for (String tag : ungroupedTabs)
		{
			if (available.contains(tag) && getGroupId(tag) == null && !ordered.contains(tag))
			{
				ordered.add(tag);
			}
		}
		for (String tag : availableTags)
		{
			String normalized = normalize(tag);
			if (normalized != null && getGroupId(normalized) == null && !ordered.contains(normalized))
			{
				ordered.add(normalized);
			}
		}
		return ordered;
	}

	String getGroupId(String tag)
	{
		String normalized = normalize(tag);
		if (normalized == null)
		{
			return null;
		}

		for (BankTabGroup group : groups)
		{
			if (group.getTabs().stream().anyMatch(tab -> normalized.equals(normalize(tab))))
			{
				return group.getId();
			}
		}
		return null;
	}

	void move(String sourceId, String destinationId)
	{
		if (sourceId == null && destinationId == null)
		{
			return;
		}

		List<String> order = new ArrayList<>();
		for (int i = 0; i <= groups.size(); ++i)
		{
			if (i == ungroupedIndex)
			{
				order.add(null);
			}
			if (i < groups.size())
			{
				order.add(groups.get(i).getId());
			}
		}

		int sourceIndex = indexOfGroup(order, sourceId);
		int destinationIndex = indexOfGroup(order, destinationId);
		if (sourceIndex < 0 || destinationIndex < 0 || sourceIndex == destinationIndex)
		{
			return;
		}

		// Dropping one group header onto another swaps their positions. This is
		// predictable when the target is above or below the source and also works
		// when either side is the default ungrouped section.
		String source = order.get(sourceIndex);
		String destination = order.get(destinationIndex);
		order.set(sourceIndex, destination);
		order.set(destinationIndex, source);

		List<BankTabGroup> current = new ArrayList<>(groups);
		List<BankTabGroup> reordered = new ArrayList<>();
		int newUngroupedIndex = -1;
		for (String id : order)
		{
			if (id == null)
			{
				newUngroupedIndex = reordered.size();
				continue;
			}
			for (BankTabGroup group : current)
			{
				if (id.equals(group.getId()))
				{
					reordered.add(group);
					break;
				}
			}
		}
		if (newUngroupedIndex < 0)
		{
			return;
		}
		groups.clear();
		groups.addAll(reordered);
		ungroupedIndex = newUngroupedIndex;
		save();
	}

	int getUngroupedIndex()
	{
		return Math.max(0, Math.min(groups.size(), ungroupedIndex));
	}

	void renameTab(String oldTag, String newTag)
	{
		String oldValue = normalize(oldTag);
		String newValue = normalize(newTag);
		if (oldValue == null || newValue == null || oldValue.equals(newValue))
		{
			return;
		}

		for (BankTabGroup group : groups)
		{
			for (int i = 0; i < group.getTabs().size(); ++i)
			{
				if (oldValue.equals(normalize(group.getTabs().get(i))))
				{
					group.getTabs().set(i, newValue);
				}
			}
		}
		for (int i = 0; i < ungroupedTabs.size(); ++i)
		{
			if (oldValue.equals(normalize(ungroupedTabs.get(i))))
			{
				ungroupedTabs.set(i, newValue);
			}
		}
		save();
	}

	void removeTab(String tag)
	{
		String normalized = normalize(tag);
		if (normalized == null)
		{
			return;
		}
		for (BankTabGroup group : groups)
		{
			group.getTabs().removeIf(tab -> normalized.equals(normalize(tab)));
		}
		ungroupedTabs.removeIf(tab -> normalized.equals(normalize(tab)));
		save();
	}

	private void removeFromGroups(String tag)
	{
		for (BankTabGroup group : groups)
		{
			group.getTabs().removeIf(tab -> tag.equals(normalize(tab)));
		}
		ungroupedTabs.removeIf(tab -> tag.equals(normalize(tab)));
	}

	private static void insertBefore(List<String> values, String source, String target)
	{
		values.removeIf(source::equals);
		int targetIndex = values.indexOf(target);
		if (targetIndex < 0)
		{
			values.add(source);
		}
		else
		{
			values.add(targetIndex, source);
		}
	}

	private boolean hasName(String name, String ignoredId)
	{
		return groups.stream().anyMatch(group -> !group.getId().equals(ignoredId)
			&& group.getName().equalsIgnoreCase(name));
	}

	private void save()
	{
		Storage storage = new Storage();
		storage.groups = groups;
		storage.ungroupedTabs = new ArrayList<>(ungroupedTabs);
		storage.ungroupedIndex = ungroupedIndex;
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY, gson.toJson(storage));
	}

	private static int indexOfGroup(List<String> order, String groupId)
	{
		for (int i = 0; i < order.size(); ++i)
		{
			if (groupId == null ? order.get(i) == null : groupId.equals(order.get(i)))
			{
				return i;
			}
		}
		return -1;
	}

	private static String normalize(String value)
	{
		return isBlank(value) ? null : Text.standardize(value.trim());
	}

	private static boolean isBlank(String value)
	{
		return value == null || value.trim().isEmpty();
	}

	private static final class Storage
	{
		private int version = STORAGE_VERSION;
		private List<BankTabGroup> groups = new ArrayList<>();
		private List<String> ungroupedTabs = new ArrayList<>();
		private Integer ungroupedIndex;
	}
}
