package org.hp.carryonbugfix.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.hp.carryonbugfix.CarryOnBugFixConfig;
import org.hp.carryonbugfix.PassengerStackValidator;
import org.hp.carryonbugfix.PortableJukeboxHandler;
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
 * 扩展 Carry On 放置与堆叠流程：保留整组乘客树恢复，并在放下播放中唱片机时恢复播放进度与发送客户端声道交接包。
 */
@Mixin(value = PlacementHandler.class, remap = false)
public abstract class PlacementHandlerMixin {
    @Unique
    private static final ThreadLocal<ServerPlayer> carryOnBugFix$blockPlacePlayer = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<PortableJukeboxHandler.PendingHandoff> carryOnBugFix$pendingJukeboxHandoff = new ThreadLocal<>();

    @Inject(method = "tryPlaceBlock", at = @At("HEAD"))
    private static void carryOnBugFix$beginTryPlaceBlock(
            ServerPlayer player,
            BlockPos pos,
            Direction facing,
            BiFunction<BlockPos, BlockState, Boolean> placementCallback,
            CallbackInfoReturnable<Boolean> cir
    ) {
        carryOnBugFix$blockPlacePlayer.set(player);
        carryOnBugFix$pendingJukeboxHandoff.remove();
    }

    @Inject(method = "placeCarried", at = @At("HEAD"))
    private static void carryOnBugFix$beginPlaceCarried(ServerPlayer player, CallbackInfo ci) {
        carryOnBugFix$blockPlacePlayer.set(player);
        carryOnBugFix$pendingJukeboxHandoff.remove();
    }

    @Redirect(
            method = {"tryPlaceBlock", "placeCarried"},
            at = @At(
                    value = "INVOKE",
                    target = "Ltschipp/carryon/common/carry/CarryOnData;getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/level/block/entity/BlockEntity;"
            )
    )
    private static BlockEntity carryOnBugFix$prepareJukeboxBlockEntity(CarryOnData carry, BlockPos pos, HolderLookup.Provider lookup) {
        ServerPlayer player = carryOnBugFix$blockPlacePlayer.get();
        if (player != null) {
            PortableJukeboxHandler.PendingHandoff handoff = PortableJukeboxHandler.preparePlacement(player, pos, carry);
            if (handoff != null) {
                carryOnBugFix$pendingJukeboxHandoff.set(handoff);
            }
        }
        return carry.getBlockEntity(pos, lookup);
    }

    @Inject(
            method = "tryPlaceBlock",
            at = @At(value = "INVOKE", target = "Ltschipp/carryon/common/carry/CarryOnData;clear()V")
    )
    private static void carryOnBugFix$dispatchHandoffOnPlace(
            ServerPlayer player,
            BlockPos pos,
            Direction facing,
            BiFunction<BlockPos, BlockState, Boolean> placementCallback,
            CallbackInfoReturnable<Boolean> cir
    ) {
        carryOnBugFix$dispatchPendingHandoff();
    }

    @Inject(
            method = "placeCarried",
            at = @At(value = "INVOKE", target = "Ltschipp/carryon/common/carry/CarryOnData;clear()V")
    )
    private static void carryOnBugFix$dispatchHandoffOnDrop(ServerPlayer player, CallbackInfo ci) {
        carryOnBugFix$dispatchPendingHandoff();
    }

    @Inject(method = "tryPlaceBlock", at = @At("RETURN"))
    private static void carryOnBugFix$endTryPlaceBlock(
            ServerPlayer player,
            BlockPos pos,
            Direction facing,
            BiFunction<BlockPos, BlockState, Boolean> placementCallback,
            CallbackInfoReturnable<Boolean> cir
    ) {
        carryOnBugFix$clearPlaceContext();
    }

    @Inject(method = "placeCarried", at = @At("RETURN"))
    private static void carryOnBugFix$endPlaceCarried(ServerPlayer player, CallbackInfo ci) {
        carryOnBugFix$clearPlaceContext();
    }

    @Unique
    private static void carryOnBugFix$dispatchPendingHandoff() {
        ServerPlayer player = carryOnBugFix$blockPlacePlayer.get();
        PortableJukeboxHandler.PendingHandoff handoff = carryOnBugFix$pendingJukeboxHandoff.get();
        if (player != null && handoff != null) {
            PortableJukeboxHandler.sendHandoff(player, handoff);
            carryOnBugFix$pendingJukeboxHandoff.remove();
        }
    }

    @Unique
    private static void carryOnBugFix$clearPlaceContext() {
        carryOnBugFix$blockPlacePlayer.remove();
        carryOnBugFix$pendingJukeboxHandoff.remove();
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
        if (!PassengerStackValidator.canAttach(held, entityClicked.getRootVehicle())) {
            ci.cancel();
        }
    }

    @Redirect(method = {"tryPlaceEntity", "tryStackEntity", "placeCarried"}, at = @At(value = "INVOKE", target = "Ltschipp/carryon/common/carry/CarryOnData;getEntity(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;"))
    private static Entity carryOnBugFix$loadStack(CarryOnData carry, Level level) {
        return PassengerStackValidator.loadCarried(carry, level);
    }

    @Redirect(method = {"tryPlaceEntity", "tryStackEntity", "placeCarried"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean carryOnBugFix$addStack(Level level, Entity entity) {
        return PassengerStackValidator.addWithPassengers(level, entity);
    }
}
