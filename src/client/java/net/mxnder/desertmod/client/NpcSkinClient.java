package net.mxnder.desertmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.mxnder.desertmod.DesertMod;
import net.mxnder.desertmod.NpcSkins;
import net.mxnder.desertmod.client.gui.NpcEditorScreen;
import net.mxnder.desertmod.client.scene.SceneManagerClient;
import net.mxnder.desertmod.client.scene.ScenePointsClient;
import net.mxnder.desertmod.network.NpcSkinPayloads;
import org.lwjgl.glfw.GLFW;

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

        // список точек пришёл — кладём в клиентское хранилище
        ClientPlayNetworking.registerGlobalReceiver(NpcSkinPayloads.ScenePointsSync.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                var list = payload.points().stream()
                        .map(d -> new ScenePointsClient.Point(
                                d.id(), d.x(), d.y(), d.z(), d.yaw(), d.dim(), d.scene()))
                        .toList();
                ScenePointsClient.set(list);
                DesertMod.LOGGER.info("[client] Список точек сцен получен: {}", list.size());
            });
        });

        // сцена стартовала: точка приезжает в пакете (приёмник один, без дублей)
        ClientPlayNetworking.registerGlobalReceiver(NpcSkinPayloads.SceneStart.TYPE, (payload, context) -> {
            context.client().execute(() -> SceneManagerClient.startScene(
                    payload.anim(), payload.x(), payload.y(), payload.z(), payload.yaw()));
        });

        ClientPlayNetworking.registerGlobalReceiver(NpcSkinPayloads.SceneEnd.TYPE, (payload, context) -> {
            context.client().execute(() -> SceneManagerClient.endScene());
        });
    }

    public static void initKeys() {
        KeyMapping.Category cat =
                new KeyMapping.Category(Identifier.fromNamespaceAndPath("desertmod", "scene"));
        KeyMapping key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.desertmod.scene", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, cat));
        KeyMapping addKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.desertmod.scene_add", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, cat));
        KeyMapping delKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.desertmod.scene_del", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, cat));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;
            // Подсказки здесь больше нет: сервер сам следит за радиусом
            // и шлёт actionbar тихим пакетом — без команд и без спама.
            // G шлётся всегда: решает сервер, есть ли рядом точка.
            while (key.consumeClick()) {
                if (!SceneManagerClient.isActive()) {
                    ClientPlayNetworking.send(new NpcSkinPayloads.SceneTrigger("smith_strike"));
                }
            }
            while (addKey.consumeClick()) {
                if (!SceneManagerClient.isActive()) {
                    ClientPlayNetworking.send(new NpcSkinPayloads.ScenePointAdd("smith_strike"));
                }
            }
            while (delKey.consumeClick()) {
                if (!SceneManagerClient.isActive()) {
                    ClientPlayNetworking.send(new NpcSkinPayloads.ScenePointRemove());
                }
            }
        });
    }
}