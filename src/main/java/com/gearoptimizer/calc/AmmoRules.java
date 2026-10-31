package com.gearoptimizer.calc;

import com.gearoptimizer.data.EquipmentItem;

/**
 * Which ammunition each ranged weapon can fire. Every bow and crossbow family in the dataset is
 * covered explicitly: regular bows and crossbows by metal tier, and special weapons (atlatl,
 * ogre bows, Karil's, hunters' crossbows, ballistas, ascension crossbows) by their own ammo.
 * Weapons not listed fall back to the strictest sensible rule rather than accepting anything.
 */
final class AmmoRules
{
	private static final int BRONZE = 0;
	private static final int IRON = 1;
	private static final int STEEL = 2;
	private static final int MITHRIL = 3;
	private static final int ADAMANT = 4;
	private static final int RUNE = 5;
	private static final int AMETHYST = 6;
	private static final int DRAGON = 7;
	private static final int NONE = -1;

	private AmmoRules()
	{
	}

	static boolean compatible(EquipmentItem weapon, EquipmentItem ammo)
	{
		if (weapon == null || ammo == null || !GearEffects.usesAmmoSlot(weapon))
		{
			return false;
		}
		String w = GearEffects.lowerName(weapon);
		String a = GearEffects.lowerName(ammo);
		if ("Bow".equals(weapon.getCategory()))
		{
			return bowAccepts(w, a);
		}
		return crossbowAccepts(w, a);
	}

	/**
	 * Darts a blowpipe can be loaded with. The toxic, blazing and drygore blowpipes take every dart (the drygore no
	 * poisoned ones); the Sailing blowpipes only go up to adamant (camphor, ironwood) or rune (rosewood).
	 */
	static boolean blowpipeAccepts(EquipmentItem blowpipe, EquipmentItem dart)
	{
		if (blowpipe == null || dart == null)
		{
			return false;
		}
		String d = GearEffects.lowerName(dart);
		int tier = d.startsWith("black ") ? STEEL : metalTier(d);
		if (!d.endsWith(" dart") || tier == NONE)
		{
			return false;
		}
		String b = GearEffects.lowerName(blowpipe);
		if (b.startsWith("camphor blowpipe") || b.startsWith("ironwood blowpipe"))
		{
			return tier <= ADAMANT;
		}
		if (b.startsWith("rosewood blowpipe"))
		{
			return tier <= RUNE;
		}
		if (b.startsWith("drygore blowpipe"))
		{
			return dart.getVersion() == null || !dart.getVersion().startsWith("Poison");
		}
		return true;
	}

	// ---- Bows ----

	private static boolean bowAccepts(String bow, String ammo)
	{
		if (bow.startsWith("eclipse atlatl"))
		{
			return ammo.startsWith("atlatl dart");
		}
		if (bow.startsWith("ogre bow"))
		{
			return ammo.startsWith("ogre arrow");
		}
		if (bow.startsWith("comp ogre bow"))
		{
			return ammo.startsWith("ogre arrow") || ammo.endsWith(" brutal");
		}
		if (bow.startsWith("training bow"))
		{
			return ammo.startsWith("training arrows");
		}
		if (ammo.startsWith("broad arrow") || ammo.startsWith("seeking broad arrow"))
		{
			// Broad arrows need a magic bow or stronger, or the bone shortbow
			return bowTier(bow) >= AMETHYST || bow.startsWith("bone shortbow");
		}
		int tier = arrowTier(ammo);
		return tier != NONE && tier <= bowTier(bow);
	}

	/**
	 * @return the tier of a standard arrow, or NONE for anything a regular bow cannot fire
	 */
	private static int arrowTier(String ammo)
	{
		if (!ammo.contains("arrow") || ammo.startsWith("ogre") || ammo.startsWith("training") || ammo.endsWith(" brutal")
			|| ammo.startsWith("bullet") || ammo.startsWith("field") || ammo.startsWith("blunt")
			|| ammo.startsWith("castle wars") || ammo.startsWith("barbed"))
		{
			return NONE;
		}
		if (ammo.startsWith("ice arrows"))
		{
			return BRONZE;
		}
		// Fire and seeking arrows share their metal's tier
		String metal = ammo.startsWith("seeking ") ? ammo.substring("seeking ".length()) : ammo;
		return metalTier(metal);
	}

	private static int bowTier(String bow)
	{
		if (bow.startsWith("dark bow") || bow.startsWith("twisted bow") || bow.startsWith("3rd age bow")
			|| bow.startsWith("venator bow") || bow.startsWith("echo venator bow") || bow.startsWith("scorching bow")
			|| bow.startsWith("nature's recurve"))
		{
			return DRAGON;
		}
		if (bow.startsWith("magic") || bow.startsWith("seercull"))
		{
			return AMETHYST;
		}
		if (bow.startsWith("yew"))
		{
			return RUNE;
		}
		if (bow.startsWith("maple"))
		{
			return ADAMANT;
		}
		if (bow.startsWith("willow"))
		{
			return MITHRIL;
		}
		if (bow.startsWith("oak") || bow.startsWith("signed oak"))
		{
			return STEEL;
		}
		// Shortbow, longbow, bone shortbow, cursed goblin bow, rain bow and anything unknown
		return IRON;
	}

