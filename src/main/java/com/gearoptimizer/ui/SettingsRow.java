package com.gearoptimizer.ui;

import com.gearoptimizer.GearOptimizerConfig;
import com.gearoptimizer.calc.AoeTargets;
import com.gearoptimizer.calc.CombatStyle;
import com.gearoptimizer.calc.PrayerBoost;
import com.gearoptimizer.calc.StyleResult;
import com.gearoptimizer.data.Monster;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The settings that change damage and max hit, right under the numbers they change. They apply to every tab.
 */
class SettingsRow extends JPanel
{
	SettingsRow(StyleResult result, StyleTab.Actions actions)
	{
		super(new BorderLayout(12, 0));
		GearOptimizerConfig settings = actions.settings();
		setOpaque(false);
		setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(6, 0, 0, 0)));

		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		left.setOpaque(false);
		PrayerBoost prayer = actions.account() == null ? PrayerBoost.NONE : PrayerBoost.best(result.getStyle(), actions.account());
		boolean prayerOn = !"NONE".equals(settings.prayerMode());
		left.add(Ui.toggle(prayer == PrayerBoost.NONE ? "Prayer" : prayer.getDisplayName(), prayerOn,
			"Assume " + (prayer == PrayerBoost.NONE ? "your best offensive prayer" : prayer.getDisplayName())
				+ " is on. Applies to every style's best unlocked prayer.",
			on -> actions.changeSetting("prayerMode", on ? "BEST" : "NONE")));

		left.add(Ui.small("  Slayer task", Ui.MUTED));
		boolean detected = result.getContext().isOnSlayerTask();
		left.add(Ui.segmented(new String[][]{
			{"AUTO", "AUTO".equals(settings.slayerMode()) ? (detected ? "Auto: on" : "Auto: off") : "Auto",
				"Reads your current task from the Slayer plugin"},
			{"ON", "On", "Count slayer helm and black mask bonuses"},
			{"OFF", "Off", "Ignore slayer helm and black mask bonuses"},
		}, settings.slayerMode(), value -> actions.changeSetting("slayerMode", value)));

		// The place picked can put the fight in the Wilderness whatever the setting says
		boolean wildernessPlace = result.getContext().isInWilderness() && !settings.inWilderness();
		JToggleButton wilderness = Ui.toggle("Wilderness", result.getContext().isInWilderness(), wildernessPlace
				? "On because the place you fight at is in the Wilderness"
				: "Enables Craw's bow, Viggora's chainmace, Thammaron's sceptre and their upgrades, and blighted supplies",
			on -> actions.changeSetting("inWilderness", on));
		wilderness.setEnabled(!wildernessPlace);
		left.add(wilderness);

		left.add(Ui.toggle("Cannon", settings.cannon(),
			"<html>Fire a dwarf multicannon alongside you. It shoots each monster around it about every 4.8s<br>"
				+ "(up to 8 monsters) with your accuracy, max hit 30, or 35 with granite cannonballs.<br>"
				+ "Only where a cannon can be set up; in single-combat areas set Targets hit to 1.</html>",
			on -> actions.changeSetting("cannon", on)));

		if (result.getStyle() != CombatStyle.MELEE || settings.cannon())
		{
			left.add(Ui.small("  Targets hit", Ui.MUTED));
			left.add(targets(result, settings, actions));
		}
		add(left, BorderLayout.WEST);
		add(priority(settings, actions), BorderLayout.EAST);
	}

	/**
	 * How many stacked monsters area attacks and the cannon hit. Auto shows what it assumed for this monster, so the
	 * guess is never hidden.
	 */
	private static JComboBox<String> targets(StyleResult result, GearOptimizerConfig settings, StyleTab.Actions actions)
	{
		Monster monster = result.getContext().getMonster();
		boolean auto = "AUTO".equals(settings.aoeTargets());
		String autoLabel = "Auto: " + (auto ? result.getContext().getAoeTargets() : AoeTargets.guess(monster));
		JComboBox<String> aoe = new JComboBox<>(new String[]{autoLabel, "1", "2", "3", "4", "6", "9"});
		aoe.setFocusable(false);
		aoe.setFont(FontManager.getRunescapeSmallFont());
		aoe.setSelectedItem(auto ? autoLabel : settings.aoeTargets());
		int maxInArea = AoeTargets.maxInArea(monster);
		aoe.setToolTipText("<html>Monsters stacked together in a multi-combat area: burst/barrage, chinchompas and the cannon hit them.<br>"
			+ "Auto assumes " + AoeTargets.STACKED + " only on tasks that are known to stack (dust devils, "
			+ "nechryael, abyssal demons, smoke devils), otherwise 1.<br>"
			+ "A 3x3 attack fits at most " + Ui.plural(maxInArea, monster.getDisplayName().toLowerCase())
			+ " (bigger monsters fit fewer); the cannon isn't limited by size.</html>");
		aoe.addActionListener(e ->
		{
			String picked = (String) aoe.getSelectedItem();
			actions.changeSetting("aoeTargets", picked != null && picked.startsWith("Auto") ? "AUTO" : picked);
		});
		return aoe;
	}

	/**
	 * The slider trading damage for defence.
	 */
	private static JPanel priority(GearOptimizerConfig settings, StyleTab.Actions actions)
	{
		JPanel priority = new JPanel(new BorderLayout(4, 0));
		priority.setOpaque(false);
		priority.add(Ui.small("Priority  DPS", Ui.MUTED), BorderLayout.WEST);
		JSlider slider = new JSlider(0, 100, settings.defenceWeight());
		slider.setOpaque(false);
		slider.setFocusable(false);
		slider.setPreferredSize(new Dimension(110, 20));
		Runnable describe = () ->
		{
			int value = slider.getValue();
			slider.setToolTipText("Trade damage for defence against the monster's attack styles: " + (value == 0 ? "maximum damage"
				: value >= 100 ? "maximum defence" : (100 - value) + "% damage, " + value + "% defence"));
		};
		describe.run();
		slider.addChangeListener(e ->
		{
			describe.run();
			if (!slider.getValueIsAdjusting())
			{
				actions.changeSetting("defenceWeight", slider.getValue());
			}
		});
		priority.add(slider, BorderLayout.CENTER);
		priority.add(Ui.small("Defence", Ui.MUTED), BorderLayout.EAST);
		return priority;
	}
}
