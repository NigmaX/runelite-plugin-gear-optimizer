package com.gearoptimizer.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.runelite.api.Skill;

/**
 * Curated wield requirements. The dataset has no level requirements, so this covers
 * metal tiers and commonly owned mid/high-end gear. Anything missing can be excluded in the panel.
 */
public final class ItemRequirements
{
	private static final class Rule
	{
		final Pattern pattern;
		final Map<Skill, Integer> levels;

		Rule(String regex, Object... pairs)
		{
			pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
			Map<Skill, Integer> map = new EnumMap<>(Skill.class);
			for (int i = 0; i < pairs.length; i += 2)
			{
				map.put((Skill) pairs[i], (Integer) pairs[i + 1]);
			}
			levels = Collections.unmodifiableMap(map);
		}
	}

	private static final Skill A = Skill.ATTACK;
	private static final Skill S = Skill.STRENGTH;
	private static final Skill D = Skill.DEFENCE;
	private static final Skill R = Skill.RANGED;
	private static final Skill M = Skill.MAGIC;
	private static final Skill P = Skill.PRAYER;
	private static final Skill H = Skill.HITPOINTS;

	private static final List<Rule> SPECIFIC = new ArrayList<>();
	private static final String[][] TIERS = {
		{"steel", "5"}, {"black", "10"}, {"white", "10"}, {"mithril", "20"}, {"adamant", "30"},
		{"rune", "40"}, {"runite", "40"}, {"granite", "50"}, {"dragon", "60"},
	};

	static
	{
		// Melee weapons
		add("^scythe of vitur|^(holy|sanguine) scythe", A, 80, S, 90);
		add("^osmumten's fang", A, 82);
		add("^soulreaper axe", A, 80, S, 80);
		add("^(ghrazi rapier|holy ghrazi rapier)", A, 80);
		add("^inquisitor's mace", A, 80);
		add("^blade of saeldor", A, 75);
		add("^abyssal tentacle", A, 75);
		add("^abyssal (whip|dagger)", A, 70);
		add("^abyssal bludgeon", A, 70, S, 70);
		add("^(armadyl|bandos|saradomin|zamorak|ancient) godsword", A, 75);
		add("^voidwaker", A, 75);
		add("^elder maul", A, 75, S, 75);
		add("^dragon hunter lance", A, 78);
		add("^(zamorakian (hasta|spear))", A, 70);
		add("^saradomin(')?s? (sword|blessed sword)", A, 70);
		add("^(arclight|emberlight)", A, 75);
		add("^keris partisan", A, 65);
		add("^leaf-bladed battleaxe", A, 65);
		add("^leaf-bladed sword", A, 50);
		add("^(viggora's|ursine) chainmace", A, 60);
		add("^blisterwood flail", A, 50);
		add("^ivandis flail", A, 40);
		add("^(dharok|guthan|torag|verac)'s", A, 70, S, 70);
		add("^tzhaar-ket-om", S, 60);
		add("^toktz-xil-(ak|ek)", A, 60);
		add("^(dual macuahuitl)", A, 70, S, 70);
		add("^noxious halberd", A, 80, S, 80);
		add("^bone mace", A, 70);
		add("^granite (maul|hammer)", A, 50, S, 50);
		add("^dragon (claws|warhammer)", A, 60);
		add("^burning claws", A, 70);
		add("^crystal halberd", A, 70, S, 35);
		add("^crystal (sword|axe)", A, 70);
		// Ranged weapons
		add("^twisted bow", R, 75);
		add("^bow of faerdhinen", R, 80);
		add("^(toxic|blazing|drygore|camphor|ironwood|rosewood) blowpipe", R, 75);
		add("^zaryte crossbow", R, 80);
		add("^armadyl crossbow", R, 70);
		add("^dragon hunter crossbow", R, 65);
		add("^dragon crossbow", R, 64);
		add("^rune crossbow", R, 61);
		add("^adamant crossbow", R, 46);
		add("^mithril crossbow", R, 36);
		add("^steel crossbow", R, 31);
		add("^heavy ballista", R, 75);
		add("^light ballista", R, 65);
		add("^(craw's|webweaver) bow", R, 60);
		add("^dark bow", R, 60);
		add("^magic (short|long)bow", R, 50);
		add("^yew (short|long)bow", R, 40);
		add("^maple (short|long)bow", R, 30);
		add("^willow (short|long)bow", R, 20);
		add("^oak (short|long)bow", R, 5);
		add("^crystal bow", R, 50, Skill.AGILITY, 50);
		add("^(red|black) chinchompa", R, 55);
		add("^amethyst (dart|knife|thrownaxe|arrow|javelin|broad bolts)", R, 50);
		// Magic weapons
		add("^tumeken's shadow", M, 85);
		add("^(holy )?sanguinesti staff", M, 82);
		add("^trident of the (seas|swamp)", M, 75);
		add("^(kodai wand|harmonised nightmare staff|eldritch nightmare staff|volatile nightmare staff|nightmare staff)", M, 75);
		add("^(thammaron's|accursed) sceptre", M, 60);
		add("^eye of ayak", M, 82);
		add("^warped sceptre", M, 62);
		add("^(toxic staff of the dead|staff of the dead|staff of light|staff of balance)", A, 75, M, 75);
		add("^(master wand|ancient staff)", M, 50);
		add("^iban's staff", A, 50, M, 50);
		add("^slayer's staff", M, 55);
		add("^(smoke|mystic .*|.* battle)staff", A, 30, M, 30);
		// Armour
		add("^torva", D, 80);
		add("^(masori .*\\(f\\))", R, 80, D, 30);
		add("^masori", R, 80);
		add("^ancestral", M, 75, D, 65);
		add("^virtus", M, 78, D, 65);
		add("^justiciar", D, 75);
		add("^inquisitor's", S, 70, D, 30);
		add("^(neitiznot faceguard|serpentine helm|tanzanite helm|magma helm)", D, 70);
		add("^(primordial|pegasian|eternal) boots", D, 75);
		add("^ferocious gloves", A, 80, D, 80);
		add("^zaryte vambraces", R, 80, D, 45);
		add("^(avernic defender)", A, 70, D, 70);
		add("^dragon defender", A, 60, D, 60);
		add("^rune defender", A, 40, D, 40);
		add("^bandos (chestplate|tassets|boots)", D, 65);
		add("^armadyl (helmet|chestplate|chainskirt)", R, 70, D, 40);
		add("^(dharok|guthan|torag|verac|karil|ahrim)'s .*(helm|hood|coif|top|body|legs|skirt|brassard|chaps|platebody|platelegs|chainskirt|robetop|robeskirt)", D, 70);
		add("^(elite )?void", A, 42, S, 42, D, 42, R, 42, M, 42, H, 42, P, 22);
		add("^obsidian (helmet|platebody|platelegs)", D, 60);
		add("^(dragonfire shield|dragonfire ward)", D, 75);
		add("^ancient wyvern shield", D, 70, M, 70);
		add("^crystal (helm|body|legs)", R, 70, D, 70);
		add("^(black d'hide|black dragonhide)", R, 70, D, 40);
		add("^(red d'hide|red dragonhide)", R, 60, D, 40);
		add("^(blue d'hide|blue dragonhide)", R, 50, D, 40);
		add("^(green d'hide|green dragonhide)", R, 40, D, 40);
		add("^(.*)slayer helmet", D, 10);
		add("^(berserker|archers|seers|warrior) ring", D, 1);
		add("^infernal cape", D, 1);
		add("^(mystic|infinity) ", M, 40, D, 20);
		add("^(3rd age|third age) (robe|mage)", M, 65, D, 30);
		add("^3rd age (range|archer)", R, 65, D, 45);
		add("^3rd age (full helmet|platebody|platelegs|kiteshield)", D, 65);
	}