	// ---- Crossbows and ballistas ----

	private static boolean crossbowAccepts(String crossbow, String ammo)
	{
		if (crossbow.contains("ballista"))
		{
			if (crossbow.startsWith("silvthrill"))
			{
				return ammo.startsWith("silvthrill javelin");
			}
			if (!ammo.endsWith("javelin") || ammo.startsWith("silvthrill"))
			{
				return false;
			}
			// Dragon javelins need the heavy ballista
			return !ammo.startsWith("dragon") || crossbow.startsWith("heavy ballista");
		}
		if (crossbow.startsWith("karil's crossbow"))
		{
			return ammo.startsWith("bolt rack");
		}
		if (crossbow.startsWith("hunters' sunlight crossbow"))
		{
			return ammo.startsWith("sunlight antler bolts") || ammo.startsWith("moonlight antler bolts");
		}
		if (crossbow.startsWith("hunters' crossbow"))
		{
			return ammo.startsWith("kebbit bolts") || ammo.startsWith("long kebbit bolts");
		}
		if (crossbow.startsWith("dorgeshuun crossbow"))
		{
			// Bone bolts and plain metal bolts up to iron; no silver and no gem-tipped bolts
			return ammo.startsWith("bone bolts") || ammo.startsWith("bronze bolts") || ammo.startsWith("iron bolts")
				|| ammo.startsWith("blurite bolts");
		}
		if (ammo.startsWith("broad bolts") || ammo.startsWith("amethyst broad bolts"))
		{
			// Only these five crossbows can fire broad bolts
			return crossbow.startsWith("rune crossbow") || crossbow.startsWith("dragon crossbow") || crossbow.startsWith("dragon hunter crossbow")
				|| crossbow.startsWith("armadyl crossbow") || crossbow.startsWith("zaryte crossbow");
		}
		if (ammo.contains("ascension bolts"))
		{
			return crossbow.startsWith("ascension crossbow");
		}
		int tier = boltTier(ammo);
		return tier != NONE && tier <= crossbowTier(crossbow);
	}

	/**
	 * @return the tier of a standard bolt, or NONE for special bolts
	 */
	private static int boltTier(String ammo)
	{
		if (!ammo.contains("bolts") || ammo.startsWith("bolt rack") || ammo.contains("kebbit") || ammo.contains("antler")
			|| ammo.startsWith("bone bolts") || ammo.startsWith("castle wars") || ammo.startsWith("barbed"))
		{
			return NONE;
		}
		if (ammo.contains("dragon bolts"))
		{
			return DRAGON;
		}
		if (ammo.startsWith("silver"))
		{
			return BRONZE;
		}
		// Gem-tipped bolts use the tier of the metal bolt they are made from
		if (ammo.startsWith("dragonstone") || ammo.startsWith("onyx"))
		{
			return RUNE;
		}
		if (ammo.startsWith("ruby") || ammo.startsWith("diamond"))
		{
			return ADAMANT;
		}
		if (ammo.startsWith("sapphire") || ammo.startsWith("emerald"))
		{
			return MITHRIL;
		}
		if (ammo.startsWith("topaz"))
		{
			return STEEL;
		}
		if (ammo.startsWith("pearl") || ammo.startsWith("jade") || ammo.startsWith("blurite"))
		{
			return IRON;
		}
		if (ammo.startsWith("opal"))
		{
			return BRONZE;
		}
		return metalTier(ammo);
	}

	private static int crossbowTier(String crossbow)
	{
		if (crossbow.startsWith("dragon") || crossbow.startsWith("armadyl") || crossbow.startsWith("zaryte")
			|| crossbow.startsWith("ascension crossbow") || crossbow.startsWith("king's barrage"))
		{
			return DRAGON;
		}
		if (crossbow.startsWith("rune"))
		{
			return RUNE;
		}
		if (crossbow.startsWith("adamant"))
		{
			return ADAMANT;
		}
		if (crossbow.startsWith("mithril"))
		{
			return MITHRIL;
		}
		if (crossbow.startsWith("steel"))
		{
			return STEEL;
		}
		if (crossbow.startsWith("iron") || crossbow.startsWith("blurite"))
		{
			return IRON;
		}
		// Bronze crossbow, plain crossbow, phoenix crossbow
		return BRONZE;
	}

	private static int metalTier(String name)
	{
		if (name.startsWith("dragon"))
		{
			return DRAGON;
		}
		if (name.startsWith("amethyst"))
		{
			return AMETHYST;
		}
		if (name.startsWith("rune") || name.startsWith("runite"))
		{
			return RUNE;
		}
		if (name.startsWith("adamant"))
		{
			return ADAMANT;
		}
		if (name.startsWith("mithril"))
		{
			return MITHRIL;
		}
		if (name.startsWith("steel"))
		{
			return STEEL;
		}
		if (name.startsWith("iron"))
		{
			return IRON;
		}
		if (name.startsWith("bronze"))
		{
			return BRONZE;
		}
		return NONE;
	}
}
