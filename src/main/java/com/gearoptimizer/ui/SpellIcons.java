package com.gearoptimizer.ui;

import com.gearoptimizer.calc.Spell;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.gameval.SpriteID;

/**
 * In-game spellbook icons for each combat spell.
 */
final class SpellIcons
{
	private static final Map<Spell, Integer> SPRITES = new EnumMap<>(Spell.class);

	static
	{
		SPRITES.put(Spell.WIND_STRIKE, SpriteID.Magicon.WIND_STRIKE);
		SPRITES.put(Spell.WATER_STRIKE, SpriteID.Magicon.WATER_STRIKE);
		SPRITES.put(Spell.EARTH_STRIKE, SpriteID.Magicon.EARTH_STRIKE);
		SPRITES.put(Spell.FIRE_STRIKE, SpriteID.Magicon.FIRE_STRIKE);
		SPRITES.put(Spell.WIND_BOLT, SpriteID.Magicon.WIND_BOLT);
		SPRITES.put(Spell.WATER_BOLT, SpriteID.Magicon.WATER_BOLT);
		SPRITES.put(Spell.EARTH_BOLT, SpriteID.Magicon.EARTH_BOLT);
		SPRITES.put(Spell.FIRE_BOLT, SpriteID.Magicon.FIRE_BOLT);
		SPRITES.put(Spell.CRUMBLE_UNDEAD, SpriteID.Magicon.CRUMBLE_UNDEAD);
		SPRITES.put(Spell.WIND_BLAST, SpriteID.Magicon.WIND_BLAST);
		SPRITES.put(Spell.WATER_BLAST, SpriteID.Magicon.WATER_BLAST);
		SPRITES.put(Spell.IBAN_BLAST, SpriteID.Magicon.IBAN_BLAST);
		SPRITES.put(Spell.MAGIC_DART, SpriteID.Magicon2.MAGIC_DART);
		SPRITES.put(Spell.EARTH_BLAST, SpriteID.Magicon.EARTH_BLAST);
		SPRITES.put(Spell.FIRE_BLAST, SpriteID.Magicon.FIRE_BLAST);
		SPRITES.put(Spell.WIND_WAVE, SpriteID.Magicon.WIND_WAVE);
		SPRITES.put(Spell.WATER_WAVE, SpriteID.Magicon.WATER_WAVE);
		SPRITES.put(Spell.EARTH_WAVE, SpriteID.Magicon.EARTH_WAVE);
		SPRITES.put(Spell.FIRE_WAVE, SpriteID.Magicon.FIRE_WAVE);
		SPRITES.put(Spell.WIND_SURGE, SpriteID.Magicon2.WIND_SURGE);
		SPRITES.put(Spell.WATER_SURGE, SpriteID.Magicon2.WATER_SURGE);
		SPRITES.put(Spell.EARTH_SURGE, SpriteID.Magicon2.EARTH_SURGE);
		SPRITES.put(Spell.FIRE_SURGE, SpriteID.Magicon2.FIRE_SURGE);
		SPRITES.put(Spell.SMOKE_RUSH, SpriteID.Magicon2.SMOKE_RUSH);
		SPRITES.put(Spell.SHADOW_RUSH, SpriteID.Magicon2.SHADOW_RUSH);
		SPRITES.put(Spell.BLOOD_RUSH, SpriteID.Magicon2.BLOOD_RUSH);
		SPRITES.put(Spell.ICE_RUSH, SpriteID.Magicon2.ICE_RUSH);
		SPRITES.put(Spell.SMOKE_BURST, SpriteID.Magicon2.SMOKE_BURST);
		SPRITES.put(Spell.SHADOW_BURST, SpriteID.Magicon2.SHADOW_BURST);
		SPRITES.put(Spell.BLOOD_BURST, SpriteID.Magicon2.BLOOD_BURST);
		SPRITES.put(Spell.ICE_BURST, SpriteID.Magicon2.ICE_BURST);
		SPRITES.put(Spell.SMOKE_BLITZ, SpriteID.Magicon2.SMOKE_BLITZ);
		SPRITES.put(Spell.SHADOW_BLITZ, SpriteID.Magicon2.SHADOW_BLITZ);
		SPRITES.put(Spell.BLOOD_BLITZ, SpriteID.Magicon2.BLOOD_BLITZ);
		SPRITES.put(Spell.ICE_BLITZ, SpriteID.Magicon2.ICE_BLITZ);
		SPRITES.put(Spell.SMOKE_BARRAGE, SpriteID.Magicon2.SMOKE_BARRAGE);
		SPRITES.put(Spell.SHADOW_BARRAGE, SpriteID.Magicon2.SHADOW_BARRAGE);
		SPRITES.put(Spell.BLOOD_BARRAGE, SpriteID.Magicon2.BLOOD_BARRAGE);
		SPRITES.put(Spell.ICE_BARRAGE, SpriteID.Magicon2.ICE_BARRAGE);
		SPRITES.put(Spell.GHOSTLY_GRASP, SpriteID.MagicNecroOn.GHOSTLY_GRASP);
		SPRITES.put(Spell.SKELETAL_GRASP, SpriteID.MagicNecroOn.SKELETAL_GRASP);
		SPRITES.put(Spell.UNDEAD_GRASP, SpriteID.MagicNecroOn.UNDEAD_GRASP);
		SPRITES.put(Spell.INFERIOR_DEMONBANE, SpriteID.MagicNecroOn.INFERIOR_DEMONBANE);
		SPRITES.put(Spell.SUPERIOR_DEMONBANE, SpriteID.MagicNecroOn.SUPERIOR_DEMONBANE);
		SPRITES.put(Spell.DARK_DEMONBANE, SpriteID.MagicNecroOn.DARK_DEMONBANE);
	}

	private SpellIcons()
	{
	}

	/**
	 * @return the spell's sprite id, or -1 if unknown
	 */
	static int sprite(Spell spell)
	{
		return spell == null ? -1 : SPRITES.getOrDefault(spell, -1);
	}

	static String spellbookName(Spell spell)
	{
		switch (spell.getSpellbook())
		{
			case ANCIENT:
				return "Ancient Magicks";
			case ARCEUUS:
				return "Arceuus spellbook";
			default:
				return "Standard spellbook";
		}
	}
}
