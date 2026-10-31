package com.gearoptimizer.calc;

import com.gearoptimizer.data.EquipmentItem;
import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.Monster;
import com.gearoptimizer.data.OwnedItem;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Skill;

/**
 * OSRS damage-per-second formulas, following the OSRS Wiki DPS calculator.
 */
public final class DpsCalculator
{

	private DpsCalculator()
	{
	}

	public static DpsResult calculate(Loadout loadout, CombatContext ctx)
	{
		Stance stance = loadout.getStance();
		if (stance == null)
		{
			return DpsResult.NONE;
		}
		DpsResult player;
		switch (stance.getStyle())
		{
			case MELEE:
				player = melee(loadout, ctx);
				break;
			case RANGED:
				player = ranged(loadout, ctx);
				break;
			default:
				player = magic(loadout, ctx);
				break;
		}
		return Cannon.addTo(player, loadout, ctx);
	}

	/**
	 * Mutable accumulator for the attack roll and max hit while bonuses are applied in game order.
	 */
	private static final class Roll
	{
		long attack;
		int max;
		final List<String> effects = new ArrayList<>();

		void both(int numerator, int denominator, String effect)
		{
			attack = attack * numerator / denominator;
			max = max * numerator / denominator;
			effects.add(effect);
		}

		void accuracy(int numerator, int denominator, String effect)
		{
			attack = attack * numerator / denominator;
			effects.add(effect);
		}

		void damage(int numerator, int denominator, String effect)
		{
			max = max * numerator / denominator;
			effects.add(effect);
		}
	}

