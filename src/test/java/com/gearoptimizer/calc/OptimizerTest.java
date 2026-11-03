package com.gearoptimizer.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gearoptimizer.account.AccountSnapshot;
import com.gearoptimizer.data.EquipmentItem;
import com.gearoptimizer.data.GameDataRepository;
import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.Monster;
import com.gearoptimizer.data.OwnedItem;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import org.junit.BeforeClass;
import org.junit.Test;

public class OptimizerTest
{
	private static GameDataRepository repository;

	@BeforeClass
	public static void loadData()
	{
		repository = new GameDataRepository(new Gson());
		repository.load();
	}

	private static EquipmentItem item(String name)
	{
		for (EquipmentItem item : repository.getEquipment())
		{
			if (item.getName().equalsIgnoreCase(name) && (item.getVersion() == null || item.getVersion().isEmpty()
				|| item.getVersion().equals("Charged") || item.getVersion().equals("Normal") || item.getVersion().equals("Unpoisoned")))
			{
				return item;
			}
		}
		for (EquipmentItem item : repository.getEquipment())
		{
			if (item.getName().equalsIgnoreCase(name))
			{
				return item;
			}
		}
		throw new AssertionError("No item " + name);
	}

	private static Monster monster(String name)
	{
		for (Monster monster : repository.getMonsters())
		{
			if (monster.getName().equalsIgnoreCase(name))
			{
				return monster;
			}
		}
		throw new AssertionError("No monster " + name);
	}