	private ItemRequirements()
	{
	}

	private static void add(String regex, Object... pairs)
	{
		SPECIFIC.add(new Rule(regex, pairs));
	}

	/** Requirements found per item name; the rules are scanned once per item, not on every check. */
	private static final Map<String, Map<Skill, Integer>> CACHE = new ConcurrentHashMap<>();

	/**
	 * @return the levels needed to equip the item; empty when unknown or none
	 */
	public static Map<Skill, Integer> requirementsFor(EquipmentItem item)
	{
		return CACHE.computeIfAbsent(item.getName() + "|" + item.getGearSlot() + "|" + item.getCategory(), key -> lookUp(item));
	}

	private static Map<Skill, Integer> lookUp(EquipmentItem item)
	{
		String name = item.getName();
		for (Rule rule : SPECIFIC)
		{
			if (rule.pattern.matcher(name).find())
			{
				return rule.levels;
			}
		}

		String lower = item.getLowerName();
		GearSlot slot = item.getGearSlot();
		for (String[] tier : TIERS)
		{
			if (lower.startsWith(tier[0] + " "))
			{
				int level = Integer.parseInt(tier[1]);
				Map<Skill, Integer> map = new EnumMap<>(Skill.class);
				if (slot == GearSlot.WEAPON)
				{
					map.put(isRangedCategory(item.getCategory()) ? R : A, level);
				}
				else if (slot == GearSlot.AMMO)
				{
					map.put(R, level);
				}
				else
				{
					map.put(D, level);
				}
				return Collections.unmodifiableMap(map);
			}
		}
		return Collections.emptyMap();
	}

	/**
	 * @return null when the player meets all requirements, otherwise a short description of what is missing
	 */
	public static String missingRequirement(EquipmentItem item, Map<Skill, Integer> playerLevels)
	{
		for (Map.Entry<Skill, Integer> req : requirementsFor(item).entrySet())
		{
			int have = playerLevels.getOrDefault(req.getKey(), 1);
			if (have < req.getValue())
			{
				return req.getValue() + " " + req.getKey().getName();
			}
		}
		return null;
	}

	private static boolean isRangedCategory(String category)
	{
		return "Bow".equals(category) || "Crossbow".equals(category) || "Thrown".equals(category) || "Chinchompas".equals(category);
	}
}
