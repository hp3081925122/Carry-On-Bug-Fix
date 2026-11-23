package org.hp.carryonbugfix.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.hp.carryonbugfix.CarryOnBugFixConfig;
import org.hp.carryonbugfix.PassengerStackValidator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tschipp.carryon.events.CommonEvents;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

@Mixin(value = CommonEvents.class, remap = false)
public abstract class CommonEventsMixin {
    private static final Logger carryOnBugFix$LOGGER = LogUtils.getLogger();
    @Redirect(
            method = "onEntityRightClick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/event/entity/player/PlayerInteractEvent$EntityInteract;getTarget()Lnet/minecraft/world/entity/Entity;"
            )
    )
    private static Entity carryOnBugFix$selectRootEntity(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!CarryOnBugFixConfig.PASSENGER_STACK_ENABLED.get()) {
            return target;
        }
        Entity root = PassengerStackValidator.getRootVehicle(target);
        if (root != target) {
            carryOnBugFix$LOGGER.debug("Redirected Carry On target {} to root {} with {} passengers", target.getType(), root.getType(), root.getPassengers().size());
        }
        return root;
    }
}
