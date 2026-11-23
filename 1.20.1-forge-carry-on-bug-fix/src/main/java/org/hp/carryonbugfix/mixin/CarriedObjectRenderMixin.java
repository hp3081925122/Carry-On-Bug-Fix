package org.hp.carryonbugfix.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.hp.carryonbugfix.client.CarriedPassengerRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tschipp.carryon.client.render.CarriedObjectRender;

@Mixin(value = CarriedObjectRender.class, remap = false)
public abstract class CarriedObjectRenderMixin {
    @Redirect(method = {"drawFirstPersonEntity", "drawThirdPerson"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", remap = true))
    private static void carryOnBugFix$renderPassenger(EntityRenderDispatcher dispatcher, Entity entity, double x, double y, double z, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffer, int light) {
        CarriedPassengerRender.render(dispatcher, entity, x, y, z, yaw, partialTicks, pose, buffer, light);
    }
}
