package net.mxnder.desertmod.mixin;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.mxnder.desertmod.client.scene.SceneManagerClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    // В 26.2 руки/предмет рисует новый конвейер; глушим все вероятные входы.
    // Открой ItemInHandRenderer (Ctrl+N): если видишь там другое имя главного
    // метода отрисовки рук — добавь его в список, лишние имена просто не применятся.
    @Inject(method = {"renderHandsWithItems", "renderHandWithItem",
            "renderArmWithItem", "renderPlayerArm", "extractRenderState"},
            at = @At("HEAD"), cancellable = true)
    private void hideHandsDuringScene(CallbackInfo ci) {
        if (SceneManagerClient.isActive()) ci.cancel();
    }
}