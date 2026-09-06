package net.mxnder.desertmod.client.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.mxnder.desertmod.block.ModBlocks;
import net.mxnder.desertmod.item.ModItems;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends FabricRecipeProvider {
    public ModRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        return new RecipeProvider(registries, output) {
            @Override
            public void buildRecipes() {

                oreCooking(
                        SmokingRecipe::new,
                        List.of(ModItems.BLESSED_KIFI),
                        RecipeCategory.MISC,
                        CookingBookCategory.MISC,
                        ModItems.KIFI,
                        0.7f,   // опыт повыше — путь длиннее
                        200,
                        "desertmod",
                        null
                );

                shaped(RecipeCategory.MISC, ModItems.KIFI_RAW)
                        .pattern("SHS")
                        .pattern("RSR")
                        .pattern("HRH")
                        .define('R', Ingredient.of(Items.COAL, Items.CHARCOAL))
                        .define('H', Items.HONEYCOMB)
                        .define('S', Items.WHEAT)
                        .unlockedBy("has_coal", has(Items.COAL))
                        .unlockedBy("has_charcoal", has(Items.CHARCOAL))
                        .unlockedBy("has_honeycomb", has(Items.HONEYCOMB))
                        .unlockedBy("has_wheat", has(Items.WHEAT))
                        .group("desertmod")
                        .save(output);

                shaped(RecipeCategory.DECORATIONS, ModBlocks.KIFI_BRAZIER)
                        .pattern("ABA")
                        .pattern("CDC")
                        .pattern("EFE")
                        .define('A', Items.SANDSTONE_SLAB)
                        .define('B', Items.AMETHYST_SHARD)
                        .define('C', Items.ENDER_PEARL)
                        .define('D', Items.FIRE_CHARGE)
                        .define('E', Items.SANDSTONE)
                        .define('F', Items.IRON_INGOT)
                        .unlockedBy("has_amethyst", has(Items.AMETHYST_SHARD))
                        .unlockedBy("has_ender_pearl", has(Items.ENDER_PEARL))
                        .unlockedBy("has_fire_charge", has(Items.FIRE_CHARGE))
                        .unlockedBy("has_iron", has(Items.IRON_INGOT))
                        .save(output);

                // сырое кифи, обложенное 4 аметистами, — освещённое
                shaped(RecipeCategory.MISC, ModItems.BLESSED_KIFI)
                        .pattern(" A ")
                        .pattern("ARA")
                        .pattern(" A ")
                        .define('R', ModItems.KIFI_RAW)
                        .define('A', Items.AMETHYST_SHARD)
                        .unlockedBy("has_kifi_raw", has(ModItems.KIFI_RAW))
                        .unlockedBy("has_amethyst", has(Items.AMETHYST_SHARD))
                        .group("desertmod")
                        .save(output);
            }


        };
    }

    @Override
    public String getName() {
        return "DesertMod Recipes";
    }
}
