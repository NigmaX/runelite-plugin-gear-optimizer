package com.gearoptimizer.calc;

import com.gearoptimizer.account.AccountSnapshot;
import com.gearoptimizer.data.EquipmentItem;
import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.Monster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.Value;

/**
 * Items a monster demands: protective gear that takes a slot, finishing items, supplies,
 * and weapon restrictions (kurask, vampyres).
 */
public final class MonsterRules
{
	/** An equipment slot that must hold one of the matching items. */
	@Value
	public static class SlotRequirement
	{
		String label;
		GearSlot slot;
		Pattern pattern;

		public boolean matches(EquipmentItem item)
		{
			return item != null && pattern.matcher(item.getName()).find();
		}
	}

	/** Something to bring in the inventory. */
	@Value
	public static class InventoryRequirement
	{
		String label;
		Pattern pattern;
		boolean mandatory;
		/** The requirement does not apply when the loadout already covers it, e.g. a granite hammer for gargoyles. */
		Predicate<Loadout> waivedBy;
		/** Used on the monster to finish it off, costing time on every kill. */
		boolean finisher;
	}

	/** Result of checking one requirement against the account, for display. */
	@Value
	public static class CheckedItem
	{
		String label;
		boolean mandatory;
		boolean owned;
		String detail;
		Set<Integer> itemIds;
	}

	@Getter
	private final List<SlotRequirement> slotRequirements = new ArrayList<>();
	@Getter
	private final List<InventoryRequirement> inventoryRequirements = new ArrayList<>();
	private final List<Predicate<Loadout>> weaponRules = new ArrayList<>();
	@Getter
	private final List<String> notes = new ArrayList<>();

	/** Ticks to use a finishing item on the monster and get back to attacking. */
	private static final int FINISH_TICKS = 3;
	static final Pattern FOOD = Pattern.compile("^(blighted )?(manta ray|anglerfish|karambwan)|^(shark|cooked karambwan|monkfish|dark crab|"
		+ "sea turtle|tuna potato|lobster|swordfish|potato with cheese|cooked moonlight antelope|cooked sunlight antelope|saradomin brew|"
		+ "curry|bass)", Pattern.CASE_INSENSITIVE);
	private static final Pattern BLIGHTED = Pattern.compile("^blighted ", Pattern.CASE_INSENSITIVE);
	static final Pattern PRAYER_RESTORE = Pattern.compile(
		"^(prayer potion|(blighted )?super restore|sanfew serum|prayer regeneration)", Pattern.CASE_INSENSITIVE);
	/** The cannon's base, stand, barrels and furnace, each with its ornamented version. */
	private static final String[] CANNON_PART_NAMES = {"base", "stand", "barrels", "furnace"};
	private static final int[][] CANNON_PARTS = {
		{ItemID.TWPART1, ItemID.LEAGUE_3_MULTICANNON_BASE},
		{ItemID.TWPART2, ItemID.LEAGUE_3_MULTICANNON_STAND},
		{ItemID.TWPART3, ItemID.LEAGUE_3_MULTICANNON_BARRELS},
		{ItemID.TWPART4, ItemID.LEAGUE_3_MULTICANNON_FURNACE},
	};
	/** Rune-name patterns (a rune or the combination runes that include it), compiled once per rune. */
	private static final Map<String, Pattern> RUNE_PATTERNS = new java.util.concurrent.ConcurrentHashMap<>();
	private static final String ANTIPOISON = "^(antipoison|superantipoison|antidote|anti-venom|extended anti-venom|sanfew serum)";
	private static final Pattern POISONOUS = Pattern.compile(
		"spider|scorpion|lizardman|hydra|zulrah|vorkath|araxyte|araxxor|sarachnis|venenatis|spindel|cave crawler"
			+ "|poison|snake|nagua|scorpia|kalphite queen|tortured gorilla|demonic gorilla|jungle horror",
		Pattern.CASE_INSENSITIVE);

	private MonsterRules()
	{
	}

	public static MonsterRules forMonster(Monster monster, AccountSnapshot account)
	{
		return forMonster(monster, account, null);
	}

