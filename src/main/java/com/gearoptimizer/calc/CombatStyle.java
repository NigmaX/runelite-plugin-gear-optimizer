package com.gearoptimizer.calc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CombatStyle
{
	MELEE("Melee"),
	RANGED("Ranged"),
	MAGIC("Magic");

	private final String displayName;
}
