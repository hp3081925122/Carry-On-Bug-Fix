package org.hp.carryonbugfix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.hp.carryonbugfix.CarryOnBugFixConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.hp.carryonbugfix.CarryOnBugFix;
import org.slf4j.Logger;
import tschipp.carryon.common.carry.CarryOnData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private static Entity lastRoot;
    private static Cached lastCached;
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
        lastRoot = null;
        lastCached = null;
    }

    public static Entity track(CarryOnData carry, Level level) {
        Entity root = carry.getEntity(level);
        lastRoot = null;
        lastCached = null;
        if (cacheLevel != level) {
            clear();
            cacheLevel = level;
        }
        if (!CarryOnBugFixConfig.PASSENGER_STACK_ENABLED.get()) {
            return root;
        }
        CompoundTag tag = carry.getNbt().getCompoundOrEmpty("entity");
        if (tag.getListOrEmpty(Entity.TAG_PASSENGERS).isEmpty()) {
            return root;
        }
        Cached cached = CACHE.get(tag);
        if (cached == null) {
            cached = create(tag, level);
            CACHE.put(tag.copy(), cached);
        }
        if (!cached.passengers.isEmpty()) {
            lastRoot = root;
            lastCached = cached;
        }
        return root;
    }

    public static void submit(EntityRenderDispatcher dispatcher, EntityRenderState rootState, CameraRenderState camera, double x, double y, double z, PoseStack pose, SubmitNodeCollector collector) {
        dispatcher.submit(rootState, camera, x, y, z, pose, collector);
        Cached cached = lastCached;
        if (lastRoot == null || cached == null) {
            return;
        }
        lastRoot = null;
        lastCached = null;
        for (RenderedPassenger rendered : cached.passengers) {
            Entity passenger = rendered.entity;
            resetRotation(passenger);
            if (passenger instanceof LivingEntity living) {
                living.hurtTime = 0;
            }
            try {
                EntityRenderState state = dispatcher.extractEntity(passenger, 0.0F);
                state.shadowPieces.clear();
                state.lightCoords = rootState.lightCoords;
                dispatcher.submit(state, camera, x + rendered.offset.x, y + rendered.offset.y, z + rendered.offset.z, pose, collector);
            } catch (Exception e) {
                LOGGER.debug("Failed to render carried passenger {}", passenger.getType(), e);
            }
        }
    }

    private static Cached create(CompoundTag rootTag, Level level) {
        Entity vehicle = EntityType.loadEntityRecursive(rootTag, level, EntitySpawnReason.BUCKET, EntityProcessor.NOP);
        if (vehicle == null) {
            return new Cached(List.of());
        }
        vehicle.setPos(0.0D, 0.0D, 0.0D);
        vehicle.setYRot(0.0F);
        vehicle.yRotO = 0.0F;
        List<RenderedPassenger> rendered = new ArrayList<>();
        collectPassengers(vehicle, rendered);
        LOGGER.debug("Cached carried passenger render vehicle={} count={}", vehicle.getType(), rendered.size());
        return new Cached(List.copyOf(rendered));
    }

    private static void collectPassengers(Entity vehicle, List<RenderedPassenger> rendered) {
        for (Entity passenger : vehicle.getPassengers()) {
            vehicle.positionRider(passenger);
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
