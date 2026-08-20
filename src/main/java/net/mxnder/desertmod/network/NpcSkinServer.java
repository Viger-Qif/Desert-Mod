package net.mxnder.desertmod.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.mxnder.desertmod.NpcSkins;
import net.mxnder.desertmod.entity.SimpleNpcEntity;

import java.util.List;
import java.util.UUID;

public final class NpcSkinServer {

    public static void init() {
        // Клиент выбрал скин в окне: проверяем имя и применяем
        ServerPlayNetworking.registerGlobalReceiver(NpcSkinPayloads.SetSkin.TYPE, (payload, context) -> {
            String name = NpcSkins.sanitizeName(payload.skin());
            if (name == null) return;
            var server = context.player().level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!NpcSkins.listAll(server.getResourceManager()).contains(name)) return;
                UUID npcId;
                try {
                    npcId = UUID.fromString(payload.npcId());
                } catch (Exception e) {
                    return;
                }
                for (ServerLevel level : server.getAllLevels()) {
                    if (level.getEntity(npcId) instanceof SimpleNpcEntity npc) {
                        npc.setSkinName(name);
                        break;
                    }
                }
            });
        });

        // Клиент выбрал анимацию в редакторе: проверяем по json модели и применяем
        ServerPlayNetworking.registerGlobalReceiver(NpcSkinPayloads.SetAnim.TYPE, (payload, context) -> {
            String name = payload.anim().trim();
            if (name.isEmpty()) return;
            var server = context.player().level().getServer();
            if (server == null) return;
            server.execute(() -> {
                List<String> allowed = SimpleNpcEntity.listAnimations(server);
                // Сервер может не видеть assets — тогда доверяем клиенту
                // и проверяем только формат имени (ключи анимаций: буквы/цифры/._-)
                if (!allowed.isEmpty() && !allowed.contains(name)) return;
                if (name.length() > 64 || !name.matches("[A-Za-z0-9_.\\-]+")) return;
                UUID npcId;
                try {
                    npcId = UUID.fromString(payload.npcId());
                } catch (Exception e) {
                    return;
                }
                for (ServerLevel level : server.getAllLevels()) {
                    if (level.getEntity(npcId) instanceof SimpleNpcEntity npc) {
                        npc.setAnimName(name);
                        break;
                    }
                }
            });
        });

        // Клиент выгрузил свой локальный скин: сохраняем в папку сервера
        // и раздаём всем, кто онлайн. Без этого приёмника аплоад уходил в пустоту.
        ServerPlayNetworking.registerGlobalReceiver(NpcSkinPayloads.Upload.TYPE, (payload, context) -> {
            String name = NpcSkins.sanitizeName(payload.name());
            byte[] data = payload.data();
            if (name == null || data == null || data.length == 0) return;
            var server = context.player().level().getServer();
            if (server == null) return;
            server.execute(() -> {
                NpcSkins.save(name, data); // переживёт рестарт, попадёт в списки
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(p, new NpcSkinPayloads.Sync(name, data));
                }
            });
        });

        // Игрок зашёл на сервер — отдаём ему все накопленные скины
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            for (NpcSkins.StoredSkin skin : NpcSkins.readAll()) {
                ServerPlayNetworking.send(handler.getPlayer(),
                        new NpcSkinPayloads.Sync(skin.name(), skin.data()));
            }
        });
    }
}