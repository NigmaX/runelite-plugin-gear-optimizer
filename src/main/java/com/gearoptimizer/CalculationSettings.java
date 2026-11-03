package com.gearoptimizer;

import com.gearoptimizer.account.AccountSnapshot;
import com.gearoptimizer.calc.AoeTargets;
import com.gearoptimizer.calc.Cannon;
import com.gearoptimizer.calc.CombatContext;
import com.gearoptimizer.calc.CombatStyle;
import com.gearoptimizer.calc.PotionBoost;
import com.gearoptimizer.calc.PrayerBoost;
import com.gearoptimizer.calc.SlayerTaskMatcher;
import com.gearoptimizer.data.Monster;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;

/**
 * Turns the player's settings into calculation inputs: one combat context per style, whether they're on task, and
 * the items they excluded or forced.
 */
@Singleton
public class CalculationSettings
{
	public static final String EXCLUDED_ITEMS = "excludedItems";
	public static final String LOCKED_ITEMS = "lockedItems";
	static final String LAST_MONSTER = "lastMonsterId";
	static final String LAST_PLACE = "lastPlace";
	private static final String SLAYER_GROUP = "slayer";
	private static final int MAX_AOE_TARGETS = 9;

	private final GearOptimizerConfig config;
	private final ConfigManager configManager;

	@Inject
	CalculationSettings(GearOptimizerConfig config, ConfigManager configManager)
	{
		this.config = config;
		this.configManager = configManager;
	}

	/**
	 * @param wildernessPlace the player fights in the Wilderness, whatever the Wilderness setting says
	 * @return a combat context for each style the player wants compared
	 */
	Map<CombatStyle, CombatContext> contexts(Monster monster, AccountSnapshot account, boolean onTask, boolean wildernessPlace)
	{
		Map<CombatStyle, CombatContext> contexts = new EnumMap<>(CombatStyle.class);
		String filter = config.styleFilter();
		int cannonMaxHit = !config.cannon() ? 0
			: account.quantityOf(ItemID.GRANITE_CANNONBALL) > 0 ? Cannon.GRANITE_MAX_HIT : Cannon.STEEL_MAX_HIT;
		for (CombatStyle style : CombatStyle.values())
		{
			if (!"BEST".equals(filter) && !style.name().equals(filter))
			{
				continue;
			}
			contexts.put(style, CombatContext.builder()
				.monster(monster)
				.levels(account.getLevels())
				.prayer("NONE".equals(config.prayerMode()) ? PrayerBoost.NONE : PrayerBoost.best(style, account))
				.potion(potion(style, account))
				.onSlayerTask(onTask)
				.inWilderness(config.inWilderness() || wildernessPlace)
				.cannonMaxHit(cannonMaxHit)
				.kandarinHardDiary(account.isKandarinHardDiary())
				.aoeTargets(aoeTargets(monster))
				.ancientMagicks(account.getAncientMagicks() == null || account.getAncientMagicks())
				.build());
		}
		return contexts;
	}

	private PotionBoost potion(CombatStyle style, AccountSnapshot account)
	{
		switch (config.potionChoice())
		{
			case "NONE":
				return PotionBoost.NONE;
			case "STRONGEST":
				return PotionBoost.strongest(style);
			default:
				return PotionBoost.bestOwned(style, account);
		}
	}

	private int aoeTargets(Monster monster)
	{
		String setting = config.aoeTargets();
		if ("AUTO".equals(setting))
		{
			return AoeTargets.guess(monster);
		}
		try
		{
			return Math.max(1, Math.min(MAX_AOE_TARGETS, Integer.parseInt(setting)));
		}
		catch (NumberFormatException e)
		{
			return 1;
		}
	}

	/**
	 * @param task the current slayer task's name, or null
	 */
	boolean isOnTask(Monster monster, String task)
	{
		switch (config.slayerMode())
		{
			case "ON":
				return true;
			case "OFF":
				return false;
			default:
				return SlayerTaskMatcher.matches(task, monster);
		}
	}

	/**
	 * @return the logged-in account's slayer task as tracked by the Slayer plugin, or null
	 */
	String slayerTaskName()
	{
		String amount = configManager.getRSProfileConfiguration(SLAYER_GROUP, "amount");
		if (amount == null || "0".equals(amount))
		{
			return null;
		}
		return configManager.getRSProfileConfiguration(SLAYER_GROUP, "taskName");
	}

	Set<Integer> excluded()
	{
		return parseIds(config.excludedItems());
	}

	Set<Integer> locked()
	{
		return parseIds(config.lockedItems());
	}

	/**
	 * Adds the item to the list under {@code key}, or removes it if it's already there.
	 */
	void toggle(String key, int itemId)
	{
		Set<Integer> ids = parseIds(configManager.getConfiguration(GearOptimizerConfig.GROUP, key));
		if (!ids.remove(itemId))
		{
			ids.add(itemId);
		}
		StringJoiner csv = new StringJoiner(",");
		ids.forEach(id -> csv.add(String.valueOf(id)));
		configManager.setConfiguration(GearOptimizerConfig.GROUP, key, csv.toString());
	}

	private static Set<Integer> parseIds(String csv)
	{
		Set<Integer> ids = new LinkedHashSet<>();
		if (csv == null || csv.isEmpty())
		{
			return ids;
		}
		for (String part : csv.split(","))
		{
			try
			{
				ids.add(Integer.parseInt(part.trim()));
			}
			catch (NumberFormatException ignored)
			{
				// skip malformed entries
			}
		}
		return ids;
	}
}
