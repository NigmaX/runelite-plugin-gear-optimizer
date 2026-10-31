package com.gearoptimizer.calc;

import com.gearoptimizer.data.EquipmentItem;
import com.gearoptimizer.data.Monster;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import net.runelite.api.Skill;

/**
 * Autocastable combat spells. Runes are listed by name so they can be matched against the bank.
 */
@Getter
public enum Spell
{
	WIND_STRIKE("Wind Strike", Spellbook.STANDARD, 1, 2, "air", "1 Air", "1 Mind"),
	WATER_STRIKE("Water Strike", Spellbook.STANDARD, 5, 4, "water", "1 Air", "1 Water", "1 Mind"),
	EARTH_STRIKE("Earth Strike", Spellbook.STANDARD, 9, 6, "earth", "1 Air", "2 Earth", "1 Mind"),
	FIRE_STRIKE("Fire Strike", Spellbook.STANDARD, 13, 8, "fire", "2 Air", "3 Fire", "1 Mind"),
	WIND_BOLT("Wind Bolt", Spellbook.STANDARD, 17, 9, "air", "2 Air", "1 Chaos"),
	WATER_BOLT("Water Bolt", Spellbook.STANDARD, 23, 10, "water", "2 Air", "2 Water", "1 Chaos"),
	EARTH_BOLT("Earth Bolt", Spellbook.STANDARD, 29, 11, "earth", "2 Air", "3 Earth", "1 Chaos"),
	FIRE_BOLT("Fire Bolt", Spellbook.STANDARD, 35, 12, "fire", "3 Air", "4 Fire", "1 Chaos"),
	CRUMBLE_UNDEAD("Crumble Undead", Spellbook.STANDARD, 39, 15, null, "2 Air", "2 Earth", "1 Chaos"),
	WIND_BLAST("Wind Blast", Spellbook.STANDARD, 41, 13, "air", "3 Air", "1 Death"),
	WATER_BLAST("Water Blast", Spellbook.STANDARD, 47, 14, "water", "3 Air", "3 Water", "1 Death"),
	IBAN_BLAST("Iban Blast", Spellbook.STANDARD, 50, 25, null, "5 Fire", "1 Death"),
	MAGIC_DART("Magic Dart", Spellbook.STANDARD, 50, 10, null, "1 Death", "4 Mind"),
	EARTH_BLAST("Earth Blast", Spellbook.STANDARD, 53, 15, "earth", "3 Air", "4 Earth", "1 Death"),
	FIRE_BLAST("Fire Blast", Spellbook.STANDARD, 59, 16, "fire", "4 Air", "5 Fire", "1 Death"),
	WIND_WAVE("Wind Wave", Spellbook.STANDARD, 62, 17, "air", "5 Air", "1 Blood"),
	WATER_WAVE("Water Wave", Spellbook.STANDARD, 65, 18, "water", "5 Air", "7 Water", "1 Blood"),
	EARTH_WAVE("Earth Wave", Spellbook.STANDARD, 70, 19, "earth", "5 Air", "7 Earth", "1 Blood"),
	FIRE_WAVE("Fire Wave", Spellbook.STANDARD, 75, 20, "fire", "5 Air", "7 Fire", "1 Blood"),
	WIND_SURGE("Wind Surge", Spellbook.STANDARD, 81, 21, "air", "7 Air", "1 Wrath"),
	WATER_SURGE("Water Surge", Spellbook.STANDARD, 85, 22, "water", "7 Air", "10 Water", "1 Wrath"),
	EARTH_SURGE("Earth Surge", Spellbook.STANDARD, 90, 23, "earth", "7 Air", "10 Earth", "1 Wrath"),
	FIRE_SURGE("Fire Surge", Spellbook.STANDARD, 95, 24, "fire", "7 Air", "10 Fire", "1 Wrath"),
	SMOKE_RUSH("Smoke Rush", Spellbook.ANCIENT, 50, 13, null, "1 Air", "1 Fire", "2 Chaos", "2 Death"),
	SHADOW_RUSH("Shadow Rush", Spellbook.ANCIENT, 52, 14, null, "1 Air", "1 Soul", "2 Chaos", "2 Death"),
	BLOOD_RUSH("Blood Rush", Spellbook.ANCIENT, 56, 15, null, "2 Blood", "2 Chaos", "2 Death"),
	ICE_RUSH("Ice Rush", Spellbook.ANCIENT, 58, 16, null, "2 Water", "2 Chaos", "2 Death"),
	SMOKE_BURST("Smoke Burst", Spellbook.ANCIENT, 62, 17, null, "2 Air", "2 Fire", "4 Chaos", "2 Death"),
	SHADOW_BURST("Shadow Burst", Spellbook.ANCIENT, 64, 18, null, "1 Air", "2 Soul", "4 Chaos", "2 Death"),
	BLOOD_BURST("Blood Burst", Spellbook.ANCIENT, 68, 21, null, "2 Blood", "4 Chaos", "2 Death"),
	ICE_BURST("Ice Burst", Spellbook.ANCIENT, 70, 22, null, "4 Water", "4 Chaos", "2 Death"),
	SMOKE_BLITZ("Smoke Blitz", Spellbook.ANCIENT, 74, 23, null, "2 Air", "2 Fire", "2 Blood", "2 Death"),
	SHADOW_BLITZ("Shadow Blitz", Spellbook.ANCIENT, 76, 24, null, "2 Air", "2 Soul", "2 Blood", "2 Death"),
	BLOOD_BLITZ("Blood Blitz", Spellbook.ANCIENT, 80, 25, null, "4 Blood", "2 Death"),
	ICE_BLITZ("Ice Blitz", Spellbook.ANCIENT, 82, 26, null, "3 Water", "2 Blood", "2 Death"),
	SMOKE_BARRAGE("Smoke Barrage", Spellbook.ANCIENT, 86, 27, null, "4 Air", "4 Fire", "2 Blood", "4 Death"),
	SHADOW_BARRAGE("Shadow Barrage", Spellbook.ANCIENT, 88, 28, null, "4 Air", "3 Soul", "2 Blood", "4 Death"),
	BLOOD_BARRAGE("Blood Barrage", Spellbook.ANCIENT, 92, 29, null, "4 Blood", "4 Death", "1 Soul"),
	ICE_BARRAGE("Ice Barrage", Spellbook.ANCIENT, 94, 30, null, "6 Water", "2 Blood", "4 Death"),
	GHOSTLY_GRASP("Ghostly Grasp", Spellbook.ARCEUUS, 35, 12, null, "4 Air", "1 Chaos"),
	SKELETAL_GRASP("Skeletal Grasp", Spellbook.ARCEUUS, 56, 17, null, "8 Earth", "1 Death"),
	UNDEAD_GRASP("Undead Grasp", Spellbook.ARCEUUS, 79, 24, null, "4 Fire", "1 Blood"),
	INFERIOR_DEMONBANE("Inferior Demonbane", Spellbook.ARCEUUS, 44, 16, null, "4 Fire", "1 Chaos"),
	SUPERIOR_DEMONBANE("Superior Demonbane", Spellbook.ARCEUUS, 62, 23, null, "5 Fire", "1 Soul"),
	DARK_DEMONBANE("Dark Demonbane", Spellbook.ARCEUUS, 82, 30, null, "6 Fire", "2 Soul");

