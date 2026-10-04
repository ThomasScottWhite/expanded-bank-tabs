package com.expandedbanktagsviewer;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.chatbox.ChatboxItemSearch;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsService;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BankTagsAdapterTest
{
	private ConfigManager configManager;
	private BankTagsService service;
	private BankTagsAdapter adapter;
	private final Map<String, String> values = new HashMap<>();

	@Before
	public void setUp()
	{
		configManager = mock(ConfigManager.class);
		service = mock(BankTagsService.class);
		when(configManager.getConfiguration(eq("banktags"), anyString()))
			.thenAnswer(invocation -> values.get(invocation.getArgument(1)));
		doAnswer(invocation ->
		{
			values.put(invocation.getArgument(1), String.valueOf((Object) invocation.getArgument(2)));
			return null;
		}).when(configManager).setConfiguration(eq("banktags"), anyString(), any(Object.class));
		doAnswer(invocation ->
		{
			values.put(invocation.getArgument(1), invocation.getArgument(2));
			return null;
		}).when(configManager).setConfiguration(eq("banktags"), anyString(), anyString());
		adapter = new BankTagsAdapter(configManager, service);
	}

	@Test
	public void createsTabWithoutReplacingExistingOrderIconsOrLayouts()
	{
		values.put("tagtabs", "slayer,raids");
		values.put("icon_slayer", "4151");
		values.put("layout_raids", "1,-1,2");

		assertEquals("skilling", adapter.createTab(" Skilling "));
		assertEquals("slayer,raids,skilling", values.get("tagtabs"));
		assertEquals(String.valueOf(ItemID.SPADE), values.get("icon_skilling"));
		assertEquals("4151", values.get("icon_slayer"));
		assertEquals("1,-1,2", values.get("layout_raids"));
	}

	@Test
	public void rejectsDuplicateAndBlankNamesWithoutWriting()
	{
		values.put("tagtabs", "slayer,raids");
		assertNull(adapter.createTab(" Slayer "));
		assertNull(adapter.createTab(" "));
		verify(configManager, never()).setConfiguration(anyString(), anyString(), any());
		verify(configManager, never()).setConfiguration(anyString(), anyString(), any(Object.class));
	}

	@Test
	public void createsFirstTabWhenNoTabsAreSaved()
	{
		assertEquals("slayer", adapter.createTab("slayer"));
		assertEquals("slayer", values.get("tagtabs"));
	}

	@Test
	public void changingIconDoesNotRewriteTabList()
	{
		values.put("tagtabs", "slayer,raids");
		adapter.setIcon(" Slayer ", 4151);
		assertEquals("4151", values.get("icon_slayer"));
		assertEquals("slayer,raids", values.get("tagtabs"));
		verify(configManager, never()).setConfiguration(eq("banktags"), eq("tagtabs"), any());
	}

	@Test
	public void pluginInjectsWithOnlyThePublicBankTagsService()
	{
		// Match RuneLite 1.13's dependency boundary: no BankTagsPlugin,
		// BankTagsConfig or TabManager binding is available to this plugin.
		Injector injector = Guice.createInjector(binder ->
		{
			binder.bind(ConfigManager.class).toInstance(configManager);
			binder.bind(BankTagsService.class).toInstance(service);
			binder.bind(ClientThread.class).toInstance(mock(ClientThread.class));
			binder.bind(Client.class).toInstance(mock(Client.class));
			binder.bind(PluginManager.class).toInstance(mock(PluginManager.class));
			binder.bind(Gson.class).toInstance(new Gson());
			binder.bind(ExpandedBankTabsConfig.class).toInstance(mock(ExpandedBankTabsConfig.class));
			binder.bind(ChatboxPanelManager.class).toProvider(() -> mock(ChatboxPanelManager.class));
			binder.bind(ChatboxItemSearch.class).toProvider(() -> mock(ChatboxItemSearch.class));
		});
		assertNotNull(injector.getInstance(ExpandedBankTabsPlugin.class));
		assertNotNull(injector.getInstance(BankTagsAdapter.class));
		assertNotNull(injector.getInstance(BankTagsSidebarRefresher.class));
	}
}
