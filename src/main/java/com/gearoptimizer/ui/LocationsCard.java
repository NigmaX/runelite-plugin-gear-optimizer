package com.gearoptimizer.ui;

import com.gearoptimizer.data.MonsterLocation;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lombok.Getter;
import net.runelite.client.ui.ColorScheme;

/**
 * The sidebar card listing where the selected monster lives, with buttons to pin a place on the world map or get
 * a route there from the Shortest Path plugin. The place the player fights at is highlighted; clicking another
 * switches to it, since some rules hold only in some places.
 */
final class LocationsCard
{
	/** Places listed before "Show more". */
	private static final int SHOWN = 5;
	private static final int ROW_BUTTONS_WIDTH = 48;
	/** The colour of the monster pins on the world map. */
	private static final Color PIN = new Color(200, 50, 40);

	@Getter
	private final JPanel panel = Ui.card(null);
	private final int width;
	private final Consumer<String> onPick;
	private final Consumer<List<MonsterLocation>> onMap;
	private final Consumer<List<MonsterLocation>> onGo;
	private final Runnable onClear;

	private List<MonsterLocation> locations = Collections.emptyList();
	/** The name of the place the player fights at, or null. */
	private String selected;
	private boolean expanded;

	/**
	 * @param onPick gets the name of a place the player picks to fight at
	 * @param onMap pins the places on the world map
	 * @param onGo asks for a route to the nearest of the places
	 */
	LocationsCard(int width, Consumer<String> onPick, Consumer<List<MonsterLocation>> onMap, Consumer<List<MonsterLocation>> onGo,
		Runnable onClear)
	{
		this.width = width;
		this.onPick = onPick;
		this.onMap = onMap;
		this.onGo = onGo;
		this.onClear = onClear;
		panel.setVisible(false);
	}

	void showNone()
	{
		panel.setVisible(false);
	}

	/**
	 * @param selected the name of the place the player fights at, or null
	 */
	void show(List<MonsterLocation> locations, String selected)
	{
		boolean sameMonster = locations == this.locations;
		this.locations = locations;
		this.selected = selected;
		// Keep the full list open while switching between its places
		expanded = sameMonster && expanded;
		render();
	}

	private void render()
	{
		panel.removeAll();
		panel.setVisible(true);
		panel.add(header());
		panel.add(Ui.gap(4));

		if (locations.isEmpty())
		{
			panel.add(Ui.wrapped("The wiki lists no location for this monster", Ui.MUTED, width));
			refresh();
			return;
		}

		if (locations.size() > 1)
		{
			panel.add(Ui.wrapped("Click the place you fight at: some rules depend on it", Ui.MUTED, width));
			panel.add(Ui.gap(2));
		}
		int shown = expanded ? locations.size() : Math.min(SHOWN, locations.size());
		for (MonsterLocation location : locations.subList(0, shown))
		{
			panel.add(row(location));
		}
		if (!expanded && locations.subList(shown, locations.size()).stream().anyMatch(l -> l.getName().equals(selected)))
		{
			expanded = true;
			render();
			return;
		}
		if (shown < locations.size())
		{
			JLabel more = Ui.small("Show " + (locations.size() - shown) + " more", Ui.INFO);
			more.setToolTipText("List every place");
			more.setBorder(BorderFactory.createEmptyBorder(2, 18, 2, 4));
			Ui.clickable(more, null, ColorScheme.DARK_GRAY_HOVER_COLOR, () ->
			{
				expanded = true;
				render();
			});
			panel.add(more);
		}
		refresh();
	}

	/**
	 * The title, with the buttons that act on every place on the right.
	 */
	private JPanel header()
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		header.add(Ui.bold("Where to find it", ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.WEST);

		JPanel actions = buttons();
		if (locations.stream().filter(MonsterLocation::hasPoints).count() > 1)
		{
			actions.add(iconButton(Ui.Glyph.MAP, "Pin every place on the world map", () -> onMap.accept(locations)));
			actions.add(iconButton(Ui.Glyph.ROUTE, "Route to the nearest place with Shortest Path", () -> onGo.accept(locations)));
		}
		if (locations.stream().anyMatch(MonsterLocation::hasPoints))
		{
			actions.add(iconButton(Ui.Glyph.CROSS, "Remove the map pins and the route", onClear));
		}
		header.add(actions, BorderLayout.EAST);
		return header;
	}

	private JPanel row(MonsterLocation location)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		String name = location.isUnderground() ? location.getName() + " (underground)" : location.getName();
		boolean chosen = location.getName().equals(selected);
		row.add(Ui.markedWrapped(Ui.Glyph.PIN, PIN, name, chosen ? ColorScheme.BRAND_ORANGE : Color.WHITE, width - ROW_BUTTONS_WIDTH - 20),
			BorderLayout.CENTER);
		if (chosen && locations.size() > 1)
		{
			row.setToolTipText("You fight here");
		}
		else if (!chosen)
		{
			row.setToolTipText("Fight here");
			Ui.clickable(row, null, ColorScheme.DARK_GRAY_HOVER_COLOR, () -> onPick.accept(location.getName()));
		}
		if (location.hasPoints())
		{
			JPanel actions = buttons();
			List<MonsterLocation> one = Collections.singletonList(location);
			actions.add(iconButton(Ui.Glyph.MAP, location.getEntrances().isEmpty()
				? "Pin it on the world map" : "Pin it and its entrance on the world map", () -> onMap.accept(one)));
			actions.add(iconButton(Ui.Glyph.ROUTE, "Route there with Shortest Path", () -> onGo.accept(one)));
			row.add(actions, BorderLayout.EAST);
		}
		return row;
	}

	private static JPanel buttons()
	{
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		buttons.setOpaque(false);
		return buttons;
	}

	private static JLabel iconButton(Ui.Glyph glyph, String tooltip, Runnable action)
	{
		JLabel button = new JLabel(Ui.icon(glyph, Ui.INFO));
		button.setToolTipText(tooltip);
		button.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
		Ui.clickable(button, null, ColorScheme.DARK_GRAY_HOVER_COLOR, action);
		return button;
	}

	private void refresh()
	{
		panel.revalidate();
		panel.repaint();
	}
}
