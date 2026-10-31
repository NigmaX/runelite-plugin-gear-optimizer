package com.gearoptimizer.calc;

import com.gearoptimizer.data.Monster;
import java.util.Map;

/**
 * Matches the Slayer plugin's task name ("Gargoyles", "Lizardmen") against a monster name.
 */
public final class SlayerTaskMatcher
{
	private static final Map<String, String> ALIASES = Map.ofEntries(
		Map.entry("tzhaar", "tz"),
		Map.entry("spiritual creatures", "spiritual"),
		Map.entry("fossil island wyverns", "wyvern"),
		Map.entry("elves", "elf"),
		Map.entry("wolves", "wolf"),
		Map.entry("dwarves", "dwarf"),
		Map.entry("mutated zygomites", "zygomite"),
		Map.entry("mutated bloodveld", "bloodveld"),
		Map.entry("kalphite", "kalphite"),
		Map.entry("dagannoth", "dagannoth"),
		Map.entry("bloodveld", "bloodveld"),
		Map.entry("aviansies", "aviansie"),
		Map.entry("lizardmen", "lizardman"),
		Map.entry("ankou", "ankou"),
		Map.entry("jad", "jad"),
		Map.entry("zuk", "zuk")
	);

	private SlayerTaskMatcher()
	{
	}

	public static boolean matches(String taskName, Monster monster)
	{
		if (taskName == null || taskName.isEmpty() || monster == null)
		{
			return false;
		}
		String task = taskName.toLowerCase().trim();
		String name = monster.getName().toLowerCase();
		String target = ALIASES.getOrDefault(task, singular(task));
		return name.contains(target) || name.contains(task);
	}

	static String singular(String task)
	{
		if (task.endsWith("men"))
		{
			return task.substring(0, task.length() - 3) + "man";
		}
		if (task.endsWith("ies"))
		{
			return task.substring(0, task.length() - 3) + "y";
		}
		if (task.endsWith("ses") || task.endsWith("xes") || task.endsWith("ches"))
		{
			return task.substring(0, task.length() - 2);
		}
		if (task.endsWith("s") && !task.endsWith("ss"))
		{
			return task.substring(0, task.length() - 1);
		}
		return task;
	}
}