	/**
	 * Karuulm Slayer Dungeon's floor burns without heat-proof boots. Wyrms and wyrmlings also live in Varlamore,
	 * where it doesn't.
	 */
	private static boolean inKaruulm(String name, String place)
	{
		return place == null ? !name.startsWith("wyrmling") : place.toLowerCase().contains("karuulm");
	}

	/**
	 * @param place where the player fights it, from its wiki locations, or null when unknown
	 */
	public static MonsterRules forMonster(Monster monster, AccountSnapshot account, String place)
	{
		MonsterRules rules = new MonsterRules();
		String name = monster.getName().toLowerCase();

		if (matches(name, "banshee"))
		{
			rules.slot("Earmuffs", GearSlot.HEAD, "^earmuffs|slayer helmet");
		}
		if (matches(name, "aberrant spectre|deviant spectre|abhorrent spectre|repugnant spectre"))
		{
			rules.slot("Nose peg", GearSlot.HEAD, "^nose peg|slayer helmet");
		}
		if (matches(name, "dust devil|smoke devil|choke devil"))
		{
			rules.slot("Facemask", GearSlot.HEAD, "^facemask|^gas mask|slayer helmet");
		}
		if (matches(name, "^wall beast"))
		{
			rules.slot("Spiny helmet", GearSlot.HEAD, "^spiny helmet|slayer helmet");
		}
		if (matches(name, "^basilisk|^cockatrice|^cockathrice|^monstrous basilisk"))
		{
			rules.slot("Mirror shield", GearSlot.SHIELD, "^mirror shield|^v's shield");
		}
		if (matches(name, "^killerwatt"))
		{
			rules.slot("Insulated boots", GearSlot.FEET, "^insulated boots");
		}
		if (matches(name, "^cave horror|^cave abomination"))
		{
			rules.slot("Witchwood icon", GearSlot.NECK, "^witchwood icon");
		}
		if (matches(name, "^(wyrm|drake|hydra|alchemical hydra|sulphur lizard|wyrmling)") && inKaruulm(name, place))
		{
			rules.slot("Boots of stone (Karuulm)", GearSlot.FEET, "^(boots of stone|boots of brimstone|granite boots)");
		}
		if (monster.hasAttribute("leafy"))
		{
			rules.weaponRules.add(l -> isLeafBladed(l));
			rules.notes.add("Only leaf-bladed weapons, broad ammunition or Magic Dart can damage this monster.");
		}
		if (!monster.hasAttribute("rat"))
		{
			// Scurrius' bone weapons can only attack rats
			rules.weaponRules.add(l -> !isRatBoneWeapon(l));
		}
		if (monster.hasAttribute("flying"))
		{
			rules.weaponRules.add(l -> l.getStance() == null || l.getStance().getStyle() != CombatStyle.MELEE
				|| "Polearm".equals(GearEffects.weaponCategory(l)) || "Salamander".equals(GearEffects.weaponCategory(l)));
			rules.notes.add("Flying: melee can only reach it with a halberd or salamander.");
		}
		if (monster.hasAttribute("vampyre3"))
		{
			rules.weaponRules.add(l -> isVampyrebane(l, false));
			rules.notes.add("Tier 3 vampyres: only blisterwood, hallowed or Ivandis flails, the sunspear or a blisterwood stake hurt it.");
		}
		else if (monster.hasAttribute("vampyre2"))
		{
			rules.weaponRules.add(l -> isVampyrebane(l, true) || DpsCalculator.wearsEfaritay(l));
			rules.notes.add("Tier 2 vampyres: needs a vampyrebane weapon, or Efaritay's aid (half damage with other weapons).");
		}

		if (matches(name, "^gargoyle|^marble gargoyle") && !account.isGargoyleSmasher())
		{
			rules.finisher("Rock hammer", "^(rock hammer|rock thrownhammer|granite hammer)",
				l -> GearEffects.lowerName(l.weapon()).startsWith("granite hammer"));
			rules.notes.add("A granite hammer finishes gargoyles itself; other weapons need a rock hammer swap every kill.");
		}
		if (matches(name, "^rockslug|^giant rockslug"))
		{
			rules.finisher("Bag of salt", "^bag of salt|^salt", null);
		}
		if (matches(name, "^(desert lizard|small lizard|lizard)$"))
		{
			rules.finisher("Ice cooler", "^ice cooler", null);
		}
		if (matches(name, "zygomite|^fungi"))
		{
			rules.finisher("Fungicide spray", "^fungicide spray", null);
		}
		if (matches(name, "^molanisk"))
		{
			rules.inventory("Slayer bell", "^slayer bell", true, null);
		}
		if (matches(name, "^lizardman shaman|^lizardman"))
		{
			rules.notes.add("Shamans' acid spit is dodgeable; antipoison recommended.");
		}

		// Only monsters that breathe fire or ice need protection from it; wyrms, hydras and baby dragons don't
		if (monster.attacksWith("Icy breath"))
		{
			rules.slot("Wyvern protection", GearSlot.SHIELD, "^(elemental shield|mind shield|dragonfire ward|dragonfire shield|ancient wyvern shield)");
		}
		else if (monster.attacksWith("Dragonfire"))
		{
			boolean ownsAntifire = owns(account, Pattern.compile("^(extended )?(super )?antifire|^antifire mix", Pattern.CASE_INSENSITIVE));
			if (ownsAntifire)
			{
				rules.inventory("Antifire potion", "^(extended )?(super )?antifire|^antifire mix", true,
					l -> GearEffects.lowerName(l.stats(GearSlot.SHIELD)).matches("^(anti-dragon shield|dragonfire shield|dragonfire ward|ancient wyvern shield).*")
						&& matches(name, "^(green|blue|red|black|baby|brutal)"));
				rules.notes.add("Metal and chromatic dragons: an antifire with an anti-dragon shield gives full protection.");
			}
			else
			{
				rules.slot("Dragonfire shield", GearSlot.SHIELD, "^(anti-dragon shield|dragonfire shield|dragonfire ward|ancient wyvern shield)");
			}
		}

		if (POISONOUS.matcher(name).find())
		{
			rules.inventory("Antipoison / anti-venom", ANTIPOISON, false, null);
		}
		return rules;
	}

