package com.gearoptimizer;

import com.gearoptimizer.account.AccountSnapshot;
import com.gearoptimizer.account.AccountSyncService;
import com.gearoptimizer.calc.StyleResult;
import com.gearoptimizer.data.DataUpdater;
import com.gearoptimizer.data.GameDataRepository;
import com.gearoptimizer.data.Monster;
import com.gearoptimizer.ui.MaxDpsView;
import com.gearoptimizer.ui.OptimizerView;
import com.gearoptimizer.ui.OptimizerWindow;
import com.google.inject.Provides;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Wires the plugin together: starts and stops the services, routes game and config events, and backs the window.
 * The work itself lives in {@link CalculationService}, {@link CalculationSettings}, {@link TargetSelector},
 * {@link AccountSyncService} and the bank classes.
 */
@Slf4j
@PluginDependency(BankTagsPlugin.class)
@PluginDescriptor(
	name = "Gear Optimizer",
	description = "Finds the best gear in your bank for any monster: DPS, slayer bonuses, required items and upgrades",
	tags = {"gear", "dps", "bank", "slayer", "equipment", "setup", "loadout", "calculator"}
)
public class GearOptimizerPlugin extends Plugin
{
	/** Settings that only change how things look; everything else recalculates. */
	private static final Set<String> DISPLAY_ONLY_KEYS = Set.of(CalculationSettings.LAST_MONSTER, "highlightColor", "highlightInBank",
		"npcMenuOption", "autoSelectTarget");

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private EventBus eventBus;
	@Inject
	private ConfigManager configManager;
	@Inject
	private GearOptimizerConfig config;
	@Inject
	private GameDataRepository repository;
	@Inject
	private DataUpdater dataUpdater;
	@Inject
	private AccountSyncService accountSync;
	@Inject
	private CalculationService calculations;
	@Inject
	private CalculationSettings settings;
	@Inject
	private TargetSelector targets;
	@Inject
	private BankHighlightOverlay bankOverlay;
	@Inject
	private BankLayoutService bankLayout;

	private OptimizerWindow window;
	private NavigationButton navButton;
	private final Runnable accountListener = this::onAccountChanged;

	/** The monster being optimized for. EDT only. */
	private Monster selectedMonster;
	/** Profile key of another account being viewed, or null for the logged-in account. */
	private volatile String viewedProfile;

