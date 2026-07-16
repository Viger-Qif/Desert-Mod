package net.mxnder.desertmod.item;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.mxnder.desertmod.DesertMod;
import net.mxnder.desertmod.block.ModBlocks;

import java.util.function.Function;

public class ModItems {
    // регистрация предметов
    public static final Item ICON_TAB = registerItem("icon_tab", Item::new);
    public static final Item KIFI = registerItem("kifi", Item::new);
    public static final Item KIFI_RAW = registerItem("kifi_raw", Item::new);

    // предмет-блок для жаровни (используем то же имя, что и у блока)
    public static final Item KIFI_BRAZIER = registerItem("kifi_brazier",
            properties -> new BlockItem(ModBlocks.KIFI_BRAZIER, properties));
    // 1. скопировать строчку сюда для создания нового предмета


    // вспомогательный метод для регистрации предметов
    private static Item registerItem(String name, Function<Item.Properties, Item> function) {
        return Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(DesertMod.MOD_ID, name),
                function.apply(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(DesertMod.MOD_ID, name)))));
    }

    public static void registerModItems() {
        DesertMod.LOGGER.info("Registering Mod Items for " + DesertMod.MOD_ID);

        // добавление пользовательского предмета на вкладку ИНГРИДИЕНТЫ в креативе
        //CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
        //output.accept(KIFI);
        // 2. скопировать стрчоку output.accept(предмет) для добавления в творческое меню
        //});
    }

}