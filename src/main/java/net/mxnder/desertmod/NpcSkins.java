package net.mxnder.desertmod;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
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

    /** Встроенные скины: png-файлы из assets/desertmod/textures/skins/. */
    public static LinkedHashSet<String> builtinNames(ResourceManager rm) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        rm.listResources("textures/skins", id -> id.getPath().endsWith(".png"))
                .keySet()
                .forEach(id -> {
                    String path = id.getPath(); // вид: textures/skins/<имя>.png
                    names.add(path.substring("textures/skins/".length(), path.length() - ".png".length()));
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


}