	public enum Spellbook
	{
		STANDARD,
		ANCIENT,
		ARCEUUS
	}

	private final String displayName;
	private final Spellbook spellbook;
	private final int level;
	private final int baseMaxHit;
	/** Element for weaknesses and tomes, or null. */
	private final String element;
	/** Rune names, e.g. "Air". */
	private final List<String> runes;
	/** Rune name -> runes used per cast. */
	private final Map<String, Integer> runeCosts;

	Spell(String displayName, Spellbook spellbook, int level, int baseMaxHit, String element, String... costs)
	{
		this.displayName = displayName;
		this.spellbook = spellbook;
		this.level = level;
		this.baseMaxHit = baseMaxHit;
		this.element = element;
		Map<String, Integer> map = new LinkedHashMap<>();
		for (String cost : costs)
		{
			String[] parts = cost.split(" ", 2);
			map.put(parts[1], Integer.parseInt(parts[0]));
		}
		this.runeCosts = Collections.unmodifiableMap(map);
		this.runes = Collections.unmodifiableList(new ArrayList<>(map.keySet()));
	}

	public boolean isDemonbane()
	{
		return this == INFERIOR_DEMONBANE || this == SUPERIOR_DEMONBANE || this == DARK_DEMONBANE;
	}

	/**
	 * @return whether this spell can be autocast with the weapon against the monster
	 */
	public boolean usableWith(EquipmentItem weapon, Monster monster)
	{
		Loadout loadout = new Loadout();
		loadout.set(com.gearoptimizer.data.GearSlot.WEAPON, weapon == null ? null : new com.gearoptimizer.data.OwnedItem(weapon, weapon.getId(), false));
		return usableWith(loadout, monster);
	}

