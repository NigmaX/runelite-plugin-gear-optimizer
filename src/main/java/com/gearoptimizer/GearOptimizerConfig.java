package com.gearoptimizer;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(GearOptimizerConfig.GROUP)
public interface GearOptimizerConfig extends Config
{
	String GROUP = "gear-optimizer";

	@ConfigSection(
		name = "Data",
		description = "Monster and equipment data",
		position = 10
	)
	String dataSection = "data";

	@ConfigItem(
		keyName = "highlightInBank",
		name = "Highlight in bank",
		description = "Outline the selected loadout and the items to bring when the bank is open",
		position = 1
	)
	default boolean highlightInBank()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "highlightColor",
		name = "Highlight colour",
		description = "Outline colour for highlighted bank items",
		position = 2
	)
	default Color highlightColor()
	{
		return new Color(0, 255, 120, 220);
	}

	@ConfigItem(
		keyName = "autoSelectTarget",
		name = "Auto-select target",
		description = "Switch the optimizer to the monster you attack",
		position = 3
	)
	default boolean autoSelectTarget()
	{
		return false;
	}

	@ConfigItem(
		keyName = "npcMenuOption",
		name = "Right-click 'Gear setup'",
		description = "Add a 'Gear setup' option to monsters' right-click menu that opens the optimizer for them",
		position = 4
	)
	default boolean npcMenuOption()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showUpgrades",
		name = "Show upgrade suggestions",
		description = "List the items you don't own that would raise your DPS the most, with GE prices",
		position = 5
	)
	default boolean showUpgrades()
	{
		return true;
	}

	@ConfigItem(
		keyName = "allowDataDownload",
		name = "Allow data refresh",
		description = "Enables the 'Refresh data' button, which downloads the OSRS Wiki DPS calculator dataset from GitHub (2 requests per refresh)",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = dataSection,
		position = 11
	)
	default boolean allowDataDownload()
	{
		return false;
	}

	// Panel state, edited from the side panel rather than the config screen

	@ConfigItem(keyName = "styleFilter", name = "", description = "", hidden = true)
	default String styleFilter()
	{
		return "BEST";
	}

	@ConfigItem(keyName = "slayerMode", name = "", description = "", hidden = true)
	default String slayerMode()
	{
		return "AUTO";
	}

	@ConfigItem(keyName = "prayerMode", name = "", description = "", hidden = true)
	default String prayerMode()
	{
		return "BEST";
	}

	/** Potions are off unless the player turns them on. Replaces the unreleased "potionMode" key. */
	@ConfigItem(keyName = "potionChoice", name = "", description = "", hidden = true)
	default String potionChoice()
	{
		return "NONE";
	}

	/** "AUTO" or a number of monsters hit by burst/barrage and chinchompas. */
	@ConfigItem(keyName = "aoeTargets", name = "", description = "", hidden = true)
	default String aoeTargets()
	{
		return "AUTO";
	}

	@ConfigItem(keyName = "budgetMode", name = "", description = "", hidden = true)
	default boolean budgetMode()
	{
		return false;
	}

	@ConfigItem(keyName = "inWilderness", name = "", description = "", hidden = true)
	default boolean inWilderness()
	{
		return false;
	}

	/** Fire a dwarf multicannon alongside the player; off by default since many places don't allow one. */
	@ConfigItem(keyName = "cannon", name = "", description = "", hidden = true)
	default boolean cannon()
	{
		return false;
	}

	@ConfigItem(keyName = "defenceWeight", name = "", description = "", hidden = true)
	default int defenceWeight()
	{
		return 0;
	}

	@ConfigItem(keyName = "excludedItems", name = "", description = "", hidden = true)
	default String excludedItems()
	{
		return "";
	}

	@ConfigItem(keyName = "lockedItems", name = "", description = "", hidden = true)
	default String lockedItems()
	{
		return "";
	}

	@ConfigItem(keyName = "lastMonsterId", name = "", description = "", hidden = true)
	default int lastMonsterId()
	{
		return -1;
	}
}
