package com.gearoptimizer.calc;

import com.gearoptimizer.data.EquipmentItem;
import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.Monster;

/**
 * Detects passive gear effects by item name, so every colour/ornament/imbue variant is covered.
 */
final class GearEffects
{
	private GearEffects()
	{
	}

	static String lowerName(EquipmentItem item)
	{
		return item == null ? "" : item.getLowerName();
	}

	static boolean hasSlayerHelm(Loadout loadout)
	{
		String head = lowerName(loadout.stats(GearSlot.HEAD));
		return head.contains("slayer helmet") || head.startsWith("black mask");
	}

	static boolean hasImbuedSlayerHelm(Loadout loadout)
	{
		return hasSlayerHelm(loadout) && lowerName(loadout.stats(GearSlot.HEAD)).contains("(i)");
	}

	enum Salve
	{
		NONE, PLAIN, E, I, EI
	}

	static Salve salve(Loadout loadout)
	{
		String neck = lowerName(loadout.stats(GearSlot.NECK)).replace(" ", "");
		if (!neck.startsWith("salveamulet"))
		{
			return Salve.NONE;
		}
		if (neck.contains("(ei)"))
		{
			return Salve.EI;
		}
		if (neck.contains("(e)"))
		{
			return Salve.E;
		}
		if (neck.contains("(i)"))
		{
			return Salve.I;
		}
		return Salve.PLAIN;
	}

	/**
	 * @return true if wearing a full void set with the helm for the style
	 */
	static boolean hasVoid(Loadout loadout, String helm)
	{
		return lowerName(loadout.stats(GearSlot.HEAD)).startsWith(helm)
			&& isVoidPiece(loadout.stats(GearSlot.BODY), "top")
			&& isVoidPiece(loadout.stats(GearSlot.LEGS), "robe")
			&& lowerName(loadout.stats(GearSlot.HANDS)).startsWith("void knight gloves");
	}

	static boolean hasEliteVoid(Loadout loadout, String helm)
	{
		return hasVoid(loadout, helm)
			&& lowerName(loadout.stats(GearSlot.BODY)).startsWith("elite void")
			&& lowerName(loadout.stats(GearSlot.LEGS)).startsWith("elite void");
	}

	private static boolean isVoidPiece(EquipmentItem item, String piece)
	{
		String name = lowerName(item);
		return name.startsWith("void knight " + piece) || name.startsWith("elite void " + piece);
	}

	static boolean hasObsidianSet(Loadout loadout)
	{
		return lowerName(loadout.stats(GearSlot.HEAD)).startsWith("obsidian helmet")
			&& lowerName(loadout.stats(GearSlot.BODY)).startsWith("obsidian platebody")
			&& lowerName(loadout.stats(GearSlot.LEGS)).startsWith("obsidian platelegs");
	}

	static boolean hasTzhaarWeapon(Loadout loadout)
	{
		String weapon = lowerName(loadout.weapon());
		return weapon.startsWith("toktz-") || weapon.startsWith("tzhaar-ket-");
	}

	/**
	 * @return inquisitor's crush bonus in half-percents: helm 1, hauberk 2, plateskirt 2
	 */
	static int inquisitorBonus(Loadout loadout)
	{
		int bonus = 0;
		if (lowerName(loadout.stats(GearSlot.HEAD)).startsWith("inquisitor's great helm"))
		{
			bonus += 1;
		}
		if (lowerName(loadout.stats(GearSlot.BODY)).startsWith("inquisitor's hauberk"))
		{
			bonus += 2;
		}
		if (lowerName(loadout.stats(GearSlot.LEGS)).startsWith("inquisitor's plateskirt"))
		{
			bonus += 2;
		}
		return bonus;
	}

	static boolean hasFullAhrims(Loadout loadout)
	{
		return lowerName(loadout.stats(GearSlot.HEAD)).startsWith("ahrim's hood")
			&& lowerName(loadout.stats(GearSlot.BODY)).startsWith("ahrim's robetop")
			&& lowerName(loadout.stats(GearSlot.LEGS)).startsWith("ahrim's robeskirt")
			&& lowerName(loadout.stats(GearSlot.NECK)).startsWith("amulet of the damned");
	}

	/**
	 * @return crystal armour's accuracy bonus in percent with a crystal bow or bowfa: helm 5, body 15, legs 10
	 */
	static int crystalAccuracyBonus(Loadout loadout)
	{
		if (!isCrystalBow(loadout.weapon()))
		{
			return 0;
		}
		return (lowerName(loadout.stats(GearSlot.HEAD)).startsWith("crystal helm") ? 5 : 0)
			+ (lowerName(loadout.stats(GearSlot.BODY)).startsWith("crystal body") ? 15 : 0)
			+ (lowerName(loadout.stats(GearSlot.LEGS)).startsWith("crystal legs") ? 10 : 0);
	}