	private static DpsResult melee(Loadout loadout, CombatContext ctx)
	{
		Stance stance = loadout.getStance();
		Monster monster = ctx.getMonster();
		PrayerBoost prayer = ctx.getPrayer();
		EquipmentItem weapon = loadout.weapon();
		String weaponName = GearEffects.lowerName(weapon);

		int effAtt = (int) (ctx.boostedLevel(Skill.ATTACK) * prayer.getAccuracy()) + stance.getAttackBonus() + 8;
		int effStr = (int) (ctx.boostedLevel(Skill.STRENGTH) * prayer.getStrength()) + stance.getStrengthBonus() + 8;
		boolean voidMelee = GearEffects.hasVoid(loadout, "void melee helm");
		if (voidMelee)
		{
			effAtt = effAtt * 11 / 10;
			effStr = effStr * 11 / 10;
		}

		Roll roll = new Roll();
		roll.attack = (long) effAtt * (attackBonus(loadout, stance.getType()) + 64);
		roll.max = (effStr * (loadout.total(i -> i.getBonuses().getStr()) + 64) + 320) / 640;
		if (voidMelee)
		{
			roll.effects.add("Void melee +10%");
		}

		GearEffects.Salve salve = GearEffects.salve(loadout);
		if (monster.isUndead() && (salve == GearEffects.Salve.E || salve == GearEffects.Salve.EI))
		{
			roll.both(6, 5, "Salve amulet (e) +20%");
		}
		else if (monster.isUndead() && salve != GearEffects.Salve.NONE)
		{
			roll.both(7, 6, "Salve amulet +16.7%");
		}
		else if (ctx.isOnSlayerTask() && GearEffects.hasSlayerHelm(loadout))
		{
			roll.both(7, 6, "Slayer helm +16.7%");
		}

		if (GearEffects.hasObsidianSet(loadout) && GearEffects.hasTzhaarWeapon(loadout))
		{
			roll.both(11, 10, "Obsidian set +10%");
		}
		if (GearEffects.lowerName(loadout.stats(GearSlot.NECK)).startsWith("berserker necklace") && GearEffects.hasTzhaarWeapon(loadout))
		{
			roll.damage(6, 5, "Berserker necklace +20%");
		}
		if (monster.isDragon() && weaponName.startsWith("dragon hunter lance"))
		{
			roll.both(6, 5, "Dragonbane +20%");
		}
		else if (monster.isDragon() && weaponName.startsWith("dragon hunter wand"))
		{
			// Like the wiki DPS calculator: the wand's dragonbane also applies when bashing with it
			roll.accuracy(7, 4, "Dragonbane +75% accuracy");
			roll.damage(7, 5, "Dragonbane +40% damage");
		}
		if (monster.isDemon())
		{
			if (weaponName.startsWith("arclight") || weaponName.startsWith("emberlight"))
			{
				roll.both(170, 100, "Demonbane +70%");
			}
			else if (weaponName.startsWith("darklight") || weaponName.startsWith("silverlight"))
			{
				roll.both(160, 100, "Demonbane +60%");
			}
			else if (weaponName.startsWith("bone claws") || weaponName.startsWith("burning claws"))
			{
				roll.both(105, 100, "Demonbane +5%");
			}
		}
		if (monster.hasAttribute("kalphite") && weaponName.startsWith("keris"))
		{
			if (weaponName.startsWith("keris partisan of breaching"))
			{
				roll.accuracy(133, 100, "Keris of breaching +33% accuracy");
			}
			if (weaponName.startsWith("keris partisan of amascut"))
			{
				roll.damage(115, 100, "Keris +15% vs kalphites");
			}
			else
			{
				roll.damage(133, 100, "Keris +33% vs kalphites");
			}
		}
		if (GearEffects.vampyreTier(monster) > 0)
		{
			if (weaponName.startsWith("blisterwood flail") || weaponName.startsWith("blisterwood sickle"))
			{
				roll.accuracy(105, 100, "Vampyrebane +5% accuracy");
			}
			else if (weaponName.startsWith("hallowed flail") || weaponName.startsWith("sunspear"))
			{
				roll.accuracy(125, 100, "Vampyrebane +25% accuracy");
			}
			if (isSilverMelee(weaponName) && wearsEfaritay(loadout))
			{
				roll.accuracy(23, 20, "Efaritay's aid +15% accuracy");
			}
		}
		if (monster.hasAttribute("golem") && weaponName.startsWith("granite hammer"))
		{
			roll.both(13, 10, "Golembane +30%");
		}
		else if (monster.hasAttribute("golem") && weaponName.startsWith("barronite mace"))
		{
			roll.damage(23, 20, "Golembane +15%");
		}
		if (ctx.isInWilderness() && (weaponName.startsWith("viggora's chainmace") || weaponName.startsWith("ursine chainmace")))
		{
			roll.both(3, 2, "Wilderness weapon +50%");
		}
		if (monster.hasAttribute("leafy") && weaponName.startsWith("leaf-bladed battleaxe"))
		{
			roll.damage(47, 40, "Leaf-bladed battleaxe +17.5%");
		}
		if (weaponName.startsWith("colossal blade"))
		{
			int bonus = Math.min(monster.getSize() * 2, 10);
			roll.max += bonus;
			roll.effects.add("Colossal blade +" + bonus + " max hit (size)");
		}
		if (monster.hasAttribute("rat") && weaponName.startsWith("bone mace"))
		{
			roll.max += 10;
			roll.effects.add("Ratbane +10 max hit");
		}
		if (stance.getType() == AttackType.CRUSH)
		{
			int bonus = GearEffects.inquisitorBonus(loadout);
			if (bonus > 0)
			{
				roll.both(200 + bonus, 200, "Inquisitor's +" + (bonus / 2.0) + "%");
			}
		}

		long defRoll = npcDefenceRoll(monster, stance.getType(), loadout);
		double accuracy;
		double averageHit;
		if (weaponName.startsWith("osmumten's fang") && stance.getType() == AttackType.STAB)
		{
			accuracy = fangHitChance(roll.attack, defRoll);
			int min = roll.max * 15 / 100;
			int max = roll.max - min;
			averageHit = accuracy * (min + max) / 2.0;
			roll.effects.add("Fang double accuracy roll");
		}
		else
		{
			accuracy = hitChance(roll.attack, defRoll);
			averageHit = accuracy * roll.max / 2.0;
			if (weaponName.contains("scythe of vitur") && monster.getSize() > 1)
			{
				averageHit += accuracy * (roll.max / 2) / 2.0;
				if (monster.getSize() > 2)
				{
					averageHit += accuracy * (roll.max / 4) / 2.0;
				}
				roll.effects.add("Scythe extra hits");
			}
		}
		if (monster.hasAttribute("kalphite") && weaponName.startsWith("keris"))
		{
			// 1 in 51 hits deals triple damage
			averageHit *= 53.0 / 51.0;
			roll.effects.add("Keris 1/51 triple-damage hit");
		}
		if (monster.hasAttribute("shade") && weaponName.startsWith("gadderhammer"))
		{
			// +25% damage, or a 5% chance of double damage
			averageHit *= 0.95 * 1.25 + 0.05 * 2;
			roll.effects.add("Gadderhammer vs shades");
		}
		averageHit *= vampyreDamage(monster, loadout, weaponName, true, roll);
		averageHit = applyFlatArmour(averageHit, accuracy, monster);

		int speed = Math.max(1, weaponSpeed(weapon) + stance.getSpeedDelta());
		return result(averageHit, roll, accuracy, speed, defRoll);
	}

