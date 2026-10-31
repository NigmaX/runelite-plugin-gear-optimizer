package com.gearoptimizer.ui;

import com.gearoptimizer.calc.Cannon;
import com.gearoptimizer.calc.DpsResult;
import com.gearoptimizer.calc.StyleResult;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * The strip across the top of a style tab: DPS with how it adds up, then max hit, accuracy, kill time and the other
 * numbers, with the settings that change them underneath.
 */
class StatsHeader extends JPanel
{
	/**
	 * @param settings the row of settings shown under the numbers
	 */
	StatsHeader(TabModel model, JComponent settings)
	{
		super(new BorderLayout());
		StyleResult result = model.getResult();
		DpsResult dps = result.getDps();
		Color accent = model.isBest() ? ColorScheme.BRAND_ORANGE : CompareTab.colorOf(result.getStyle());
		setBackground(Ui.CARD);
		setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
			BorderFactory.createEmptyBorder(6, 12, 6, 12)));

		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row.setOpaque(false);
		row.add(hero(model));
		row.add(divider());

		row.add(metric("Max hit", String.valueOf(dps.getMaxHit()), Color.WHITE, null, "Highest single hit"));
		row.add(metric("Accuracy", String.format("%.1f%%", dps.getAccuracy() * 100), Color.WHITE, null, "Chance each attack hits"));
		row.add(metric("Kill time", Ui.formatSeconds(result.timeToKill()), Color.WHITE, result.getContext().getMonster().getHitpoints() + " HP",
			"Average time to kill one monster"));
		row.add(metric("Speed", String.format("%.1fs", dps.getSpeedTicks() * DpsResult.SECONDS_PER_TICK), Color.WHITE,
			dps.getSpeedTicks() + " ticks", "Time between attacks"));
		row.add(metric("Defence", String.valueOf(result.getDefence()), Color.WHITE, "vs its attacks",
			"Your average defence bonus against the styles this monster attacks with"));
		row.add(metric("Supplies", result.getCost().getPerHour() > 0 ? Ui.gp(result.getCost().getPerHour()) : "Free", Color.WHITE,
			result.getCost().getPerHour() > 0 ? "per hour" : "no ammo or runes", "Ammo, runes and charges used per hour of attacking"));
		if (model.isMaxView())
		{
			TabModel.Shopping shopping = model.getShopping();
			int count = shopping.getSlots().size();
			row.add(metric("To buy", count == 0 ? "Nothing" : Ui.gp(shopping.getTotal()), Ui.MAX_GOLD, Ui.plural(count, "item"),
				"GE cost of the pieces you don't own yet"));
		}
		JPanel stats = new JPanel(new BorderLayout());
		stats.setOpaque(false);
		stats.add(row, BorderLayout.WEST);
		add(stats, BorderLayout.NORTH);
		add(settings, BorderLayout.SOUTH);
	}

	/**
	 * The one number that matters, with its context.
	 */
	private static JPanel hero(TabModel model)
	{
		StyleResult result = model.getResult();
		DpsResult dps = result.getDps();
		JPanel hero = Ui.column();
		JPanel heroTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		heroTop.setOpaque(false);
		heroTop.setAlignmentX(LEFT_ALIGNMENT);
		heroTop.add(Ui.small(result.getStyle().getDisplayName() + " DPS", Ui.MUTED));
		if (model.isMaxView())
		{
			heroTop.add(Ui.pill("MAX DPS", Ui.MAX_GOLD, Color.BLACK));
		}
		else if (model.isEdited())
		{
			heroTop.add(Ui.pill("CUSTOM", new Color(40, 70, 115), Color.WHITE));
		}
		else if (model.isBest())
		{
			heroTop.add(Ui.pill("BEST STYLE", ColorScheme.BRAND_ORANGE, Color.BLACK));
		}
		hero.add(heroTop);
		JPanel heroValue = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		heroValue.setOpaque(false);
		heroValue.setAlignmentX(LEFT_ALIGNMENT);
		heroValue.add(Ui.bold(String.format("%.2f", result.effectiveDps()), model.isBest() ? ColorScheme.BRAND_ORANGE : Color.WHITE, 30f));
		StyleResult compareTo = model.isMaxView() ? model.getOwnedBest() : model.getCurrent();
		if (compareTo != null)
		{
			double delta = result.effectiveDps() - compareTo.effectiveDps();
			Color tone = Ui.deltaColor(delta, new Color(40, 95, 45), new Color(110, 40, 35), new Color(60, 60, 60));
			JLabel pill = Ui.pill(String.format(model.isMaxView() ? "%+.2f vs your best" : "%+.2f vs worn", delta), tone, Color.WHITE);
			pill.setToolTipText(model.isMaxView() ? String.format("Your best owned setup does %.2f DPS", compareTo.effectiveDps())
				: String.format("What you're wearing now does %.2f DPS", compareTo.effectiveDps()));
			heroValue.add(pill);
		}
		hero.add(heroValue);
		int targets = result.targetsHit();
		Cannon.Fire cannon = dps.getCannon();
		hero.add(Ui.small(cannon != null
			? String.format("Incl. %.2f from the cannon on %s", cannon.getDps(), Ui.plural(cannon.getTargets(), "monster"))
			: targets > 1 ? String.format("Total over %d monsters (%.2f each)", targets, result.effectiveDps() / targets)
			: result.getFinishSeconds() > 0 ? String.format("%.2f before the %.1fs finishing swap", dps.getDps(), result.getFinishSeconds())
			: "against one monster", cannon != null ? Ui.INFO : Ui.MUTED));
		hero.add(Ui.small(breakdown(dps, targets), ColorScheme.LIGHT_GRAY_COLOR));
		hero.setToolTipText(breakdownTooltip(dps, targets));
		return hero;
	}

	/**
	 * Damage from plain hits (accuracy x max hit / 2) per attack; the rest of the average comes from
	 * effects like enchanted bolts, extra hits or area damage.
	 */
	private static double plainHitDamage(DpsResult dps)
	{
		return dps.getAccuracy() * dps.getMaxHit() / 2.0;
	}

	/**
	 * One line showing how DPS follows from damage per attack and attack speed.
	 */
	private static String breakdown(DpsResult dps, int targets)
	{
		double perAttack = dps.getAverageHit();
		double plain = plainHitDamage(dps) * Math.max(1, targets);
		double extra = perAttack - plain;
		String parts = Math.abs(extra) >= 0.05
			? String.format(" (%.1f hits %s %.1f %s)", plain, extra > 0 ? "+" : "-", Math.abs(extra), extraLabel(dps))
			: "";
		return String.format("%.1f avg per attack%s, every %.1fs", perAttack, parts, dps.getSpeedTicks() * DpsResult.SECONDS_PER_TICK);
	}

	private static String extraLabel(DpsResult dps)
	{
		for (String effect : dps.getEffects())
		{
			String e = effect.toLowerCase();
			if (e.contains("bolts (e)"))
			{
				return "bolt effect";
			}
			if (e.contains("scythe") || e.contains("dark bow") || e.contains("tonalztics") || e.contains("keris") || e.contains("fang"))
			{
				return "weapon effect";
			}
		}
		return "effects";
	}

	private static String breakdownTooltip(DpsResult dps, int targets)
	{
		double plain = plainHitDamage(dps);
		Cannon.Fire cannon = dps.getCannon();
		double own = cannon == null ? dps.getDps() : dps.getDps() - cannon.getDps();
		double speed = dps.getSpeedTicks() * DpsResult.SECONDS_PER_TICK;
		return String.format("<html>DPS = average damage per attack / seconds between attacks<br>"
				+ "Hits: %.1f%% accuracy x %d max hit / 2 = %.2f per attack%s<br>"
				+ "All damage incl. effects: %.2f per attack<br>"
				+ "Speed: %d ticks = %.1fs<br>"
				+ "%.2f / %.1fs = <b>%.2f DPS</b>%s</html>",
			dps.getAccuracy() * 100, dps.getMaxHit(), plain, targets > 1 ? " per monster, x" + targets + " monsters" : "",
			dps.getAverageHit(), dps.getSpeedTicks(), speed,
			dps.getAverageHit(), speed, own,
			cannon == null ? "" : String.format("<br>Cannon: %.1f%% accuracy x %d max hit / 2 x %s / 4.8s = %.2f DPS<br>"
					+ "Total: <b>%.2f DPS</b>", cannon.getAccuracy() * 100, cannon.getMaxHit(), Ui.plural(cannon.getTargets(), "monster"),
				cannon.getDps(), dps.getDps()));
	}

	private static JPanel divider()
	{
		JPanel divider = new JPanel();
		divider.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		divider.setPreferredSize(new Dimension(1, 52));
		JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 4));
		wrap.setOpaque(false);
		wrap.add(divider);
		return wrap;
	}

	private static JPanel metric(String name, String value, Color valueColor, String sub, String tooltip)
	{
		JPanel metric = Ui.column();
		metric.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		metric.add(Ui.small(name, Ui.MUTED));
		metric.add(Ui.bold(value, valueColor, 18f));
		metric.add(Ui.small(sub == null ? " " : sub, Ui.MUTED));
		metric.setToolTipText(tooltip);
		metric.setPreferredSize(new Dimension(98, 56));
		return metric;
	}
}