	/**
	 * @return whether the player can autocast this spell here: magic level, Ancient Magicks access, the weapon and
	 * the monster
	 */
	public boolean castable(Loadout loadout, CombatContext ctx)
	{
		return level <= ctx.boostedLevel(Skill.MAGIC) && (spellbook != Spellbook.ANCIENT || ctx.isAncientMagicks())
			&& usableWith(loadout, ctx.getMonster());
	}

	/**
	 * @return whether this spell can be autocast with the loadout's weapon against the monster
	 * (some staves depend on other worn items, e.g. Ahrim's staff)
	 */
	public boolean usableWith(Loadout loadout, Monster monster)
	{
		if (this == CRUMBLE_UNDEAD && !monster.isUndead())
		{
			return false;
		}
		if (isDemonbane() && !monster.isDemon())
		{
			return false;
		}
		return canAutocastWith(loadout);
	}

	/**
	 * @return true for burst and barrage spells, which hit everything in a 3x3 area
	 */
	public boolean isAoe()
	{
		return spellbook == Spellbook.ANCIENT && (name().endsWith("_BURST") || name().endsWith("_BARRAGE"));
	}

	/**
	 * Autocast rules from the OSRS Wiki "Autocast" page: every staff and wand autocasts the elemental
	 * standard spells, while other spells and the Ancient and Arceuus spellbooks need specific staves.
	 */
	public boolean canAutocastWith(Loadout loadout)
	{
		EquipmentItem weapon = loadout.weapon();
		String w = weapon == null ? "" : weapon.getName().toLowerCase();
		boolean skullSceptre = w.startsWith("skull sceptre");
		boolean slayerStaff = w.startsWith("slayer's staff");
		boolean voidMace = w.startsWith("void knight mace");
		boolean deadStaff = w.startsWith("staff of the dead") || w.startsWith("toxic staff of the dead");
		boolean lightOrBalance = w.startsWith("staff of light") || w.startsWith("staff of balance");

		switch (spellbook)
		{
			case STANDARD:
				switch (this)
				{
					case IBAN_BLAST:
						return w.startsWith("iban's staff");
					case MAGIC_DART:
						return slayerStaff || deadStaff || lightOrBalance;
					case CRUMBLE_UNDEAD:
						return w.startsWith("skull sceptre (i)") || voidMace || slayerStaff || deadStaff || lightOrBalance;
					default:
						if (skullSceptre)
						{
							return false;
						}
						if (slayerStaff || voidMace)
						{
							// These only autocast wave and surge spells from the standard book
							return name().endsWith("_WAVE") || name().endsWith("_SURGE");
						}
						return true;
				}
			case ANCIENT:
				if (w.startsWith("ahrim's staff"))
				{
					return GearEffects.hasFullAhrims(loadout);
				}
				return w.startsWith("ancient staff") || w.contains("ancient sceptre") || w.startsWith("thammaron's sceptre (a)")
					|| w.startsWith("accursed sceptre (a)") || w.startsWith("blue moon spear") || w.startsWith("master wand")
					|| w.startsWith("dragon hunter wand") || w.startsWith("kodai wand") || w.startsWith("nightmare staff")
					|| w.startsWith("eldritch nightmare staff") || w.startsWith("volatile nightmare staff");
			case ARCEUUS:
				return skullSceptre || slayerStaff || w.startsWith("ahrim's staff") || w.startsWith("blue moon spear") || deadStaff
					|| w.startsWith("purging staff") || w.startsWith("master wand") || w.startsWith("kodai wand");
			default:
				return false;
		}
	}

	/**
	 * Ahrim's staff only autocasts Ancient Magicks with the full Ahrim's set and the Amulet of the damned.
	 */
}
