package net.mxnder.desertmod.scene;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Сцена из ресурсов мода: assets/desertmod/scenes/<name>.json.
 *  Всё зашито на каждую сцену отдельно: где стоит дублёр,
 *  где камера, сколько длится и как едет. */
public final class SceneFile {

    public record Key(float t, float fwd, float strafe, float up, float yaw, float pitch) {}
    /** Жёсткая точка в мире: позиция + куда смотрит. */
    public record Transform(double x, double y, double z, float yaw, float pitch) {}

    public final String anim;
    public final float duration;
    public final List<Key> keys;
    public final Transform npc;    // где стоит и куда смотрит дублёр
    public final Transform camera; // куда телепортируется игрок-камера

    public static SceneFile load(String name) {
        var container = FabricLoader.getInstance().getModContainer("desertmod");
        if (container.isEmpty()) return null;
        Path p = container.get().getPath("assets/desertmod/scenes/" + name + ".json");
        if (!Files.isRegularFile(p)) return null;
        try (var reader = Files.newBufferedReader(p)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            String anim = root.has("anim") ? root.get("anim").getAsString() : name;
            float duration = root.has("duration") ? root.get("duration").getAsFloat() : 4f;
            Transform npc = root.has("npc") ? readTransform(root.getAsJsonObject("npc")) : null;
            Transform camera = root.has("camera") ? readTransform(root.getAsJsonObject("camera")) : null;
            List<Key> keys = new ArrayList<>();
            for (var e : root.getAsJsonArray("keys")) {
                JsonObject k = e.getAsJsonObject();
                keys.add(new Key(
                        k.get("t").getAsFloat(),
                        k.get("fwd").getAsFloat(),
                        k.get("strafe").getAsFloat(),
                        k.get("up").getAsFloat(),
                        k.get("yaw").getAsFloat(),
                        k.get("pitch").getAsFloat()));
            }
            return new SceneFile(anim, duration, keys, npc, camera);
        } catch (Exception e) {
            return null;
        }
    }

    private static Transform readTransform(JsonObject o) {
        return new Transform(
                o.get("x").getAsDouble(),
                o.get("y").getAsDouble(),
                o.get("z").getAsDouble(),
                o.has("yaw") ? o.get("yaw").getAsFloat() : 0f,
                o.has("pitch") ? o.get("pitch").getAsFloat() : 0f);
    }

    private SceneFile(String anim, float duration, List<Key> keys,
                      Transform npc, Transform camera) {
        this.anim = anim;
        this.duration = duration;
        this.keys = keys;
        this.npc = npc;
        this.camera = camera;
    }
}