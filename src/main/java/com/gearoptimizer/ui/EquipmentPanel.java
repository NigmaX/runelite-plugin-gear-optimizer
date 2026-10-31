package com.gearoptimizer.ui;

import com.gearoptimizer.calc.CombatStyle;
import com.gearoptimizer.calc.Loadout;
import com.gearoptimizer.calc.StyleResult;
import com.gearoptimizer.calc.UpgradeFinder;
import com.gearoptimizer.data.EquipmentItem;
import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.Monster;
import com.gearoptimizer.data.OwnedItem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The equipment column: the worn items laid out like the in-game equipment tab, then the spell or darts, how the setup
 * attacks, and the bonuses that apply.
 */
class EquipmentPanel extends JPanel
{
	private static final GearSlot[][] GRID = {
		{null, GearSlot.HEAD, null},
		{GearSlot.CAPE, GearSlot.NECK, GearSlot.AMMO},
		{GearSlot.WEAPON, GearSlot.BODY, GearSlot.SHIELD},
		{null, GearSlot.LEGS, null},
		{GearSlot.HANDS, GearSlot.FEET, GearSlot.RING},
	};
	/** Width of text under the equipment grid, matching the grid. */
	static final int TEXT_WIDTH = 150;
	/** Upgrades below this share of the setup's DPS don't get a marker, so markers stay meaningful. */
	private static final double MARKER_MIN_GAIN = 0.01;
	private static final Color SELECTED = new Color(70, 50, 20);

	private final TabModel model;
	private final StyleResult result;
	private final StyleTab.Actions actions;
	private final ItemIcons icons;
	private final Consumer<GearSlot> onSlotClicked;
	/** Items the player forces into every setup. */
	private final Set<Integer> locked;
	private final Map<GearSlot, JLabel> cells = new EnumMap<>(GearSlot.class);
	private GearSlot highlighted;

	/**
	 * @param onSlotClicked runs when the player clicks a slot to see its options
	 */
	EquipmentPanel(TabModel model, StyleTab.Actions actions, ItemIcons icons, Consumer<GearSlot> onSlotClicked)
	{
		this.model = model;
		this.result = model.getResult();
		this.actions = actions;
		this.icons = icons;
		this.onSlotClicked = onSlotClicked;
		this.locked = actions.locked();
		Ui.asCard(this, null);

		Loadout loadout = result.getLoadout();
		add(titleRow());
		add(Ui.gap(6));
		JPanel grid = new JPanel(new GridLayout(GRID.length, 3, 3, 3));
		grid.setOpaque(false);
		grid.setAlignmentX(LEFT_ALIGNMENT);
		for (GearSlot[] row : GRID)
		{
			for (GearSlot slot : row)
			{
				if (slot == null)
				{
					JPanel spacer = new JPanel();
					spacer.setOpaque(false);
					grid.add(spacer);
				}
				else
				{
					JLabel cell = slotCell(slot, loadout.get(slot), worthwhileUpgrade(slot));
					cells.put(slot, cell);
					grid.add(cell);
				}
			}
		}
		grid.setMaximumSize(grid.getPreferredSize());
		add(grid);
		add(Ui.gap(4));
		add(legend());
		add(Ui.gap(6));
		SpellDartBox boxes = new SpellDartBox(model, actions, icons);
		JPanel spell = boxes.spellBox();
		if (spell != null)
		{
			add(spell);
			add(Ui.gap(4));
		}
		JPanel darts = boxes.dartBox();
		if (darts != null)
		{
			add(darts);
			add(Ui.gap(4));
		}
		add(Ui.regular(attackLine(loadout), Color.WHITE));
		add(Ui.wrapped(targetDefenceLine(loadout, result.getContext().getMonster()), Ui.INFO, TEXT_WIDTH));
		if (result.getFinishSeconds() > 0)
		{
			add(Ui.wrapped(String.format("+%.1fs per kill to finish it", result.getFinishSeconds()), Ui.WARN, TEXT_WIDTH));
		}
		if (!result.getDps().getEffects().isEmpty())
		{
			add(Ui.gap(6));
			add(Ui.small("Bonuses", Ui.MUTED));
			for (String effect : result.getDps().getEffects())
			{
				add(Ui.markedWrapped(Ui.Glyph.DOT, Ui.GOOD, effect, Color.WHITE, TEXT_WIDTH - 18));
			}
		}
	}

	/**
	 * Marks the slot whose options are listed.
	 */
	void highlight(GearSlot slot)
	{
		if (highlighted != null && cells.containsKey(highlighted))
		{
			cells.get(highlighted).setBackground(Ui.BACKGROUND);
		}
		highlighted = slot;
		if (cells.containsKey(slot))
		{
			cells.get(slot).setBackground(SELECTED);
		}
	}

