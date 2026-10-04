package com.expandedbanktagsviewer;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates the Bank Tags plugin lifecycle when its persisted tab list changes. */
@Singleton
final class BankTagsSidebarRefresher
{
	private static final Logger log = LoggerFactory.getLogger(BankTagsSidebarRefresher.class);

	private final ClientThread clientThread;
	private final PluginManager pluginManager;
	private boolean pending;
	private int generation;

	@Inject
	BankTagsSidebarRefresher(ClientThread clientThread, PluginManager pluginManager)
	{
		this.clientThread = clientThread;
		this.pluginManager = pluginManager;
	}

	void refresh(Runnable beforeRestart, Runnable afterRestart)
	{
		// Bank Tags exports services, not its plugin instance. Resolve the loaded
		// instance through PluginManager so Guice cannot construct another one.
		BankTagsPlugin bankTagsPlugin = pluginManager.getPlugins().stream()
			.filter(BankTagsPlugin.class::isInstance)
			.map(BankTagsPlugin.class::cast)
			.findFirst().orElse(null);
		boolean active = bankTagsPlugin != null && pluginManager.isPluginActive(bankTagsPlugin);
		log.debug("Sidebar refresh requested: pending={}, bankTagsActive={}, generation={}",
			pending, active, generation);
		beforeRestart.run();
		if (pending || !active)
		{
			log.debug("Skipping Bank Tags restart; queueing plugin widget refresh only");
			clientThread.invokeLater(afterRestart);
			return;
		}

		pending = true;
		int requestGeneration = generation;
		log.debug("Scheduling Bank Tags restart for generation {}", requestGeneration);
		SwingUtilities.invokeLater(() -> restart(bankTagsPlugin, requestGeneration, afterRestart));
	}

	void reset()
	{
		log.debug("Resetting sidebar refresher: generation {} -> {}", generation, generation + 1);
		pending = false;
		generation++;
	}

	private void restart(BankTagsPlugin bankTagsPlugin, int requestGeneration, Runnable afterRestart)
	{
		if (requestGeneration != generation)
		{
			log.debug("Discarding stale sidebar restart generation {}; current={}",
				requestGeneration, generation);
			return;
		}
		try
		{
			log.debug("Stopping Bank Tags for sidebar refresh generation {}", requestGeneration);
			pluginManager.stopPlugin(bankTagsPlugin);
		}
		catch (PluginInstantiationException ex)
		{
			log.warn("Unable to restart Bank Tags after changing its tabs", ex);
			finish(requestGeneration, afterRestart);
			return;
		}

		// Bank Tags deinitializes on the client thread. Queue the restart after
		// that work so its TabInterface rebuilds from the newly saved config.
		clientThread.invokeLater(() -> SwingUtilities.invokeLater(() ->
		{
			if (requestGeneration != generation)
			{
				return;
			}
			try
			{
				if (pluginManager.isPluginEnabled(bankTagsPlugin))
				{
					log.debug("Starting Bank Tags for sidebar refresh generation {}", requestGeneration);
					pluginManager.startPlugin(bankTagsPlugin);
				}
			}
			catch (PluginInstantiationException ex)
			{
				log.warn("Unable to start Bank Tags after changing its tabs", ex);
			}
			finally
			{
				finish(requestGeneration, afterRestart);
			}
		}));
	}

	private void finish(int requestGeneration, Runnable afterRestart)
	{
		if (requestGeneration != generation)
		{
			return;
		}
		pending = false;
		log.debug("Sidebar refresh generation {} finished; queueing plugin refresh", requestGeneration);
		clientThread.invokeLater(afterRestart);
	}
}
