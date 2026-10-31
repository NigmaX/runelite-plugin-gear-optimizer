package com.gearoptimizer.ui;

import com.gearoptimizer.account.AccountSnapshot;
import com.gearoptimizer.calc.Optimizer;
import com.gearoptimizer.calc.UpgradeFinder;
import com.gearoptimizer.data.Monster;
import lombok.Value;

/**
 * Everything the panel needs to render one optimizer run.
 */
@Value
public class OptimizerView
{
	Monster monster;
	Optimizer.Result result;
	UpgradeFinder.Upgrades upgrades;
	AccountSnapshot account;
	/** The Slayer plugin's current task, or null. */
	String slayerTask;
	boolean onTask;
}
