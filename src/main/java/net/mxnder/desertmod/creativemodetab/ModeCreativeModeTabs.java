package net.mxnder.desertmod.creativemodetab;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;import net.minecraft.core.Registry;import net.minecraft.core.registries.BuiltInRegistries;import net.minecraft.network.chat.Component;import net.minecraft.resources.Identifier;import net.minecraft.world.item.CreativeModeTab;import net.minecraft.world.item.ItemStack;import net.mxnder.desertmod.DesertMod;import net.mxnder.desertmod.item.ModItems;

public class ModeCreativeModeTabs {

    public static final CreativeModeTab DESERT_MOD_TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(DesertMod.MOD_ID, "desert_mod"),
                    FabricCreativeModeTab.builder()
                            .icon(() -> new ItemStack(ModItems.ICON_TAB))
                            .title(Component.translatable("creativemodetab.desertmod.desert_mod"))
                            .displayItems((parameters, output) -> {
                                output.accept(ModItems.KIFI);
                                output.accept(ModItems.ICON_TAB);
                                output.accept(ModItems.KIFI_RAW);


                            })
                            .build());


    public static void registerModCreativeModeTabs() {
        DesertMod.LOGGER.info("Registering Creative Mode Tabs for " + DesertMod.MOD_ID);
    }
}
