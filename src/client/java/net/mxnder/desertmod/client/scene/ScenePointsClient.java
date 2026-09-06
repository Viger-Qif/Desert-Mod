package net.mxnder.desertmod.client.scene;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ScenePointsClient {

    public record Point(UUID id, double x, double y, double z,
                        float yaw, String dim, String scene) {}

    private static final List<Point> points = new ArrayList<>();
    private static final double TRIGGER_RADIUS = 1.5;

    public static void set(List<Point> list) {
        points.clear();
        points.addAll(list);
    }

    /** Ближайшая точка, если игрок в радиусе и в её измерении, иначе null. */
    public static Point nearest(Minecraft mc) {
        if (mc.player == null || mc.level == null) return null;
        String dim = mc.level.dimension().toString();
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        Point best = null;
        double bestD = TRIGGER_RADIUS * TRIGGER_RADIUS;
        for (Point p : points) {
            if (!p.dim().equals(dim)) continue;
            double dx = p.x() - x, dy = p.y() - y, dz = p.z() - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d <= bestD) { bestD = d; best = p; }
        }
        return best;
    }
}