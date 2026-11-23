package org.hp.carryonbugfix;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.level.Level;
import org.hp.carryonbugfix.mixin.EntityAccessor;
import org.slf4j.Logger;
import tschipp.carryon.Constants;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.carry.CarryOnData.CarryType;
import tschipp.carryon.common.carry.CarryOnDataManager;
import tschipp.carryon.common.scripting.CarryOnScript.ScriptEffects;
import tschipp.carryon.networking.clientbound.ClientboundStartRidingOtherPlayerPacket;
import tschipp.carryon.platform.Services;

/**
 * 处理抱着生物或玩家时直接右键放入船或载客矿车的逻辑。
 */
public final class VehicleBoardingHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private VehicleBoardingHandler() {
    }

    /**
     * 尝试将玩家正搬运的实体放入目标载具。
     * 返回 true 表示本次右键交互已被接管（成功放入或明确拦截拒绝），应取消后续堆叠与玩家自行上车。
     */
    public static boolean tryBoardVehicle(ServerPlayer player, Entity clickedTarget) {
        if (!CarryOnBugFixConfig.VEHICLE_BOARDING_ENABLED.get()) {
            return false;
        }
        CarryOnData carry = CarryOnDataManager.getCarryData(player);
        if (!carry.isCarrying(CarryType.ENTITY) && !carry.isCarrying(CarryType.PLAYER)) {
            return false;
        }
        if (CarryOnBugFixConfig.VEHICLE_BOARDING_REQUIRE_SHIFT.get() && !player.isShiftKeyDown()) {
            return false;
        }

        Entity vehicle = clickedTarget.getRootVehicle();
        if (!isSupportedVehicle(vehicle)) {
            return false;
        }

        // 防止与拾取发生在同一 tick 导致误塞回
        if (player.tickCount == carry.getTick()) {
            return true;
        }

        Level level = player.level();
        double maxDist = Constants.COMMON_CONFIG.settings.maxDistance + 2.0D;
        if (player.distanceToSqr(vehicle) > maxDist * maxDist) {
            return false;
        }

        boolean carryingPlayer = carry.isCarrying(CarryType.PLAYER);
        Entity held = carryingPlayer
                ? player.getFirstPassenger()
                : PassengerStackValidator.loadCarried(carry, level);
        if (held == null || held == vehicle) {
            return false;
        }

        if (!canEntityBoardVehicle(held, vehicle, carryingPlayer)) {
            playFailSound(level, player);
            return true;
        }

        if (carryingPlayer) {
            player.ejectPassengers();
            if (level instanceof ServerLevel serverLevel) {
                Services.PLATFORM.sendPacketToAllPlayers(
                        Constants.PACKET_ID_START_RIDING_OTHER,
                        new ClientboundStartRidingOtherPlayerPacket(player.getId(), held.getId(), false),
                        serverLevel
                );
            }
            held.teleportTo(vehicle.getX(), vehicle.getY(), vehicle.getZ());
            if (!held.startRiding(vehicle, false, true)) {
                playFailSound(level, player);
                return true;
            }
        } else {
            held.setPos(vehicle.getX(), vehicle.getY(), vehicle.getZ());
            if (!PassengerStackValidator.addWithPassengers(level, held)) {
                playFailSound(level, player);
                return true;
            }
            if (!held.startRiding(vehicle, false, true)) {
                // 上车失败时完整回滚已生成的实体树，避免吞怪或刷怪
                held.getSelfAndPassengers().forEach(Entity::discard);
                LOGGER.debug("Vehicle boarding failed for {} into {}, rolled back spawned entity", held.getType(), vehicle.getType());
                playFailSound(level, player);
                return true;
            }
            if (held instanceof Mob mob) {
                mob.playAmbientSound();
            }
        }

        if (carry.getActiveScript().isPresent()) {
            ScriptEffects effects = carry.getActiveScript().get().scriptEffects();
            String cmd = effects.commandPlace();
            if (!cmd.isEmpty() && level.getServer() != null) {
                level.getServer().getCommands().performPrefixedCommand(
                        level.getServer().createCommandSourceStack(),
                        "/execute as " + player.getGameProfile().name() + " run " + cmd
                );
            }
        }

        carry.clear();
        CarryOnDataManager.setCarryData(player, carry);
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, vehicle.getX(), vehicle.getY(), vehicle.getZ(), SoundEvents.HORSE_SADDLE.value(), SoundSource.PLAYERS, 0.5F, 1.35F);
        if (!player.isCreative() || Constants.COMMON_CONFIG.settings.slownessInCreative) {
            player.removeEffect(MobEffects.SLOWNESS);
        }
        LOGGER.debug("Boarded carried entity {} into vehicle {}", held.getType(), vehicle.getType());
        return true;
    }

    private static boolean isSupportedVehicle(Entity entity) {
        if (entity instanceof AbstractBoat) {
            return CarryOnBugFixConfig.VEHICLE_BOARDING_ALLOW_BOATS.get();
        }
        if (entity instanceof Minecart) {
            return CarryOnBugFixConfig.VEHICLE_BOARDING_ALLOW_MINECARTS.get();
        }
        return false;
    }

    private static boolean canEntityBoardVehicle(Entity held, Entity vehicle, boolean carryingPlayer) {
        // 禁止将船或矿车套娃塞进另一个船或矿车
        if (held instanceof AbstractBoat || held instanceof AbstractMinecart) {
            return false;
        }
        if (!(held instanceof LivingEntity)) {
            return false;
        }
        if (!PassengerStackValidator.canAttach(held, vehicle)) {
            return false;
        }
        if (!((EntityAccessor) vehicle).carryOnBugFix$canAddPassenger(held)) {
            return false;
        }
        if (vehicle instanceof AbstractBoat boat) {
            if (!carryingPlayer && held instanceof WaterAnimal) {
                return false;
            }
            return boat.hasEnoughSpaceFor(held);
        }
        if (vehicle instanceof Minecart minecart) {
            return minecart.getPassengers().isEmpty();
        }
        return false;
    }

    private static void playFailSound(Level level, ServerPlayer player) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LAVA_POP, SoundSource.PLAYERS, 0.5F, 0.5F);
    }
}