	@Provides
	GearOptimizerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GearOptimizerConfig.class);
	}

	@Override
	protected void startUp()
	{
		window = injector.getInstance(OptimizerWindow.class);
		calculations.start(this::currentJob, new CalculationService.Listener()
		{
			@Override
			public void calculating()
			{
				window.showCalculating();
			}

			@Override
			public void finished(OptimizerView view)
			{
				window.showResult(view);
			}

			@Override
			public void failed(String message)
			{
				window.showStatus(message);
			}
		});
		navButton = NavigationButton.builder()
			.tooltip("Gear Optimizer")
			.icon(OptimizerWindow.toolbarIcon())
			.priority(7)
			.onClick(() -> window.open())
			.build();
		clientToolbar.addNavigation(navButton);

		eventBus.register(accountSync);
		accountSync.addListener(accountListener);
		clientThread.invokeLater(accountSync::start);
		targets.start(this::followTarget, monster ->
		{
			window.open();
			selectMonster(monster);
		});
		eventBus.register(targets);
		overlayManager.add(bankOverlay);
		bankLayout.start(this::showCurrentSetupInBank);
		eventBus.register(bankLayout);

		calculations.background(() ->
		{
			try
			{
				repository.load();
			}
			catch (RuntimeException e)
			{
				log.warn("Unable to load gear optimizer data", e);
			}
			Monster last = repository.getMonster(config.lastMonsterId());
			onWindow(view ->
			{
				view.onDataLoaded();
				if (last != null)
				{
					selectMonster(last);
				}
			});
		});
	}

	@Override
	protected void shutDown()
	{
		calculations.stop();
		accountSync.removeListener(accountListener);
		accountSync.stop();
		eventBus.unregister(accountSync);
		eventBus.unregister(targets);
		targets.stop();
		overlayManager.remove(bankOverlay);
		bankOverlay.highlight(null);
		eventBus.unregister(bankLayout);
		bankLayout.stop();
		clientToolbar.removeNavigation(navButton);
		window.dispose();
		window = null;
		selectedMonster = null;
		viewedProfile = null;
	}

	/**
	 * Runs an action on the EDT with the window, unless the plugin has shut down by then.
	 */
	private void onWindow(Consumer<OptimizerWindow> action)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (window != null)
			{
				action.accept(window);
			}
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!GearOptimizerConfig.GROUP.equals(event.getGroup()) || DISPLAY_ONLY_KEYS.contains(event.getKey()))
		{
			return;
		}
		if ("allowDataDownload".equals(event.getKey()))
		{
			onWindow(OptimizerWindow::onDataLoaded);
			return;
		}
		calculations.recalculate();
	}

	private void onAccountChanged()
	{
		if (viewedProfile == null)
		{
			calculations.recalculate();
		}
	}

	/**
	 * @return what to calculate now, or null without a monster; EDT
	 */
	private CalculationService.Job currentJob()
	{
		if (selectedMonster == null)
		{
			return null;
		}
		return new CalculationService.Job(selectedMonster, currentSnapshot(), viewedProfile == null ? settings.slayerTaskName() : null);
	}

	private void followTarget(Monster monster)
	{
		if (window != null && monster != selectedMonster)
		{
			selectMonster(monster);
		}
	}

	// ---- Called from the window (EDT) ----

	public void selectMonster(Monster monster)
	{
		selectedMonster = monster;
		configManager.setConfiguration(GearOptimizerConfig.GROUP, CalculationSettings.LAST_MONSTER, monster.getId());
		window.showMonster(monster);
		calculations.recalculate();
	}

	public void selectLastTarget()
	{
		Monster monster = targets.lastTarget();
		if (monster != null)
		{
			selectMonster(monster);
		}
		else
		{
			window.showStatus("Attack a monster first, then press Target");
		}
	}

	public void viewProfile(String profileKey)
	{
		viewedProfile = profileKey;
		calculations.recalculate();
	}

	public AccountSnapshot currentSnapshot()
	{
		String profile = viewedProfile;
		return profile == null ? accountSync.getSnapshot() : accountSync.loadSnapshot(profile);
	}

	public void toggleItemSet(String key, int itemId)
	{
		settings.toggle(key, itemId);
	}

	public Set<Integer> getExcluded()
	{
		return settings.excluded();
	}

	public Set<Integer> getLocked()
	{
		return settings.locked();
	}

	public void highlight(StyleResult result)
	{
		bankOverlay.highlight(config.highlightInBank() ? result : null);
	}

	public void showInBank(StyleResult result, AccountSnapshot account, Consumer<String> onDone)
	{
		bankLayout.show(result, account, onDone);
	}

	public void computeMaxDps(Consumer<MaxDpsView> onDone)
	{
		calculations.computeMaxDps(onDone);
	}

	public void lookupPrices(Collection<Integer> itemIds, Consumer<Map<Integer, Long>> onLoaded)
	{
		calculations.lookupPrices(itemIds, onLoaded);
	}

	public void refreshData()
	{
		window.showStatus("Downloading monster and item data…");
		dataUpdater.refresh(calculations::background, error -> onWindow(view ->
		{
			view.onDataLoaded();
			view.showStatus(error == null ? "Data updated" : error);
			if (error == null && selectedMonster != null)
			{
				Monster refreshed = repository.getMonster(selectedMonster.getId());
				if (refreshed != null)
				{
					selectMonster(refreshed);
				}
			}
		}));
	}

	public void resetData()
	{
		calculations.background(() ->
		{
			dataUpdater.resetToBundled();
			onWindow(view ->
			{
				view.onDataLoaded();
				view.showStatus("Using bundled data");
			});
		});
	}

	// ---- Bank button ----

	/**
	 * The bank's Gear Optimizer button: shows the setup open in the window (the last style tab viewed) as a bank
	 * tab. Called on the client thread.
	 */
	private void showCurrentSetupInBank()
	{
		onWindow(view ->
		{
			StyleResult shown = view.currentSetup();
			if (shown == null)
			{
				chat("Pick a monster in the Gear Optimizer window first.");
				return;
			}
			String setup = shown.getStyle().getDisplayName() + " setup for " + shown.getContext().getMonster().getDisplayName();
			bankLayout.show(shown, view.account(), message ->
			{
				view.showStatus(message);
				chat(message.startsWith("Turn on") ? message : "Showing your " + setup + ".");
			});
		});
	}

	private void chat(String message)
	{
		clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Gear Optimizer: " + message, null));
	}
}
