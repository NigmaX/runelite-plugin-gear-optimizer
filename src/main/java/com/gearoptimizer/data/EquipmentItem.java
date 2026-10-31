package com.gearoptimizer.data;

import com.google.gson.annotations.SerializedName;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * One equippable item from the OSRS Wiki DPS calculator dataset.
 */
@Data
public class EquipmentItem
{
	private int id;
	private String name;
	private String version;
	private String slot;
	private int speed;
	private String category;
	private Bonuses bonuses;
	private Attack offensive;
	private Attack defensive;
	@SerializedName("isTwoHanded")
	private boolean twoHanded;

	/** Worked out once: the optimizer asks for these on every evaluation. Not part of the data. */
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private transient String lowerName;
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private transient GearSlot gearSlot;

	@Data
	public static class Bonuses
	{
		private int str;
		@SerializedName("ranged_str")
		private int rangedStr;
		/** Magic damage in tenths of a percent (50 = 5%). */
		@SerializedName("magic_str")
		private int magicStr;
		private int prayer;
	}

	@Data
	public static class Attack
	{
		private int stab;
		private int slash;
		private int crush;
		private int magic;
		private int ranged;

		/**
		 * @return the five bonuses added up
		 */
		public int total()
		{
			return stab + slash + crush + magic + ranged;
		}
	}

	public String getDisplayName()
	{
		return version == null || version.isEmpty() ? name : name + " (" + version + ")";
	}

	public GearSlot getGearSlot()
	{
		if (gearSlot == null)
		{
			gearSlot = GearSlot.fromKey(slot);
		}
		return gearSlot;
	}

	/**
	 * @return the name in lower case, for the many name-based rules
	 */
	public String getLowerName()
	{
		if (lowerName == null)
		{
			lowerName = name == null ? "" : name.toLowerCase(Locale.ROOT);
		}
		return lowerName;
	}

	public String getCategory()
	{
		return category == null ? "" : category;
	}
}
