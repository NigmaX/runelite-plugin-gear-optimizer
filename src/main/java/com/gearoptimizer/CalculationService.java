package com.gearoptimizer;

import com.gearoptimizer.account.AccountSnapshot;
import com.gearoptimizer.calc.Consumables;
import com.gearoptimizer.calc.Optimizer;
import com.gearoptimizer.calc.StyleResult;
import com.gearoptimizer.calc.UpgradeFinder;
import com.gearoptimizer.data.GameDataRepository;
import com.gearoptimizer.data.Monster;
import com.gearoptimizer.ui.MaxDpsView;
import com.gearoptimizer.ui.OptimizerView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;

/**
 * Runs the optimizer away from the client thread and the EDT. One background thread does the work, a short delay
 * turns bursts of changes into one run, and a generation counter makes every newer run discard older results.
 */
@Slf4j
@Singleton
class CalculationService
{
	/** What to calculate; the plugin decides each time a run starts. */
	@Value
	static class Job
	{
		Monster monster;
		AccountSnapshot account;
		/** The slayer task's name, or null when it isn't known (another account is shown). */
		String slayerTask;
	}

	/** Receives the results; always called on the EDT. */
	interface Listener
	{
		void calculating();

		void finished(OptimizerView view);

		void failed(String message);
	}

	private static final int DEBOUNCE_MS = 400;
	private static final int UPGRADE_LIMIT = 10;

	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final GameDataRepository repository;
	private final CalculationSettings settings;
	private final GearOptimizerConfig config;

	private final AtomicInteger generation = new AtomicInteger();
	private ExecutorService executor;
	private Timer debounce;
	private Supplier<Job> jobs;
	private volatile Listener listener;
	/** Inputs of the latest run, reused by the on-demand max DPS run. */
	private volatile Optimizer.Input lastInput;

	@Inject
	CalculationService(ClientThread clientThread, ItemManager itemManager, GameDataRepository repository, CalculationSettings settings,
		GearOptimizerConfig config)
	{
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.repository = repository;
		this.settings = settings;
		this.config = config;
	}

	/**
	 * Called on the EDT at plugin start-up.
	 */
	void start(Supplier<Job> jobs, Listener listener)
	{
		this.jobs = jobs;
		this.listener = listener;
		executor = Executors.newSingleThreadExecutor(r ->
		{
			Thread thread = new Thread(r, "gear-optimizer");
			thread.setDaemon(true);
			return thread;
		});
		debounce = new Timer(DEBOUNCE_MS, e -> run());
		debounce.setRepeats(false);
	}

	void stop()
	{
		listener = null;
		generation.incrementAndGet();
		debounce.stop();
		executor.shutdownNow();
		lastInput = null;
	}

