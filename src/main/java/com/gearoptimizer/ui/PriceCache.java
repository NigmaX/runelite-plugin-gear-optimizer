package com.gearoptimizer.ui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * GE prices for the window, looked up on demand. An item already being looked up isn't asked for again, but every
 * caller waiting on it is still called back once it arrives. Used on the EDT only.
 */
final class PriceCache
{
	/** A caller waiting for some items. */
	private static final class Waiter
	{
		final Set<Integer> remaining;
		final Component owner;
		final Runnable onLoaded;

		Waiter(Set<Integer> remaining, Component owner, Runnable onLoaded)
		{
			this.remaining = remaining;
			this.owner = owner;
			this.onLoaded = onLoaded;
		}
	}

	/** Looks up prices off the EDT and hands them back on it. */
	private final BiConsumer<Collection<Integer>, Consumer<Map<Integer, Long>>> lookup;
	private final Map<Integer, Long> prices = new HashMap<>();
	/** Items being looked up, with the callers waiting on each. */
	private final Map<Integer, List<Waiter>> pending = new HashMap<>();

	PriceCache(BiConsumer<Collection<Integer>, Consumer<Map<Integer, Long>>> lookup)
	{
		this.lookup = lookup;
	}

	/**
	 * @return the GE price, 0 if untradeable, or null if not looked up yet
	 */
	Long get(int itemId)
	{
		return prices.get(itemId);
	}

	void put(int itemId, long price)
	{
		prices.put(itemId, price);
	}

	void putAll(Map<Integer, Long> known)
	{
		prices.putAll(known);
	}

	/**
	 * Looks up the prices not known yet, then runs the callback once all of them are in. Nothing runs if every price
	 * is already known.
	 *
	 * @param owner the component the callback updates; the callback is skipped if it has left the window by then
	 */
	void request(Collection<Integer> itemIds, Component owner, Runnable onLoaded)
	{
		Set<Integer> waiting = new HashSet<>();
		List<Integer> missing = new ArrayList<>();
		for (int id : itemIds)
		{
			if (!prices.containsKey(id) && waiting.add(id) && !pending.containsKey(id))
			{
				missing.add(id);
			}
		}
		if (waiting.isEmpty())
		{
			return;
		}
		Waiter waiter = new Waiter(waiting, owner, onLoaded);
		for (int id : waiting)
		{
			pending.computeIfAbsent(id, k -> new ArrayList<>()).add(waiter);
		}
		if (!missing.isEmpty())
		{
			lookup.accept(missing, this::loaded);
		}
	}

	private void loaded(Map<Integer, Long> loaded)
	{
		prices.putAll(loaded);
		List<Waiter> ready = new ArrayList<>();
		for (int id : loaded.keySet())
		{
			List<Waiter> waiters = pending.remove(id);
			if (waiters == null)
			{
				continue;
			}
			for (Waiter waiter : waiters)
			{
				if (waiter.remaining.remove(id) && waiter.remaining.isEmpty())
				{
					ready.add(waiter);
				}
			}
		}
		for (Waiter waiter : ready)
		{
			// A tab rebuilt since asking is no longer in the window; nothing to update
			if (SwingUtilities.getWindowAncestor(waiter.owner) != null)
			{
				waiter.onLoaded.run();
			}
		}
	}
}
