package net.mxnder.desertmod.scene;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.mxnder.desertmod.DesertMod;
import net.mxnder.desertmod.entity.ModEntities;
import net.mxnder.desertmod.entity.SimpleNpcEntity;
import net.mxnder.desertmod.network.NpcSkinPayloads;

import java.util.*;

public final class SceneManager {

    private record Scene(UUID player, UUID doubleNpc, long endMs,
                         double px, double py, double pz,
                         float yaw, float pitch) {}

    private static final List<Scene> active = new ArrayList<>();

    private static final Map<UUID, Long> lastHintMs = new HashMap<>();

    public static void init() {
        // точки грузятся на старте сервера, список раздаётся зашедшим
        ServerLifecycleEvents.SERVER_STARTING.register(s -> ScenePoints.load());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ScenePoints.syncTo(handler.getPlayer()));

        // H: создать точку там, где стою
        ServerPlayNetworking.registerGlobalReceiver(NpcSkinPayloads.ScenePointAdd.TYPE, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (active.stream().anyMatch(s -> s.player().equals(player.getUUID()))) return;
                ScenePoints.add(player.getX(), player.getY(), player.getZ(), player.getYRot(),
                        player.level().dimension().identifier().toString(), payload.scene());
                ScenePoints.syncAll(server);
                player.sendSystemMessage(Component.literal("§aТочка сцены создана: " + payload.scene()));
            });
        });

        // I: удалить ближайшую точку
        ServerPlayNetworking.registerGlobalReceiver(NpcSkinPayloads.ScenePointRemove.TYPE, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                boolean ok = ScenePoints.removeNearest(player.getX(), player.getY(), player.getZ(),
                        player.level().dimension().identifier().toString(), 3.0);
                ScenePoints.syncAll(server);
                player.sendSystemMessage(Component.literal(ok ? "§cБлижайшая точка удалена" : "§7Рядом нет точки"));
            });
        });

        // G: триггер — сервер сам проверяет близость и сам объясняет причину
        ServerPlayNetworking.registerGlobalReceiver(NpcSkinPayloads.SceneTrigger.TYPE, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (active.stream().anyMatch(s -> s.player().equals(player.getUUID()))) return;
                ScenePoints.ScenePoint pt = ScenePoints.nearestForPlayer(player, ScenePoints.TRIGGER_RADIUS);
                if (pt == null) {
                    // причина «не работает» теперь приходит с сервера,
                    // независимо от того, доехал ли до клиента список точек
                    player.sendSystemMessage(Component.literal("§7Рядом нет точки сцены (H — создать)"));
                    return;
                }
                start(server, player, pt.scene());
            });
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();

            // Подсказка над хотбаром: сервер сам следит, кто в радиусе точки,
            // и шлёт actionbar прямым пакетом — без команд, значит без спама
            // «Отображение надписи…» в чате и логе. Работает у всех в мультиплеере.
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                boolean inScene = active.stream().anyMatch(s -> s.player().equals(p.getUUID()));
                if (inScene) continue; // во время сцены подсказка не мозолит
                if (ScenePoints.nearestForPlayer(p, ScenePoints.TRIGGER_RADIUS) != null
                        && now - lastHintMs.getOrDefault(p.getUUID(), 0L) > 2000) {
                    lastHintMs.put(p.getUUID(), now);
                    // 26.2: если пакет подчеркнётся — поищи в автокомплите пакет
                    // со словом ActionBar в имени
                    p.connection.send(new ClientboundSetActionBarTextPacket(
                            Component.literal("§eНажмите G для взаимодействия")));
                }
            }

            if (active.isEmpty()) return;
            active.removeIf(s -> {
                if (now < s.endMs()) return false;
                finish(server, s);
                return true;
            });
        });
    }

    private static void start(MinecraftServer server, ServerPlayer player, String anim) {
        if (active.stream().anyMatch(s -> s.player().equals(player.getUUID()))) return;
        ScenePoints.ScenePoint pt = ScenePoints.nearestForPlayer(player, ScenePoints.TRIGGER_RADIUS);
        if (pt == null) {
            // вот это теперь видно в логе, а не «кнопка молча не работает»
            DesertMod.LOGGER.warn("Сцена: рядом нет точки — триггер проигнорирован (игрок у {}, {})",
                    player.blockPosition().toShortString(),
                    player.level().dimension().identifier());
            return;
        }
        String scene = pt.scene();
        DesertMod.LOGGER.info("Сцена: старт, анимация {}, точка {}", scene, pt.id());
        ServerLevel level = (ServerLevel) player.level();
        SceneFile sf = SceneFile.load(scene);

        // где игрок стоял — туда вернём в финале
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        float yaw = player.getYRot(), pitch = player.getXRot();

        // NPC: якорь + полблока в сторону взгляда (математика инлайн,
        // не зависит от версии SceneLayout)
        double fx = -Math.sin(pt.yaw() * Math.PI / 180.0);
        double fz =  Math.cos(pt.yaw() * Math.PI / 180.0);
        double nx = pt.x() + 0.5 * fx;
        double nz = pt.z() + 0.5 * fz;
        SimpleNpcEntity dbl = new SimpleNpcEntity(ModEntities.SIMPLE_NPC, level);
        dbl.setPos(nx, pt.y(), nz);
        dbl.setYRot(pt.yaw());
        dbl.setYHeadRot(pt.yaw());
        dbl.setYBodyRot(pt.yaw());
        dbl.setAnimName(scene);
        // дублёр «надевает» скин игрока, который играет сцену:
        // клиент по приставке player: подставит его живую текстуру
        dbl.setSkinName("player:" + player.getUUID());
        level.addFreshEntity(dbl);

        // камера — от позиции NPC, в его локальных осях (CAM_* из SceneLayout)
        double cx = nx + SceneLayout.CAM_FWD * (-fx) + SceneLayout.CAM_RIGHT * (-fz);
        double cy = pt.y() + SceneLayout.CAM_UP;
        double cz = nz + SceneLayout.CAM_FWD * fz + SceneLayout.CAM_RIGHT * (-fx);
        float cyaw = pt.yaw() + SceneLayout.CAM_YAW_DELTA;

        // сначала поворот, потом телепорт С пакетом и подтверждением:
        // сервер будет игнорировать входящие пакеты движения игрока,
        // пока клиент не подтвердит телепорт — снапа «назад в бег» больше нет
        player.setYRot(cyaw);
        player.setXRot(SceneLayout.CAM_PITCH);
        player.setDeltaMovement(0, 0, 0); // гасим инерцию бега на сервере
        player.teleportTo(cx, cy, cz);
        player.setInvisible(true);
        ServerPlayNetworking.send(player, new NpcSkinPayloads.SceneStart(
                scene, pt.x(), pt.y(), pt.z(), pt.yaw()));

        long ms = sf != null ? (long) (sf.duration * 1000) + 250 : 4250;
        active.add(new Scene(player.getUUID(), dbl.getUUID(),
                System.currentTimeMillis() + ms, px, py, pz, yaw, pitch));
    }

    private static void finish(MinecraftServer server, Scene s) {
        DesertMod.LOGGER.info("Сцена: финал");
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(s.doubleNpc()) instanceof SimpleNpcEntity npc) {
                npc.discard();
                break;
            }
        }
        ServerPlayer p = null;
        for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
            if (pl.getUUID().equals(s.player())) {
                p = pl;
                break;
            }
        }
        if (p == null) return;
        p.setYRot(s.yaw());
        p.setXRot(s.pitch());
        p.teleportTo(s.px(), s.py(), s.pz());
        p.setInvisible(false);
        ServerPlayNetworking.send(p, new NpcSkinPayloads.SceneEnd());
    }
}