	/**
	 * Vampyre damage multipliers, tested in game by the DPS calculator team.
	 */
	private static double vampyreDamage(Monster monster, Loadout loadout, String weaponName, boolean melee, Roll roll)
	{
		int tier = GearEffects.vampyreTier(monster);
		if (tier == 0)
		{
			return 1;
		}
		double efaritay = wearsEfaritay(loadout) ? 1.1 : 1;
		double factor;
		GearEffects.Vampyrebane bane = GearEffects.Vampyrebane.of(weaponName);
		if (bane != null && bane.maxTier >= tier)
		{
			factor = bane.damage;
		}
		else if (melee && isSilverMelee(weaponName) && tier == 1)
		{
			factor = 1.1;
		}
		else if (tier == 2 && wearsEfaritay(loadout))
		{
			// Any other weapon only deals half damage to tier 2 vampyres, and only with Efaritay's aid
			roll.effects.add("Efaritay's aid: half damage with a non-vampyrebane weapon");
			return 0.5;
		}
		else
		{
			return 1;
		}
		roll.effects.add(String.format("Vampyrebane x%.2f", factor * efaritay));
		return factor * efaritay;
	}

	static boolean wearsEfaritay(Loadout loadout)
	{
		return GearEffects.lowerName(loadout.stats(GearSlot.RING)).startsWith("efaritay's aid");
	}

	static boolean isSilverMelee(String weaponName)
	{
		return weaponName.startsWith("blessed axe") || weaponName.startsWith("ivandis flail") || weaponName.startsWith("blisterwood flail")
			|| weaponName.startsWith("hallowed flail") || weaponName.contains("sickle") || weaponName.startsWith("silverlight")
			|| weaponName.startsWith("darklight") || weaponName.startsWith("rod of ivandis") || weaponName.startsWith("sunspear");
	}

