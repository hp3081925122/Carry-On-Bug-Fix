package org.hp.carryonbugfix.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.hp.carryonbugfix.CarryOnBugFixConfig;
import org.hp.carryonbugfix.PassengerStackValidator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tschipp.carryon.common.carry.PickupHandler;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.Function;

@Mixin(value = PickupHandler.class, remap = false)
public abstract class PickupHandlerMixin {
    @Unique
    private static final Logger carryOnBugFix$LOGGER = LogUtils.getLogger();
    @Unique
    private static final ThreadLocal<Boolean> carryOnBugFix$preservePassengers = new ThreadLocal<>();

    @Inject(method = "tryPickupEntity", at = @At("HEAD"), cancellable = true)
    private static void carryOnBugFix$validateStack(ServerPlayer player, Entity entity, Function<Entity, Boolean> callback, CallbackInfoReturnable<Boolean> cir) {
        carryOnBugFix$preservePassengers.remove();
        if (!CarryOnBugFixConfig.PASSENGER_STACK_ENABLED.get() || !entity.isVehicle()) {
            return;
        }
        carryOnBugFix$LOGGER.debug("Validating passenger stack pickup target={} passengers={} {}", entity.getType(), entity.getPassengers().size(), PassengerStackValidator.describe(player, entity));
        if (!PassengerStackValidator.shouldPreserve(player, entity)) {
            carryOnBugFix$LOGGER.debug("Rejected passenger stack pickup target={}", entity.getType());
            cir.setReturnValue(false);
            return;
        }
        carryOnBugFix$LOGGER.debug("Preserving passenger stack pickup target={}", entity.getType());
        carryOnBugFix$preservePassengers.set(Boolean.TRUE);
    }

    @Inject(method = "tryPickupEntity", at = @At("RETURN"))
    private static void carryOnBugFix$clearStack(ServerPlayer player, Entity entity, Function<Entity, Boolean> callback, CallbackInfoReturnable<Boolean> cir) {
        carryOnBugFix$preservePassengers.remove();
    }

    @Redirect(method = "tryPickupEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;ejectPassengers()V", remap = true))
    private static void carryOnBugFix$preservePassengers(Entity entity) {
        if (!Boolean.TRUE.equals(carryOnBugFix$preservePassengers.get())) {
            entity.ejectPassengers();
        } else {
            carryOnBugFix$LOGGER.debug("Skipped ejectPassengers for preserved entity={}", entity.getType());
        }
    }

    @Redirect(method = "tryPickupEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", remap = true))
    private static void carryOnBugFix$removeStack(Entity entity, Entity.RemovalReason reason) {
        if (!Boolean.TRUE.equals(carryOnBugFix$preservePassengers.get())) {
            entity.remove(reason);
            return;
        }
        List<Entity> stack = entity.getPassengersAndSelf().toList();
        carryOnBugFix$LOGGER.debug("Removing preserved passenger stack root={} count={}", entity.getType(), stack.size());
        for (Entity member : stack) {
            member.remove(reason);
        }
    }

    @Redirect(method = "tryPickupEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;stopRiding()V", remap = true))
    private static void carryOnBugFix$preserveVehicle(Entity entity) {
        if (!Boolean.TRUE.equals(carryOnBugFix$preservePassengers.get())) {
            entity.stopRiding();
        } else {
            carryOnBugFix$LOGGER.debug("Skipped stopRiding for preserved entity={}", entity.getType());
        }
    }
}
