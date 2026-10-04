package com.expandedbanktagsviewer;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BankTagsSidebarRefresherTest
{
	private ClientThread clientThread;
	private PluginManager pluginManager;
	private BankTagsPlugin bankTagsPlugin;
	private BankTagsSidebarRefresher refresher;
	private Runnable before;
	private Runnable after;
	private final Queue<Runnable> clientCallbacks = new ArrayDeque<>();

	@Before
	public void setUp()
	{
		clientThread = mock(ClientThread.class);
		pluginManager = mock(PluginManager.class);
		bankTagsPlugin = mock(BankTagsPlugin.class);
		before = mock(Runnable.class);
		after = mock(Runnable.class);
		when(pluginManager.getPlugins()).thenReturn(Collections.singletonList(bankTagsPlugin));
		when(pluginManager.isPluginActive(bankTagsPlugin)).thenReturn(true);
		when(pluginManager.isPluginEnabled(bankTagsPlugin)).thenReturn(true);
		doAnswer(invocation ->
		{
			clientCallbacks.add(invocation.getArgument(0));
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
		refresher = new BankTagsSidebarRefresher(clientThread, pluginManager);
	}

	@Test
	public void restartsTheLoadedInstanceAfterClientThreadDeinitialization() throws Exception
	{
		refresher.refresh(before, after);
		flushSwing();
		verify(before).run();
		verify(pluginManager).stopPlugin(bankTagsPlugin);
		verify(pluginManager, never()).startPlugin(any());
		verify(after, never()).run();

		clientCallbacks.remove().run();
		flushSwing();
		verify(pluginManager).startPlugin(bankTagsPlugin);
		clientCallbacks.remove().run();
		verify(after).run();
		assertTrue(clientCallbacks.isEmpty());
	}

	@Test
	public void missingPluginOnlyRefreshesOurWidgets() throws Exception
	{
		when(pluginManager.getPlugins()).thenReturn(Collections.emptyList());
		refresher.refresh(before, after);
		clientCallbacks.remove().run();
		verify(before).run();
		verify(after).run();
		verify(pluginManager, never()).stopPlugin(any());
		verify(pluginManager, never()).startPlugin(any());
	}

	@Test
	public void inactivePluginIsNotStarted() throws Exception
	{
		when(pluginManager.isPluginActive(bankTagsPlugin)).thenReturn(false);
		refresher.refresh(before, after);
		clientCallbacks.remove().run();
		verify(after).run();
		verify(pluginManager, never()).stopPlugin(any());
		verify(pluginManager, never()).startPlugin(any());
	}

	@Test
	public void pluginDisabledDuringRestartStaysDisabled() throws Exception
	{
		refresher.refresh(before, after);
		flushSwing();
		when(pluginManager.isPluginEnabled(bankTagsPlugin)).thenReturn(false);
		clientCallbacks.remove().run();
		flushSwing();
		clientCallbacks.remove().run();
		verify(pluginManager, never()).startPlugin(any());
		verify(after).run();
	}

	@Test
	public void shutdownCancelsQueuedRestart() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			refresher.refresh(before, after);
			refresher.reset();
		});
		flushSwing();
		verify(pluginManager, never()).stopPlugin(any());
		verify(pluginManager, never()).startPlugin(any());
		verify(after, never()).run();
		assertTrue(clientCallbacks.isEmpty());
	}

	private static void flushSwing() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> { });
	}
}