	private static DpsResult ranged(Loadout loadout, CombatContext ctx)
	{
		Stance stance = loadout.getStance();
		Monster monster = ctx.getMonster();
		PrayerBoost prayer = ctx.getPrayer();
		EquipmentItem weapon = loadout.weapon();
		String weaponName = GearEffects.lowerName(weapon);
		int rangedLevel = ctx.boostedLevel(Skill.RANGED);

		int effAcc = (int) (rangedLevel * prayer.getAccuracy()) + stance.getAttackBonus() + 8;
		int effStr = (int) (rangedLevel * prayer.getStrength()) + stance.getStrengthBonus() + 8;
		boolean voidRanged = GearEffects.hasVoid(loadout, "void ranger helm");
		boolean eliteVoid = voidRanged && GearEffects.hasEliteVoid(loadout, "void ranger helm");
		if (voidRanged)
		{
			effAcc = effAcc * 11 / 10;
			effStr = eliteVoid ? effStr * 9 / 8 : effStr * 11 / 10;
		}

		int strengthBonus = rangedStrength(loadout);
		if (GearEffects.isAtlatl(weapon))
		{
			// The atlatl's max hit uses Strength level and melee strength bonus instead of Ranged
			effStr = (int) (ctx.boostedLevel(Skill.STRENGTH) * prayer.getStrength()) + stance.getStrengthBonus() + 8;
			if (voidRanged)
			{
				effStr = eliteVoid ? effStr * 9 / 8 : effStr * 11 / 10;
			}
			strengthBonus = loadout.total(i -> i.getBonuses().getStr());
		}

		Roll roll = new Roll();
		roll.attack = (long) effAcc * (loadout.total(i -> i.getOffensive().getRanged()) + 64);
		roll.max = (int) (0.5 + effStr * (strengthBonus + 64) / 640.0);
		if (GearEffects.isAtlatl(weapon))
		{
			roll.effects.add("Atlatl: max hit from Strength");
		}
		if (voidRanged)
		{
			roll.effects.add(eliteVoid ? "Elite void ranged +12.5%" : "Void ranged +10%");
		}

		GearEffects.Salve salve = GearEffects.salve(loadout);
		if (monster.isUndead() && salve == GearEffects.Salve.EI)
		{
			roll.both(6, 5, "Salve amulet (ei) +20%");
		}
		else if (monster.isUndead() && salve == GearEffects.Salve.I)
		{
			roll.both(7, 6, "Salve amulet (i) +16.7%");
		}
		boolean dragonbane = monster.isDragon() && weaponName.startsWith("dragon hunter crossbow");
		boolean demonbane = monster.isDemon() && weaponName.startsWith("scorching bow");
		boolean slayerRanged = !(monster.isUndead() && (salve == GearEffects.Salve.EI || salve == GearEffects.Salve.I))
			&& ctx.isOnSlayerTask() && GearEffects.hasImbuedSlayerHelm(loadout);
		if (slayerRanged)
		{
			roll.accuracy(23, 20, "Imbued slayer helm +15% accuracy");
		}

		if (weaponName.startsWith("twisted bow"))
		{
			Monster.Offensive offensive = monster.getOffensive();
			int magic = Math.min(250, Math.max(monster.getSkills().getMagic(), offensive == null ? 0 : offensive.getMagic()));
			int m = 3 * magic / 10;
			int accuracyPercent = Math.min(140, 140 + (3 * magic - 10) / 100 - (m - 100) * (m - 100) / 100);
			int damagePercent = Math.min(250, 250 + (3 * magic - 14) / 100 - (m - 140) * (m - 140) / 100);
			roll.attack = roll.attack * accuracyPercent / 100;
			roll.max = roll.max * damagePercent / 100;
			roll.effects.add("Twisted bow: " + accuracyPercent + "% accuracy, " + damagePercent + "% damage");
		}
		if (dragonbane)
		{
			roll.accuracy(13, 10, "Dragonbane +30% accuracy");
		}
		if (demonbane)
		{
			roll.accuracy(130, 100, "Demonbane +30% accuracy");
		}
		// Slayer helm damage is additive with dragonbane and demonbane, multiplicative otherwise
		if (slayerRanged)
		{
			int numerator = 23 + (dragonbane ? 5 : 0) + (demonbane ? 6 : 0);
			roll.damage(numerator, 20, "Imbued slayer helm +15% damage");
		}
		else
		{
			if (dragonbane)
			{
				roll.damage(5, 4, "Dragonbane +25% damage");
			}
			if (demonbane)
			{
				roll.damage(130, 100, "Demonbane +30% damage");
			}
		}
		if (monster.hasAttribute("rat") && weaponName.startsWith("bone shortbow"))
		{
			roll.max += 10;
			roll.effects.add("Ratbane +10 max hit");
		}
		boolean tonalztics = weaponName.startsWith("tonalztics of ralos");
		if (tonalztics)
		{
			roll.max -= roll.max / 4;
		}
		if (ctx.isInWilderness() && (weaponName.startsWith("craw's bow") || weaponName.startsWith("webweaver bow")))
		{
			roll.both(3, 2, "Wilderness weapon +50%");
		}
		int crystalAccuracy = GearEffects.crystalAccuracyBonus(loadout);
		if (crystalAccuracy > 0)
		{
			int crystalDamage = GearEffects.crystalDamageBonus(loadout);
			roll.attack = roll.attack * (100 + crystalAccuracy) / 100;
			roll.max = roll.max * (1000 + crystalDamage) / 1000;
			roll.effects.add("Crystal armour +" + crystalAccuracy + "% accuracy, +" + (crystalDamage / 10.0) + "% damage");
		}

		long defRoll = npcDefenceRoll(monster, AttackType.RANGED, loadout);
		double accuracy = hitChance(roll.attack, defRoll);
		double averageHit = boltAverage(loadout, ctx, roll, accuracy, rangedLevel);
		if (weaponName.startsWith("dark bow"))
		{
			// Fires two arrows per attack, each rolled separately
			averageHit *= 2;
			roll.effects.add("Dark bow fires two arrows");
		}
		if (weapon != null && "Chinchompas".equals(weapon.getCategory()) && ctx.areaTargets() > 1)
		{
			averageHit *= ctx.areaTargets();
			roll.effects.add("Hits " + ctx.areaTargets() + " monsters (3x3 area)");
		}
		if (tonalztics)
		{
			averageHit *= 2;
			roll.effects.add("Tonalztics: two hits at 75% max");
		}
		if (GearEffects.vampyreTier(monster) > 0 && weaponName.startsWith("blisterwood stake"))
		{
			averageHit *= vampyreDamage(monster, loadout, weaponName, false, roll);
		}
		averageHit = applyFlatArmour(averageHit, accuracy, monster);

		int speed = Math.max(1, weaponSpeed(weapon) + stance.getSpeedDelta());
		return result(averageHit, roll, accuracy, speed, defRoll);
	}

