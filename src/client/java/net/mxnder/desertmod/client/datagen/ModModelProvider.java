package net.mxnder.desertmod.client.datagen;

import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.mxnder.desertmod.block.ModBlocks;
import net.mxnder.desertmod.item.ModItems;

public class ModModelProvider extends FabricModelProvider {

    public ModModelProvider(FabricPackOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators blockModelGenerators) {
        // Простой куб без вариантов, blockstate + block-модель + item-модель
        // генерируются автоматически из одной текстуры assets/desertmod/textures/block/kifi_brazier.png
        // ⚠ ПРОВЕРИТЬ: имя метода для "обычного полного куба" может отличаться в вашей версии
        // (createTrivialCube / createTrivialBlock / cubeAll — смотрите BlockModelGenerators в вашем маппинге).
        blockModelGenerators.createTrivialCube(ModBlocks.KIFI_BRAZIER);
    }

    @Override
    public void generateItemModels(ItemModelGenerators itemModelGenerators) {
        itemModelGenerators.generateFlatItem(ModItems.KIFI, ModelTemplates.FLAT_ITEM);
        itemModelGenerators.generateFlatItem(ModItems.KIFI_RAW, ModelTemplates.FLAT_ITEM);

        itemModelGenerators.generateFlatItem(ModItems.ICON_TAB, ModelTemplates.FLAT_ITEM);
        // KIFI_BRAZIER не нужен здесь: его item-модель (block/kifi_brazier) генерируется
        // автоматически через createTrivialCube() выше.
        // 3. скопировать строку сюда для добавлению item, запустить датаген
        // 4. в файл en_us.json добавить перевод, а потом текстуру закинуть
    }
}