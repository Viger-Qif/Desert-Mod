package net.mxnder.desertmod.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.mxnder.desertmod.client.scene.SceneManagerClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

    // Любое перемещение (ходьба, прыжок, вода) идёт через move() —
    // отмена здесь держит игрока на месте.
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void blockMovement(CallbackInfo ci) {
        if (SceneManagerClient.isActive()) {
            ci.cancel();
        }
    }

    // Присед — не перемещение, а поза. После тика ванили
// возвращаем стойку: нет ни визуала приседа, ни опускания камеры.
    @Inject(method = "tick", at = @At("RETURN"))
    private void forceStandingPose(CallbackInfo ci) {
        if (SceneManagerClient.isActive()) {
            LocalPlayer self = (LocalPlayer) (Object) this;
            self.setPose(Pose.STANDING); // поза = модель приседа + высота камеры
        }
    }
}