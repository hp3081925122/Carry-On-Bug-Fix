package org.hp.carryonbugfix.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.hp.carryonbugfix.CarryOnBugFixConfig;
import org.hp.carryonbugfix.PassengerStackValidator;
import org.hp.carryonbugfix.PortableJukeboxHandler;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.carry.PickupHandler;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 扩展 Carry On 拾取流程：保留整组乘客栈，并在抱起播放中唱片机时捕获实时播放状态。
 */
@Mixin(value = PickupHandler.class, remap = false)
public abstract class PickupHandlerMixin {
    @Unique
    private static final Logger carryOnBugFix$LOGGER = LogUtils.getLogger();
    @Unique
    private static final ThreadLocal<Boolean> carryOnBugFix$preservePassengers = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<ServerPlayer> carryOnBugFix$blockPickupPlayer = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<BlockPos> carryOnBugFix$blockPickupPos = new ThreadLocal<>();

    @Inject(method = "tryPickUpBlock", at = @At("HEAD"))
    private static void carryOnBugFix$beginBlockPickup(
            ServerPlayer player,
            BlockPos pos,
            Level level,
            BiFunction<BlockState, BlockPos, Boolean> pickupCallback,
            CallbackInfoReturnable<Boolean> cir
    ) {
        carryOnBugFix$blockPickupPlayer.set(player);
        carryOnBugFix$blockPickupPos.set(pos);
    }

    @Inject(method = "tryPickUpBlock", at = @At("RETURN"))
    private static void carryOnBugFix$endBlockPickup(
            ServerPlayer player,
            BlockPos pos,
            Level level,
            BiFunction<BlockState, BlockPos, Boolean> pickupCallback,
            CallbackInfoReturnable<Boolean> cir
    ) {
        carryOnBugFix$blockPickupPlayer.remove();
        carryOnBugFix$blockPickupPos.remove();
    }

    @Redirect(
            method = "tryPickUpBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Ltschipp/carryon/common/carry/CarryOnData;setBlock(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"
            )
    )
    private static void carryOnBugFix$capturePlayingJukebox(CarryOnData carry, BlockState state, BlockEntity blockEntity) {
        carry.setBlock(state, blockEntity);
        ServerPlayer player = carryOnBugFix$blockPickupPlayer.get();
        BlockPos pos = carryOnBugFix$blockPickupPos.get();
        if (player != null && pos != null) {
            PortableJukeboxHandler.onAfterSetBlock(player, pos, state, blockEntity, carry);
        }
    }

    @Inject(method = "tryPickupEntity", at = @At("HEAD"), cancellable = true)
    private static void carryOnBugFix$validateStack(ServerPlayer player, Entity entity, Function<Entity, Boolean> callback, CallbackInfoReturnable<Boolean> cir) {
        carryOnBugFix$preservePassengers.remove();
        if (!CarryOnBugFixConfig.PASSENGER_STACK_ENABLED.get() || !entity.isVehicle()) {
            return;
        }
        if (!PassengerStackValidator.shouldPreserve(player, entity)) {
            cir.setReturnValue(false);
            return;
        }
        carryOnBugFix$preservePassengers.set(Boolean.TRUE);
    }

    @Inject(method = "tryPickupEntity", at = @At("RETURN"))
    private static void carryOnBugFix$clearStack(ServerPlayer player, Entity entity, Function<Entity, Boolean> callback, CallbackInfoReturnable<Boolean> cir) {
        carryOnBugFix$preservePassengers.remove();
    }

    @Redirect(method = "tryPickupEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;ejectPassengers()V", remap = false))
    private static void carryOnBugFix$preservePassengers(Entity entity) {
        if (!Boolean.TRUE.equals(carryOnBugFix$preservePassengers.get())) {
            entity.ejectPassengers();
        }
    }

    @Redirect(method = "tryPickupEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", remap = false))
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

    @Redirect(method = "tryPickupEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;stopRiding()V", remap = false))
    private static void carryOnBugFix$preserveVehicle(Entity entity) {
        if (!Boolean.TRUE.equals(carryOnBugFix$preservePassengers.get())) {
            entity.stopRiding();
        }
    }
}
