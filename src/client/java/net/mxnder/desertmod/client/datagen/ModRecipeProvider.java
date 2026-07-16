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
                        SmokingRecipe::new,                    // factory
                        List.of(ModItems.KIFI_RAW),          // smeltables
                        RecipeCategory.MISC,                    // craftingCategory
                        CookingBookCategory.MISC,               // cookingCategory
                        ModItems.KIFI,        // result
                        0.35f,                                  // experience
                        200,                                    // cookingTime (в тиках)
                        "desertmod",                            // group
                        null                                    // fromDesc (можно null)
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
            }
        };
    }

    @Override
    public String getName() {
        return "DesertMod Recipes";
    }
}