	/**
	 * Schedules a run; safe on any thread. Changes within a short delay of each other cost one run.
	 */
	void recalculate()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (listener != null)
			{
				debounce.restart();
			}
		});
	}

	/**
	 * Runs a task on the background thread, in order with the calculations.
	 *
	 * @return false if the service has stopped and the task won't run
	 */
	boolean background(Runnable task)
	{
		try
		{
			executor.execute(task);
			return true;
		}
		catch (RejectedExecutionException e)
		{
			return false;
		}
	}

	/**
	 * Starts a run with the current job; EDT only. GE prices need the client thread, so they're looked up first,
	 * then the optimizer runs in the background.
	 */
	private void run()
	{
		Listener out = listener;
		Job job = jobs.get();
		if (out == null || job == null || !repository.isLoaded())
		{
			return;
		}
		int run = generation.incrementAndGet();
		boolean onTask = settings.isOnTask(job.getMonster(), job.getSlayerTask());
		Optimizer.Input.InputBuilder input = Optimizer.Input.builder()
			.monster(job.getMonster())
			.account(job.getAccount())
			.contexts(settings.contexts(job.getMonster(), job.getAccount(), onTask))
			.defenceWeight(config.defenceWeight() / 100.0)
			.excludedItemIds(settings.excluded())
			.lockedItemIds(settings.locked())
			.budget(config.budgetMode());
		boolean upgrades = config.showUpgrades();
		out.calculating();

		clientThread.invokeLater(() ->
		{
			Set<Integer> ids = new HashSet<>(job.getAccount().ownedItemIds());
			ids.addAll(Consumables.PRICED_SUPPLIES);
			Map<Integer, Integer> prices = new HashMap<>();
			for (int id : ids)
			{
				prices.put(id, (int) Math.min(Integer.MAX_VALUE, itemManager.getItemPrice(id)));
			}
			Optimizer.Input built = input.prices(prices).build();
			lastInput = built;
			background(() -> optimize(run, built, upgrades, job, onTask));
		});
	}

	private void optimize(int run, Optimizer.Input input, boolean upgrades, Job job, boolean onTask)
	{
		try
		{
			Optimizer optimizer = new Optimizer(repository);
			Optimizer.Result result = optimizer.optimize(input);
			UpgradeFinder.Upgrades found = upgrades && result.getBest() != null
				? UpgradeFinder.find(optimizer, result, repository, job.getAccount(), UPGRADE_LIMIT)
				: UpgradeFinder.Upgrades.NONE;
			if (generation.get() != run)
			{
				return;
			}
			clientThread.invokeLater(() ->
			{
				for (UpgradeFinder.Upgrade upgrade : found.all())
				{
					upgrade.setPrice(itemManager.getItemPrice(upgrade.getItem().getId()));
				}
				OptimizerView view = new OptimizerView(job.getMonster(), result, found, job.getAccount(), job.getSlayerTask(), onTask);
				deliver(run, out -> out.finished(view));
			});
		}
		catch (RuntimeException e)
		{
			log.warn("Gear optimizer calculation failed", e);
			deliver(run, out -> out.failed("Calculation failed: " + e.getMessage()));
		}
	}

	/**
	 * Hands a result to the listener on the EDT, unless a newer run has started or the service stopped.
	 */
	private void deliver(int run, Consumer<Listener> action)
	{
		SwingUtilities.invokeLater(() ->
		{
			Listener out = listener;
			if (out != null && generation.get() == run)
			{
				action.accept(out);
			}
		});
	}

	/**
	 * Finds the best setups using any item in the game within the player's levels, in the background. The callback
	 * runs on the EDT, and only if no newer calculation has started.
	 */
	void computeMaxDps(Consumer<MaxDpsView> onDone)
	{
		Optimizer.Input base = lastInput;
		if (base == null)
		{
			return;
		}
		int run = generation.get();
		background(() ->
		{
			try
			{
				Optimizer.Result result = new Optimizer(repository).optimize(base.toBuilder().unrestricted(true).budget(false)
					.lockedItemIds(Collections.emptySet()).build());
				Set<Integer> ids = new HashSet<>();
				for (StyleResult style : result.getStyles().values())
				{
					ids.addAll(style.getLoadout().statIds());
				}
				lookupPrices(ids, prices ->
				{
					if (generation.get() == run && listener != null)
					{
						onDone.accept(new MaxDpsView(result, prices));
					}
				});
			}
			catch (RuntimeException e)
			{
				log.warn("Max DPS calculation failed", e);
			}
		});
	}

	/**
	 * Looks up GE prices on the client thread and hands them back on the EDT.
	 */
	void lookupPrices(Collection<Integer> itemIds, Consumer<Map<Integer, Long>> onLoaded)
	{
		List<Integer> ids = new ArrayList<>(itemIds);
		clientThread.invokeLater(() ->
		{
			Map<Integer, Long> prices = new HashMap<>();
			for (int id : ids)
			{
				prices.put(id, (long) itemManager.getItemPrice(id));
			}
			SwingUtilities.invokeLater(() -> onLoaded.accept(prices));
		});
	}
}