	private JPanel titleRow()
	{
		JPanel titleRow = new JPanel(new BorderLayout(10, 0));
		titleRow.setOpaque(false);
		titleRow.setAlignmentX(LEFT_ALIGNMENT);
		titleRow.add(Ui.bold("Equipment", ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.WEST);
		JToggleButton maxToggle = new JToggleButton(model.isMaxLoading() ? "Max DPS..." : "Max DPS", actions.isMaxMode());
		maxToggle.setFocusable(false);
		maxToggle.setFont(FontManager.getRunescapeSmallFont());
		maxToggle.setForeground(actions.isMaxMode() ? Ui.MAX_GOLD : ColorScheme.LIGHT_GRAY_COLOR);
		maxToggle.setToolTipText(actions.isMaxMode() ? "Back to the best gear you own"
			: "Show the best gear in the game (within your levels) and what it costs to buy");
		maxToggle.addActionListener(e -> actions.setMaxMode(maxToggle.isSelected()));
		titleRow.add(maxToggle, BorderLayout.EAST);
		return titleRow;
	}

	/**
	 * @return the best item to buy for the slot if it adds at least 1% DPS, else null
	 */
	private UpgradeFinder.Upgrade worthwhileUpgrade(GearSlot slot)
	{
		List<UpgradeFinder.Upgrade> list = model.getUpgrades().forSlot(result.getStyle(), slot);
		if (list.isEmpty() || model.isEdited() || model.isMaxView())
		{
			return null;
		}
		UpgradeFinder.Upgrade best = list.get(0);
		return best.getDpsGain() >= result.effectiveDps() * MARKER_MIN_GAIN ? best : null;
	}

	private boolean changedFromRecommended(GearSlot slot)
	{
		if (!model.isEdited())
		{
			return false;
		}
		EquipmentItem a = result.getLoadout().stats(slot);
		EquipmentItem b = model.getRecommended().getLoadout().stats(slot);
		return (a == null) != (b == null) || (a != null && a.getId() != b.getId());
	}

	private String requirementText(GearSlot slot)
	{
		return NoticeStrip.requirementText(actions.rules(), result, slot);
	}

	private JPanel legend()
	{
		JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		legend.setOpaque(false);
		legend.setAlignmentX(LEFT_ALIGNMENT);
		for (GearSlot slot : GearSlot.values())
		{
			if (requirementText(slot) != null)
			{
				JLabel lock = Ui.small("required", Ui.MUTED);
				lock.setIcon(Ui.icon(Ui.Glyph.LOCK, Ui.PROTECTION));
				lock.setToolTipText("Protection this monster requires; it always stays in the setup");
				legend.add(lock);
				break;
			}
		}
		if (model.isMaxView())
		{
			legend.add(Ui.marked(Ui.Glyph.DOT, Ui.MAX_GOLD, "gold border: not owned", Ui.MUTED));
		}
		else if (model.isEdited())
		{
			legend.add(Ui.marked(Ui.Glyph.DOT, Ui.INFO, "changed", Ui.MUTED));
		}
		else
		{
			legend.add(Ui.marked(Ui.Glyph.DOT, ColorScheme.BRAND_ORANGE, "upgrade worth 1%+ DPS", Ui.MUTED));
		}
		for (Component component : legend.getComponents())
		{
			component.setFont(FontManager.getRunescapeSmallFont());
		}
		legend.setToolTipText("Click a slot to compare every option for it");
		return legend;
	}

	/**
	 * @return which of the monster's defence bonuses this setup attacks, e.g. "vs crush defence -20"
	 */
	private static String targetDefenceLine(Loadout loadout, Monster monster)
	{
		if (loadout.getStance() == null)
		{
			return " ";
		}
		Monster.Defensive def = monster.getDefensive();
		switch (loadout.getStance().getType())
		{
			case STAB:
				return "Hits its stab defence (" + def.getStab() + ")";
			case SLASH:
				return "Hits its slash defence (" + def.getSlash() + ")";
			case CRUSH:
				return "Hits its crush defence (" + def.getCrush() + ")";
			case MAGIC:
				return "Hits its magic defence (" + def.getMagic() + ")";
			default:
				String category = loadout.weapon() == null ? "" : loadout.weapon().getCategory();
				if ("Thrown".equals(category))
				{
					return "Hits its light ranged defence (" + def.getLight() + ")";
				}
				if ("Crossbow".equals(category) || "Chinchompas".equals(category))
				{
					return "Hits its heavy ranged defence (" + def.getHeavy() + ")";
				}
				return "Hits its standard ranged defence (" + def.getStandard() + ")";
		}
	}

	private static String attackLine(Loadout loadout)
	{
		StringBuilder sb = new StringBuilder();
		if (loadout.getStance() != null)
		{
			sb.append(loadout.getStance().getName());
			if (loadout.getStance().getStyle() == CombatStyle.MELEE)
			{
				sb.append(" (").append(loadout.getStance().getType().name().toLowerCase()).append(')');
			}
		}
		if (loadout.getSpell() != null)
		{
			sb.append(" - ").append(loadout.getSpell().getDisplayName());
		}
		return sb.toString();
	}

	private JLabel slotCell(GearSlot slot, OwnedItem item, UpgradeFinder.Upgrade upgrade)
	{
		JLabel cell = new SlotLabel(upgrade != null, changedFromRecommended(slot), requirementText(slot));
		cell.setPreferredSize(new Dimension(44, 40));
		cell.setHorizontalAlignment(SwingConstants.CENTER);
		cell.setOpaque(true);
		cell.setBackground(Ui.BACKGROUND);
		if (model.isMaxView())
		{
			// Max DPS view is read-only: it shows what to aim for, not a setup to edit
			if (item == null)
			{
				cell.setToolTipText(slot.getDisplayName() + ": empty");
				cell.setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR.brighter()));
				return cell;
			}
			boolean buy = model.mustBuy(item);
			cell.setBorder(BorderFactory.createLineBorder(buy ? Ui.MAX_GOLD : ColorScheme.MEDIUM_GRAY_COLOR, buy ? 2 : 1));
			icons.addTo(cell, item.getStats().getId());
			long price = model.priceOf(item);
			cell.setToolTipText("<html><b>" + Ui.escape(item.getStats().getName()) + "</b><br>"
				+ (buy ? (price > 0 ? "Buy for " + Ui.exactGp(price) : "Not owned (untradeable)") : "You own this")
				+ "</html>");
			return cell;
		}
		cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		cell.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					onSlotClicked.accept(slot);
				}
			}
		});

		JPopupMenu menu = new JPopupMenu();
		JMenuItem tryOther = new JMenuItem("Try another item...");
		tryOther.addActionListener(e -> actions.chooseItem(result.getStyle(), slot));
		menu.add(tryOther);
		cell.setComponentPopupMenu(menu);

		if (item == null)
		{
			cell.setToolTipText(slot.getDisplayName() + ": empty. Click to see options, right-click to try an item.");
			cell.setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR.brighter()));
			return cell;
		}

		boolean isLocked = locked.contains(item.getItemId()) || locked.contains(item.getStats().getId());
		cell.setBorder(BorderFactory.createLineBorder(isLocked ? ColorScheme.BRAND_ORANGE
			: item.isNeedsCharges() ? Ui.WARN : ColorScheme.MEDIUM_GRAY_COLOR));
		icons.addTo(cell, item.getItemId());
		cell.setToolTipText(tooltip(slot, item, isLocked, upgrade));

		menu.addSeparator();
		JMenuItem lock = new JMenuItem(isLocked ? "Stop forcing this item" : "Always use this item");
		lock.addActionListener(e -> actions.toggleLocked(item.getItemId()));
		JMenuItem exclude = new JMenuItem("Exclude (I can't use this)");
		exclude.addActionListener(e -> actions.toggleExcluded(item.getItemId()));
		menu.add(lock);
		menu.add(exclude);
		return cell;
	}

	/**
	 * Equipment cell that marks slots with an available upgrade with an orange asterisk.
	 */
	private static final class SlotLabel extends JLabel
	{
		private static final Icon LOCK = Ui.icon(Ui.Glyph.LOCK, Ui.PROTECTION);
		private final boolean upgradable;
		private final boolean changed;
		/** Why this slot is forced (protection against the monster), or null. */
		private final String requirement;

		SlotLabel(boolean upgradable, boolean changed, String requirement)
		{
			this.upgradable = upgradable;
			this.changed = changed;
			this.requirement = requirement;
		}

		@Override
		public String getToolTipText()
		{
			String base = super.getToolTipText();
			if (requirement == null)
			{
				return base;
			}
			String note = Ui.colored("Required: " + Ui.escape(requirement), Ui.PROTECTION);
			if (base == null)
			{
				return "<html>" + note + "</html>";
			}
			return base.startsWith("<html>") ? base.replace("</html>", "<br>" + note + "</html>") : "<html>" + Ui.escape(base) + "<br>" + note + "</html>";
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (upgradable)
			{
				g.setColor(ColorScheme.BRAND_ORANGE);
				g.fillOval(getWidth() - 10, 3, 7, 7);
			}
			if (changed)
			{
				g.setColor(Ui.INFO);
				g.fillOval(3, 3, 7, 7);
			}
			g.dispose();
			if (requirement != null)
			{
				LOCK.paintIcon(this, graphics, 3, getHeight() - 15);
			}
		}
	}

	private static String tooltip(GearSlot slot, OwnedItem item, boolean locked, UpgradeFinder.Upgrade upgrade)
	{
		EquipmentItem stats = item.getStats();
		StringBuilder sb = new StringBuilder("<html><b>").append(Ui.escape(stats.getName())).append("</b>");
		if (locked)
		{
			sb.append(" (forced)");
		}
		sb.append("<br>").append(slot.getDisplayName());
		if (stats.isTwoHanded())
		{
			sb.append(", two-handed");
		}
		sb.append("<br>").append(Ui.itemStats(stats, false));
		if (item.isNeedsCharges())
		{
			sb.append("<br>").append(Ui.colored("Needs charging", Ui.WARN));
		}
		if (upgrade != null)
		{
			sb.append("<br>").append(Ui.colored("Upgrade: " + Ui.escape(upgrade.getItem().getName())
				+ String.format(" (%+.2f DPS). Click to see all options.", upgrade.getDpsGain()), ColorScheme.BRAND_ORANGE));
		}
		sb.append("<br>Right-click to try another item, exclude or force</html>");
		return sb.toString();
	}
}
