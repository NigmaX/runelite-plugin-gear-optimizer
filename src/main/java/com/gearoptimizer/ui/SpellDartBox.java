package com.gearoptimizer.ui;

import com.gearoptimizer.calc.CombatStyle;
import com.gearoptimizer.calc.Consumables;
import com.gearoptimizer.calc.Experiment;
import com.gearoptimizer.calc.Loadout;
import com.gearoptimizer.calc.Spell;
import com.gearoptimizer.calc.StyleResult;
import com.gearoptimizer.data.OwnedItem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;

/**
 * The boxes under the equipment grid that say what the weapon fires: the spell to autocast with its runes, or the darts
 * loaded in a blowpipe.
 */
final class SpellDartBox
{
	private static final int[] DART_COLUMNS = {150, 54, 54, 70};

	private final TabModel model;
	private final StyleResult result;
	private final StyleTab.Actions actions;
	private final ItemIcons icons;
	/** Every dart the blowpipe takes, scored on first use; null until then. */
	private List<Experiment.SlotOption> dartOptions;

	SpellDartBox(TabModel model, StyleTab.Actions actions, ItemIcons icons)
	{
		this.model = model;
		this.result = model.getResult();
		this.actions = actions;
		this.icons = icons;
	}

	/**
	 * Shows which spell to autocast and from which spellbook, with the in-game spell icon.
	 *
	 * @return the box, or null for setups other than magic
	 */
	JPanel spellBox()
	{
		if (result.getStyle() != CombatStyle.MAGIC)
		{
			return null;
		}
		Loadout loadout = result.getLoadout();
		JPanel box = box();
		JLabel icon = icon();
		JPanel text = new JPanel(new GridLayout(0, 1));
		text.setOpaque(false);
		Spell spell = loadout.getSpell();
		String weapon = loadout.weapon() == null ? "your weapon" : loadout.weapon().getName();
		if (spell == null)
		{
			text.add(Ui.bold("Built-in spell", Color.WHITE));
			text.add(Ui.small(weapon + " casts its own spell", Ui.MUTED));
			text.add(Ui.small("No runes or spellbook needed", Ui.MUTED));
		}
		else
		{
			int sprite = SpellIcons.sprite(spell);
			if (sprite >= 0)
			{
				actions.spriteManager().addSpriteTo(icon, sprite, 0);
			}
			text.add(Ui.bold(spell.getDisplayName(), Color.WHITE));
			text.add(Ui.small(SpellIcons.spellbookName(spell), spell.getSpellbook() == Spell.Spellbook.ANCIENT
				? new Color(170, 140, 230) : spell.getSpellbook() == Spell.Spellbook.ARCEUUS
				? new Color(120, 200, 170) : Ui.INFO));
			text.add(Ui.small("Autocast with " + weapon, Ui.MUTED));
			List<String> free = Consumables.runesFromGear(loadout);
			if (!free.isEmpty())
			{
				text.add(Ui.small(String.join(" and ", free) + " runes come from your gear", Ui.GOOD));
			}
		}
		box.add(icon, BorderLayout.WEST);
		box.add(text, BorderLayout.CENTER);
		JPanel runes = runeRow(loadout);
		if (runes != null)
		{
			box.add(runes, BorderLayout.SOUTH);
		}
		return box;
	}

	/**
	 * The runes one cast uses, drawn like inventory stacks with their counts.
	 */
	private JPanel runeRow(Loadout loadout)
	{
		Map<String, Integer> runes = Consumables.runesToCarry(loadout);
		if (runes.isEmpty())
		{
			return null;
		}
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		JLabel label = Ui.small("Runes per cast", Ui.MUTED);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
		row.add(label);
		for (Map.Entry<String, Integer> rune : runes.entrySet())
		{
			JLabel stack = ItemIcons.blank(32);
			int id = Consumables.runeItemId(rune.getKey());
			if (id >= 0)
			{
				icons.addTo(stack, id, rune.getValue(), true);
			}
			else
			{
				stack.setText(rune.getValue() + " " + rune.getKey());
			}
			stack.setToolTipText(Ui.plural(rune.getValue(), rune.getKey() + " rune") + " per cast");
			row.add(stack);
		}
		return row;
	}

