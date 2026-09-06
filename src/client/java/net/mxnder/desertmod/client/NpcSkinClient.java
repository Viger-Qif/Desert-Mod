package net.mxnder.desertmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.mxnder.desertmod.NpcSkins;
import net.mxnder.desertmod.client.gui.NpcEditorScreen;
import net.mxnder.desertmod.client.scene.SceneManagerClient;
import net.mxnder.desertmod.network.NpcSkinPayloads;
import net.mxnder.desertmod.scene.SceneLayout;
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

        // Сцена стартовала: передаём режиссёру ИМЯ сцены —
        // по нему он прочитает ключи камеры из assets/desertmod/scenes/<name>.json
        ClientPlayNetworking.registerGlobalReceiver(NpcSkinPayloads.SceneStart.TYPE, (payload, context) -> {
            context.client().execute(() -> SceneManagerClient.startScene(payload.anim()));
        });

        ClientPlayNetworking.registerGlobalReceiver(NpcSkinPayloads.SceneEnd.TYPE, (payload, context) -> {
            context.client().execute(() -> SceneManagerClient.endScene());
        });
    }

    public static void initKeys() {
        KeyMapping key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.desertmod.scene",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                new KeyMapping.Category(Identifier.fromNamespaceAndPath("desertmod", "scene"))));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean near = mc.player != null
                    && mc.level != null
                    && mc.level.dimension() == SceneLayout.SCENE_DIM
                    && !SceneManagerClient.isActive()
                    && SceneLayout.isNear(mc.player.getX(), mc.player.getY(), mc.player.getZ());

            // Подсказка над хотбаром: actionbar-заголовок — надёжный во всех версиях способ.
            // Обновляем раз в 4 секунды, пока стоишь рядом (и сразу при подходе),
            // чтобы надпись не гасла. В мультиплеере без прав она просто не покажется —
            // кнопка и так гейтится сервером, подсказка чисто косметическая.
            if (near && System.currentTimeMillis() - lastHintMs > 4000) {
                lastHintMs = System.currentTimeMillis();
                mc.player.connection.sendCommand(
                        "title @s actionbar {\"text\":\"Нажми G — начать сцену кузнеца\"}");
            }

            while (key.consumeClick()) {
                if (near) {
                    ClientPlayNetworking.send(new NpcSkinPayloads.SceneTrigger("smith_strike"));
                }
                // вне радиуса нажатие съедается — кнопка «не существует»
            }
        });
    }

    // когда в последний раз освежали подсказку
    private static long lastHintMs = 0;
}