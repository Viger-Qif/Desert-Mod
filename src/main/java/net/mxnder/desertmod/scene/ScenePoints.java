package net.mxnder.desertmod.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.mxnder.desertmod.network.NpcSkinPayloads;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Точки сцен: конфиг config/desertmod/scene_points.json,
 *  редактируется в игре, рассылается всем клиентам. */
public final class ScenePoints {

    public record ScenePoint(UUID id, double x, double y, double z,
                             float yaw, String dim, String scene) {}

    public static final double TRIGGER_RADIUS = 1.5; // радиус «стоишь на точке»

    private static final List<ScenePoint> points = new ArrayList<>();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("desertmod").resolve("scene_points.json");
    }

    public static void load() {
        points.clear();
        Path p = file();
        if (!Files.isRegularFile(p)) {
            // первый запуск: сеем твою отшлифованную точку, чтобы не потерять
            points.add(new ScenePoint(UUID.randomUUID(),
                    -318.5, 64.0, -365.0, 180f, "minecraft:overworld", "smith_strike"));
            save();
            return;
        }
        try (var reader = Files.newBufferedReader(p)) {
            for (var e : JsonParser.parseReader(reader).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                points.add(new ScenePoint(
                        UUID.fromString(o.get("id").getAsString()),
                        o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(),
                        o.get("yaw").getAsFloat(), o.get("dim").getAsString(),
                        o.get("scene").getAsString()));
            }
        } catch (Exception ignored) {}
    }

    public static void save() {
        JsonArray arr = new JsonArray();
        for (ScenePoint pt : points) {
            JsonObject o = new JsonObject();
            o.addProperty("id", pt.id().toString());
            o.addProperty("x", pt.x()); o.addProperty("y", pt.y()); o.addProperty("z", pt.z());
            o.addProperty("yaw", pt.yaw());
            o.addProperty("dim", pt.dim());
            o.addProperty("scene", pt.scene());
            arr.add(o);
        }
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), arr.toString());
        } catch (Exception ignored) {}
    }

    public static void add(double x, double y, double z, float yaw, String dim, String scene) {
        // снап до середины блока: в конфиге ровные .5, без «пиксельных» хвостов
        double bx = Math.floor(x) + 0.5;
        double by = Math.round(y);
        double bz = Math.floor(z) + 0.5;
        points.add(new ScenePoint(UUID.randomUUID(), bx, by, bz, yaw, dim, scene));
        save();
    }

    public static boolean removeNearest(double x, double y, double z, String dim, double radius) {
        ScenePoint best = nearest(x, y, z, dim, radius);
        if (best == null) return false;
        points.remove(best);
        save();
        return true;
    }

    public static ScenePoint nearest(double x, double y, double z, String dim, double radius) {
        ScenePoint best = null;
        double bestD = radius * radius;
        for (ScenePoint pt : points) {
            if (!pt.dim().equals(dim)) continue;
            double dx = pt.x() - x, dy = pt.y() - y, dz = pt.z() - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d <= bestD) { bestD = d; best = pt; }
        }
        return best;
    }

    public static ScenePoint nearestForPlayer(ServerPlayer p, double radius) {
        return nearest(p.getX(), p.getY(), p.getZ(),
                p.level().dimension().identifier().toString(), radius);
    }

    public static void syncTo(ServerPlayer p) {
        ServerPlayNetworking.send(p, new NpcSkinPayloads.ScenePointsSync(toDtos()));
    }

    public static void syncAll(MinecraftServer server) {
        var dto = toDtos();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, new NpcSkinPayloads.ScenePointsSync(dto));
        }
    }

    private static List<NpcSkinPayloads.ScenePointDto> toDtos() {
        var out = new ArrayList<NpcSkinPayloads.ScenePointDto>();
        for (ScenePoint pt : points) {
            out.add(new NpcSkinPayloads.ScenePointDto(
                    pt.id(), pt.x(), pt.y(), pt.z(), pt.yaw(), pt.dim(), pt.scene()));
        }
        return out;
    }

    /** Сдвинуть ближайшую точку на дельту (доводка после снапа). */
    public static ScenePoint moveNearest(double x, double y, double z, String dim, double radius,
                                         double dx, double dy, double dz) {
        ScenePoint best = nearest(x, y, z, dim, radius);
        if (best == null) return null;
        ScenePoint upd = new ScenePoint(best.id(),
                best.x() + dx, best.y() + dy, best.z() + dz,
                best.yaw(), best.dim(), best.scene());
        points.set(points.indexOf(best), upd);
        save();
        return upd;
    }

    /** Копия списка — для /desertmod scenelist. */
    public static List<ScenePoint> all() {
        return List.copyOf(points);
    }

    /** Задать точные значения ближайшей точке (null = не трогать). */
    public static ScenePoint setNearest(double x, double y, double z, String dim, double radius,
                                        Double nx, Double ny, Double nz, Float nyaw) {
        ScenePoint best = nearest(x, y, z, dim, radius);
        if (best == null) return null;
        ScenePoint upd = new ScenePoint(best.id(),
                nx != null ? nx : best.x(),
                ny != null ? ny : best.y(),
                nz != null ? nz : best.z(),
                nyaw != null ? nyaw : best.yaw(),
                best.dim(), best.scene());
        points.set(points.indexOf(best), upd);
        save();
        return upd;
    }
}