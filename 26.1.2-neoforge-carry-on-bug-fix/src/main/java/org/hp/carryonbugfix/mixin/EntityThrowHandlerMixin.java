package org.hp.carryonbugfix.mixin;

import com.lirxowo.carryonextend.handler.EntityThrowHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.hp.carryonbugfix.PassengerStackValidator;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tschipp.carryon.common.carry.CarryOnData;

@Mixin(value = EntityThrowHandler.class, remap = false)
public abstract class EntityThrowHandlerMixin {
    @Unique
    private static final Logger carryOnBugFix$LOGGER = LogUtils.getLogger();

    @Redirect(method = "throwCarriedEntityWithPower", at = @At(value = "INVOKE", target = "Ltschipp/carryon/common/carry/CarryOnData;getEntity(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;"))
    private static Entity carryOnBugFix$loadStack(CarryOnData carry, Level level) {
        Entity entity = PassengerStackValidator.loadCarried(carry, level);
        if (entity != null && entity.isVehicle()) {
            carryOnBugFix$LOGGER.debug("Carry On Extend throw loaded passenger stack root={} count={}", entity.getType(), entity.getSelfAndPassengers().count());
        }
        return entity;
    }

    @Redirect(method = "throwCarriedEntityWithPower", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean carryOnBugFix$addStack(Level level, Entity entity) {
        return PassengerStackValidator.addWithPassengers(level, entity);
    }
}