	private static AccountSnapshot account(String... itemNames)
	{
		AccountSnapshot account = new AccountSnapshot();
		Map<Integer, Integer> bank = new HashMap<>();
		Map<Integer, String> names = new HashMap<>();
		for (String name : itemNames)
		{
			EquipmentItem item = item(name);
			bank.put(item.getId(), 1);
			names.put(item.getId(), item.getName());
		}
		account.setBank(bank);
		account.setItemNames(names);
		account.setBankUpdatedAt(1);
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : new Skill[]{Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC, Skill.PRAYER, Skill.HITPOINTS, Skill.SLAYER, Skill.AGILITY})
		{
			levels.put(skill, 99);
		}
		account.setLevels(levels);
		account.setRigourUnlocked(true);
		account.setAuguryUnlocked(true);
		return account;
	}

	private static void addConsumable(AccountSnapshot account, int fakeId, String name)
	{
		account.getBank().put(fakeId, 10);
		account.getItemNames().put(fakeId, name);
	}

	private static Optimizer.Result optimize(Monster monster, AccountSnapshot account, boolean onTask)
	{
		Map<CombatStyle, CombatContext> contexts = new EnumMap<>(CombatStyle.class);
		for (CombatStyle style : CombatStyle.values())
		{
			contexts.put(style, CombatContext.builder()
				.monster(monster)
				.levels(account.getLevels())
				.prayer(PrayerBoost.best(style, account))
				.potion(PotionBoost.NONE)
				.onSlayerTask(onTask)
				.build());
		}
		return new Optimizer(repository).optimize(Optimizer.Input.builder()
			.monster(monster)
			.account(account)
			.contexts(contexts)
			.defenceWeight(0)
			.excludedItemIds(Collections.emptySet())
			.lockedItemIds(Collections.emptySet())
			.build());
	}

	private static CombatContext plainContext(Monster monster)
	{
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		levels.put(Skill.ATTACK, 99);
		levels.put(Skill.STRENGTH, 99);
		levels.put(Skill.RANGED, 99);
		levels.put(Skill.MAGIC, 99);
		return CombatContext.builder().monster(monster).levels(levels).prayer(PrayerBoost.NONE).potion(PotionBoost.NONE).build();
	}

	@Test
	public void dataLoads()
	{
		assertTrue(repository.getMonsters().size() > 2000);
		assertTrue(repository.getEquipment().size() > 4000);
		// magic_str is stored in tenths of a percent: Kodai wand is +15%
		assertEquals(150, item("Kodai wand").getBonuses().getMagicStr());
	}

	@Test
	public void whipMaxHitMatchesWiki()
	{
		Loadout loadout = new Loadout();
		loadout.set(GearSlot.WEAPON, new OwnedItem(item("Abyssal whip"), 4151, false));
		loadout.setStance(WeaponStyles.stancesFor(item("Abyssal whip"), CombatStyle.MELEE).get(0));
		// Flick is accurate (+0 str): eff str 99 + 8 = 107 -> (107 * 146 + 320) / 640 = 24
		assertEquals(24, DpsCalculator.calculate(loadout, plainContext(monster("Gargoyle"))).getMaxHit());
	}

	@Test
	public void twistedBowCapsOutsideRaids()
	{
		Monster monster = monster("Gargoyle");
		Monster.Skills skills = new Monster.Skills();
		skills.setDef(100);
		skills.setHp(100);
		skills.setMagic(500);
		Monster highMagic = new Monster();
		highMagic.setName("Test");
		highMagic.setSkills(skills);
		highMagic.setDefensive(monster.getDefensive());
		Loadout loadout = new Loadout();
		loadout.set(GearSlot.WEAPON, new OwnedItem(item("Twisted bow"), 20997, false));
		loadout.set(GearSlot.AMMO, new OwnedItem(item("Dragon arrow"), 11212, false));
		loadout.setStance(Stance.RANGED_RAPID);
		DpsResult result = DpsCalculator.calculate(loadout, plainContext(highMagic));
		assertTrue(result.getEffects().get(0).startsWith("Twisted bow: 140% accuracy"));
	}

	@Test
	public void cannonAddsItsDamagePerMonsterAroundIt()
	{
		Monster bloodveld = monster("Mutated Bloodveld");
		Loadout loadout = new Loadout();
		loadout.set(GearSlot.WEAPON, new OwnedItem(item("Rune crossbow"), 9185, false));
		loadout.set(GearSlot.AMMO, new OwnedItem(item("Adamant bolts"), 9143, false));
		loadout.set(GearSlot.HEAD, new OwnedItem(item("Slayer helmet (i)"), 11865, false));
		loadout.setStance(Stance.RANGED_RAPID);

		DpsResult alone = DpsCalculator.calculate(loadout, plainContext(bloodveld));
		assertNull(alone.getCannon());

		CombatContext withCannon = plainContext(bloodveld).toBuilder().cannonMaxHit(Cannon.STEEL_MAX_HIT).build();
		DpsResult one = DpsCalculator.calculate(loadout, withCannon);
		Cannon.Fire fire = one.getCannon();
		assertNotNull(fire);
		// Its accuracy is the player's ranged roll against heavy ranged defence
		long defence = (long) (bloodveld.getSkills().getDef() + 9) * (bloodveld.getDefensive().getHeavy() + 64);
		long attack = (long) (99 + 8) * (DpsCalculator.attackBonus(loadout, AttackType.RANGED) + 64);
		assertEquals(DpsCalculator.hitChance(attack, defence), fire.getAccuracy(), 1e-9);
		assertEquals(fire.getAccuracy() * 30 / 2.0 / 4.8, fire.getDps(), 1e-9);
		assertEquals(alone.getDps() + fire.getDps(), one.getDps(), 1e-9);
		assertEquals(750, fire.getBallsPerHour(), 1e-9);

		// A slayer task doesn't make it more accurate; stacked monsters each get shot, up to 8
		DpsResult onTask = DpsCalculator.calculate(loadout, withCannon.toBuilder().onSlayerTask(true).build());
		assertEquals(fire.getAccuracy(), onTask.getCannon().getAccuracy(), 1e-9);
		DpsResult four = DpsCalculator.calculate(loadout, withCannon.toBuilder().aoeTargets(4).build());
		assertEquals(fire.getDps() * 4, four.getCannon().getDps(), 1e-9);
		DpsResult twelve = DpsCalculator.calculate(loadout, withCannon.toBuilder().aoeTargets(12).build());
		assertEquals(8, twelve.getCannon().getTargets());

		// Demonic gorillas ignore it
		assertNull(DpsCalculator.calculate(loadout, withCannon.toBuilder().monster(monster("Demonic gorilla")).build()).getCannon());
	}

	@Test
	public void cannonNeedsAllItsPartsAndCannonballsInTheInventory()
	{
		AccountSnapshot account = account("Rune crossbow", "Adamant bolts");
		String[] parts = {"Cannon base", "Cannon stand", "Cannon barrels", "Cannon furnace"};
		int[] partIds = {ItemID.TWPART1, ItemID.TWPART2, ItemID.TWPART3, ItemID.TWPART4};
		for (int i = 0; i < parts.length; i++)
		{
			addConsumable(account, partIds[i], parts[i]);
		}
		addConsumable(account, ItemID.MCANNONBALL, "Cannonball");
		Monster bloodveld = monster("Mutated Bloodveld");
		Map<CombatStyle, CombatContext> contexts = new EnumMap<>(CombatStyle.class);
		contexts.put(CombatStyle.RANGED, CombatContext.builder().monster(bloodveld).levels(account.getLevels())
			.prayer(PrayerBoost.NONE).potion(PotionBoost.NONE).cannonMaxHit(Cannon.STEEL_MAX_HIT).build());
		StyleResult ranged = new Optimizer(repository).optimize(Optimizer.Input.builder().monster(bloodveld).account(account).contexts(contexts)
			.excludedItemIds(Collections.emptySet()).lockedItemIds(Collections.emptySet()).build()).getStyles().get(CombatStyle.RANGED);

		MonsterRules.CheckedItem cannon = ranged.getInventory().stream().filter(i -> i.getLabel().equals(MonsterRules.CANNON)).findFirst().orElseThrow();
		assertTrue(cannon.isOwned());
		List<InventoryPlan.Slot> plan = InventoryPlan.build(ranged, account);
		for (String part : parts)
		{
			assertTrue(part, plan.stream().anyMatch(s -> part.equals(s.getName())));
		}
		assertTrue(plan.stream().anyMatch(s -> "Cannonball".equals(s.getName()) && s.getQuantity() == 10));

		// Missing a part is flagged
		account.getBank().remove(ItemID.TWPART3);
		StyleResult missing = new Optimizer(repository).optimize(Optimizer.Input.builder().monster(bloodveld).account(account).contexts(contexts)
			.excludedItemIds(Collections.emptySet()).lockedItemIds(Collections.emptySet()).build()).getStyles().get(CombatStyle.RANGED);
		MonsterRules.CheckedItem incomplete = missing.getInventory().stream().filter(i -> i.getLabel().equals(MonsterRules.CANNON)).findFirst().orElseThrow();
		assertFalse(incomplete.isOwned());
		assertEquals("Missing the barrels", incomplete.getDetail());
	}

	@Test
	public void runesToCarrySkipThoseTheGearProvides()
	{
		Loadout wand = new Loadout();
		wand.set(GearSlot.WEAPON, new OwnedItem(item("Dragon hunter wand"), 1, false));
		wand.setSpell(Spell.ICE_BARRAGE);
		assertEquals(Spell.ICE_BARRAGE.getRuneCosts(), Consumables.runesToCarry(wand));
		assertTrue(Consumables.runesFromGear(wand).isEmpty());

		Loadout kodai = new Loadout();
		kodai.set(GearSlot.WEAPON, new OwnedItem(item("Kodai wand"), 2, false));
		kodai.setSpell(Spell.ICE_BARRAGE);
		assertFalse(Consumables.runesToCarry(kodai).containsKey("Water"));
		assertTrue(Consumables.runesToCarry(kodai).containsKey("Death"));
		assertEquals(List.of("Water"), Consumables.runesFromGear(kodai));
		assertEquals(ItemID.DEATHRUNE, Consumables.runeItemId("Death"));
	}

	@Test
	public void areaAttacksAreLimitedByMonsterSize()
	{
		// Only known stacking tasks are assumed stacked
		assertEquals(AoeTargets.STACKED, AoeTargets.guess(monster("Dust devil")));
		assertEquals(1, AoeTargets.guess(monster("Mutated Bloodveld")));

		Monster bloodveld = monster("Mutated Bloodveld");
		assertEquals(2, bloodveld.getSize());
		assertEquals(4, AoeTargets.maxInArea(bloodveld));
		Monster huge = repository.getMonsters().stream().filter(m -> m.getSize() >= 3 && m.getHitpoints() > 0).findFirst().orElseThrow();
		assertEquals(1, AoeTargets.maxInArea(huge));

		// A barrage can't catch 6 big monsters in a 3x3, but the cannon still shoots all of them
		Loadout kodai = new Loadout();
		kodai.set(GearSlot.WEAPON, new OwnedItem(item("Kodai wand"), 2, false));
		kodai.setSpell(Spell.ICE_BARRAGE);
		kodai.setStance(Stance.AUTOCAST);
		CombatContext six = plainContext(huge).toBuilder().aoeTargets(6).cannonMaxHit(Cannon.STEEL_MAX_HIT).build();
		assertEquals(1, six.areaTargets());
		DpsResult result = DpsCalculator.calculate(kodai, six);
		assertTrue(result.getEffects().stream().noneMatch(e -> e.startsWith("Hits ")));
		assertEquals(6, result.getCannon().getTargets());
		assertEquals(4, plainContext(bloodveld).toBuilder().aoeTargets(6).build().areaTargets());
	}

	@Test
	public void hitChanceFormula()
	{
		assertEquals(0.5 * 100 / 101.0, DpsCalculator.hitChance(100, 100), 1e-9);
		assertEquals(1 - 102.0 / 402.0, DpsCalculator.hitChance(200, 100), 1e-9);
	}

	@Test
	public void gargoyleWithGraniteHammerNeedsNoRockHammer()
	{
		AccountSnapshot account = account("Granite hammer", "Helm of Neitiznot", "Amulet of fury", "Fire cape", "Dragon boots",
			"Barrows gloves", "Rune platebody", "Rune platelegs", "Dragon defender");
		Optimizer.Result result = optimize(monster("Gargoyle"), account, true);
		StyleResult melee = result.getStyles().get(CombatStyle.MELEE);
		assertNotNull(melee);
		assertEquals("Granite hammer", melee.getLoadout().weapon().getName());
		for (MonsterRules.CheckedItem item : melee.getInventory())
		{
			assertFalse("rock hammer should be waived", item.getLabel().startsWith("Rock hammer"));
		}
	}

	@Test
	public void graniteHammerBeatsWhipOnGargoyles()
	{
		AccountSnapshot account = account("Abyssal whip", "Granite hammer", "Amulet of fury", "Dragon defender");
		StyleResult melee = optimize(monster("Gargoyle"), account, false).getStyles().get(CombatStyle.MELEE);
		assertEquals("Granite hammer", melee.getLoadout().weapon().getName());
		assertTrue(melee.getDps().getEffects().contains("Golembane +30%"));
		assertEquals(0, melee.getFinishSeconds(), 1e-9);
	}

	@Test
	public void flyingMonstersNeedHalberdForMelee()
	{
		Monster flying = null;
		for (Monster monster : repository.getMonsters())
		{
			if (monster.hasAttribute("flying"))
			{
				flying = monster;
				break;
			}
		}
		assertNotNull(flying);
		AccountSnapshot account = account("Abyssal whip", "Dragon halberd");
		StyleResult melee = optimize(flying, account, false).getStyles().get(CombatStyle.MELEE);
		assertEquals("Dragon halberd", melee.getLoadout().weapon().getName());
	}

	@Test
	public void gargoyleWithoutHammerAsksForOne()
	{
		AccountSnapshot account = account("Abyssal whip", "Dragon defender");
		Optimizer.Result result = optimize(monster("Gargoyle"), account, false);
		StyleResult melee = result.getStyles().get(CombatStyle.MELEE);
		boolean asked = false;
		for (MonsterRules.CheckedItem item : melee.getInventory())
		{
			if (item.getLabel().equals("Rock hammer"))
			{
				asked = true;
				assertFalse(item.isOwned());
				assertTrue(item.isMandatory());
			}
		}
		assertTrue(asked);
	}

	@Test
	public void twoHandedWeaponDropsShield()
	{
		AccountSnapshot account = account("Twisted bow", "Dragon arrow", "Dragonfire ward", "Necklace of anguish");
		StyleResult ranged = optimize(monster("Gargoyle"), account, false).getStyles().get(CombatStyle.RANGED);
		assertEquals("Twisted bow", ranged.getLoadout().weapon().getName());
		assertNull(ranged.getLoadout().get(GearSlot.SHIELD));
		assertEquals("Dragon arrow", ranged.getLoadout().stats(GearSlot.AMMO).getName());
	}

	@Test
	public void slayerHelmOnlyCountsOnTask()
	{
		AccountSnapshot account = account("Abyssal whip", "Slayer helmet", "Helm of Neitiznot");
		Monster gargoyle = monster("Gargoyle");
		StyleResult onTask = optimize(gargoyle, account, true).getStyles().get(CombatStyle.MELEE);
		StyleResult offTask = optimize(gargoyle, account, false).getStyles().get(CombatStyle.MELEE);
		assertEquals("Slayer helmet", onTask.getLoadout().stats(GearSlot.HEAD).getName());
		assertEquals("Helm of Neitiznot", offTask.getLoadout().stats(GearSlot.HEAD).getName());
		assertTrue(onTask.getDps().getDps() > offTask.getDps().getDps());
	}

	@Test
	public void salveBeatsFuryOnUndead()
	{
		AccountSnapshot account = account("Abyssal whip", "Salve amulet(ei)", "Amulet of fury");
		StyleResult melee = optimize(monster("Ankou"), account, false).getStyles().get(CombatStyle.MELEE);
		assertEquals("Salve amulet(ei)", melee.getLoadout().stats(GearSlot.NECK).getName());
		assertTrue(melee.getDps().getEffects().contains("Salve amulet (e) +20%"));
	}

	@Test
	public void bansheeForcesEarProtection()
	{
		AccountSnapshot account = account("Abyssal whip", "Earmuffs", "Helm of Neitiznot");
		StyleResult melee = optimize(monster("Banshee"), account, false).getStyles().get(CombatStyle.MELEE);
		assertEquals("Earmuffs", melee.getLoadout().stats(GearSlot.HEAD).getName());
	}

	@Test
	public void basiliskKnightKeepsMirrorShieldOverTwoHandedWeapons()
	{
		AccountSnapshot account = account("Toxic blowpipe", "Dragon dart", "Twisted bow", "Dragon arrow", "Mirror shield",
			"Rune crossbow", "Runite bolts", "Abyssal whip", "Scythe of Vitur");
		Optimizer.Result result = optimize(monster("Basilisk Knight"), account, true);
		for (StyleResult style : result.getStyles().values())
		{
			assertEquals(style.getStyle() + " must keep the mirror shield", "Mirror shield",
				style.getLoadout().stats(GearSlot.SHIELD).getName());
			assertFalse(style.getLoadout().weapon() != null && style.getLoadout().weapon().isTwoHanded());
		}
		assertEquals("Rune crossbow", result.getStyles().get(CombatStyle.RANGED).getLoadout().weapon().getName());
	}

	@Test
	public void missingProtectionIsFlaggedUnsafe()
	{
		AccountSnapshot account = account("Abyssal whip");
		StyleResult melee = optimize(monster("Basilisk Knight"), account, true).getStyles().get(CombatStyle.MELEE);
		assertTrue(melee.getWarnings().get(0).startsWith("UNSAFE"));
	}

	@Test
	public void kuraskNeedsLeafBladedWeapon()
	{
		AccountSnapshot account = account("Abyssal whip", "Leaf-bladed battleaxe");
		StyleResult melee = optimize(monster("Kurask"), account, false).getStyles().get(CombatStyle.MELEE);
		assertEquals("Leaf-bladed battleaxe", melee.getLoadout().weapon().getName());
	}

	@Test
	public void runeCrossbowCannotFireDragonBolts()
	{
		assertFalse(AmmoRules.compatible(item("Rune crossbow"), item("Dragon bolts")));
		assertTrue(AmmoRules.compatible(item("Rune crossbow"), item("Runite bolts")));
		assertTrue(AmmoRules.compatible(item("Armadyl crossbow"), item("Ruby dragon bolts (e)")));
		assertFalse(AmmoRules.compatible(item("Magic shortbow"), item("Dragon arrow")));
		assertFalse(AmmoRules.compatible(item("Eclipse atlatl"), item("Dragon arrow")));
		assertTrue(AmmoRules.compatible(item("Eclipse atlatl"), item("Atlatl dart")));
		assertFalse(AmmoRules.compatible(item("Bow of Faerdhinen"), item("Dragon arrow")));
	}

	@Test
	public void dragonWithoutAntifireNeedsShield()
	{
		AccountSnapshot account = account("Abyssal whip", "Anti-dragon shield", "Dragon defender");
		StyleResult melee = optimize(monster("Blue dragon"), account, false).getStyles().get(CombatStyle.MELEE);
		assertEquals("Anti-dragon shield", melee.getLoadout().stats(GearSlot.SHIELD).getName());

		addConsumable(account, 999_001, "Extended antifire(4)");
		StyleResult withPotion = optimize(monster("Blue dragon"), account, false).getStyles().get(CombatStyle.MELEE);
		assertEquals("Dragon defender", withPotion.getLoadout().stats(GearSlot.SHIELD).getName());
	}

	@Test
	public void staffPicksBestSpellAndChecksRunes()
	{
		AccountSnapshot account = account("Staff of fire");
		addConsumable(account, 999_002, "Air rune");
		addConsumable(account, 999_003, "Wrath rune");
		addConsumable(account, 999_004, "Earth rune");
		StyleResult magic = optimize(monster("Gargoyle"), account, false).getStyles().get(CombatStyle.MAGIC);
		// Gargoyles are weak to earth, so Earth Surge beats Fire Surge
		assertEquals(Spell.EARTH_SURGE, magic.getLoadout().getSpell());
		boolean runesOk = false;
		for (MonsterRules.CheckedItem item : magic.getInventory())
		{
			if (item.getLabel().startsWith("Runes for"))
			{
				runesOk = item.isOwned();
			}
		}
		assertTrue("air, earth and wrath owned", runesOk);
	}

	@Test
	public void everyAmmoWeaponHasSomeAmmo()
	{
		for (EquipmentItem weapon : repository.getEquipment())
		{
			if (!GearEffects.usesAmmoSlot(weapon))
			{
				continue;
			}
			boolean any = false;
			for (EquipmentItem ammo : repository.getEquipment())
			{
				if (ammo.getGearSlot() == GearSlot.AMMO && AmmoRules.compatible(weapon, ammo))
				{
					any = true;
					break;
				}
			}
			assertTrue("No ammo fits " + weapon.getName(), any);
		}
	}

	@Test
	public void specialWeaponAmmo()
	{
		assertTrue(AmmoRules.compatible(item("Karil's crossbow"), item("Bolt rack")));
		assertFalse(AmmoRules.compatible(item("Karil's crossbow"), item("Runite bolts")));
		assertFalse(AmmoRules.compatible(item("Armadyl crossbow"), item("Bolt rack")));
		assertTrue(AmmoRules.compatible(item("Heavy ballista"), item("Dragon javelin")));
		assertFalse(AmmoRules.compatible(item("Light ballista"), item("Dragon javelin")));
		assertFalse(AmmoRules.compatible(item("Heavy ballista"), item("Dragon bolts")));
		assertTrue(AmmoRules.compatible(item("Comp ogre bow"), item("Rune brutal")));
		assertFalse(AmmoRules.compatible(item("Twisted bow"), item("Rune brutal")));
		assertFalse(AmmoRules.compatible(item("Twisted bow"), item("Ogre arrow")));
		assertTrue(AmmoRules.compatible(item("Hunters' crossbow"), item("Kebbit bolts")));
		assertFalse(AmmoRules.compatible(item("Hunters' crossbow"), item("Bronze bolts")));
		assertTrue(AmmoRules.compatible(item("Yew shortbow"), item("Rune arrow")));
		assertFalse(AmmoRules.compatible(item("Yew shortbow"), item("Amethyst arrow")));
		assertTrue(AmmoRules.compatible(item("Dragon hunter crossbow"), item("Diamond dragon bolts (e)")));
		assertFalse(AmmoRules.compatible(item("Adamant crossbow"), item("Onyx bolts (e)")));
		assertTrue(AmmoRules.compatible(item("Rune crossbow"), item("Onyx bolts (e)")));
		assertFalse(GearEffects.usesAmmoSlot(item("Toxic blowpipe")));
		assertFalse(GearEffects.usesAmmoSlot(item("Craw's bow")));
		assertFalse(GearEffects.usesAmmoSlot(item("Corrupted bow (perfected)")));
	}

	@Test
	public void blowpipeLeavesArrowsInBank()
	{
		AccountSnapshot account = account("Toxic blowpipe", "Dragon dart", "Dragon arrow", "Rune arrow");
		StyleResult ranged = optimize(monster("Gargoyle"), account, false).getStyles().get(CombatStyle.RANGED);
		assertEquals("Toxic blowpipe", ranged.getLoadout().weapon().getName());
		assertNull(ranged.getLoadout().get(GearSlot.AMMO));
		assertEquals("Dragon dart", ranged.getLoadout().getBlowpipeDart().getStats().getName());
	}

	@Test
	public void atlatlWithoutDartsIsSkipped()
	{
		AccountSnapshot account = account("Eclipse atlatl", "Dragon arrow", "Magic shortbow", "Amethyst arrow");
		StyleResult ranged = optimize(monster("Gargoyle"), account, false).getStyles().get(CombatStyle.RANGED);
		assertEquals("Magic shortbow", ranged.getLoadout().weapon().getName());

		account = account("Eclipse atlatl", "Atlatl dart", "Dragon arrow");
		ranged = optimize(monster("Gargoyle"), account, false).getStyles().get(CombatStyle.RANGED);
		assertEquals("Eclipse atlatl", ranged.getLoadout().weapon().getName());
		assertEquals("Atlatl dart", ranged.getLoadout().stats(GearSlot.AMMO).getName());
	}

	@Test
	public void experimentSwapsAmmoAndDropsShieldWhenNeeded()
	{
		AccountSnapshot account = account("Armadyl crossbow", "Dragon bolts", "Runite bolts", "Twisted bow", "Dragon arrow", "Dragonfire ward");
		Optimizer.Result result = optimize(monster("Gargoyle"), account, false);
		StyleResult ranged = result.getStyles().get(CombatStyle.RANGED);

		Loadout edited = new Loadout(ranged.getLoadout());
		edited.set(GearSlot.WEAPON, new OwnedItem(item("Armadyl crossbow"), item("Armadyl crossbow").getId(), false));
		edited.set(GearSlot.AMMO, new OwnedItem(item("Dragon bolts"), item("Dragon bolts").getId(), false));
		edited.set(GearSlot.SHIELD, new OwnedItem(item("Dragonfire ward"), item("Dragonfire ward").getId(), false));
		List<OwnedItem> ammo = List.of(new OwnedItem(item("Dragon arrow"), item("Dragon arrow").getId(), false));

		String note = Experiment.apply(edited, GearSlot.WEAPON, new OwnedItem(item("Twisted bow"), 20997, false), ammo,
			null, CombatStyle.RANGED, ranged.getContext());
		assertNull(edited.get(GearSlot.SHIELD));
		assertEquals("Dragon arrow", edited.stats(GearSlot.AMMO).getName());
		assertTrue(note.contains("Removed the shield"));

		StyleResult evaluated = Experiment.evaluate(ranged, edited, result.getRules(), account);
		assertTrue(evaluated.getDps().getDps() > 0);
	}

	@Test
	public void inventoryPlanFillsTwentyEightSlots()
	{
		AccountSnapshot account = account("Abyssal whip");
		addConsumable(account, 999_010, "Shark");
		addConsumable(account, 999_011, "Prayer potion(4)");
		StyleResult melee = optimize(monster("Gargoyle"), account, false).getStyles().get(CombatStyle.MELEE);
		List<InventoryPlan.Slot> plan = InventoryPlan.build(melee, account);
		assertEquals(28, plan.size());
		// Rock hammer is required and missing, so it's the first (red) slot
		assertFalse(plan.get(0).isOwned());
		assertEquals("Shark", plan.get(27).getName());
	}

	@Test
	public void budgetPicksCheaperAmmoWithinTenPercent()
	{
		AccountSnapshot account = account("Rune crossbow", "Runite bolts", "Diamond bolts (e)");
		int runite = item("Runite bolts").getId();
		int diamond = item("Diamond bolts (e)").getId();
		Map<Integer, Integer> prices = new HashMap<>();
		prices.put(runite, 100);
		prices.put(diamond, 1000);

		Map<CombatStyle, CombatContext> contexts = new EnumMap<>(CombatStyle.class);
		contexts.put(CombatStyle.RANGED, CombatContext.builder().monster(monster("Gargoyle")).levels(account.getLevels())
			.prayer(PrayerBoost.NONE).potion(PotionBoost.NONE).build());
		Optimizer.Input.InputBuilder input = Optimizer.Input.builder().monster(monster("Gargoyle")).account(account).contexts(contexts)
			.excludedItemIds(Collections.emptySet()).lockedItemIds(Collections.emptySet()).prices(prices);

		StyleResult maxDps = new Optimizer(repository).optimize(input.budget(false).build()).getStyles().get(CombatStyle.RANGED);
		StyleResult budget = new Optimizer(repository).optimize(input.budget(true).build()).getStyles().get(CombatStyle.RANGED);
		assertEquals(2, maxDps.getConsumableOptions().size());
		assertTrue(maxDps.getCost().getPerHour() >= budget.getCost().getPerHour());
		assertTrue(budget.getDps().getDps() >= maxDps.getDps().getDps() * Consumables.BUDGET_DPS_SHARE);
		assertTrue(maxDps.getCost().getPerHour() > 0);
	}

	@Test
	public void spellRunesAreCosted()
	{
		AccountSnapshot account = account("Staff of fire");
		addConsumable(account, ItemIDs.AIR, "Air rune");
		addConsumable(account, ItemIDs.WRATH, "Wrath rune");
		Map<Integer, Integer> prices = new HashMap<>();
		prices.put(ItemIDs.AIR, 5);
		prices.put(ItemIDs.WRATH, 400);
		Loadout loadout = new Loadout();
		loadout.set(GearSlot.WEAPON, new OwnedItem(item("Staff of fire"), item("Staff of fire").getId(), false));
		loadout.setSpell(Spell.FIRE_SURGE);
		loadout.setStance(Stance.AUTOCAST);
		DpsResult dps = DpsCalculator.calculate(loadout, plainContext(monster("Gargoyle")));
		// 7 air (5gp) + 1 wrath (400gp); fire runes come from the staff
		assertEquals(435, Consumables.cost(loadout, dps, prices).getPerAttack(), 1e-9);
	}

	private static final class ItemIDs
	{
		static final int AIR = net.runelite.api.gameval.ItemID.AIRRUNE;
		static final int WRATH = net.runelite.api.gameval.ItemID.WRATHRUNE;
	}

	@Test
	public void slotOptionsIncludeWorseOwnedItemsAndRunFast()
	{
		AccountSnapshot account = account("Dragon crossbow", "Rune crossbow", "Runite bolts", "Dragon bolts", "Mirror shield");
		Optimizer.Result result = optimize(monster("Basilisk Knight"), account, true);
		StyleResult ranged = result.getStyles().get(CombatStyle.RANGED);
		List<OwnedItem> owned = new java.util.ArrayList<>();
		for (int id : account.ownedItemIds())
		{
			OwnedItem item = repository.resolve(id);
			if (item != null)
			{
				owned.add(item);
			}
		}
		long start = System.nanoTime();
		List<Experiment.SlotOption> weapons = Experiment.slotOptions(ranged, GearSlot.WEAPON, owned, repository.getEquipment(), result.getRules(),
			ranged.getLoadout().get(GearSlot.AMMO));
		long millis = (System.nanoTime() - start) / 1_000_000;
		System.out.println("Weapon slot options: " + weapons.size() + " in " + millis + " ms");
		assertTrue(millis < 1500);

		Experiment.SlotOption rune = null;
		for (Experiment.SlotOption option : weapons)
		{
			if (option.getItem().getStats().getName().equals("Rune crossbow"))
			{
				rune = option;
			}
			if (option.getItem().getStats().isTwoHanded())
			{
				assertTrue("2H weapons must be flagged unsafe here", option.isUnsafe());
			}
		}
		assertNotNull(rune);
		assertTrue(rune.isOwned());
		assertTrue(rune.getChange() < 0);
	}

	@Test
	public void revertingWeaponRestoresRecommendedAmmo()
	{
		AccountSnapshot account = account("Dragon crossbow", "Ruby dragon bolts (e)", "Dragon bolts", "Dorgeshuun crossbow", "Bone bolts", "Mirror shield");
		Optimizer.Result result = optimize(monster("Basilisk Knight"), account, true);
		StyleResult ranged = result.getStyles().get(CombatStyle.RANGED);
		OwnedItem recommendedWeapon = ranged.getLoadout().get(GearSlot.WEAPON);
		OwnedItem recommendedAmmo = ranged.getLoadout().get(GearSlot.AMMO);
		List<OwnedItem> ammo = List.of(
			new OwnedItem(item("Ruby dragon bolts (e)"), item("Ruby dragon bolts (e)").getId(), false),
			new OwnedItem(item("Dragon bolts"), item("Dragon bolts").getId(), false),
			new OwnedItem(item("Bone bolts"), item("Bone bolts").getId(), false));

		Loadout edited = new Loadout(ranged.getLoadout());
		Experiment.apply(edited, GearSlot.WEAPON, new OwnedItem(item("Dorgeshuun crossbow"), item("Dorgeshuun crossbow").getId(), false),
			ammo, recommendedAmmo, CombatStyle.RANGED, ranged.getContext());
		assertFalse(Experiment.sameItems(edited, ranged.getLoadout()));

		Experiment.apply(edited, GearSlot.WEAPON, recommendedWeapon, ammo, recommendedAmmo, CombatStyle.RANGED, ranged.getContext());
		assertEquals(recommendedAmmo.getStats().getId(), edited.stats(GearSlot.AMMO).getId());
		assertTrue(Experiment.sameItems(edited, ranged.getLoadout()));
	}

	@Test
	public void tiesKeepWornItem()
	{
		AccountSnapshot account = account("Magic shortbow", "Amethyst arrow", "Ancient d'hide boots", "Guthix d'hide boots", "Zamorak d'hide boots");
		Map<Integer, Integer> worn = new HashMap<>();
		worn.put(GearSlot.FEET.getInventorySlot().getSlotIdx(), item("Guthix d'hide boots").getId());
		account.setEquipment(worn);
		StyleResult ranged = optimize(monster("Gargoyle"), account, false).getStyles().get(CombatStyle.RANGED);
		assertEquals("Guthix d'hide boots", ranged.getLoadout().stats(GearSlot.FEET).getName());
	}

	@Test
	public void barrageWinsWhenMonstersAreStacked()
	{
		AccountSnapshot account = account("Kodai wand");
		for (String rune : new String[]{"Water rune", "Blood rune", "Death rune", "Air rune", "Wrath rune", "Earth rune", "Fire rune"})
		{
			addConsumable(account, 990_000 + rune.length() * 31 + rune.charAt(0), rune);
		}
		Monster devil = monster("Dust devil");
		assertEquals(AoeTargets.STACKED, AoeTargets.guess(devil));

		Map<CombatStyle, CombatContext> contexts = new EnumMap<>(CombatStyle.class);
		contexts.put(CombatStyle.MAGIC, CombatContext.builder().monster(devil).levels(account.getLevels())
			.prayer(PrayerBoost.NONE).potion(PotionBoost.NONE).aoeTargets(AoeTargets.guess(devil)).build());
		StyleResult magic = new Optimizer(repository).optimize(Optimizer.Input.builder().monster(devil).account(account).contexts(contexts)
			.excludedItemIds(Collections.emptySet()).lockedItemIds(Collections.emptySet()).build()).getStyles().get(CombatStyle.MAGIC);
		assertTrue("expected a barrage, got " + magic.getLoadout().getSpell(), magic.getLoadout().getSpell().isAoe());
		assertTrue(magic.getDps().getEffects().contains("Hits 4 monsters (3x3 area)"));
	}

	@Test
	public void ratBoneWeaponsOnlyWorkOnRats()
	{
		AccountSnapshot account = account("Bone staff", "Trident of the Swamp");
		for (String name : new String[]{"Twisted Banshee", "Brine rat"})
		{
			Monster target = monster(name);
			Map<CombatStyle, CombatContext> contexts = new EnumMap<>(CombatStyle.class);
			contexts.put(CombatStyle.MAGIC, CombatContext.builder().monster(target).levels(account.getLevels())
				.prayer(PrayerBoost.NONE).potion(PotionBoost.NONE).build());
			StyleResult magic = new Optimizer(repository).optimize(Optimizer.Input.builder().monster(target).account(account).contexts(contexts)
				.excludedItemIds(Collections.emptySet()).lockedItemIds(Collections.emptySet()).build()).getStyles().get(CombatStyle.MAGIC);
			assertEquals(name, name.endsWith("rat") ? "Bone staff" : "Trident of the Swamp", magic.getLoadout().weapon().getName());
		}
	}

	@Test
	public void blowpipesOnlyLoadDartsTheyAccept()
	{
		assertTrue(AmmoRules.blowpipeAccepts(item("Toxic blowpipe"), item("Dragon dart")));
		assertTrue(AmmoRules.blowpipeAccepts(item("Camphor blowpipe"), item("Adamant dart")));
		assertFalse(AmmoRules.blowpipeAccepts(item("Camphor blowpipe"), item("Rune dart")));
		assertTrue(AmmoRules.blowpipeAccepts(item("Rosewood blowpipe"), item("Rune dart")));
		assertFalse(AmmoRules.blowpipeAccepts(item("Rosewood blowpipe"), item("Amethyst dart")));
		assertFalse(AmmoRules.blowpipeAccepts(item("Toxic blowpipe"), item("Atlatl dart")));
		EquipmentItem poisoned = repository.getEquipment().stream()
			.filter(i -> i.getName().equals("Dragon dart") && "Poison".equals(i.getVersion())).findFirst().orElseThrow();
		assertFalse(AmmoRules.blowpipeAccepts(item("Drygore blowpipe"), poisoned));

		// A camphor blowpipe gets the best darts it can fire, not the best darts owned
		AccountSnapshot account = account("Camphor blowpipe", "Adamant dart", "Dragon dart");
		Monster target = monster("Twisted Banshee");
		Map<CombatStyle, CombatContext> contexts = new EnumMap<>(CombatStyle.class);
		contexts.put(CombatStyle.RANGED, CombatContext.builder().monster(target).levels(account.getLevels())
			.prayer(PrayerBoost.NONE).potion(PotionBoost.NONE).build());
		StyleResult ranged = new Optimizer(repository).optimize(Optimizer.Input.builder().monster(target).account(account).contexts(contexts)
			.excludedItemIds(Collections.emptySet()).lockedItemIds(java.util.Set.of(item("Camphor blowpipe").getId())).build())
			.getStyles().get(CombatStyle.RANGED);
		assertEquals("Camphor blowpipe", ranged.getLoadout().weapon().getName());
		assertEquals("Adamant dart", ranged.getLoadout().getBlowpipeDart().getStats().getName());

		// The dart picker lists every type it takes, owned or not, with the loaded one marked
		List<Experiment.SlotOption> darts = Experiment.dartOptions(ranged, List.of(ranged.getLoadout().getBlowpipeDart()), repository.getEquipment());
		assertEquals(6, darts.size());
		assertTrue(darts.stream().anyMatch(o -> o.isInUse() && o.isOwned() && o.getItem().getStats().getName().equals("Adamant dart")));
		assertTrue(darts.stream().noneMatch(o -> o.getItem().getStats().getName().equals("Rune dart")));
	}

	@Test
	public void ancientsNeedAnAutocastingStaff()
	{
		Monster devil = monster("Dust devil");
		assertFalse(Spell.ICE_BARRAGE.usableWith(item("Staff of fire"), devil));
		assertTrue(Spell.ICE_BARRAGE.usableWith(item("Kodai wand"), devil));
		assertTrue(Spell.ICE_BARRAGE.usableWith(item("Dragon hunter wand"), devil));
		assertTrue(Spell.FIRE_SURGE.usableWith(item("Staff of fire"), devil));

		AccountSnapshot account = account("Staff of fire");
		StyleResult magic = optimize(devil, account, false).getStyles().get(CombatStyle.MAGIC);
		assertEquals(Spell.Spellbook.STANDARD, magic.getLoadout().getSpell().getSpellbook());
	}

	@Test
	public void dragonHunterWandBarragesBetterThanAhrimsStaff()
	{
		AccountSnapshot account = account("Dragon hunter wand", "Ahrim's staff");
		StyleResult magic = optimize(monster("Dust devil"), account, false).getStyles().get(CombatStyle.MAGIC);
		assertEquals("Dragon hunter wand", magic.getLoadout().weapon().getName());
		assertTrue(magic.getLoadout().getSpell().isAoe());
	}

	@Test
	public void autocastRulesFollowTheWiki()
	{
		Monster devil = monster("Dust devil");
		Monster demon = monster("Greater demon");
		Monster undead = monster("Ankou");
		// Ancient Magicks
		for (String staff : new String[]{"Ancient staff", "Master wand", "Kodai wand", "Dragon hunter wand", "Nightmare staff",
			"Eldritch nightmare staff", "Volatile nightmare staff", "Blue moon spear", "Ice ancient sceptre"})
		{
			assertTrue(staff + " should autocast ancients", Spell.ICE_BARRAGE.usableWith(item(staff), devil));
		}
		for (String staff : new String[]{"Staff of the dead", "Staff of light", "Harmonised nightmare staff", "Ahrim's staff", "Void knight mace", "Purging staff"})
		{
			assertFalse(staff + " should not autocast ancients", Spell.ICE_BARRAGE.usableWith(item(staff), devil));
		}
		// Arceuus
		assertTrue(Spell.DARK_DEMONBANE.usableWith(item("Purging staff"), demon));
		assertTrue(Spell.DARK_DEMONBANE.usableWith(item("Kodai wand"), demon));
		assertFalse(Spell.DARK_DEMONBANE.usableWith(item("Dragon hunter wand"), demon));
		assertFalse(Spell.DARK_DEMONBANE.usableWith(item("Staff of fire"), demon));
		// Standard restrictions
		assertFalse(Spell.FIRE_BLAST.usableWith(item("Slayer's staff"), devil));
		assertTrue(Spell.FIRE_SURGE.usableWith(item("Slayer's staff"), devil));
		assertTrue(Spell.MAGIC_DART.usableWith(item("Staff of the dead"), devil));
		assertFalse(Spell.MAGIC_DART.usableWith(item("Kodai wand"), devil));
		assertFalse(Spell.FIRE_SURGE.usableWith(item("Skull sceptre (i)"), devil));
		assertTrue(Spell.CRUMBLE_UNDEAD.usableWith(item("Skull sceptre (i)"), undead));
	}

	@Test
	public void ahrimsStaffNeedsFullSetForAncients()
	{
		Loadout loadout = new Loadout();
		loadout.set(GearSlot.WEAPON, new OwnedItem(item("Ahrim's staff"), item("Ahrim's staff").getId(), false));
		assertFalse(Spell.ICE_BARRAGE.canAutocastWith(loadout));
		loadout.set(GearSlot.HEAD, new OwnedItem(item("Ahrim's hood"), 1, false));
		loadout.set(GearSlot.BODY, new OwnedItem(item("Ahrim's robetop"), 2, false));
		loadout.set(GearSlot.LEGS, new OwnedItem(item("Ahrim's robeskirt"), 3, false));
		loadout.set(GearSlot.NECK, new OwnedItem(item("Amulet of the damned"), 4, false));
		assertTrue(Spell.ICE_BARRAGE.canAutocastWith(loadout));
	}

	@Test(timeout = 30_000)
	public void maxDpsModeUsesUnownedGearAndKeepsSafetyRules()
	{
		AccountSnapshot account = account("Abyssal whip", "Mirror shield");
		Monster knight = monster("Basilisk Knight");
		Map<CombatStyle, CombatContext> contexts = new EnumMap<>(CombatStyle.class);
		for (CombatStyle style : CombatStyle.values())
		{
			contexts.put(style, CombatContext.builder().monster(knight).levels(account.getLevels())
				.prayer(PrayerBoost.best(style, account)).potion(PotionBoost.NONE).onSlayerTask(true).build());
		}
		long start = System.nanoTime();
		Optimizer.Result max = new Optimizer(repository).optimize(Optimizer.Input.builder().monster(knight).account(account).contexts(contexts)
			.excludedItemIds(Collections.emptySet()).lockedItemIds(Collections.emptySet()).unrestricted(true).build());
		System.out.println("Max DPS mode took " + (System.nanoTime() - start) / 1_000_000 + " ms");
		StyleResult melee = max.getStyles().get(CombatStyle.MELEE);
		assertNotNull(melee);
		assertTrue(melee.getDps().getDps() > optimize(knight, account, true).getStyles().get(CombatStyle.MELEE).getDps().getDps());
		// Basilisk knights still require a mirror shield, so no two-handed weapon
		for (StyleResult style : max.getStyles().values())
		{
			assertNotNull(style.getLoadout().stats(GearSlot.SHIELD));
			assertTrue(MonsterRules.forMonster(knight, account).requirementFor(GearSlot.SHIELD).matches(style.getLoadout().stats(GearSlot.SHIELD)));
		}
	}

	@Test
	public void broadAmmoAndDorgeshuunRulesFollowTheWiki()
	{
		// Broad bolts: rune, dragon, dragon hunter, Armadyl and Zaryte crossbows only
		for (String crossbow : new String[]{"Rune crossbow", "Dragon crossbow", "Dragon hunter crossbow", "Armadyl crossbow", "Zaryte crossbow"})
		{
			assertTrue(crossbow, AmmoRules.compatible(item(crossbow), item("Amethyst broad bolts")));
			assertTrue(crossbow, AmmoRules.compatible(item(crossbow), item("Broad bolts")));
		}
		assertFalse(AmmoRules.compatible(item("Adamant crossbow"), item("Broad bolts")));
		assertFalse(AmmoRules.compatible(item("Dorgeshuun crossbow"), item("Amethyst broad bolts")));
		// Dorgeshuun crossbow: bone bolts and metal bolts up to iron, no gem tips
		assertTrue(AmmoRules.compatible(item("Dorgeshuun crossbow"), item("Bone bolts")));
		assertTrue(AmmoRules.compatible(item("Dorgeshuun crossbow"), item("Iron bolts")));
		assertFalse(AmmoRules.compatible(item("Dorgeshuun crossbow"), item("Steel bolts")));
		assertFalse(AmmoRules.compatible(item("Dorgeshuun crossbow"), item("Jade bolts (e)")));
		// Broad arrows: magic bows or stronger, and the bone shortbow
		assertTrue(AmmoRules.compatible(item("Magic shortbow"), item("Broad arrows")));
		assertTrue(AmmoRules.compatible(item("Bone shortbow"), item("Broad arrows")));
		assertFalse(AmmoRules.compatible(item("Yew shortbow"), item("Broad arrows")));
		// Bows never fire bolts, broad or otherwise
		for (String bolts : new String[]{"Broad bolts", "Amethyst broad bolts", "Dragon bolts"})
		{
			for (String bow : new String[]{"Scorching bow", "Twisted bow", "Magic shortbow", "Bone shortbow"})
			{
				assertFalse(bow + " + " + bolts, AmmoRules.compatible(item(bow), item(bolts)));
			}
		}
		assertTrue(AmmoRules.compatible(item("Scorching bow"), item("Dragon arrow")));
	}

	@Test
	public void minigameOnlyItemsAreNeverRecommended()
	{
		assertTrue(UpgradeFinder.isMinigameOnly(item("Crystal staff (perfected)")));
		assertTrue(UpgradeFinder.isMinigameOnly(item("Corrupted bow (attuned)")));
		assertTrue(UpgradeFinder.isMinigameOnly(item("Dawnbringer")));
		assertTrue(UpgradeFinder.isMinigameOnly(item("Wilderness champion amulet")));
		assertTrue(UpgradeFinder.isMinigameOnly(item("Echo venator bow")));
		assertFalse(UpgradeFinder.isMinigameOnly(item("Crystal helm")));
		assertFalse(UpgradeFinder.isMinigameOnly(item("Crystal bow")));
		assertFalse(UpgradeFinder.isMinigameOnly(item("Bow of Faerdhinen")));

		// Even if the bank snapshot was taken inside the Gauntlet
		AccountSnapshot account = account("Crystal staff (perfected)", "Crystal bow (perfected)", "Staff of fire", "Magic shortbow", "Amethyst arrow");
		Monster knight = monster("Basilisk Knight");
		Map<CombatStyle, CombatContext> contexts = new EnumMap<>(CombatStyle.class);
		for (CombatStyle style : CombatStyle.values())
		{
			contexts.put(style, CombatContext.builder().monster(knight).levels(account.getLevels())
				.prayer(PrayerBoost.NONE).potion(PotionBoost.NONE).build());
		}
		for (boolean unrestricted : new boolean[]{false, true})
		{
			Optimizer.Result result = new Optimizer(repository).optimize(Optimizer.Input.builder().monster(knight).account(account)
				.contexts(contexts).excludedItemIds(Collections.emptySet()).lockedItemIds(Collections.emptySet()).unrestricted(unrestricted).build());
			for (StyleResult style : result.getStyles().values())
			{
				for (OwnedItem item : style.getLoadout().items())
				{
					assertFalse(item.getStats().getName() + " is minigame-only", UpgradeFinder.isMinigameOnly(item.getStats()));
				}
			}
		}
	}

	@Test
	public void zaryteCrossbowStrengthensRubyBolts()
	{
		Monster knight = monster("Basilisk Knight");
		for (String[] combo : new String[][]{{"Zaryte crossbow", "66"}, {"Armadyl crossbow", "60"}})
		{
			Loadout loadout = new Loadout();
			loadout.set(GearSlot.WEAPON, new OwnedItem(item(combo[0]), item(combo[0]).getId(), false));
			loadout.set(GearSlot.AMMO, new OwnedItem(item("Ruby dragon bolts (e)"), item("Ruby dragon bolts (e)").getId(), false));
			loadout.setStance(Stance.RANGED_RAPID);
			DpsResult dps = DpsCalculator.calculate(loadout, plainContext(knight));
			// 300 HP knight: 22% with the Zaryte crossbow, 20% otherwise
			assertTrue(combo[0], dps.getEffects().contains("Ruby bolts (e): " + combo[1] + " damage on proc"));
		}
	}

	@Test
	public void ancestralForStandardSpellsVirtusForAncients()
	{
		Monster devil = monster("Dust devil");
		int[] maxHits = new int[4];
		int i = 0;
		for (Spell spell : new Spell[]{Spell.FIRE_SURGE, Spell.ICE_BARRAGE})
		{
			for (String set : new String[]{"Ancestral", "Virtus"})
			{
				Loadout loadout = new Loadout();
				loadout.set(GearSlot.WEAPON, new OwnedItem(item("Kodai wand"), item("Kodai wand").getId(), false));
				String top = set.equals("Ancestral") ? "Ancestral robe top" : "Virtus robe top";
				String bottom = set.equals("Ancestral") ? "Ancestral robe bottom" : "Virtus robe bottom";
				loadout.set(GearSlot.BODY, new OwnedItem(item(top), item(top).getId(), false));
				loadout.set(GearSlot.LEGS, new OwnedItem(item(bottom), item(bottom).getId(), false));
				loadout.setSpell(spell);
				loadout.setStance(Stance.AUTOCAST);
				maxHits[i++] = DpsCalculator.calculate(loadout, plainContext(devil)).getMaxHit();
			}
		}
		// Fire Surge 24: Kodai 15% + Ancestral 6% = 29, + Virtus 4% = 28
		assertEquals(29, maxHits[0]);
		assertEquals(28, maxHits[1]);
		// Ice Barrage 30: Ancestral 21% = 36, Virtus 4% + 6% ancient bonus = 25% = 37
		assertEquals(36, maxHits[2]);
		assertEquals(37, maxHits[3]);
	}

	@Test(timeout = 60_000)
	public void maxDpsFollowsTheSlayerTaskSetting()
	{
		AccountSnapshot account = account("Abyssal whip");
		Monster gargoyle = monster("Gargoyle");
		for (boolean onTask : new boolean[]{true, false})
		{
			Map<CombatStyle, CombatContext> contexts = new EnumMap<>(CombatStyle.class);
			contexts.put(CombatStyle.MELEE, CombatContext.builder().monster(gargoyle).levels(account.getLevels())
				.prayer(PrayerBoost.NONE).potion(PotionBoost.NONE).onSlayerTask(onTask).build());
			StyleResult melee = new Optimizer(repository).optimize(Optimizer.Input.builder().monster(gargoyle).account(account).contexts(contexts)
				.excludedItemIds(Collections.emptySet()).lockedItemIds(Collections.emptySet()).unrestricted(true).build())
				.getStyles().get(CombatStyle.MELEE);
			boolean slayerHelm = GearEffects.hasSlayerHelm(melee.getLoadout());
			assertEquals("on task: " + onTask + ", head " + melee.getLoadout().stats(GearSlot.HEAD).getName(), onTask, slayerHelm);
		}
	}

	@Test
	public void bootsOfStoneOnlyInKaruulm()
	{
		Monster wyrm = repository.getMonster(NpcID.WYRM_LIGHT);
		AccountSnapshot account = account("Abyssal whip");
		assertNotNull(MonsterRules.forMonster(wyrm, account, "Karuulm Slayer Dungeon - lower level").requirementFor(GearSlot.FEET));
		assertNull(MonsterRules.forMonster(wyrm, account, "Wyrmscraig Cavern").requirementFor(GearSlot.FEET));
		// Unknown place: wyrms are mostly fought in Karuulm, wyrmlings never are
		assertNotNull(MonsterRules.forMonster(wyrm, account, null).requirementFor(GearSlot.FEET));
		assertNull(MonsterRules.forMonster(repository.getMonster(NpcID.WYRMSCRAIG_WYRM01), account, null).requirementFor(GearSlot.FEET));
	}

	@Test
	public void onlyFireBreathersNeedDragonfireProtection()
	{
		AccountSnapshot account = account("Abyssal whip");
		MonsterRules wyrm = MonsterRules.forMonster(repository.getMonster(NpcID.WYRM_LIGHT), account, "Wyrmscraig Cavern");
		assertNull(wyrm.requirementFor(GearSlot.SHIELD));
		assertNotNull(MonsterRules.forMonster(monster("Black dragon"), account).requirementFor(GearSlot.SHIELD));
	}

	@Test
	public void blightedSuppliesOnlyWorkInTheWilderness()
	{
		AccountSnapshot account = account("Abyssal whip");
		addConsumable(account, 900_001, "Blighted super restore(4)");
		assertTrue(MonsterRules.matchingIds(account, MonsterRules.PRAYER_RESTORE, false).isEmpty());
		assertEquals(1, MonsterRules.matchingIds(account, MonsterRules.PRAYER_RESTORE, true).size());
	}

	@Test
	public void slayerTaskNames()
	{
		assertTrue(SlayerTaskMatcher.matches("Gargoyles", monster("Gargoyle")));
		assertTrue(SlayerTaskMatcher.matches("Lizardmen", monster("Lizardman shaman")));
		assertFalse(SlayerTaskMatcher.matches("Kurask", monster("Gargoyle")));
	}

	@Test(timeout = 20_000)
	public void largeBankFinishesQuickly()
	{
		AccountSnapshot account = account("Abyssal whip");
		int added = 0;
		for (EquipmentItem item : repository.getEquipment())
		{
			// Every 8th item gives a ~680 item bank spread across all slots
			if (added++ % 8 == 0)
			{
				account.getBank().put(item.getId(), 1);
				account.getItemNames().put(item.getId(), item.getName());
			}
		}
		long start = System.nanoTime();
		Optimizer.Result result = optimize(monster("Gargoyle"), account, true);
		long millis = (System.nanoTime() - start) / 1_000_000;
		System.out.println("Optimized " + account.getBank().size() + " items in " + millis + " ms");
		assertEquals(3, result.getStyles().size());
	}
}
