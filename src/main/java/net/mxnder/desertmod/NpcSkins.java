package net.mxnder.desertmod;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/** Имена скинов NPC: из папки конфигов и встроенные в мод. */
public final class NpcSkins {


    public static Path skinsDir() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("desertmod").resolve("skins");
    }

    /** Скины из папки конфигов: имена png в config/desertmod/skins/. */
    public static LinkedHashSet<String> listNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        Path dir = skinsDir();
        if (!Files.isDirectory(dir)) return names;
        try (Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".png"))
                    .forEach(n -> names.add(n.substring(0, n.length() - 4))); // без .png
        } catch (IOException ignored) {
        }
        return names;
    }

    /** Встроенные скины: png рядом со стандартной текстурой сущности,
     *  то есть assets/desertmod/textures/entity/. */
    public static LinkedHashSet<String> builtinNames(ResourceManager rm) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        rm.listResources("textures/entity", id -> id.getPath().endsWith(".png"))
                .keySet()
                .forEach(id -> {
                    String path = id.getPath(); // вид: textures/entity/<имя>.png
                    String name = path.substring("textures/entity/".length(),
                            path.length() - ".png".length());
                    if (!name.contains("/")) names.add(name); // подпапки не тянем
                });
        return names;
    }

    // Кэш имён встроенных скинов, заполняется клиентом на старте.
    // Сервер в одиночке — та же JVM, берёт имена отсюда,
    // если его ресурс-менеджер не видит assets.
    private static final Set<String> BUILTIN_CACHE = new LinkedHashSet<>();

    public static void cacheBuiltin(Set<String> names) {
        BUILTIN_CACHE.clear();
        BUILTIN_CACHE.addAll(names);
    }

    /** Полный список для чата: конфиг + скан сервером + кэш клиента. */
    public static LinkedHashSet<String> listAll(ResourceManager rm) {
        LinkedHashSet<String> all = listNames();      // папка конфигов — приоритет
        all.addAll(builtinNames(rm));                 // скан assets сервером
        all.addAll(BUILTIN_CACHE);                    // то, что нашёл клиент
        return all;
    }

    /** Строгое имя: латиница/цифры/_/- до 32 символов, иначе null. */
    public static String sanitizeName(String raw) {
        if (raw == null) return null;
        String n = raw.trim().toLowerCase(Locale.ROOT);
        if (n.isEmpty() || n.length() > 32 || !n.matches("[a-z0-9_-]+")) return null;
        return n;
    }

    public static void save(String name, byte[] data) {
        try {
            Files.write(skinsDir().resolve(name + ".png"), data);
        } catch (IOException ignored) {
        }
    }

    public record StoredSkin(String name, byte[] data) {}

    /** Все скины из папки конфигов — сервер для рассылки, клиент для аплоада. */
    public static List<StoredSkin> readAll() {
        List<StoredSkin> out = new ArrayList<>();
        Path dir = skinsDir();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".png")).forEach(p -> {
                try {
                    String n = p.getFileName().toString().replace(".png", "");
                    out.add(new StoredSkin(n, Files.readAllBytes(p)));
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
        return out;
    }

}