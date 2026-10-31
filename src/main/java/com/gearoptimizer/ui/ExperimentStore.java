package com.gearoptimizer.ui;

import com.gearoptimizer.account.AccountSnapshot;
import com.gearoptimizer.calc.CombatStyle;
import com.gearoptimizer.calc.Experiment;
import com.gearoptimizer.calc.Loadout;
import com.gearoptimizer.calc.MonsterRules;
import com.gearoptimizer.calc.StyleResult;
import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.Monster;
import java.util.EnumMap;
import java.util.Map;

/**
 * Hand-edited setups per style, kept until the monster changes or the player resets them, plus the slot the player
 * last looked at so rebuilt tabs keep it.
 */
final class ExperimentStore
{
	/** One style's edit. The slot is remembered with or without an edit. */
	private static final class Edit
	{
		Loadout loadout;
		String note;
		GearSlot slot;
		/** The edited setup scored against {@link #scoredFrom}, so rebuilds don't score it again. */
		StyleResult scored;
		StyleResult scoredFrom;
	}

	private final Map<CombatStyle, Edit> edits = new EnumMap<>(CombatStyle.class);
	private Monster monster;

	/**
	 * Forgets every edit when the monster changes.
	 */
	void useMonster(Monster monster)
	{
		if (this.monster != monster)
		{
			edits.clear();
			this.monster = monster;
		}
	}

	boolean isEdited(CombatStyle style)
	{
		Edit edit = edits.get(style);
		return edit != null && edit.loadout != null;
	}

	/**
	 * @return what the player changed, or null without an edit
	 */
	String note(CombatStyle style)
	{
		Edit edit = edits.get(style);
		return edit == null ? null : edit.note;
	}

	/**
	 * @return the slot the player last looked at, or null
	 */
	GearSlot slot(CombatStyle style)
	{
		Edit edit = edits.get(style);
		return edit == null ? null : edit.slot;
	}

	void edit(CombatStyle style, Loadout loadout, String note)
	{
		Edit edit = edits.computeIfAbsent(style, s -> new Edit());
		edit.loadout = loadout;
		edit.note = note;
		edit.scored = null;
		edit.scoredFrom = null;
	}

	/**
	 * Goes back to the recommendation, keeping the slot the player looked at.
	 */
	void reset(CombatStyle style)
	{
		Edit edit = edits.get(style);
		if (edit != null)
		{
			edit.loadout = null;
			edit.note = null;
			edit.scored = null;
			edit.scoredFrom = null;
		}
	}

	void selectSlot(CombatStyle style, GearSlot slot)
	{
		edits.computeIfAbsent(style, s -> new Edit()).slot = slot;
	}

	/**
	 * @return the style's edited setup, scored with the recommendation's assumptions, or the recommendation itself
	 * when it isn't edited. The score is kept until the edit or the recommendation changes.
	 */
	StyleResult shown(StyleResult recommended, MonsterRules rules, AccountSnapshot account)
	{
		Edit edit = edits.get(recommended.getStyle());
		if (edit == null || edit.loadout == null)
		{
			return recommended;
		}
		if (edit.scoredFrom != recommended)
		{
			edit.scored = Experiment.evaluate(recommended, edit.loadout, rules, account);
			edit.scoredFrom = recommended;
		}
		return edit.scored;
	}
}
