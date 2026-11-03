package com.gearoptimizer.data;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;
import lombok.Data;

/**
 * One monster (one wiki "version") from the OSRS Wiki DPS calculator dataset.
 */
@Data
public class Monster
{
	private int id;
	private String name;
	private String version;
	private int level;
	private int speed;
	private List<String> style;
	private int size;
	@SerializedName("max_hit")
	private String maxHit;
	private Skills skills;
	private Offensive offensive;
	private Defensive defensive;
	private List<String> attributes;
	@SerializedName("is_slayer_monster")
	private boolean slayerMonster;
	private Weakness weakness;

	@Data
	public static class Skills
	{
		private int atk;
		private int def;
		private int hp;
		private int magic;
		private int ranged;
		private int str;
	}

	@Data
	public static class Offensive
	{
		private int atk;
		private int magic;
		private int ranged;
		private int str;
	}

	@Data
	public static class Defensive
	{
		@SerializedName("flat_armour")
		private int flatArmour;
		private int stab;
		private int slash;
		private int crush;
		private int magic;
		private int light;
		private int standard;
		private int heavy;
	}

	@Data
	public static class Weakness
	{
		private String element;
		private int severity;
	}

	public String getDisplayName()
	{
		return version == null || version.isEmpty() ? name : name + " (" + version + ")";
	}

	public boolean hasAttribute(String attribute)
	{
		return attributes != null && attributes.contains(attribute);
	}

	public List<String> getStyle()
	{
		return style == null ? Collections.emptyList() : style;
	}

	public int getHitpoints()
	{
		return skills == null ? 1 : Math.max(1, skills.getHp());
	}

	public boolean isDemon()
	{
		return hasAttribute("demon");
	}

	public boolean isDragon()
	{
		return hasAttribute("dragon");
	}

	/**
	 * @param attackStyle as the wiki names it, e.g. "Dragonfire" or "Icy breath"
	 */
	public boolean attacksWith(String attackStyle)
	{
		return style != null && style.stream().anyMatch(attackStyle::equalsIgnoreCase);
	}

	public boolean isUndead()
	{
		return hasAttribute("undead");
	}
}