	private void slot(String label, GearSlot slot, String regex)
	{
		slotRequirements.add(new SlotRequirement(label, slot, Pattern.compile(regex, Pattern.CASE_INSENSITIVE)));
	}

	private void inventory(String label, String regex, boolean mandatory, Predicate<Loadout> waivedBy)
	{
		inventoryRequirements.add(new InventoryRequirement(label, Pattern.compile(regex, Pattern.CASE_INSENSITIVE), mandatory, waivedBy, false));
	}

	private void finisher(String label, String regex, Predicate<Loadout> waivedBy)
	{
		inventoryRequirements.add(new InventoryRequirement(label, Pattern.compile(regex, Pattern.CASE_INSENSITIVE), true, waivedBy, true));
	}

	/**
	 * @return seconds per kill spent using a finishing item (rock hammer, bag of salt...), 0 if the loadout avoids it
	 */
	public double finishingSeconds(Loadout loadout)
	{
		for (InventoryRequirement requirement : inventoryRequirements)
		{
			if (requirement.isFinisher() && (requirement.getWaivedBy() == null || !requirement.getWaivedBy().test(loadout)))
			{
				return FINISH_TICKS * DpsResult.SECONDS_PER_TICK;
			}
		}
		return 0;
	}

	/**
	 * @return whether the loadout's weapon/ammo can damage this monster at all
	 */
	public boolean allowsWeapon(Loadout loadout)
	{
		for (Predicate<Loadout> rule : weaponRules)
		{
			if (!rule.test(loadout))
			{
				return false;
			}
		}
		return true;
	}

	public SlotRequirement requirementFor(GearSlot slot)
	{
		for (SlotRequirement requirement : slotRequirements)
		{
			if (requirement.getSlot() == slot)
			{
				return requirement;
			}
		}
		return null;
	}

