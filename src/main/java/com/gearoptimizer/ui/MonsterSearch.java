package com.gearoptimizer.ui;

import com.gearoptimizer.data.GameDataRepository;
import com.gearoptimizer.data.Monster;
import com.gearoptimizer.data.MonsterLocation;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import lombok.Getter;
import lombok.Value;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.IconTextField;

/**
 * The monster search field and the list of matches under it, one row per place each monster lives: names starting
 * with the text first, then names containing it, then rows whose name and place hold every word typed, so
 * "wyrm wyrmscraig" or "catacombs" finds monsters by where they are.
 */
final class MonsterSearch
{
	private static final int MAX_RESULTS = 14;
	private static final int MAX_POPUP_WIDTH = 520;

	/** One row: a monster, at one of its places or at none. */
	@Value
	private static class Match
	{
		Monster monster;
		MonsterLocation place;
	}

	private final GameDataRepository repository;
	private final BiConsumer<Monster, String> onPick;
	@Getter
	private final IconTextField field = new IconTextField();
	private final JPopupMenu popup = new JPopupMenu();

	/**
	 * @param onPick gets the monster and the name of the place picked, or null for a monster with no places
	 */
	MonsterSearch(GameDataRepository repository, BiConsumer<Monster, String> onPick)
	{
		this.repository = repository;
		this.onPick = onPick;
		field.setIcon(IconTextField.Icon.SEARCH);
		field.setPreferredSize(new Dimension(300, 30));
		field.setBackground(Ui.BACKGROUND);
		field.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		Ui.onTextChange(field.getDocument(), this::update);
		field.addActionListener(e -> pickFirst());
		popup.setFocusable(false);
	}

	/**
	 * Lists the matches for the text, e.g. after the data has loaded.
	 */
	void update()
	{
		popup.setVisible(false);
		popup.removeAll();
		String query = query();
		if (query.length() < 2 || !repository.isLoaded() || !field.isShowing())
		{
			return;
		}

		List<Match> matches = matches(query);
		if (matches.isEmpty())
		{
			JMenuItem none = new JMenuItem("No monster matches \"" + query + "\"");
			none.setEnabled(false);
			popup.add(none);
		}
		for (Match match : matches)
		{
			JMenuItem item = new JMenuItem(label(match));
			item.addActionListener(e -> choose(match));
			popup.add(item);
		}
		// Measure without the size set for the previous list, or every list gets squeezed into its height
		popup.setPreferredSize(null);
		Dimension size = popup.getPreferredSize();
		popup.setPreferredSize(new Dimension(Math.max(field.getWidth(), Math.min(size.width, MAX_POPUP_WIDTH)), size.height));
		popup.show(field, 0, field.getHeight());
		field.requestFocusInWindow();
	}

	private List<Match> matches(String query)
	{
		String[] words = query.split("\\s+");
		Map<String, Match> matches = new LinkedHashMap<>();
		for (int pass = 0; pass < 3 && matches.size() < MAX_RESULTS; pass++)
		{
			for (Monster monster : repository.getMonsters())
			{
				String name = monster.getDisplayName().toLowerCase();
				for (Match match : places(monster))
				{
					boolean hit;
					if (pass == 0)
					{
						hit = name.startsWith(query);
					}
					else if (pass == 1)
					{
						hit = name.contains(query);
					}
					else
					{
						String text = match.place == null ? name : name + " " + match.place.getName().toLowerCase();
						hit = words.length > 0 && Arrays.stream(words).allMatch(text::contains);
					}
					if (hit)
					{
						matches.putIfAbsent(monster.getDisplayName() + "|" + (match.place == null ? "" : match.place.getName()), match);
						if (matches.size() >= MAX_RESULTS)
						{
							return new ArrayList<>(matches.values());
						}
					}
				}
			}
		}
		return new ArrayList<>(matches.values());
	}

	private List<Match> places(Monster monster)
	{
		List<MonsterLocation> places = repository.getLocations(monster.getId());
		if (places.isEmpty())
		{
			return Collections.singletonList(new Match(monster, null));
		}
		List<Match> rows = new ArrayList<>(places.size());
		for (MonsterLocation place : places)
		{
			rows.add(new Match(monster, place));
		}
		return rows;
	}

	/**
	 * @return the name and level, with the place on a second line
	 */
	private static String label(Match match)
	{
		Monster monster = match.monster;
		String title = Ui.escape(monster.getDisplayName()) + Ui.colored("&nbsp;&nbsp;level " + monster.getLevel(), Ui.MUTED);
		if (match.place == null)
		{
			return "<html>" + title + "</html>";
		}
		String place = match.place.getName() + (match.place.isUnderground() ? " (underground)" : "");
		return "<html>" + title + "<br>" + Ui.colored(Ui.escape(place), Ui.MUTED) + "</html>";
	}

	private String query()
	{
		return field.getText() == null ? "" : field.getText().trim().toLowerCase();
	}

	private void pickFirst()
	{
		List<Match> matches = matches(query());
		if (!matches.isEmpty())
		{
			choose(matches.get(0));
		}
	}

	private void choose(Match match)
	{
		field.setText("");
		popup.setVisible(false);
		onPick.accept(match.monster, match.place == null ? null : match.place.getName());
	}
}
