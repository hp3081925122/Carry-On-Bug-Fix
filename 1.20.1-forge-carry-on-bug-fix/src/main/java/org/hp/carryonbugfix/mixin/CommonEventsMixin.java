package org.hp.carryonbugfix.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.hp.carryonbugfix.CarryOnBugFixConfig;
import org.hp.carryonbugfix.PassengerStackValidator;
import org.hp.carryonbugfix.VehicleBoardingHandler;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tschipp.carryon.events.CommonEvents;

/**
 * 扩展 Carry On 实体右键事件：支持抱着生物直接放入船/矿车，以及整组乘客连根抱起。
 */
@Mixin(value = CommonEvents.class, remap = false)
public abstract class CommonEventsMixin {
    @Unique
    private static final Logger carryOnBugFix$LOGGER = LogUtils.getLogger();

    @Inject(method = "onEntityRightClick", at = @At("HEAD"), cancellable = true)
    private static void carryOnBugFix$tryBoardVehicle(PlayerInteractEvent.EntityInteract event, CallbackInfo ci) {
        if (event.isCanceled() || event.getLevel().isClientSide) {
            return;
        }
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (VehicleBoardingHandler.tryBoardVehicle(serverPlayer, event.getTarget())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            ci.cancel();
        }
    }

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
