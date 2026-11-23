package org.hp.carryonbugfix.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.carry.CarryOnDataManager;
import tschipp.carryon.common.carry.PlacementHandler;

import java.util.function.BiFunction;

/**
 * 扩展 Carry On 放置流程：还原乘客叠罗汉结构，并在便携唱片机落地时无缝交接声源。
 */
@Mixin(value = PlacementHandler.class, remap = false)
public abstract class PlacementHandlerMixin {
    @Unique
    private static final Logger carryOnBugFix$LOGGER = LogUtils.getLogger();
    @Unique
    private static final ThreadLocal<ServerPlayer> carryOnBugFix$currentPlacer = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<PortableJukeboxHandler.PendingHandoff> carryOnBugFix$pendingHandoff = new ThreadLocal<>();

    @Inject(method = "tryPlaceBlock", at = @At("HEAD"))
    private static void carryOnBugFix$enterTryPlaceBlock(ServerPlayer player, BlockPos pos, Direction facing, BiFunction<BlockPos, BlockState, Boolean> placementCallback, CallbackInfoReturnable<Boolean> cir) {
        carryOnBugFix$beginPlacement(player);
    }

    @Inject(method = "tryPlaceBlock", at = @At("RETURN"))
    private static void carryOnBugFix$exitTryPlaceBlock(ServerPlayer player, BlockPos pos, Direction facing, BiFunction<BlockPos, BlockState, Boolean> placementCallback, CallbackInfoReturnable<Boolean> cir) {
        carryOnBugFix$finishPlacement(player, Boolean.TRUE.equals(cir.getReturnValue()));
    }

    @Inject(method = "placeCarried", at = @At("HEAD"))
    private static void carryOnBugFix$enterPlaceCarried(ServerPlayer player, CallbackInfo ci) {
        carryOnBugFix$beginPlacement(player);
    }

    @Inject(method = "placeCarried", at = @At("RETURN"))
    private static void carryOnBugFix$exitPlaceCarried(ServerPlayer player, CallbackInfo ci) {
        carryOnBugFix$finishPlacement(player, true);
    }

    @Redirect(
            method = {"tryPlaceBlock", "placeCarried"},
            at = @At(
                    value = "INVOKE",
                    target = "Ltschipp/carryon/common/carry/CarryOnData;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"
            )
    )
    private static BlockEntity carryOnBugFix$prepareJukeboxPlacement(CarryOnData carry, BlockPos pos) {
        ServerPlayer player = carryOnBugFix$currentPlacer.get();
        if (player != null) {
            PortableJukeboxHandler.PendingHandoff handoff = PortableJukeboxHandler.preparePlacement(player, pos, carry);
            if (handoff != null) {
                carryOnBugFix$pendingHandoff.set(handoff);
            }
        }
        return carry.getBlockEntity(pos);
    }

    @Unique
    private static void carryOnBugFix$beginPlacement(ServerPlayer player) {
        carryOnBugFix$currentPlacer.set(player);
        carryOnBugFix$pendingHandoff.remove();
    }

    @Unique
    private static void carryOnBugFix$finishPlacement(ServerPlayer player, boolean placed) {
        PortableJukeboxHandler.PendingHandoff handoff = carryOnBugFix$pendingHandoff.get();
        carryOnBugFix$pendingHandoff.remove();
        carryOnBugFix$currentPlacer.remove();
        if (placed && handoff != null) {
            PortableJukeboxHandler.sendHandoff(player, handoff);
        }
    }

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
