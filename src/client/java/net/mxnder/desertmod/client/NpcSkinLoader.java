package net.mxnder.desertmod.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.mxnder.desertmod.DesertMod;
import net.mxnder.desertmod.NpcSkins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class NpcSkinLoader {

    // id, по которому GeckoLib ищет стандартную текстуру NPC.
    // Зарегистрируем свою текстуру под этим же id — и она подменит ассет.
    private static final Identifier DEFAULT_SKIN_ID =
            Identifier.fromNamespaceAndPath("desertmod", "textures/entity/simple_npc.png");

    /** Вызвать один раз из onInitializeClient. */
    public static void init() {
        // грузим после полного старта клиента, когда текстур-менеджер уже жив
        ClientLifecycleEvents.CLIENT_STARTED.register(NpcSkinLoader::loadSkins);
    }

    private static final Map<String, Identifier> SKINS = new LinkedHashMap<>();

    private static void loadSkins(Minecraft mc) {
        Path dir = NpcSkins.skinsDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".png")).forEach(p -> {
                String name = p.getFileName().toString().replace(".png", "");
                try (var input = Files.newInputStream(p)) {
                    NativeImage image = NativeImage.read(input);
                    // label — отладочное имя, помнишь про Supplier<String> из прошлой правки
                    DynamicTexture texture = new DynamicTexture(() -> "desertmod npc skin " + name, image);
                    Identifier id = Identifier.fromNamespaceAndPath("desertmod", "dyn/" + name);
                    mc.getTextureManager().register(id, texture);
                    SKINS.put(name, id);
                } catch (Exception e) {
                    DesertMod.LOGGER.warn("Не смог загрузить скин NPC {}", p, e);
                }
            });
        } catch (Exception e) {
            DesertMod.LOGGER.warn("Не смог просканировать папку скинов NPC", e);
        }

        BUILTIN.clear();
        BUILTIN.addAll(NpcSkins.builtinNames(mc.getResourceManager()));
        NpcSkins.cacheBuiltin(BUILTIN); // сервер (одиночка = та же JVM) увидит имена
        DesertMod.LOGGER.info("Загружены скины NPC: конфиг {}, встроенные {}", SKINS.keySet(), BUILTIN);
    }

    private static final Set<String> BUILTIN = new LinkedHashSet<>();
    // в loadSkins(mc), после блока с папкой конфигов:

    /** Текстура по имени скина; null, если такого скина нет. */
    public static Identifier get(String name) {
        Identifier dyn = SKINS.get(name);   // папка конфигов — приоритет
        if (dyn != null) return dyn;
        if (BUILTIN.contains(name))         // встроенный скин из ассетов мода
            return Identifier.fromNamespaceAndPath("desertmod", "textures/skins/" + name + ".png");
        return null;
    }

    /** Запасная текстура, если скин не назначен. */
    public static Identifier getDefault() {
        return Identifier.fromNamespaceAndPath("desertmod", "textures/entity/simple_npc.png");
    }

    private static void applySkin(Minecraft mc, Path png) {
        try (var input = Files.newInputStream(png)) {
            // NativeImage — из com.mojang.blaze3d.platform (в textures его нет)
            NativeImage image = NativeImage.read(input);
            // В 26.2 конструктор требует label — это просто отладочное имя,
            // передаётся как Supplier<String>, подойдёт любая строка
            DynamicTexture texture = new DynamicTexture(() -> "desertmod npc skin", image);
            // регистрируем под стандартным id — рендерер получит её вместо ассета
            mc.getTextureManager().register(DEFAULT_SKIN_ID, texture);
            DesertMod.LOGGER.info("Скин NPC загружен из конфига: {}", png.getFileName());
        } catch (Exception e) {
            DesertMod.LOGGER.warn("Не смог загрузить скин NPC {}", png, e);
        }
    }
}