package net.mxnder.desertmod.mixin;

import net.minecraft.client.gui.Gui;
import net.mxnder.desertmod.client.scene.SceneManagerClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
    // Главный метод отрисовки HUD. В 26.2 он может зваться render или
    // extractRenderState — открой класс Gui (Ctrl+N) и подставь точное имя.
    @Inject(method = {"render", "extractRenderState"}, at = @At("HEAD"), cancellable = true)
    private void hideHudDuringScene(CallbackInfo ci) {
        if (SceneManagerClient.isActive()) ci.cancel();
    }
}