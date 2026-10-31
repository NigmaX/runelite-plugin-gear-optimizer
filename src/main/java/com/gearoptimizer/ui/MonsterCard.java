package com.gearoptimizer.ui;

import com.gearoptimizer.data.Monster;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import lombok.Getter;
import net.runelite.client.ui.ColorScheme;

/**
 * The sidebar card describing the selected monster: its stats, defences and what it is weak to.
 */
final class MonsterCard
{
	@Getter
	private final JPanel panel = Ui.card(null);
	/** Width text wraps at. */
	private final int width;

	MonsterCard(int width)
	{
		this.width = width;
	}

	void showNone()
	{
		panel.removeAll();
		panel.add(Ui.bold("No monster selected", Ui.MUTED));
	}

	void show(Monster monster)
	{
		panel.removeAll();
		panel.add(Ui.bold(monster.getDisplayName(), Color.WHITE));
		panel.add(Ui.small("Combat level " + monster.getLevel(), Ui.MUTED));
		panel.add(Ui.gap(6));

		Monster.Skills skills = monster.getSkills();
		panel.add(statGrid(new String[][]{
			{"Hitpoints", String.valueOf(skills.getHp())}, {"Defence", String.valueOf(skills.getDef())},
			{"Magic", String.valueOf(skills.getMagic())}, {"Size", monster.getSize() + "x" + monster.getSize()},
		}));
		panel.add(Ui.gap(6));

		Monster.Defensive def = monster.getDefensive();
		panel.add(Ui.small("Defence bonuses", Ui.MUTED));
		panel.add(statGrid(new String[][]{
			{"Stab", String.valueOf(def.getStab())}, {"Slash", String.valueOf(def.getSlash())},
			{"Crush", String.valueOf(def.getCrush())}, {"Magic", String.valueOf(def.getMagic())},
			{"Light", String.valueOf(def.getLight())}, {"Standard", String.valueOf(def.getStandard())},
			{"Heavy", String.valueOf(def.getHeavy())}, {"Flat armour", String.valueOf(def.getFlatArmour())},
		}));
		panel.add(Ui.gap(6));

		panel.add(Ui.small("Weak to", Ui.MUTED));
		for (String weakness : weaknesses(monster))
		{
			panel.add(Ui.markedWrapped(Ui.Glyph.DOT, Ui.GOOD, weakness, Color.WHITE, width - 20));
		}
		panel.add(Ui.gap(6));

		List<String> tags = new ArrayList<>();
		if (monster.isSlayerMonster())
		{
			tags.add("Slayer monster");
		}
		if (monster.getAttributes() != null)
		{
			for (String attribute : monster.getAttributes())
			{
				String clean = attribute.replaceAll("\\d", "");
				tags.add(Character.toUpperCase(clean.charAt(0)) + clean.substring(1));
			}
		}
		if (!tags.isEmpty())
		{
			panel.add(Ui.wrapped(String.join(", ", tags), ColorScheme.BRAND_ORANGE, width));
		}
		if (hasElementalWeakness(monster))
		{
			panel.add(Ui.small("Weak to " + monster.getWeakness().getElement() + " spells (+" + monster.getWeakness().getSeverity() + "%)",
				Ui.INFO));
		}
		if (!monster.getStyle().isEmpty())
		{
			panel.add(Ui.wrapped("Attacks with " + String.join(", ", monster.getStyle()).toLowerCase(), Ui.MUTED, width));
		}
		panel.revalidate();
		panel.repaint();
	}

	private static boolean hasElementalWeakness(Monster monster)
	{
		return monster.getWeakness() != null && monster.getWeakness().getElement() != null && monster.getWeakness().getSeverity() > 0;
	}

	/**
	 * Lists what this monster is weak to: its lowest defence per combat style, its elemental
	 * weakness, and the weapons that exploit its attributes.
	 */
	private static List<String> weaknesses(Monster monster)
	{
		List<String> list = new ArrayList<>();
		Monster.Defensive def = monster.getDefensive();
		String[] melee = lowest(new String[]{"stab", "slash", "crush"}, new int[]{def.getStab(), def.getSlash(), def.getCrush()});
		list.add("Melee: " + melee[0] + " (" + melee[1] + " defence)");
		String[] ranged = lowest(new String[]{"light (darts, knives, blowpipe)", "standard (arrows)", "heavy (bolts, javelins)"},
			new int[]{def.getLight(), def.getStandard(), def.getHeavy()});
		list.add("Ranged: " + ranged[0] + ", " + ranged[1] + " defence");
		String magic = "Magic: " + def.getMagic() + " defence, level " + monster.getSkills().getMagic();
		if (hasElementalWeakness(monster))
		{
			magic += ", " + monster.getWeakness().getElement() + " spells +" + monster.getWeakness().getSeverity() + "%";
		}
		list.add(magic);

		String name = monster.getName().toLowerCase();
		if (monster.isDemon())
		{
			list.add("Demonbane: Arclight, Emberlight, Darklight, Scorching bow, demonbane spells");
		}
		if (monster.isDragon())
		{
			list.add("Dragonbane: Dragon hunter lance, crossbow and wand");
		}
		if (monster.isUndead())
		{
			list.add("Undead: Salve amulet (stronger than slayer helm), Crumble Undead");
		}
		if (monster.hasAttribute("kalphite"))
		{
			list.add("Kalphite: Keris partisans");
		}
		if (monster.hasAttribute("leafy"))
		{
			list.add("Leafy: leaf-bladed weapons (battleaxe best), broad ammo, Magic Dart");
		}
		if (monster.hasAttribute("vampyre1") || monster.hasAttribute("vampyre2") || monster.hasAttribute("vampyre3"))
		{
			list.add("Vampyre: Blisterwood flail/sickle, Ivandis flail");
		}
		if (monster.hasAttribute("golem"))
		{
			list.add("Golembane: Granite hammer (+30%), Barronite mace");
		}
		if (monster.hasAttribute("rat"))
		{
			list.add("Rat: bone mace, bone shortbow, bone staff");
		}
		if (monster.hasAttribute("shade"))
		{
			list.add("Shade: Gadderhammer");
		}
		if (monster.hasAttribute("flying"))
		{
			list.add("Flying: ranged, magic, halberds and salamanders only");
		}
		if (name.startsWith("gargoyle") || name.startsWith("marble gargoyle"))
		{
			list.add("Granite hammer finishes it without a rock hammer swap");
		}
		return list;
	}

	private static String[] lowest(String[] names, int[] values)
	{
		int best = 0;
		for (int i = 1; i < values.length; i++)
		{
			if (values[i] < values[best])
			{
				best = i;
			}
		}
		return new String[]{names[best], String.valueOf(values[best])};
	}

	private static JPanel statGrid(String[][] pairs)
	{
		JPanel grid = new JPanel(new GridLayout(0, 2, 14, 2));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (String[] pair : pairs)
		{
			JPanel cell = new JPanel(new BorderLayout());
			cell.setOpaque(false);
			cell.add(Ui.small(pair[0], Ui.MUTED), BorderLayout.WEST);
			cell.add(Ui.small(pair[1], Color.WHITE), BorderLayout.EAST);
			grid.add(cell);
		}
		return grid;
	}
}