	private static double boltAverage(Loadout loadout, CombatContext ctx, Roll roll, double accuracy, int rangedLevel)
	{
		double normal = accuracy * roll.max / 2.0;
		EquipmentItem weapon = loadout.weapon();
		if (weapon == null || !"Crossbow".equals(weapon.getCategory()))
		{
			return normal;
		}
		String ammo = GearEffects.lowerName(loadout.stats(GearSlot.AMMO));
		if (!ammo.contains("bolts (e)"))
		{
			return normal;
		}
		Monster monster = ctx.getMonster();
		double chanceMultiplier = ctx.isKandarinHardDiary() ? 1.1 : 1.0;
		boolean zaryte = GearEffects.lowerName(weapon).startsWith("zaryte crossbow");

		// Bolt effects follow the OSRS Wiki DPS calculator; the Zaryte crossbow strengthens every one of them
		if (ammo.startsWith("ruby"))
		{
			double chance = 0.06 * chanceMultiplier;
			int proc = Math.min(zaryte ? 110 : 100, monster.getHitpoints() * (zaryte ? 22 : 20) / 100);
			roll.effects.add("Ruby bolts (e): " + proc + " damage on proc");
			return chance * proc + (1 - chance) * normal;
		}
		if (ammo.startsWith("diamond"))
		{
			double chance = 0.10 * chanceMultiplier;
			int procMax = roll.max * (zaryte ? 126 : 115) / 100;
			roll.effects.add("Diamond bolts (e)");
			return chance * procMax / 2.0 + (1 - chance) * normal;
		}
		if (ammo.startsWith("onyx") && !monster.isUndead())
		{
			double chance = 0.11 * chanceMultiplier;
			int procMax = roll.max * (zaryte ? 132 : 120) / 100;
			roll.effects.add("Onyx bolts (e)");
			return accuracy * (chance * procMax / 2.0 + (1 - chance) * roll.max / 2.0);
		}
		if (ammo.startsWith("dragonstone") && !monster.isDragon() && !monster.hasAttribute("fiery"))
		{
			double chance = 0.06 * chanceMultiplier;
			roll.effects.add("Dragonstone bolts (e)");
			return normal + accuracy * chance * (rangedLevel * 2 / (zaryte ? 9 : 10));
		}
		if (ammo.startsWith("opal"))
		{
			double chance = 0.05 * chanceMultiplier;
			roll.effects.add("Opal bolts (e)");
			return normal + chance * (rangedLevel / (zaryte ? 9 : 10));
		}
		if (ammo.startsWith("pearl"))
		{
			double chance = 0.06 * chanceMultiplier;
			int divisor = monster.hasAttribute("fiery") ? 15 : 20;
			roll.effects.add("Pearl bolts (e)");
			return normal + chance * (rangedLevel / (zaryte ? divisor - 2 : divisor));
		}
		return normal;
	}