	/**
	 * The darts loaded in a blowpipe, styled like the spell box. Clicking it lists every dart type the blowpipe takes.
	 *
	 * @return the box, or null if the weapon isn't a blowpipe
	 */
	JPanel dartBox()
	{
		Loadout loadout = result.getLoadout();
		if (!loadout.isBlowpipe())
		{
			return null;
		}
		OwnedItem dart = loadout.getBlowpipeDart();
		JPanel box = box();
		JLabel icon = icon();
		if (dart != null)
		{
			icons.addTo(icon, dart.getItemId());
		}
		JPanel text = new JPanel(new GridLayout(0, 1));
		text.setOpaque(false);
		text.add(Ui.bold(dart == null ? "No darts" : dart.getStats().getName(), Color.WHITE));
		text.add(Ui.small("Loaded in the blowpipe", Ui.MUTED));
		if (!model.isMaxView())
		{
			text.add(Ui.small("Click to change darts", Ui.INFO));
			box.setToolTipText("Pick another dart type and see its DPS and price");
			Ui.clickable(box, Ui.BACKGROUND, ColorScheme.DARK_GRAY_HOVER_COLOR, () -> showDartMenu(box));
			box.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					// Score the darts and look up their prices on the way in, so the list opens with prices
					dartOptions(box);
				}
			});
		}
		box.add(icon, BorderLayout.WEST);
		box.add(text, BorderLayout.CENTER);
		return box;
	}

	private static JPanel box()
	{
		JPanel box = new JPanel(new BorderLayout(8, 0));
		box.setBackground(Ui.BACKGROUND);
		box.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		box.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		return box;
	}

	private static JLabel icon()
	{
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(28, 28));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		return icon;
	}

	/**
	 * @return every dart type the blowpipe takes, scored once; the first call also looks up their prices
	 */
	private List<Experiment.SlotOption> dartOptions(JComponent owner)
	{
		if (dartOptions == null)
		{
			dartOptions = Experiment.dartOptions(result, actions.ownedItems(), actions.allItems());
			List<Integer> ids = new ArrayList<>();
			for (Experiment.SlotOption option : dartOptions)
			{
				ids.add(option.getItem().getItemId());
			}
			actions.requestPrices(ids, owner, () -> { });
		}
		return dartOptions;
	}

	private void showDartMenu(JPanel anchor)
	{
		JPopupMenu menu = new JPopupMenu();
		menu.setBackground(Ui.CARD);
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		header.add(dartRow(Ui.small("Darts", Ui.MUTED), Ui.small("DPS", Ui.MUTED), Ui.small("Change", Ui.MUTED), Ui.small("Each", Ui.MUTED)));
		menu.add(header);
		for (Experiment.SlotOption option : dartOptions(anchor))
		{
			OwnedItem dart = option.getItem();
			JLabel name = Ui.regular(dart.getStats().getName(), option.isOwned() ? Color.WHITE : Ui.MUTED);
			icons.addTo(name, dart.getItemId());
			name.setIconTextGap(6);
			if (option.isInUse())
			{
				name.setForeground(ColorScheme.BRAND_ORANGE);
			}
			JLabel change = Ui.regular(option.isInUse() ? "-" : String.format("%+.2f", option.getChange()),
				option.isInUse() ? Ui.MUTED : option.getChange() >= 0 ? Ui.GOOD : Ui.BAD);
			JLabel each = Ui.regular(Ui.priceText(actions.price(dart.getItemId()), "-"), Ui.MUTED);
			JPanel row = new JPanel(new BorderLayout());
			row.setBackground(Ui.CARD);
			row.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
			row.add(dartRow(name, Ui.regular(String.format("%.2f", option.getDps()), Color.WHITE), change, each));
			row.setToolTipText(option.isInUse() ? "Loaded now" : (option.isOwned() ? "You own these. " : "You don't own these. ") + "Click to load them.");
			Ui.clickable(row, Ui.CARD, ColorScheme.DARK_GRAY_HOVER_COLOR, () ->
			{
				menu.setVisible(false);
				if (!option.isInUse())
				{
					actions.tryDart(result.getStyle(), dart);
				}
			});
			menu.add(row);
		}
		menu.show(anchor, 0, anchor.getHeight());
	}

	private static JPanel dartRow(JLabel name, JLabel dps, JLabel change, JLabel price)
	{
		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);
		JLabel[] cells = {name, dps, change, price};
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.WEST;
		for (int i = 0; i < cells.length; i++)
		{
			cells[i].setPreferredSize(new Dimension(DART_COLUMNS[i], 26));
			c.gridx = i;
			row.add(cells[i], c);
		}
		return row;
	}
}
