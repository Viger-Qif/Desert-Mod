package net.mxnder.desertmod.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.mxnder.desertmod.item.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Освящённое копчение, вся логика в одной точке — getBurnDuration:
 *  - сверху blessed_kifi и топливо НЕ стержень → 0, печь не разгорается;
 *  - сверху blessed_kifi и топливо стержень → ровно 200 тиков,
 *    то есть один стержень = один прожар (ваниль съедает стержень при розжиге,
 *    и этих 200 тиков хватает ровно на одну готовку);
 *  - всё остальное (руда, еда, обычные рецепты) не тронуто. */
@Mixin(AbstractFurnaceBlockEntity.class)
public class BlessedFuelMixin {

    // instance-метод getBurnDuration(FuelValues, ItemStack) -> int, сигнатура из 26.2
    @Inject(method = "getBurnDuration", at = @At("RETURN"), cancellable = true)
    private void desertmod$sacredFuel(FuelValues fuelValues, ItemStack fuel,
                                      CallbackInfoReturnable<Integer> cir) {
        AbstractFurnaceBlockEntity self = (AbstractFurnaceBlockEntity) (Object) this;
        if (self.getItem(0).is(ModItems.BLESSED_KIFI)) {
            cir.setReturnValue(fuel.is(Items.BLAZE_ROD) ? 400 : 0);
        }
    }
}