	private static DpsResult magic(Loadout loadout, CombatContext ctx)
	{
		Stance stance = loadout.getStance();
		Monster monster = ctx.getMonster();
		PrayerBoost prayer = ctx.getPrayer();
		EquipmentItem weapon = loadout.weapon();
		String weaponName = GearEffects.lowerName(weapon);
		Spell spell = loadout.getSpell();
		int magicLevel = ctx.boostedLevel(Skill.MAGIC);

		int base;
		int speed;
		if (spell == null)
		{
			base = poweredStaffMaxHit(weaponName, magicLevel);
			speed = weaponSpeed(weapon);
		}
		else
		{
			base = spell.getBaseMaxHit();
			speed = 5;
			if (spell == Spell.MAGIC_DART)
			{
				base = weaponName.startsWith("slayer's staff (e)") && ctx.isOnSlayerTask()
					? 13 + magicLevel / 6
					: 10 + magicLevel / 10;
			}
			if (spell.name().endsWith("_BOLT") && GearEffects.lowerName(loadout.stats(GearSlot.HANDS)).startsWith("chaos gauntlets"))
			{
				base += 3;
			}
			if (spell.getSpellbook() == Spell.Spellbook.STANDARD && weaponName.startsWith("harmonised nightmare staff"))
			{
				speed = 4;
			}
		}
		if (base <= 0)
		{
			return DpsResult.NONE;
		}

		int effMagic = (int) (magicLevel * prayer.getAccuracy()) + stance.getAttackBonus() + 9;
		boolean voidMagic = GearEffects.hasVoid(loadout, "void mage helm");
		if (voidMagic)
		{
			effMagic = effMagic * 145 / 100;
		}

		boolean shadow = weaponName.startsWith("tumeken's shadow");
		int magicAttack = loadout.total(i -> i.getOffensive().getMagic());
		int magicDamage = loadout.total(i -> i.getBonuses().getMagicStr());
		if (shadow)
		{
			magicAttack *= 3;
			magicDamage = Math.min(1000, magicDamage * 3);
		}
		magicDamage += prayer.getMagicDamage() * 10;
		// Virtus: each piece adds another 3% magic damage to Ancient Magicks (5% per piece in total)
		int virtus = spell != null && spell.getSpellbook() == Spell.Spellbook.ANCIENT ? GearEffects.virtusPieces(loadout) : 0;
		magicDamage += virtus * 30;
		if (voidMagic && GearEffects.hasEliteVoid(loadout, "void mage helm"))
		{
			magicDamage += 25;
		}

		Roll roll = new Roll();
		if (virtus > 0)
		{
			roll.effects.add("Virtus +" + (virtus * 3) + "% with Ancient Magicks");
		}
		roll.attack = (long) effMagic * (magicAttack + 64);
		if (voidMagic)
		{
			roll.effects.add("Void magic +45% accuracy");
		}
		if (shadow)
		{
			roll.effects.add("Tumeken's shadow triples magic bonuses");
		}

		GearEffects.Salve salve = GearEffects.salve(loadout);
		boolean slayerBoost = false;
		if (monster.isUndead() && salve == GearEffects.Salve.EI)
		{
			roll.accuracy(6, 5, "Salve amulet (ei) +20%");
			magicDamage += 200;
		}
		else if (monster.isUndead() && salve == GearEffects.Salve.I)
		{
			roll.accuracy(23, 20, "Salve amulet (i) +15%");
			magicDamage += 150;
		}
		else if (ctx.isOnSlayerTask() && GearEffects.hasImbuedSlayerHelm(loadout))
		{
			roll.accuracy(23, 20, "Imbued slayer helm +15%");
			slayerBoost = true;
		}

		if (spell != null && spell.getSpellbook() == Spell.Spellbook.STANDARD
			&& (weaponName.startsWith("smoke battlestaff") || weaponName.startsWith("mystic smoke staff")))
		{
			roll.accuracy(110, 100, "Smoke staff +10% accuracy, +10% damage");
			magicDamage += 100;
		}
		roll.max = base * (1000 + magicDamage) / 1000;
		if (slayerBoost)
		{
			roll.max = roll.max * 23 / 20;
		}


		if (spell != null && spell.getElement() != null)
		{
			String shield = GearEffects.lowerName(loadout.stats(GearSlot.SHIELD));
			if (shield.startsWith("tome of " + spell.getElement()))
			{
				roll.damage(11, 10, "Tome +10%");
			}
		}
		if (monster.isDragon() && weaponName.startsWith("dragon hunter wand"))
		{
			roll.accuracy(7, 4, "Dragonbane +75% accuracy");
			roll.damage(7, 5, "Dragonbane +40% damage");
		}
		if (spell != null && spell.isDemonbane() && monster.isDemon())
		{
			boolean purging = weaponName.startsWith("purging staff");
			roll.accuracy(purging ? 140 : 120, 100, purging ? "Demonbane spell +40% (Purging staff)" : "Demonbane spell +20% accuracy");
		}
		if (ctx.isInWilderness() && (weaponName.startsWith("thammaron's sceptre") || weaponName.startsWith("accursed sceptre")))
		{
			roll.both(3, 2, "Wilderness weapon +50%");
		}

		String element = spell != null ? spell.getElement() : null;
		Monster.Weakness weakness = monster.getWeakness();
		if (element != null && weakness != null && element.equals(weakness.getElement()) && weakness.getSeverity() > 0)
		{
			roll.attack = roll.attack * (100 + weakness.getSeverity()) / 100;
			roll.max += base * weakness.getSeverity() / 100;
			roll.effects.add("Weak to " + element + " +" + weakness.getSeverity() + "%");
		}

		long defRoll = npcDefenceRoll(monster, AttackType.MAGIC, loadout);
		double accuracy = hitChance(roll.attack, defRoll);
		double averageHit = applyFlatArmour(accuracy * roll.max / 2.0, accuracy, monster);
		if (spell != null && spell.isAoe() && ctx.areaTargets() > 1)
		{
			averageHit *= ctx.areaTargets();
			roll.effects.add("Hits " + ctx.areaTargets() + " monsters (3x3 area)");
		}

		return result(averageHit, roll, accuracy, Math.max(1, speed), defRoll);
	}

