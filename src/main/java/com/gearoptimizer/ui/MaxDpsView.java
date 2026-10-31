package com.gearoptimizer.ui;

import com.gearoptimizer.calc.Optimizer;
import java.util.Map;
import lombok.Value;

/**
 * The best setups using any item in the game, with GE prices for everything they wear.
 */
@Value
public class MaxDpsView
{
	Optimizer.Result result;
	Map<Integer, Long> prices;
}