	/**
	 * Lists what to bring for the loadout, and whether the account has it.
	 */
	public List<CheckedItem> checkInventory(Loadout loadout, AccountSnapshot account, CombatContext ctx)
	{
		PrayerBoost prayer = ctx.getPrayer();
		List<CheckedItem> result = new ArrayList<>();
		if (ctx.getCannonMaxHit() > 0)
		{
			result.add(checkCannon(account));
			boolean granite = ctx.getCannonMaxHit() >= Cannon.GRANITE_MAX_HIT;
			result.add(checkItem(granite ? "Granite cannonballs" : "Cannonballs", granite ? ItemID.GRANITE_CANNONBALL : ItemID.MCANNONBALL, account));
		}
		for (InventoryRequirement requirement : inventoryRequirements)
		{
			if (requirement.getWaivedBy() != null && requirement.getWaivedBy().test(loadout))
			{
				continue;
			}
			result.add(check(requirement.getLabel(), requirement.getPattern(), requirement.isMandatory(), account, ctx.isInWilderness()));
		}

		if (loadout.getSpell() != null)
		{
			result.add(checkRunes(loadout, account));
		}
		if (loadout.isBlowpipe())
		{
			String dart = loadout.getBlowpipeDart() == null ? "darts" : loadout.getBlowpipeDart().getStats().getName();
			result.add(new CheckedItem("Load blowpipe with " + dart, true, loadout.getBlowpipeDart() != null,
				loadout.getBlowpipeDart() == null ? "No darts owned" : null,
				loadout.getBlowpipeDart() == null ? Collections.emptySet() : Set.of(loadout.getBlowpipeDart().getItemId())));
		}
		if (prayer != PrayerBoost.NONE)
		{
			result.add(check("Prayer potions (" + prayer.getDisplayName() + ")", PRAYER_RESTORE, false, account, ctx.isInWilderness()));
		}
		result.add(check("Food", FOOD, false, account, ctx.isInWilderness()));
		return result;
	}

	/** Label of the cannon's checklist entry; the inventory gives each of its parts a slot. */
	public static final String CANNON = "Dwarf multicannon";

	private static CheckedItem checkCannon(AccountSnapshot account)
	{
		Set<Integer> ids = new LinkedHashSet<>();
		List<String> missing = new ArrayList<>();
		for (int i = 0; i < CANNON_PARTS.length; i++)
		{
			int found = -1;
			for (int id : CANNON_PARTS[i])
			{
				if (account.quantityOf(id) > 0)
				{
					found = id;
					break;
				}
			}
			if (found < 0)
			{
				missing.add(CANNON_PART_NAMES[i]);
			}
			else
			{
				ids.add(found);
			}
		}
		return new CheckedItem(CANNON, true, missing.isEmpty(),
			missing.isEmpty() ? "Base, stand, barrels and furnace" : "Missing the " + String.join(", ", missing), ids);
	}

	/**
	 * Checks for one exact item, such as a cannonball type; always required.
	 */
	private static CheckedItem checkItem(String label, int itemId, AccountSnapshot account)
	{
		int count = account.quantityOf(itemId);
		String name = account.nameOf(itemId);
		return new CheckedItem(label, true, count > 0, count > 0 ? (name == null ? label : name) + " ×" + count : "Not in bank",
			count > 0 ? Set.of(itemId) : Collections.emptySet());
	}

	private static CheckedItem check(String label, Pattern pattern, boolean mandatory, AccountSnapshot account, boolean wilderness)
	{
		Set<Integer> ids = matchingIds(account, pattern, wilderness);
		int count = 0;
		String example = null;
		for (int id : ids)
		{
			count += account.quantityOf(id);
			if (example == null)
			{
				example = account.nameOf(id);
			}
		}
		String detail = ids.isEmpty() ? "Not in bank" : example + (count > 1 ? " ×" + count : "");
		return new CheckedItem(label, mandatory, !ids.isEmpty(), detail, ids);
	}

	private static CheckedItem checkRunes(Loadout loadout, AccountSnapshot account)
	{
		Spell spell = loadout.getSpell();
		List<String> missing = new ArrayList<>();
		Set<Integer> ids = new LinkedHashSet<>();
		for (String rune : spell.getRunes())
		{
			if (providedByGear(rune, loadout))
			{
				continue;
			}
			Pattern runes = RUNE_PATTERNS.computeIfAbsent(rune,
				r -> Pattern.compile("^(" + r + "|" + combinationRunes(r) + ") rune", Pattern.CASE_INSENSITIVE));
			Set<Integer> found = matchingIds(account, runes);
			if (found.isEmpty())
			{
				missing.add(rune);
			}
			ids.addAll(found);
		}
		String label = "Runes for " + spell.getDisplayName() + " (" + spell.getSpellbook().name().toLowerCase() + ")";
		return new CheckedItem(label, true, missing.isEmpty(),
			missing.isEmpty() ? String.join(", ", spell.getRunes()) : "Missing " + String.join(", ", missing), ids);
	}

