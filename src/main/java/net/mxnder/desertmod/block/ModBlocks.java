package net.mxnder.desertmod.block;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.mxnder.desertmod.DesertMod;

import java.util.function.Function;

public class ModBlocks {

    public static final Block KIFI_BRAZIER = registerBlock("kifi_brazier",
            KifiBrazierBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.5f)
                    .lightLevel(state -> 15)
                    .sound(net.minecraft.world.level.block.SoundType.STONE));
    // 1. скопировать строчку сюда для создания нового блока

    // вспомогательный метод для регистрации блоков (по аналогии с registerItem в ModItems)
    private static Block registerBlock(String name, Function<BlockBehaviour.Properties, Block> function, BlockBehaviour.Properties properties) {
        Identifier id = Identifier.fromNamespaceAndPath(DesertMod.MOD_ID, name);
        // ⚠ ПРОВЕРИТЬ: если BlockBehaviour.Properties в вашей версии не требует .setId(...),
        // как это делает Item.Properties в ModItems — просто уберите вызов .setId(...) ниже.
        Block block = function.apply(properties.setId(ResourceKey.create(Registries.BLOCK, id)));
        return Registry.register(BuiltInRegistries.BLOCK, id, block);
    }

    public static void registerModBlocks() {
        DesertMod.LOGGER.info("Registering Mod Blocks for " + DesertMod.MOD_ID);
    }
}
