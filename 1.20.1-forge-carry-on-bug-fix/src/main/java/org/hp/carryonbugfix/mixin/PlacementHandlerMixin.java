package org.hp.carryonbugfix.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.hp.carryonbugfix.CarryOnBugFixConfig;
import org.hp.carryonbugfix.PassengerStackValidator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.carry.CarryOnDataManager;
import tschipp.carryon.common.carry.PlacementHandler;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

@Mixin(value = PlacementHandler.class, remap = false)
public abstract class PlacementHandlerMixin {
    private static final Logger carryOnBugFix$LOGGER = LogUtils.getLogger();
    @Inject(method = "tryStackEntity", at = @At("HEAD"), cancellable = true)
    private static void carryOnBugFix$limitStack(ServerPlayer player, Entity entityClicked, CallbackInfo ci) {
        if (!CarryOnBugFixConfig.PASSENGER_STACK_ENABLED.get()) {
            return;
        }
        CarryOnData carry = CarryOnDataManager.getCarryData(player);
        Entity held = carry.isCarrying(CarryOnData.CarryType.ENTITY) ? PassengerStackValidator.loadCarried(carry, player.level()) : player.getFirstPassenger();
        if (held == null) {
            return;
        }
        boolean allowed = PassengerStackValidator.canAttach(held, entityClicked.getRootVehicle());
        carryOnBugFix$LOGGER.debug("Checking passenger stack attachment held={} target={} allowed={}", held.getType(), entityClicked.getType(), allowed);
        if (!allowed) {
            ci.cancel();
        }
    }

    @Redirect(method = {"tryPlaceEntity", "tryStackEntity", "placeCarried"}, at = @At(value = "INVOKE", target = "Ltschipp/carryon/common/carry/CarryOnData;getEntity(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;"))
    private static Entity carryOnBugFix$loadStack(CarryOnData carry, Level level) {
        return PassengerStackValidator.loadCarried(carry, level);
    }

    @Redirect(method = {"tryPlaceEntity", "tryStackEntity", "placeCarried"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", remap = true))
    private static boolean carryOnBugFix$addStack(Level level, Entity entity) {
        if (!CarryOnBugFixConfig.PASSENGER_STACK_ENABLED.get()) {
            return level.addFreshEntity(entity);
        }
        return PassengerStackValidator.addWithPassengers(level, entity);
    }
}