	/**
	 * @return crystal armour's damage bonus in tenths of a percent with a crystal bow or bowfa: half its accuracy bonus
	 */
	static int crystalDamageBonus(Loadout loadout)
	{
		return crystalAccuracyBonus(loadout) * 5;
	}

	/**
	 * Weapons that hurt vampyres, with the damage the DPS calculator team measured in game and the highest vampyre
	 * tier each one can harm. The blisterwood stake works with any style, the others only in melee.
	 */
	enum Vampyrebane
	{
		SUNSPEAR("sunspear", 1.5, 3, true),
		BLISTERWOOD_FLAIL("blisterwood flail", 1.25, 3, true),
		HALLOWED_FLAIL("hallowed flail", 1.25, 3, true),
		BLISTERWOOD_STAKE("blisterwood stake", 1.25, 3, false),
		IVANDIS_FLAIL("ivandis flail", 1.2, 3, true),
		BLISTERWOOD_SICKLE("blisterwood sickle", 1.15, 3, true),
		ROD_OF_IVANDIS("rod of ivandis", 1.1, 2, true);

		private final String prefix;
		final double damage;
		final int maxTier;
		final boolean meleeOnly;

		Vampyrebane(String prefix, double damage, int maxTier, boolean meleeOnly)
		{
			this.prefix = prefix;
			this.damage = damage;
			this.maxTier = maxTier;
			this.meleeOnly = meleeOnly;
		}

		/**
		 * @return the vampyrebane weapon with this lower-case name, or null
		 */
		static Vampyrebane of(String weaponName)
		{
			for (Vampyrebane bane : values())
			{
				if (weaponName.startsWith(bane.prefix))
				{
					return bane;
				}
			}
			return null;
		}
	}

	/**
	 * @return the monster's vampyre tier (1 to 3), or 0 if it isn't a vampyre
	 */
	static int vampyreTier(Monster monster)
	{
		return monster.hasAttribute("vampyre3") ? 3 : monster.hasAttribute("vampyre2") ? 2 : monster.hasAttribute("vampyre1") ? 1 : 0;
	}

	static boolean isCrystalBow(EquipmentItem weapon)
	{
		String name = lowerName(weapon);
		return name.startsWith("crystal bow") || name.startsWith("bow of faerdhinen");
	}

	static boolean isAtlatl(EquipmentItem weapon)
	{
		return lowerName(weapon).startsWith("eclipse atlatl");
	}

	/**
	 * Bows that fire without ammunition (crystal-based and wilderness bows).
	 */
	static boolean isAmmoFreeBow(EquipmentItem weapon)
	{
		String name = lowerName(weapon);
		return isCrystalBow(weapon) || name.startsWith("corrupted bow") || name.startsWith("craw's")
			|| name.startsWith("webweaver") || name.startsWith("starter bow");
	}

	/**
	 * @return whether this ranged weapon fires ammunition from the ammo slot
	 */
	static boolean usesAmmoSlot(EquipmentItem weapon)
	{
		if (weapon == null)
		{
			return false;
		}
		String category = weapon.getCategory();
		return ("Bow".equals(category) && !isAmmoFreeBow(weapon)) || "Crossbow".equals(category);
	}

	/**
	 * @return true for arrows, bolts, javelins and other ammunition that is fired by a weapon
	 */
	static boolean isProjectile(EquipmentItem ammo)
	{
		String name = lowerName(ammo);
		return name.contains("arrow") || name.contains("bolts") || name.contains("javelin") || name.startsWith("atlatl dart")
			|| name.startsWith("bolt rack") || name.endsWith(" brutal");
	}

	static String weaponCategory(Loadout loadout)
	{
		return loadout.weapon() == null ? "Unarmed" : loadout.weapon().getCategory();
	}

	static int virtusPieces(Loadout loadout)
	{
		int pieces = 0;
		if (lowerName(loadout.stats(GearSlot.HEAD)).startsWith("virtus mask"))
		{
			pieces++;
		}
		if (lowerName(loadout.stats(GearSlot.BODY)).startsWith("virtus robe top"))
		{
			pieces++;
		}
		if (lowerName(loadout.stats(GearSlot.LEGS)).startsWith("virtus robe bottom"))
		{
			pieces++;
		}
		return pieces;
	}
}