	/**
	 * @return the built-in spell max hit of a powered staff, or 0 if unknown
	 */
	static int poweredStaffMaxHit(String weaponName, int magicLevel)
	{
		if (weaponName.startsWith("tumeken's shadow"))
		{
			return magicLevel / 3 + 1;
		}
		if (weaponName.contains("sanguinesti staff"))
		{
			return magicLevel / 3;
		}
		if (weaponName.startsWith("bone staff"))
		{
			// Includes its +10 ratbane; it can only be used on rats
			return Math.max(1, magicLevel / 3 - 5) + 10;
		}
		if (weaponName.startsWith("starter staff"))
		{
			return 8;
		}
		if (weaponName.startsWith("trident of the swamp"))
		{
			return magicLevel / 3 - 2;
		}
		if (weaponName.startsWith("trident of the seas"))
		{
			return magicLevel / 3 - 5;
		}
		if (weaponName.startsWith("eye of ayak") || weaponName.startsWith("accursed sceptre"))
		{
			return magicLevel / 3 - 6;
		}
		if (weaponName.startsWith("thammaron's sceptre"))
		{
			return magicLevel / 3 - 8;
		}
		if (weaponName.startsWith("warped sceptre"))
		{
			return (8 * magicLevel + 96) / 37;
		}
		if (weaponName.startsWith("crystal staff (perfected)") || weaponName.startsWith("corrupted staff (perfected)"))
		{
			return 39;
		}
		if (weaponName.startsWith("crystal staff (attuned)") || weaponName.startsWith("corrupted staff (attuned)"))
		{
			return 31;
		}
		if (weaponName.startsWith("crystal staff (basic)") || weaponName.startsWith("corrupted staff (basic)"))
		{
			return 23;
		}
		return 0;
	}

