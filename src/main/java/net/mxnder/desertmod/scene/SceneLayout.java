package net.mxnder.desertmod.scene;

/** Геометрия сцены от точки-якоря. Сами точки живут в ScenePoints
 *  (конфиг, редактируется в игре). */
public final class SceneLayout {

    // КАМЕРА в локальных осях якоря — общая для всех точек,
    // отшлифована один раз: fwd — вперёд от лица, right — вправо,
    // up — высота; доворот взгляда вправо и наклон вниз
    public static final double CAM_FWD = 0.3, CAM_RIGHT = 0.2, CAM_UP = -0.1;
    public static final float CAM_YAW_DELTA = 20f, CAM_PITCH = 50f;

    /** NPC: якорь + полблока в сторону взгляда. */
    public static double[] npcWorld(double ax, double az, float yaw) {
        double fx = -Math.sin(yaw * Math.PI / 180.0);
        double fz =  Math.cos(yaw * Math.PI / 180.0);
        return new double[] { ax + 0.5 * fx, az + 0.5 * fz };
    }

    /** Камера в мировых координатах, от позиции NPC. */
    public static double[] cameraWorld(double ax, double ay, double az, float yaw) {
        double[] npc = npcWorld(ax, az, yaw);
        double s = -Math.sin(yaw * Math.PI / 180.0);
        double c =  Math.cos(yaw * Math.PI / 180.0);
        return new double[] {
                npc[0] + CAM_FWD * (-s) + CAM_RIGHT * (-c),
                ay + CAM_UP,
                npc[1] + CAM_FWD * c + CAM_RIGHT * (-s)
        };
    }

    public static float cameraYaw(float yaw) {
        return yaw + CAM_YAW_DELTA;
    }
}