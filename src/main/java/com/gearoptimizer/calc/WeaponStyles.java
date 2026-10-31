package com.gearoptimizer.calc;

import com.gearoptimizer.data.EquipmentItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Combat options available for each weapon category, from the wiki's weapon type tables.
 */
public final class WeaponStyles
{
	private static final AttackType STAB = AttackType.STAB;
	private static final AttackType SLASH = AttackType.SLASH;
	private static final AttackType CRUSH = AttackType.CRUSH;

	private static final Map<String, List<Stance>> MELEE = new HashMap<>();

	static
	{
		melee("Unarmed", m("Punch", CRUSH, "accurate"), m("Kick", CRUSH, "aggressive"));
		melee("Blunt", m("Pound", CRUSH, "accurate"), m("Pummel", CRUSH, "aggressive"));
		melee("blunt", m("Pound", CRUSH, "accurate"), m("Pummel", CRUSH, "aggressive"));
		melee("Staff", m("Bash", CRUSH, "accurate"), m("Pound", CRUSH, "aggressive"));
		melee("Stab Sword", m("Stab", STAB, "accurate"), m("Lunge", STAB, "aggressive"), m("Slash", SLASH, "aggressive"));
		melee("Slash Sword", m("Chop", SLASH, "accurate"), m("Slash", SLASH, "aggressive"), m("Lunge", STAB, "controlled"));
		melee("Spear", m("Lunge", STAB, "controlled"), m("Swipe", SLASH, "controlled"), m("Pound", CRUSH, "controlled"));
		melee("Axe", m("Chop", SLASH, "accurate"), m("Hack", SLASH, "aggressive"), m("Smash", CRUSH, "aggressive"));
		melee("Banner", m("Lunge", STAB, "accurate"), m("Swipe", SLASH, "aggressive"), m("Pound", CRUSH, "controlled"));
		melee("Spiked", m("Pound", CRUSH, "accurate"), m("Pummel", CRUSH, "aggressive"), m("Spike", STAB, "controlled"));
		melee("Flail", m("Pound", CRUSH, "accurate"), m("Pummel", CRUSH, "aggressive"), m("Spike", STAB, "controlled"));
		melee("2h Sword", m("Chop", SLASH, "accurate"), m("Slash", SLASH, "aggressive"), m("Smash", CRUSH, "aggressive"));
		melee("Pickaxe", m("Spike", STAB, "accurate"), m("Impale", STAB, "aggressive"), m("Smash", CRUSH, "aggressive"));
		melee("Polearm", m("Jab", STAB, "controlled"), m("Swipe", SLASH, "aggressive"));
		melee("Claw", m("Chop", SLASH, "accurate"), m("Slash", SLASH, "aggressive"), m("Lunge", STAB, "controlled"));
		melee("Polestaff", m("Bash", CRUSH, "accurate"), m("Pound", CRUSH, "aggressive"));
		melee("Bladed Staff", m("Jab", STAB, "accurate"), m("Swipe", SLASH, "aggressive"));
		melee("Scythe", m("Reap", SLASH, "accurate"), m("Chop", SLASH, "aggressive"), m("Jab", CRUSH, "aggressive"));
		melee("Whip", m("Flick", SLASH, "accurate"), m("Lash", SLASH, "controlled"));
		melee("Partisan", m("Stab", STAB, "accurate"), m("Lunge", STAB, "aggressive"), m("Pound", CRUSH, "aggressive"));
		melee("Bludgeon", m("Pound", CRUSH, "aggressive"));
		melee("Bulwark", m("Pummel", CRUSH, "accurate"));
		melee("Salamander", m("Scorch", SLASH, "aggressive"));
		melee("Extending Chain Mace", m("Pound", CRUSH, "accurate"), m("Pummel", CRUSH, "aggressive"));
		melee("Multi-Melee", m("Pound", CRUSH, "accurate"), m("Pummel", CRUSH, "aggressive"));
	}

	private WeaponStyles()
	{
	}

	private static Stance m(String name, AttackType type, String stance)
	{
		return Stance.melee(name, type, stance);
	}

	private static void melee(String category, Stance... stances)
	{
		MELEE.put(category, Collections.unmodifiableList(Arrays.asList(stances)));
	}

	/**
	 * @return the stances worth trying for this weapon in the given style; empty if it cannot attack that way
	 */
	public static List<Stance> stancesFor(EquipmentItem weapon, CombatStyle style)
	{
		String category = weapon == null ? "Unarmed" : weapon.getCategory();
		switch (style)
		{
			case MELEE:
				return MELEE.getOrDefault(category, Collections.emptyList());
			case RANGED:
				if (isRangedWeapon(weapon))
				{
					List<Stance> stances = new ArrayList<>();
					stances.add(Stance.RANGED_RAPID);
					stances.add(Stance.RANGED_ACCURATE);
					return stances;
				}
				return Collections.emptyList();
			case MAGIC:
				if ("Powered Staff".equals(category))
				{
					return Collections.singletonList(Stance.MAGIC_ACCURATE);
				}
				if (canAutocast(weapon))
				{
					return Collections.singletonList(Stance.AUTOCAST);
				}
				return Collections.emptyList();
			default:
				return Collections.emptyList();
		}
	}

	public static boolean isRangedWeapon(EquipmentItem weapon)
	{
		if (weapon == null)
		{
			return false;
		}
		String category = weapon.getCategory();
		String name = weapon.getName().toLowerCase();
		if (name.startsWith("holy water") || name.startsWith("mud pie") || name.equals("spine")
			|| name.startsWith("hunter's spear"))
		{
			// Thrown quest/minigame items, not real weapons
			return false;
		}
		return "Bow".equals(category) || "Crossbow".equals(category) || "Thrown".equals(category) || "Chinchompas".equals(category);
	}

	public static boolean canAutocast(EquipmentItem weapon)
	{
		if (weapon == null)
		{
			return false;
		}
		String category = weapon.getCategory();
		return "Staff".equals(category) || "Bladed Staff".equals(category);
	}
}
