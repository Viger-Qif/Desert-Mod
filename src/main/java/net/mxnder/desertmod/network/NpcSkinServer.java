package net.mxnder.desertmod.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
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

        // Поворот: крутим голову и тело, чтобы статуя не «смотрела» по-старому
        ServerPlayNetworking.registerGlobalReceiver(NpcSkinPayloads.SetRotation.TYPE, (payload, context) -> {
            float yaw = payload.yaw();
            if (!Float.isFinite(yaw)) return;
            var server = context.player().level().getServer();
            if (server == null) return;
            server.execute(() -> withNpc(server, payload.npcId(), npc -> {
                npc.setYRot(yaw);
                npc.setYHeadRot(yaw);
                npc.setYBodyRot(yaw);
            }));
        });

        // Позиция: границы мира и конечность проверяем заранее
        ServerPlayNetworking.registerGlobalReceiver(NpcSkinPayloads.SetPosition.TYPE, (payload, context) -> {
            double x = payload.x(), y = payload.y(), z = payload.z();
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
            x = Math.max(-29_999_000, Math.min(29_999_000, x));
            z = Math.max(-29_999_000, Math.min(29_999_000, z));
            y = Math.max(-64, Math.min(320, y));
            var server = context.player().level().getServer();
            if (server == null) return;
            double fx = x, fy = y, fz = z;
            server.execute(() -> withNpc(server, payload.npcId(), npc ->
                    npc.setPos(fx, fy, fz)));
        });
    }

    private static void withNpc(MinecraftServer server, String uuid,
                                java.util.function.Consumer<SimpleNpcEntity> action) {
        UUID id;
        try {
            id = UUID.fromString(uuid);
        } catch (Exception e) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof SimpleNpcEntity npc) {
                action.accept(npc);
                break;
            }
        }
    }
}