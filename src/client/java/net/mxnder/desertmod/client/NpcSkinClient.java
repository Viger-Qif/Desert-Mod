package net.mxnder.desertmod.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.mxnder.desertmod.NpcSkins;
import net.mxnder.desertmod.client.gui.NpcEditorScreen;
import net.mxnder.desertmod.network.NpcSkinPayloads;

import java.util.List;
import java.util.UUID;

public final class NpcSkinClient {

    public static void init() {
        // Сервер велел открыть редактор — открываем окно в потоке рендера
        ClientPlayNetworking.registerGlobalReceiver(NpcSkinPayloads.OpenEditor.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                try {
                    List<String> anims = payload.anims().isEmpty()
                            ? NpcSkinLoader.listAnimations()   // сервер не видит assets — клиент сам
                            : payload.anims();
                    context.client().setScreenAndShow(new NpcEditorScreen(
                            UUID.fromString(payload.npcId()),
                            payload.skins(), anims,
                            payload.currentSkin(), payload.currentAnim()));
                } catch (Exception ignored) {
                }
            });
        });

        // Сервер прислал скин (чужой аплоад или раздача при входе) —
        // ставим текстуру в менеджер, иначе в мультиплеере скины «не доезжают»
        ClientPlayNetworking.registerGlobalReceiver(NpcSkinPayloads.Sync.TYPE, (payload, context) -> {
            context.client().execute(() ->
                    NpcSkinLoader.registerFromBytes(payload.name(), payload.data()));
        });

        // Зашли на сервер — выгружаем свои локальные скины наверх
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            for (NpcSkins.StoredSkin skin : NpcSkins.readAll()) {
                ClientPlayNetworking.send(new NpcSkinPayloads.Upload(skin.name(), skin.data()));
            }
        });
    }
}