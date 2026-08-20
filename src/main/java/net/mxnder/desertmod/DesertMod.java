package net.mxnder.desertmod;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.resources.Identifier;

import net.mxnder.desertmod.block.ModBlocks;
import net.mxnder.desertmod.blockentity.ModBlockEntities;
import net.mxnder.desertmod.command.ModCommands;
import net.mxnder.desertmod.creativemodetab.ModeCreativeModeTabs;
import net.mxnder.desertmod.entity.SimpleNpcEntity;
import net.mxnder.desertmod.item.ModItems;
import net.mxnder.desertmod.network.NpcSkinPayloads;
import net.mxnder.desertmod.network.NpcSkinServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.mxnder.desertmod.entity.ModEntities;

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
		ModEntities.registerModEntities();
		ModEntities.registerAttributes();
		NpcSkinPayloads.register();
		NpcSkinServer.init();

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof SimpleNpcEntity));

		SimpleNpcEntity.registerEvents();

		// Регистрация команд NPC через Fabric API
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			NpcCommands.register(dispatcher);
		});
	}
}