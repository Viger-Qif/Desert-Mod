package net.mxnder.desertmod.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.mxnder.desertmod.client.scene.SceneManagerClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Во время сцены руки локального игрока «пустые» — предмет не рисуется
 *  ни в первом лице, ни в F5, кем бы он ни рендерился. */
@Mixin({LivingEntity.class, Player.class})
public class HandItemMixin {

    // Геттеры с аргументом руки: getItemInHand(InteractionHand) и наследники
    @Inject(method = {"getItemInHand", "getHandItem", "getItemByHand"},
            at = @At("RETURN"), cancellable = true, require = 0)
    private void desertmod$emptyHandWithArg(InteractionHand hand, CallbackInfoReturnable<ItemStack> ci) {
        if (SceneManagerClient.isActive() && (Object) this == Minecraft.getInstance().player) {
            ci.setReturnValue(ItemStack.EMPTY);
        }
    }

    // Геттеры без аргументов: getMainHandItem() / getOffhandItem() и наследники
    @Inject(method = {"getMainHandItem", "getOffhandItem", "getWeaponItem", "getCarriedItem"},
            at = @At("RETURN"), cancellable = true, require = 0)
    private void desertmod$emptyHandNoArg(CallbackInfoReturnable<ItemStack> ci) {
        if (SceneManagerClient.isActive() && (Object) this == Minecraft.getInstance().player) {
            ci.setReturnValue(ItemStack.EMPTY);
        }
    }
}