package org.hp.carryonbugfix.mixin;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.hp.carryonbugfix.CarryOnBugFixConfig;
import org.hp.carryonbugfix.PassengerStackValidator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tschipp.carryon.events.CommonEvents;

/**
 * 右键交互乘骑树中的任意乘客时自动定位至根载具实体。
 */
@Mixin(value = CommonEvents.class, remap = false)
public abstract class CommonEventsMixin {
    @Redirect(
            method = "onEntityRightClick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/event/entity/player/PlayerInteractEvent$EntityInteract;getTarget()Lnet/minecraft/world/entity/Entity;"
            )
    )
    private static Entity carryOnBugFix$selectRootEntity(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!CarryOnBugFixConfig.PASSENGER_STACK_ENABLED.get()) {
            return target;
        }
        return PassengerStackValidator.getRootVehicle(target);
    }
}
