package org.hp.carryonbugfix.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.hp.carryonbugfix.client.CarriedPassengerRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tschipp.carryon.client.render.CarryRenderHelper;
import tschipp.carryon.common.carry.CarryOnData;

@Mixin(value = CarryRenderHelper.class, remap = false)
public abstract class CarryRenderHelperMixin {
    @Redirect(method = "getRenderEntity", at = @At(value = "INVOKE", target = "Ltschipp/carryon/common/carry/CarryOnData;getEntity(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;"))
    private static Entity carryOnBugFix$trackPassenger(CarryOnData carry, Level level) {
        return CarriedPassengerRender.track(carry, level);
    }
}
