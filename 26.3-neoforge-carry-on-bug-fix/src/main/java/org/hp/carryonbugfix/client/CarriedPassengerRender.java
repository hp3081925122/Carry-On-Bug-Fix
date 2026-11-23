package org.hp.carryonbugfix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.hp.carryonbugfix.CarryOnBugFix;
import org.hp.carryonbugfix.CarryOnBugFixConfig;
import org.slf4j.Logger;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.carry.CarryOnDataManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 客户端手持实体时递归提取并提交全部乘客渲染状态。
 */
@EventBusSubscriber(modid = CarryOnBugFix.MOD_ID, value = Dist.CLIENT)
public final class CarriedPassengerRender {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CACHE_SIZE = 8;
    private static final Map<CompoundTag, Cached> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CompoundTag, Cached> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    private static Level cacheLevel;

    private CarriedPassengerRender() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        CACHE.clear();
        cacheLevel = null;
    }

    public static void submitPassengers(Player player, PoseStack pose, int light, SubmitNodeCollector collector) {
        if (player == null || !CarryOnBugFixConfig.PASSENGER_STACK_ENABLED.get()) {
            return;
        }
        Level level = player.level();
        if (cacheLevel != level) {
            clear();
            cacheLevel = level;
        }
        CarryOnData carry = CarryOnDataManager.getCarryData(player);
        if (!carry.isCarrying(CarryOnData.CarryType.ENTITY)) {
            return;
        }
        CompoundTag tag = carry.getNbt().getCompoundOrEmpty("entity");
        if (tag.getListOrEmpty(Entity.TAG_PASSENGERS).isEmpty()) {
            return;
        }
        Cached cached = CACHE.get(tag);
        if (cached == null) {
            cached = create(tag, level);
            CACHE.put(tag.copy(), cached);
        }
        if (cached.passengers.isEmpty()) {
            return;
        }
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        CameraRenderState camera = new CameraRenderState();
        for (RenderedPassenger rendered : cached.passengers) {
            Entity passenger = rendered.entity;
            resetRotation(passenger);
            if (passenger instanceof LivingEntity living) {
                living.hurtTime = 0;
            }
            try {
                EntityRenderState state = dispatcher.extractEntity(passenger, 0.0F);
                state.nameTag = null;
                state.scoreText = null;
                state.leashStates = null;
                state.shadowPieces.clear();
                state.lightCoords = light;
                dispatcher.submit(state, camera, rendered.offset.x, rendered.offset.y, rendered.offset.z, pose, collector);
            } catch (Exception e) {
                LOGGER.warn("Failed to render carried passenger {}", passenger.getType(), e);
            }
        }
    }

    private static Cached create(CompoundTag rootTag, Level level) {
        // 26.2+ 客户端临时实体默认 ID 为 0，在 startRiding/positionRider 之前分配互不相同的负临时 ID。
        AtomicInteger nextTempId = new AtomicInteger(-1000);
        Entity vehicle = EntityType.loadEntityRecursive(
                rootTag,
                level,
                new EntitySpawnRequest(EntitySpawnReason.BUCKET, false),
                entity -> {
                    entity.setId(nextTempId.getAndDecrement());
                    return entity;
                }
        );
        if (vehicle == null) {
            return new Cached(List.of());
        }
        vehicle.setPos(0.0D, 0.0D, 0.0D);
        vehicle.xOld = 0.0D;
        vehicle.yOld = 0.0D;
        vehicle.zOld = 0.0D;
        vehicle.setYRot(0.0F);
        vehicle.yRotO = 0.0F;
        List<RenderedPassenger> rendered = new ArrayList<>();
        collectPassengers(vehicle, rendered);
        LOGGER.debug("Cached carried passenger render vehicle={} count={} lastTempId={}", vehicle.getType(), rendered.size(), nextTempId.get() + 1);
        return new Cached(List.copyOf(rendered));
    }

    private static void collectPassengers(Entity vehicle, List<RenderedPassenger> rendered) {
        for (Entity passenger : vehicle.getPassengers()) {
            vehicle.positionRider(passenger);
            passenger.xOld = passenger.getX();
            passenger.yOld = passenger.getY();
            passenger.zOld = passenger.getZ();
            rendered.add(new RenderedPassenger(passenger, passenger.position()));
            collectPassengers(passenger, rendered);
        }
    }

    private static void resetRotation(Entity passenger) {
        passenger.setYRot(0.0F);
        passenger.yRotO = 0.0F;
        passenger.setXRot(0.0F);
        passenger.xRotO = 0.0F;
        passenger.setYHeadRot(0.0F);
        if (passenger instanceof LivingEntity living) {
            living.yHeadRotO = 0.0F;
            living.yBodyRot = 0.0F;
            living.yBodyRotO = 0.0F;
        }
    }

    private record Cached(List<RenderedPassenger> passengers) {
    }

    private record RenderedPassenger(Entity entity, Vec3 offset) {
    }
}