	static int attackBonus(Loadout loadout, AttackType type)
	{
		switch (type)
		{
			case STAB:
				return loadout.total(i -> i.getOffensive().getStab());
			case SLASH:
				return loadout.total(i -> i.getOffensive().getSlash());
			case CRUSH:
				return loadout.total(i -> i.getOffensive().getCrush());
			case RANGED:
				return loadout.total(i -> i.getOffensive().getRanged());
			default:
				return loadout.total(i -> i.getOffensive().getMagic());
		}
	}

	/**
	 * Ranged strength counts ammo only for weapons that fire it; blowpipes use their loaded darts instead.
	 */
	static int rangedStrength(Loadout loadout)
	{
		EquipmentItem weapon = loadout.weapon();
		boolean countAmmo = GearEffects.usesAmmoSlot(weapon);
		int total = 0;
		for (java.util.Map.Entry<GearSlot, OwnedItem> entry : loadout.asMap().entrySet())
		{
			if (entry.getKey() == GearSlot.AMMO && !countAmmo)
			{
				continue;
			}
			total += entry.getValue().getStats().getBonuses().getRangedStr();
		}
		if (loadout.isBlowpipe() && loadout.getBlowpipeDart() != null)
		{
			total += loadout.getBlowpipeDart().getStats().getBonuses().getRangedStr();
		}
		return total;
	}

	static long npcDefenceRoll(Monster monster, AttackType type, Loadout loadout)
	{
		Monster.Defensive def = monster.getDefensive();
		Monster.Skills skills = monster.getSkills();
		int bonus;
		int level = skills.getDef();
		switch (type)
		{
			case STAB:
				bonus = def.getStab();
				break;
			case SLASH:
				bonus = def.getSlash();
				break;
			case CRUSH:
				bonus = def.getCrush();
				break;
			case MAGIC:
				bonus = def.getMagic();
				level = skills.getMagic();
				break;
			default:
				bonus = rangedDefence(def, loadout.weapon());
				break;
		}
		return (long) (level + 9) * Math.max(0, bonus + 64);
	}

	/**
	 * Ranged defence is split by ammunition weight: light (thrown), standard (arrows), heavy (bolts).
	 */
	private static int rangedDefence(Monster.Defensive def, EquipmentItem weapon)
	{
		String category = weapon == null ? "" : weapon.getCategory();
		switch (category)
		{
			case "Thrown":
				return def.getLight();
			case "Crossbow":
			case "Chinchompas":
				return def.getHeavy();
			default:
				return def.getStandard();
		}
	}

	static double hitChance(long attackRoll, long defenceRoll)
	{
		if (attackRoll > defenceRoll)
		{
			return 1 - (defenceRoll + 2.0) / (2.0 * (attackRoll + 1));
		}
		return attackRoll / (2.0 * (defenceRoll + 1));
	}

	/**
	 * Osmumten's fang rolls accuracy twice and hits if either roll succeeds.
	 */
	static double fangHitChance(long attackRoll, long defenceRoll)
	{
		double a = attackRoll;
		double d = defenceRoll;
		if (a > d)
		{
			return 1 - (d + 2) * (2 * d + 3) / (6 * (a + 1) * (a + 1));
		}
		return a * (4 * a + 5) / (6 * (a + 1) * (d + 1));
	}

	private static double applyFlatArmour(double averageHit, double accuracy, Monster monster)
	{
		int flat = monster.getDefensive().getFlatArmour();
		if (flat <= 0)
		{
			return averageHit;
		}
		return Math.max(accuracy, averageHit - accuracy * flat);
	}

	private static int weaponSpeed(EquipmentItem weapon)
	{
		return weapon == null || weapon.getSpeed() <= 0 ? 4 : weapon.getSpeed();
	}

	private static DpsResult result(double averageHit, Roll roll, double accuracy, int speed, long defRoll)
	{
		double dps = averageHit / (speed * DpsResult.SECONDS_PER_TICK);
		return new DpsResult(dps, roll.max, accuracy, averageHit, speed, roll.attack, defRoll, List.copyOf(roll.effects), null);
	}

}
