package net.mxnder.desertmod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.mxnder.desertmod.client.scene.SceneManagerClient;
import net.mxnder.desertmod.entity.ModEntities;
import net.mxnder.desertmod.client.entity.SimpleNpcRenderer;


public class DesertModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {

		NpcSkinClient.init();
		NpcSkinClient.initKeys();
		NpcSkinLoader.init();
		SceneManagerClient.init();
		EntityRendererRegistry.register(ModEntities.SIMPLE_NPC, SimpleNpcRenderer::new);
	}
}