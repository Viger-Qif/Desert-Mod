package net.mxnder.desertmod.entity;


import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.mxnder.desertmod.DesertMod;

public class ModEntities {

    public static final EntityType<SimpleNpcEntity> SIMPLE_NPC = register(
            "simple_npc",
            EntityType.Builder.<SimpleNpcEntity>of(SimpleNpcEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)         // хитбокс как у игрока
                    .clientTrackingRange(10)   // радиус отслеживания клиентом
    );

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(
                Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(DesertMod.MOD_ID, name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    // В ModEntities: базовые атрибуты моба (здоровье, скорость и т.д.)
    public static void registerAttributes() {
        FabricDefaultAttributeRegistry.register(SIMPLE_NPC, Mob.createMobAttributes());
    }

    public static void registerModEntities() {
        DesertMod.LOGGER.info("Registering Mod Entities for " + DesertMod.MOD_ID);
    }
}
