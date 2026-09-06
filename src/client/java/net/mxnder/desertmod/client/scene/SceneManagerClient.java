package net.mxnder.desertmod.client.scene;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.mxnder.desertmod.scene.SceneFile;
import net.mxnder.desertmod.scene.SceneLayout;

/** Клиентский режиссёр: во время сцены ведёт камеру (реального игрока)
 *  каждый кадр по ключам из assets/desertmod/scenes/<name>.json. */
public final class SceneManagerClient {

    private static boolean sceneActive = false;
    private static long sceneStartMs = 0;

    private static SceneFile scene;
    private static double startX, startY, startZ;
    private static float startYaw, startPitch;

    /** Миксины читают этот флаг каждый кадр: идёт ли сейчас сцена. */
    public static boolean isActive() {
        return sceneActive;
    }

    public static void init() {
        // Применяем трансформацию КАЖДЫЙ КАДР, а не каждый тик:
        // 20 Гц тиков дают ступеньки, кадры — шелковую камеру.
        LevelRenderEvents.START_MAIN.register(context -> {
            var mc = Minecraft.getInstance();
            if (!sceneActive || scene == null || mc.player == null) return;
            float elapsed = (System.currentTimeMillis() - sceneStartMs) / 1000f;
            if (elapsed >= scene.duration || scene.keys.size() < 2) return;

            SceneFile.Key a = scene.keys.get(0);
            SceneFile.Key b = a;
            for (int i = 0; i < scene.keys.size() - 1; i++) {
                if (elapsed >= scene.keys.get(i).t() && elapsed < scene.keys.get(i + 1).t()) {
                    a = scene.keys.get(i);
                    b = scene.keys.get(i + 1);
                    break;
                }
            }
            float f = b.t() > a.t() ? (elapsed - a.t()) / (b.t() - a.t()) : 0f;
            float fwd = Mth.lerp(f, a.fwd(), b.fwd());
            float str = Mth.lerp(f, a.strafe(), b.strafe());
            float up = Mth.lerp(f, a.up(), b.up());
            float yawD = Mth.lerp(f, a.yaw(), b.yaw());
            float pitD = Mth.lerp(f, a.pitch(), b.pitch());

            // Позицию трогаем, ТОЛЬКО если ключи реально двигают камеру.
            // Стоячую сцену не надо каждый кадр пихать setPos'ом:
            // серверная физика «стоит на земле» и воюет с нами — это и была тряска.
            if (fwd != 0f || str != 0f || up != 0f) {
                double sinY = Math.sin(startYaw * Math.PI / 180.0);
                double cosY = Math.cos(startYaw * Math.PI / 180.0);
                double dx = fwd * (-sinY) + str * (-cosY);
                double dz = fwd * cosY + str * (-sinY);
                mc.player.setPos(startX + dx, startY + up, startZ + dz);
            }

            // Повороты сервер не «чинит» — их ставим каждый кадр, это безопасно
            mc.player.setYRot(startYaw + yawD);
            mc.player.setXRot(startPitch + pitD);
        });
    }

    public static void startScene(String name) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        scene = SceneFile.load(name);

        double[] cam = SceneLayout.cameraWorld();
        mc.player.setPos(cam[0], cam[1], cam[2]);
        mc.player.setYRot(SceneLayout.cameraYaw());
        mc.player.setXRot(SceneLayout.CAM_PITCH);
        startX = cam[0]; startY = cam[1]; startZ = cam[2];
        startYaw = SceneLayout.cameraYaw();
        startPitch = SceneLayout.CAM_PITCH;

        sceneStartMs = System.currentTimeMillis();
        sceneActive = true;
    }

    public static void endScene() {
        sceneActive = false;
        scene = null;
    }
}