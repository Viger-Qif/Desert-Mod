package net.mxnder.desertmod.scene;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.mxnder.desertmod.DesertMod;
import net.mxnder.desertmod.entity.ModEntities;
import net.mxnder.desertmod.entity.SimpleNpcEntity;
import net.mxnder.desertmod.network.NpcSkinPayloads;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SceneManager {

    private record Scene(UUID player, UUID doubleNpc, long endMs,
                         double px, double py, double pz,
                         float yaw, float pitch) {}

    private static final List<Scene> active = new ArrayList<>();

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(NpcSkinPayloads.SceneTrigger.TYPE, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> start(server, player, payload.anim()));
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (active.isEmpty()) return;
            long now = System.currentTimeMillis();
            active.removeIf(s -> {
                if (now < s.endMs()) return false;
                finish(server, s);
                return true;
            });
        });
    }

    private static void start(MinecraftServer server, ServerPlayer player, String anim) {
        // Серверная проверка: то же измерение и близость к точке интереса.
        // Клиент мог соврать или игрок мог отойти, пока пакет летел, —
        // авторитетное решение здесь.
        if (player.level().dimension() != SceneLayout.SCENE_DIM) return;
        if (!SceneLayout.isNear(player.getX(), player.getY(), player.getZ())) return;
        if (active.stream().anyMatch(s -> s.player().equals(player.getUUID()))) return;
        DesertMod.LOGGER.info("Сцена: старт, анимация {}", anim);
        ServerLevel level = (ServerLevel) player.level();
        SceneFile sf = SceneFile.load(anim);

        // где игрок стоял до сцены — туда вернём в финале
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        float yaw = player.getYRot(), pitch = player.getXRot();

        // дублёр на якоре; все три поворота, чтобы модель реально повернулась
        SimpleNpcEntity dbl = new SimpleNpcEntity(ModEntities.SIMPLE_NPC, level);
        dbl.setPos(SceneLayout.NPC_X, SceneLayout.ANCHOR_Y, SceneLayout.NPC_Z);
        dbl.setYRot(SceneLayout.ANCHOR_YAW);
        dbl.setYHeadRot(SceneLayout.ANCHOR_YAW);
        dbl.setYBodyRot(SceneLayout.ANCHOR_YAW);
        dbl.setAnimName(anim);
        level.addFreshEntity(dbl);

        // игрок — в точку камеры, посчитанную от того же якоря
        double[] cam = SceneLayout.cameraWorld();
        player.setPos(cam[0], cam[1], cam[2]);
        player.setYRot(SceneLayout.cameraYaw());
        player.setXRot(SceneLayout.CAM_PITCH);
        player.setInvisible(true);
        ServerPlayNetworking.send(player, new NpcSkinPayloads.SceneStart(anim));

        long ms = sf != null ? (long) (sf.duration * 1000) + 250 : 4250;
        active.add(new Scene(player.getUUID(), dbl.getUUID(),
                System.currentTimeMillis() + ms, px, py, pz, yaw, pitch));
    }

    private static void finish(MinecraftServer server, Scene s) {
        DesertMod.LOGGER.info("Сцена: финал");
        // убираем дублёра
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(s.doubleNpc()) instanceof SimpleNpcEntity npc) {
                npc.discard();
                break;
            }
        }
        // ищем игрока перебором онлайн-списка
        ServerPlayer p = null;
        for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
            if (pl.getUUID().equals(s.player())) {
                p = pl;
                break;
            }
        }
        // игрок вышел во время сцены — возвращать нечего
        if (p == null) return;
        // возвращаем туда, где нажал кнопку: сначала поворот, потом телепорт с пакетом
        p.setYRot(s.yaw());
        p.setXRot(s.pitch());
        p.teleportTo(s.px(), s.py(), s.pz()); // подчеркнётся в 26.2 — p.connection.teleport(s.px(), s.py(), s.pz(), s.yaw(), s.pitch());
        p.setInvisible(false);
        ServerPlayNetworking.send(p, new NpcSkinPayloads.SceneEnd());
    }
}