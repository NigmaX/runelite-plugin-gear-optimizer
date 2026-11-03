package com.gearoptimizer;

import com.gearoptimizer.account.AccountSnapshot;
import com.gearoptimizer.account.AccountSyncService;
import com.gearoptimizer.calc.StyleResult;
import com.gearoptimizer.data.DataUpdater;
import com.gearoptimizer.data.GameDataRepository;
import com.gearoptimizer.data.Monster;
import com.gearoptimizer.data.MonsterLocation;
import com.gearoptimizer.ui.MaxDpsView;
import com.gearoptimizer.ui.OptimizerView;
import com.gearoptimizer.ui.OptimizerWindow;
import com.google.inject.Provides;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
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
 * {@link AccountSyncService}, {@link LocationService} and the bank classes.
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
	private static final Set<String> DISPLAY_ONLY_KEYS = Set.of(CalculationSettings.LAST_MONSTER, CalculationSettings.LAST_PLACE,
		"highlightColor", "highlightInBank",
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
	@Inject
	private LocationService locations;

	private OptimizerWindow window;
	private NavigationButton navButton;
	private final Runnable accountListener = this::onAccountChanged;

	/** The monster being optimized for. EDT only. */
	private Monster selectedMonster;
	/** Where the player fights it: one of its wiki location names, or null when it has none. EDT only. */
	private String selectedPlace;
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
		targets.start(this::followTarget, (monster, seen) ->
		{
			window.open();
			selectMonster(monster, placeSeenAt(monster, seen));
		});
		eventBus.register(targets);
		overlayManager.add(bankOverlay);
		bankLayout.start(this::showCurrentSetupInBank);
		eventBus.register(bankLayout);
		eventBus.register(locations);

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
			String lastPlace = config.lastPlace();
			onWindow(view ->
			{
				view.onDataLoaded();
				if (last != null)
				{
					selectMonster(last, lastPlace);
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
		eventBus.unregister(locations);
		locations.stop();
		clientToolbar.removeNavigation(navButton);
		window.dispose();
		window = null;
		selectedMonster = null;
		selectedPlace = null;
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
		boolean wilderness = repository.getLocations(selectedMonster.getId()).stream()
			.anyMatch(place -> place.getName().equals(selectedPlace) && place.isWilderness());
		return new CalculationService.Job(selectedMonster, selectedPlace, wilderness, currentSnapshot(),
			viewedProfile == null ? settings.slayerTaskName() : null);
	}

	private void followTarget(Monster monster, WorldPoint seen)
	{
		String place = placeSeenAt(monster, seen);
		if (window != null && (monster != selectedMonster || place != null && !place.equals(selectedPlace)))
		{
			selectMonster(monster, place);
		}
	}

	/**
	 * @return the name of the monster's place nearest where it was seen, or null
	 */
	private String placeSeenAt(Monster monster, WorldPoint seen)
	{
		MonsterLocation place = seen == null ? null : MonsterLocation.at(repository.getLocations(monster.getId()), seen);
		return place == null ? null : place.getName();
	}

	/**
	 * @return the place if it's one of the monster's, else its first place, or null when it has none
	 */
	private String placeOrFirst(Monster monster, String place)
	{
		List<MonsterLocation> places = repository.getLocations(monster.getId());
		for (MonsterLocation candidate : places)
		{
			if (candidate.getName().equals(place))
			{
				return place;
			}
		}
		return places.isEmpty() ? null : places.get(0).getName();
	}

	// ---- Called from the window (EDT) ----

	/**
	 * @param place where the player fights it, one of its wiki location names; null or unknown picks its first place
	 */
	public void selectMonster(Monster monster, String place)
	{
		selectedMonster = monster;
		selectedPlace = placeOrFirst(monster, place);
		configManager.setConfiguration(GearOptimizerConfig.GROUP, CalculationSettings.LAST_MONSTER, monster.getId());
		configManager.setConfiguration(GearOptimizerConfig.GROUP, CalculationSettings.LAST_PLACE, selectedPlace == null ? "" : selectedPlace);
		window.showMonster(monster, selectedPlace);
		calculations.recalculate();
	}

	/**
	 * Changes where the player fights the selected monster.
	 */
	public void selectPlace(String place)
	{
		if (selectedMonster != null && !place.equals(selectedPlace))
		{
			selectMonster(selectedMonster, place);
		}
	}

	public void selectLastTarget()
	{
		Monster monster = targets.lastTarget();
		if (monster != null)
		{
			selectMonster(monster, placeSeenAt(monster, targets.lastTargetLocation()));
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

	public void showOnMap(Monster monster, List<MonsterLocation> places, Consumer<String> onDone)
	{
		locations.showOnMap(monster.getName(), places, onDone);
	}

	public void walkTo(Monster monster, List<MonsterLocation> places, Consumer<String> onDone)
	{
		locations.walkTo(monster.getName(), places, onDone);
	}

	public void clearMapAndRoute(Consumer<String> onDone)
	{
		locations.clear(onDone);
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
					selectMonster(refreshed, selectedPlace);
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
