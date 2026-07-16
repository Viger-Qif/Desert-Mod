package net.mxnder.desertmod.blockentity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.mxnder.desertmod.DesertMod;
import net.mxnder.desertmod.block.ModBlocks;

import java.util.Set;

public class ModBlockEntities {

    // ⚠ ПРОВЕРИТЬ: BlockEntityType.Builder в вашей версии не существует (подтверждено ошибкой компиляции).
    // Ниже — прямой конструктор BlockEntityType(BlockEntitySupplier, Set<Block>), это моя лучшая догадка.
    // Если он тоже не резолвится — Ctrl+клик на BlockEntityType, посмотрите публичные
    // конструкторы/статические методы и пришлите мне, какие есть (например create(...) или factory(...)).
    public static final BlockEntityType<KifiBrazierBlockEntity> KIFI_BRAZIER_BLOCK_ENTITY =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(DesertMod.MOD_ID, "kifi_brazier"),
                    new BlockEntityType<>(KifiBrazierBlockEntity::new, Set.of(ModBlocks.KIFI_BRAZIER))
            );

    public static void registerModBlockEntities() {
        DesertMod.LOGGER.info("Registering Mod Block Entities for " + DesertMod.MOD_ID);
    }
}