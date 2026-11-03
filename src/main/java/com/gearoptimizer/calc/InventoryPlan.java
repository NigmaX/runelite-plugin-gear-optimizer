package com.gearoptimizer.calc;

import com.gearoptimizer.account.AccountSnapshot;
import com.gearoptimizer.data.OwnedItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Value;

/**
 * Lays out a 28-slot inventory for the trip: required items, runes, spec weapon, potions, then food.
 */
public final class InventoryPlan
{
	public static final int SLOTS = 28;
	private static final int PRAYER_POTIONS = 4;

	private static final Pattern RUNE_POUCH = Pattern.compile("^(divine )?rune pouch", Pattern.CASE_INSENSITIVE);
	/** Food from best to worst healing. */
	private static final List<Pattern> FOODS = Stream.of(
		"^(blighted )?manta ray", "^(blighted )?anglerfish", "^dark crab", "^sea turtle", "^tuna potato", "^shark",
		"^(cooked|blighted) karambwan", "^potato with cheese", "^monkfish", "^swordfish", "^bass", "^lobster", "^curry")
		.map(regex -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE))
		.collect(Collectors.toList());

	@Value
	public static class Slot
	{
		/** -1 when the player doesn't own it. */
		int itemId;
		String name;
		int quantity;
		boolean owned;
		boolean mandatory;
	}

	private InventoryPlan()
	{
	}

	public static List<Slot> build(StyleResult result, AccountSnapshot account)
	{
		List<Slot> slots = new ArrayList<>();
		// Blighted supplies only work in the Wilderness
		boolean wilderness = result.getContext().isInWilderness();

		for (MonsterRules.CheckedItem item : result.getInventory())
		{
			String label = item.getLabel();
			if (label.equals("Food") || label.startsWith("Prayer potions") || label.startsWith("Load blowpipe"))
			{
				continue;
			}
			if (label.startsWith("Runes for"))
			{
				addRunes(slots, item, account);
				continue;
			}
			if (label.equals(MonsterRules.CANNON) && item.isOwned())
			{
				// The base, stand, barrels and furnace each take a slot
				for (int part : item.getItemIds())
				{
					slots.add(new Slot(part, account.nameOf(part), 1, true, true));
				}
				continue;
			}
			if (label.endsWith("annonballs"))
			{
				int id = best(account, item.getItemIds());
				slots.add(id < 0 ? new Slot(-1, label, 0, false, true) : new Slot(id, account.nameOf(id), account.quantityOf(id), true, true));
				continue;
			}
			int id = best(account, item.getItemIds());
			slots.add(id < 0
				? new Slot(-1, item.getLabel(), 0, false, item.isMandatory())
				: new Slot(id, account.nameOf(id), 1, true, item.isMandatory()));
		}

		if (!result.getSpecOptions().isEmpty())
		{
			OwnedItem spec = result.getSpecOptions().get(0).getWeapon();
			OwnedItem weapon = result.getLoadout().get(com.gearoptimizer.data.GearSlot.WEAPON);
			if (weapon == null || weapon.getStats().getId() != spec.getStats().getId())
			{
				slots.add(new Slot(spec.getItemId(), spec.getStats().getName() + " (spec)", 1, true, false));
			}
		}

		PotionBoost potion = result.getContext().getPotion();
		if (potion != PotionBoost.NONE && potion.getItemPattern() != null)
		{
			int id = best(account, MonsterRules.matchingIds(account, potion.getItemPattern(), wilderness));
			slots.add(id < 0 ? new Slot(-1, potion.getDisplayName(), 0, false, false) : new Slot(id, account.nameOf(id), 1, true, false));
		}

		if (result.getContext().getPrayer() != PrayerBoost.NONE)
		{
			int id = best(account, MonsterRules.matchingIds(account, MonsterRules.PRAYER_RESTORE, wilderness));
			for (int i = 0; i < PRAYER_POTIONS; i++)
			{
				slots.add(id < 0 ? new Slot(-1, "Prayer potion", 0, false, false) : new Slot(id, account.nameOf(id), 1, true, false));
			}
		}

		int food = bestFood(account, wilderness);
		while (slots.size() < SLOTS)
		{
			slots.add(food < 0 ? new Slot(-1, "Food", 0, false, false) : new Slot(food, account.nameOf(food), 1, true, false));
		}
		return slots.size() > SLOTS ? new ArrayList<>(slots.subList(0, SLOTS)) : slots;
	}

	private static void addRunes(List<Slot> slots, MonsterRules.CheckedItem runes, AccountSnapshot account)
	{
		int pouch = best(account, MonsterRules.matchingIds(account, RUNE_POUCH));
		if (pouch >= 0 && account.getRunePouch() != null && !account.getRunePouch().isEmpty())
		{
			slots.add(new Slot(pouch, account.nameOf(pouch), 1, true, true));
		}
		else
		{
			for (int id : runes.getItemIds())
			{
				slots.add(new Slot(id, account.nameOf(id), account.quantityOf(id), true, true));
			}
		}
		if (!runes.isOwned())
		{
			slots.add(new Slot(-1, runes.getDetail(), 0, false, true));
		}
	}

	private static int bestFood(AccountSnapshot account, boolean wilderness)
	{
		for (Pattern food : FOODS)
		{
			int id = best(account, MonsterRules.matchingIds(account, food, wilderness));
			if (id >= 0)
			{
				return id;
			}
		}
		return -1;
	}

	/**
	 * @return the full-dose version if owned, otherwise the one with the most quantity; -1 if none
	 */
	private static int best(AccountSnapshot account, Set<Integer> ids)
	{
		int best = -1;
		for (int id : ids)
		{
			String name = account.nameOf(id);
			if (name != null && name.endsWith("(4)"))
			{
				return id;
			}
			if (best < 0 || account.quantityOf(id) > account.quantityOf(best))
			{
				best = id;
			}
		}
		return best;
	}
}
