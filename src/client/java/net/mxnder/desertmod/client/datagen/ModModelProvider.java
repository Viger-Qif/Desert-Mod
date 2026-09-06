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
    }

    @Override
    public void generateItemModels(ItemModelGenerators itemModelGenerators) {
        itemModelGenerators.generateFlatItem(ModItems.KIFI, ModelTemplates.FLAT_ITEM);
        itemModelGenerators.generateFlatItem(ModItems.KIFI_RAW, ModelTemplates.FLAT_ITEM);
        itemModelGenerators.generateFlatItem(ModItems.BLESSED_KIFI, ModelTemplates.FLAT_ITEM);
        itemModelGenerators.generateFlatItem(ModItems.ICON_TAB, ModelTemplates.FLAT_ITEM);
        // KIFI_BRAZIER не нужен здесь: его item-модель (block/kifi_brazier) генерируется
        // автоматически через createTrivialCube() выше.
        // 3. скопировать строку сюда для добавлению item, запустить датаген
        // 4. в файл en_us.json добавить перевод, а потом текстуру закинуть
    }
}