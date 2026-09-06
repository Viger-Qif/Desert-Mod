package net.mxnder.desertmod.scene;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Композиция сцены: всё относительно якоря — блока под ногами NPC
 *  и стороны, куда он смотрит. Единственный источник координат
 *  для сервера и клиента. */
public final class SceneLayout {

    // ЯКОРЬ: блок под ногами NPC и направление его взгляда
    // yaw: 0 = юг, 90 = запад, 180 = север, -90 = восток
    public static final double ANCHOR_X = -318.5, ANCHOR_Y = 64.0, ANCHOR_Z = -365.0;
    public static final float  ANCHOR_YAW = 180f;

    // Вектор взгляда NPC из угла — работает для ЛЮБОГО угла,
    // на четырёх сторонах света даёт ровные ±1 по X или Z
    private static final double FACE_X = -Math.sin(ANCHOR_YAW * Math.PI / 180.0);
    private static final double FACE_Z =  Math.cos(ANCHOR_YAW * Math.PI / 180.0);

    // NPC стоит на полблока от якоря в ту сторону, куда смотрит
    public static final double NPC_X = ANCHOR_X + 0.5 * FACE_X;
    public static final double NPC_Z = ANCHOR_Z + 0.5 * FACE_Z;

    // КАМЕРА в локальных осях (считается от позиции NPC,
    // чтобы твой настроенный кадр не развалился от его смещения):
    // CAM_FWD — вперёд от лица, больше = дальше от головы
    public static final double CAM_FWD = 0.3;
    // CAM_RIGHT — вправо от NPC (минус — влево, открыть правую руку)
    public static final double CAM_RIGHT = 0.2;
    // CAM_UP — высота ног камеры относительно ног NPC (глаза +1.62)
    public static final double CAM_UP = -0.1;
    // CAM_YAW_DELTA — доворот взгляда вправо; CAM_PITCH — наклон вниз
    public static final float CAM_YAW_DELTA = 20f;
    public static final float CAM_PITCH = 50f;

    /** Точка камеры в мировых координатах, посчитанная от позиции NPC. */
    public static double[] cameraWorld() {
        double s = FACE_X; // -sin(yaw)
        double c = FACE_Z; //  cos(yaw)
        return new double[] {
                NPC_X + CAM_FWD * (-s) + CAM_RIGHT * (-c),
                ANCHOR_Y + CAM_UP,
                NPC_Z + CAM_FWD * c + CAM_RIGHT * (-s)
        };
    }

    public static float cameraYaw() {
        return ANCHOR_YAW + CAM_YAW_DELTA;
    }

    // === ИНТЕРАКТИВ: где и когда сцена вообще доступна ===
// Измерение, в котором живёт сцена — вне него кнопка молчит
    public static final ResourceKey<Level> SCENE_DIM = Level.OVERWORLD;
    // Радиус «стоишь на точке» в блоках
    public static final double TRIGGER_RADIUS = 1.5;

    /** Достаточно ли близко игрок к точке интереса (якорю). */
    public static boolean isNear(double x, double y, double z) {
        double dx = x - ANCHOR_X;
        double dy = y - ANCHOR_Y;
        double dz = z - ANCHOR_Z;
        return dx * dx + dy * dy + dz * dz <= TRIGGER_RADIUS * TRIGGER_RADIUS;
    }
}