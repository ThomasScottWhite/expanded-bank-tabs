package com.expandedbanktagsviewer;

import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ScriptEvent;
import net.runelite.api.SoundEffectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxItemSearch;
import net.runelite.client.game.chatbox.ChatboxPanelManager;

/** Owns tab/group commands and their chatbox interactions. */
@Singleton
final class ExpandedBankTabActions
{
	private final Client client;
	private final ClientThread clientThread;
	private final BankTagsAdapter bankTags;
	private final BankTabGroupManager groupManager;
	private final ChatboxPanelManager chatboxPanelManager;
	private final ChatboxItemSearch itemSearch;
	private final BankTagsSidebarRefresher sidebarRefresher;

	@Inject
	ExpandedBankTabActions(Client client, ClientThread clientThread, BankTagsAdapter bankTags,
		BankTabGroupManager groupManager, ChatboxPanelManager chatboxPanelManager,
		ChatboxItemSearch itemSearch, BankTagsSidebarRefresher sidebarRefresher)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.bankTags = bankTags;
		this.groupManager = groupManager;
		this.chatboxPanelManager = chatboxPanelManager;
		this.itemSearch = itemSearch;
		this.sidebarRefresher = sidebarRefresher;
	}

	void handleTabOperation(ScriptEvent event, String tag, Host host)
	{
		switch (event.getOp() - 1)
		{
			case ExpandedViewRenderer.MENU_VIEW:
				clientThread.invokeLater(() -> openTag(tag, host));
				break;
			case ExpandedViewRenderer.MENU_CHANGE_ICON:
				itemSearch.tooltipText(ExpandedViewRenderer.CHANGE_ICON + " (" + tag + ")")
					.onItemSelected(itemId -> setTabIcon(tag, itemId, host))
					.build();
				break;
			case ExpandedViewRenderer.MENU_LAYOUT:
				clientThread.invokeLater(() ->
				{
					bankTags.toggleLayout(tag);
					host.rebuildExpandedView();
				});
				break;
			case ExpandedViewRenderer.MENU_EXPORT:
				bankTags.exportTab(tag);
				break;
			case ExpandedViewRenderer.MENU_RENAME:
				renameTab(tag, host);
				break;
			case ExpandedViewRenderer.MENU_DELETE:
				clientThread.invokeLater(() ->
				{
					bankTags.deleteTab(tag);
					groupManager.removeTab(tag);
					host.rebuildExpandedView();
				});
				break;
			default:
				break;
		}
	}

	void handleGroupOperation(ScriptEvent event, String groupId, Host host)
	{
		BankTabGroup group = groupManager.find(groupId);
		if (group == null)
		{
			return;
		}

		switch (event.getOp() - 1)
		{
			case ExpandedViewRenderer.GROUP_MENU_RENAME:
				renameGroup(group, host);
				break;
			case ExpandedViewRenderer.GROUP_MENU_DELETE:
				chatboxPanelManager.openTextMenuInput("Delete bank tab group " + group.getName())
					.option("1. Delete group", () -> clientThread.invoke(() ->
					{
						groupManager.delete(group.getId());
						host.rebuildExpandedView();
					}))
					.option("2. Cancel", () -> { })
					.build();
				break;
			case ExpandedViewRenderer.GROUP_MENU_COLLAPSE:
				groupManager.toggleCollapsed(group.getId());
				clientThread.invokeLater(host::rebuildExpandedView);
				break;
			default:
				break;
		}
	}

	void createGroup(Host host)
	{
		chatboxPanelManager.openTextInput("Group name")
			.onDone((Consumer<String>) name -> clientThread.invoke(() ->
			{
				if (groupManager.create(name) != null)
				{
					host.rebuildExpandedView();
				}
			}))
			.build();
	}

	void createBankTab(String groupId, Host host)
	{
		chatboxPanelManager.openTextInput("Tag name")
			.addCharValidator(c -> "</>:".indexOf(c) == -1)
			.onDone((Consumer<String>) name -> clientThread.invoke(() -> createBankTabNow(name, groupId, host)))
			.build();
	}

	private void openTag(String tag, Host host)
	{
		host.closeExpandedView();
		client.setVarbit(VarbitID.BANK_CURRENTTAB, 0);
		bankTags.openTag(tag);
		client.playSoundEffect(SoundEffectID.UI_BOOP);
	}

	private void createBankTabNow(String name, String groupId, Host host)
	{
		String tag = bankTags.createTab(name);
		if (tag == null)
		{
			return;
		}
		if (groupId != null)
		{
			groupManager.assign(tag, groupId);
		}
		refreshSidebar(host);
	}

	private void renameGroup(BankTabGroup group, Host host)
	{
		chatboxPanelManager.openTextInput("Enter new name for bank tab group \"" + group.getName() + "\":")
			.onDone((Consumer<String>) name -> clientThread.invoke(() ->
			{
				if (groupManager.rename(group.getId(), name))
				{
					host.rebuildExpandedView();
				}
			}))
			.build();
	}

	private void setTabIcon(String tag, int itemId, Host host)
	{
		bankTags.setIcon(tag, itemId);
		refreshSidebar(host);
	}

	private void renameTab(String oldTag, Host host)
	{
		chatboxPanelManager.openTextInput("Enter new tag name for tag \"" + oldTag + "\":")
			.onDone((Consumer<String>) newTag -> clientThread.invoke(() ->
			{
				String newValue = bankTags.renameTab(oldTag, newTag);
				if (newValue != null)
				{
					groupManager.renameTab(oldTag, newValue);
					host.rebuildExpandedView();
				}
			}))
			.build();
	}

	private void refreshSidebar(Host host)
	{
		sidebarRefresher.refresh(host::prepareForSidebarRefresh, host::refreshPluginWidgets);
	}

	interface Host
	{
		void closeExpandedView();
		void rebuildExpandedView();
		void prepareForSidebarRefresh();
		void refreshPluginWidgets();
	}
}