	private static String combinationRunes(String rune)
	{
		switch (rune)
		{
			case "Air":
				return "mist|dust|smoke";
			case "Water":
				return "mist|mud|steam";
			case "Earth":
				return "dust|mud|lava";
			case "Fire":
				return "smoke|steam|lava|sunfire";
			default:
				return rune;
		}
	}

	static boolean providedByGear(String rune, Loadout loadout)
	{
		String weapon = GearEffects.lowerName(loadout.weapon());
		String shield = GearEffects.lowerName(loadout.stats(GearSlot.SHIELD));
		String element = rune.toLowerCase();
		if (shield.startsWith("tome of " + element))
		{
			return true;
		}
		if (weapon.contains("staff of " + element) || weapon.contains(element + " battlestaff") || weapon.contains("mystic " + element + " staff"))
		{
			return true;
		}
		for (String combo : combinationRunes(rune).split("\\|"))
		{
			if (weapon.startsWith(combo + " battlestaff") || weapon.startsWith("mystic " + combo + " staff"))
			{
				return true;
			}
		}
		return "Water".equals(rune) && weapon.startsWith("kodai wand");
	}

	/**
	 * @param wilderness whether blighted supplies count; they only work in the Wilderness
	 */
	public static Set<Integer> matchingIds(AccountSnapshot account, Pattern pattern, boolean wilderness)
	{
		Set<Integer> ids = matchingIds(account, pattern);
		if (!wilderness)
		{
			ids.removeIf(id -> BLIGHTED.matcher(String.valueOf(account.nameOf(id))).find());
		}
		return ids;
	}

	public static Set<Integer> matchingIds(AccountSnapshot account, Pattern pattern)
	{
		Set<Integer> ids = new LinkedHashSet<>();
		Map<Integer, String> names = account.getItemNames();
		if (names == null)
		{
			return ids;
		}
		for (Map.Entry<Integer, String> entry : names.entrySet())
		{
			if (entry.getValue() != null && pattern.matcher(entry.getValue()).find() && account.quantityOf(entry.getKey()) > 0)
			{
				ids.add(entry.getKey());
			}
		}
		return ids;
	}

	private static boolean owns(AccountSnapshot account, Pattern pattern)
	{
		return !matchingIds(account, pattern).isEmpty();
	}

	/** Monster-name patterns, compiled once; rules are built for every calculation. */
	private static final Map<String, Pattern> NAME_PATTERNS = new java.util.concurrent.ConcurrentHashMap<>();

	private static boolean matches(String lowerName, String regex)
	{
		return NAME_PATTERNS.computeIfAbsent(regex, Pattern::compile).matcher(lowerName).find();
	}

	private static boolean isVampyrebane(Loadout loadout, boolean tierTwo)
	{
		GearEffects.Vampyrebane bane = GearEffects.Vampyrebane.of(GearEffects.lowerName(loadout.weapon()));
		boolean melee = loadout.getStance() == null || loadout.getStance().getStyle() == CombatStyle.MELEE;
		return bane != null && (!bane.meleeOnly || melee) && bane.maxTier >= (tierTwo ? 2 : 3);
	}

	private static boolean isRatBoneWeapon(Loadout loadout)
	{
		String weapon = GearEffects.lowerName(loadout.weapon());
		return weapon.startsWith("bone mace") || weapon.startsWith("bone shortbow") || weapon.startsWith("bone staff");
	}

	private static boolean isLeafBladed(Loadout loadout)
	{
		String weapon = GearEffects.lowerName(loadout.weapon());
		if (weapon.startsWith("leaf-bladed"))
		{
			return true;
		}
		if (loadout.getSpell() == Spell.MAGIC_DART)
		{
			return true;
		}
		String ammo = GearEffects.lowerName(loadout.stats(GearSlot.AMMO));
		return GearEffects.usesAmmoSlot(loadout.weapon()) && (ammo.startsWith("broad") || ammo.contains("broad bolts"));
	}

	/**
	 * @return true for slayer helmets and black masks
	 */}
