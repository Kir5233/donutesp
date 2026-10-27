package com.example.donutesp.mixin;

import com.example.donutesp.EspConfig;
import com.example.donutesp.Xray;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class BlockMixin {
    /**
     * Xray: пока включён, грань рисуется только у выбранных блоков.
     * require = 0: если метод в этой версии вдруг отличается, мод не падает, просто Xray не сработает.
     */
    @Inject(method = "shouldDrawSide(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;Lnet/minecraft/util/math/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void donutesp$xray(BlockState state, BlockView world, BlockPos pos, Direction side,
                                      BlockPos otherPos, CallbackInfoReturnable<Boolean> cir) {
        if (EspConfig.xrayEnabled) {
            cir.setReturnValue(Xray.isWanted(state.getBlock()));
        }
    }
}
