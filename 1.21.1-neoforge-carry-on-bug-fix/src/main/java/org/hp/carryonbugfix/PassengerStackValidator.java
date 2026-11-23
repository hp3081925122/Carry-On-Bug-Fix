package org.hp.carryonbugfix;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import tschipp.carryon.Constants;
import tschipp.carryon.common.pickupcondition.PickupCondition;
import tschipp.carryon.common.pickupcondition.PickupConditionHandler;
import tschipp.carryon.common.scripting.CarryOnScript;
import tschipp.carryon.common.scripting.ScriptManager;
import java.util.Optional;
import java.util.UUID;
import tschipp.carryon.utils.SizeHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.config.ListHandler;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class PassengerStackValidator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private PassengerStackValidator() {
    }

    public static boolean shouldPreserve(ServerPlayer player, Entity entity) {
        if (!CarryOnBugFixConfig.PASSENGER_STACK_ENABLED.get() || !entity.isVehicle()) {
            return false;
        }
        return inspect(entity, player, Collections.newSetFromMap(new IdentityHashMap<>())).valid;
    }

    public static boolean canAttach(Entity held, Entity targetRoot) {
        if (!CarryOnBugFixConfig.PASSENGER_STACK_ENABLED.get()) {
            return true;
        }
        Result heldResult = inspect(held, null, Collections.newSetFromMap(new IdentityHashMap<>()));
        Result targetResult = inspect(targetRoot, null, Collections.newSetFromMap(new IdentityHashMap<>()));
        if (!heldResult.valid || !targetResult.valid) {
            return false;
        }
        return heldResult.count + targetResult.count <= CarryOnBugFixConfig.MAX_PASSENGER_ENTITIES.get()
                && heldResult.depth + targetResult.depth <= CarryOnBugFixConfig.MAX_PASSENGER_DEPTH.get();
    }

    public static Entity loadCarried(CarryOnData carry, Level level) {
        if (!CarryOnBugFixConfig.PASSENGER_STACK_ENABLED.get()) {
            return carry.getEntity(level);
        }
        CompoundTag tag = carry.getNbt().getCompound("entity");
        if (!tag.contains("Passengers", Tag.TAG_LIST)) {
            return carry.getEntity(level);
        }
        Entity root = EntityType.loadEntityRecursive(tag, level, e -> e);
        if (root == null) {
            LOGGER.warn("Failed to load carried passenger stack, falling back to single entity");
            return carry.getEntity(level);
        }
        LOGGER.debug("Loaded carried passenger stack root={} count={}", root.getType(), root.getSelfAndPassengers().count());
        return root;
    }

    public static boolean addWithPassengers(Level level, Entity entity) {
        if (!CarryOnBugFixConfig.PASSENGER_STACK_ENABLED.get() || !entity.isVehicle() || !(level instanceof ServerLevel serverLevel)) {
            return level.addFreshEntity(entity);
        }
        entity.getIndirectPassengers().forEach(passenger -> passenger.setPos(entity.position()));
        serverLevel.addFreshEntityWithPassengers(entity);
        LOGGER.debug("Placed passenger stack root={} count={}", entity.getType(), entity.getSelfAndPassengers().count());
        return true;
    }

    public static Entity getRootVehicle(Entity entity) {
        return entity.getRootVehicle();
    }

    private static Result inspect(Entity entity, ServerPlayer pickupPlayer, Set<Entity> visited) {
        return inspect(entity, 1, pickupPlayer, visited);
    }

    private static Result inspect(Entity entity, int depth, ServerPlayer pickupPlayer, Set<Entity> visited) {
        if (entity == null || entity.isRemoved() || !visited.add(entity)) {
            return Result.invalid();
        }
        if (depth > CarryOnBugFixConfig.MAX_PASSENGER_DEPTH.get()) {
            return Result.invalid();
        }
        if (pickupPlayer != null) {
            if (entity instanceof Player) {
                return Result.invalid();
            }
            if (!entity.getType().canSerialize()) {
                LOGGER.debug("Rejected passenger stack member {} because it cannot be saved", entity.getType());
                return Result.invalid();
            }
            if (depth > 1 && !canPickupPassenger(pickupPlayer, entity)) {
                LOGGER.debug("Rejected passenger stack member {} because Carry On pickup rules deny it", entity.getType());
                return Result.invalid();
            }
        }
        int count = 1;
        int maxDepth = depth;
        boolean valid = true;
        for (Entity passenger : entity.getPassengers()) {
            if (!ListHandler.isStackingPermitted(passenger)) {
                valid = false;
            }
            Result child = inspect(passenger, depth + 1, pickupPlayer, visited);
            valid &= child.valid;
            count += child.count;
            maxDepth = Math.max(maxDepth, child.depth);
        }
        if (count > CarryOnBugFixConfig.MAX_PASSENGER_ENTITIES.get()) {
            valid = false;
        }
        return new Result(valid, count, maxDepth);
    }

    private static boolean canPickupPassenger(ServerPlayer player, Entity entity) {
        if (entity.invulnerableTime != 0) {
            return false;
        }
        if (entity instanceof TamableAnimal tame) {
            UUID owner = tame.getOwnerUUID();
            if (owner != null && !owner.equals(player.getGameProfile().getId())) {
                return false;
            }
        }
        Optional<CarryOnScript> script = ScriptManager.inspectEntity(entity);
        if (script.isPresent() && !script.get().fulfillsConditions(player)) {
            return false;
        }
        boolean overrideChecks = script.map(CarryOnScript::overrideChecks).orElse(false);
        if (!ListHandler.isPermitted(entity) && !overrideChecks) {
            if (!(entity instanceof AgeableMob mob) || !Constants.COMMON_CONFIG.settings.allowBabies || mob.getAge() >= 0 && !mob.isBaby()) {
                return false;
            }
        }
        if (!player.isCreative()) {
            if (!overrideChecks && !Constants.COMMON_CONFIG.settings.pickupHostileMobs && entity.getType().getCategory() == MobCategory.MONSTER) {
                return false;
            }
            if (Constants.COMMON_CONFIG.settings.maxEntityHeight < SizeHelper.getRelativeEntityHeight(player, entity) || Constants.COMMON_CONFIG.settings.maxEntityWidth < SizeHelper.getRelativeEntityWidth(player, entity)) {
                return false;
            }
        }
        Optional<PickupCondition> condition = PickupConditionHandler.getPickupCondition(entity);
        return condition.isEmpty() || condition.get().isFulfilled(player);
    }

    private record Result(boolean valid, int count, int depth) {
        private static Result invalid() {
            return new Result(false, 0, 0);
        }
    }
}
