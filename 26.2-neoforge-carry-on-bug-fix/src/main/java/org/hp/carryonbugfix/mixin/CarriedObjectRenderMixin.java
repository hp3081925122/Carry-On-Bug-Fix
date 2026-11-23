package org.hp.carryonbugfix.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.player.Player;
import org.hp.carryonbugfix.client.CarriedPassengerRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tschipp.carryon.client.render.CarriedObjectRender;

/**
 * 在手持根实体提交渲染后，于同一矩阵栈中追加提交全部乘客。
 */
@Mixin(value = CarriedObjectRender.class, remap = false)
public abstract class CarriedObjectRenderMixin {
    @Inject(
            method = "drawEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
                    shift = At.Shift.AFTER
            )
    )
    private static void carryOnBugFix$submitPassengers(
            Player player,
            PoseStack matrix,
            int light,
            float partialTicks,
            SubmitNodeCollector nodeCollector,
            boolean firstPerson,
            CallbackInfo ci
    ) {
        CarriedPassengerRender.submitPassengers(player, matrix, light, nodeCollector);
    }
}
