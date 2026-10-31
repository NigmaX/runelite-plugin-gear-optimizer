package com.gearoptimizer;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GearOptimizerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GearOptimizerPlugin.class);
		RuneLite.main(args);
	}
}
