package com.example.donutesp.mixin;

import com.example.donutesp.FreeCam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void donutesp$freecamLook(double dx, double dy, CallbackInfo ci) {
        if (FreeCam.active && (Object) this == MinecraftClient.getInstance().player) {
            FreeCam.look(dx, dy);
            ci.cancel();
        }
    }
}
