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

            // Первое лицо — жёстко по траектории.
            // Третье лицо — режиссёр не трогает повороты, мышь крутит свободно.
            boolean firstPerson = mc.options.getCameraType().isFirstPerson();
            // если в 26.2 подчеркнётся — поищи в options метод, возвращающий тип камеры

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

            // позиция едет в обоих режимах: в F5 ты орбитируешь вокруг движущейся точки
            if (fwd != 0f || str != 0f || up != 0f) {
                double sinY = Math.sin(startYaw * Math.PI / 180.0);
                double cosY = Math.cos(startYaw * Math.PI / 180.0);
                double dx = fwd * (-sinY) + str * (-cosY);
                double dz = fwd * cosY + str * (-sinY);
                mc.player.setPos(startX + dx, startY + up, startZ + dz);
            }

            // а вот повороты — только для первого лица
            if (firstPerson) {
                mc.player.setYRot(startYaw + yawD);
                mc.player.setXRot(startPitch + pitD);
            }
        });
    }

    public static void startScene(String name, double ax, double ay, double az, float yaw) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        scene = SceneFile.load(name);

        // NPC: якорь + полблока в сторону взгляда (та же математика, что на сервере)
        double fx = -Math.sin(yaw * Math.PI / 180.0);
        double fz =  Math.cos(yaw * Math.PI / 180.0);
        double nx = ax + 0.5 * fx;
        double nz = az + 0.5 * fz;

        // камера — от позиции NPC, в его локальных осях (CAM_* из SceneLayout)
        double cx = nx + SceneLayout.CAM_FWD * (-fx) + SceneLayout.CAM_RIGHT * (-fz);
        double cy = ay + SceneLayout.CAM_UP;
        double cz = nz + SceneLayout.CAM_FWD * fz + SceneLayout.CAM_RIGHT * (-fx);
        float cyaw = yaw + SceneLayout.CAM_YAW_DELTA;

        mc.player.setPos(cx, cy, cz);
        mc.player.setDeltaMovement(0, 0, 0);
        mc.player.setYRot(cyaw);
        mc.player.setXRot(SceneLayout.CAM_PITCH);
        startX = cx; startY = cy; startZ = cz;
        startYaw = cyaw;
        startPitch = SceneLayout.CAM_PITCH;

        sceneStartMs = System.currentTimeMillis();
        sceneActive = true;
    }

    public static void endScene() {
        sceneActive = false;
        scene = null;
    }
}