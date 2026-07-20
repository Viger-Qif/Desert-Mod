package net.mxnder.desertmod;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import net.mxnder.desertmod.block.ModBlocks;
import net.mxnder.desertmod.blockentity.ModBlockEntities;
import net.mxnder.desertmod.command.ModCommands;
import net.mxnder.desertmod.creativemodetab.ModeCreativeModeTabs;
import net.mxnder.desertmod.item.ModItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DesertMod implements ModInitializer {
	public static final String MOD_ID = "desertmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModeCreativeModeTabs.registerModCreativeModeTabs();

		ModBlocks.registerModBlocks();
		ModItems.registerModItems();
		ModBlockEntities.registerModBlockEntities();
		ModCommands.register();
		TeleportFxScheduler.register();
	}
}