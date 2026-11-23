package org.hp.carryonbugfix;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import org.hp.carryonbugfix.mixin.CarryOnDataAccessor;
import org.hp.carryonbugfix.mixin.JukeboxBlockEntityAccessor;
import org.hp.carryonbugfix.network.CarryOnBugFixNetwork;
import org.hp.carryonbugfix.network.JukeboxHandoffPacket;
import org.slf4j.Logger;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.carry.CarryOnData.CarryType;
import tschipp.carryon.common.carry.CarryOnDataManager;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 1.20.1 Forge 服务端便携唱片机管理器：记录 RecordItem 播放进度、周期生成音符粒子，并在放下时无缝恢复计时与交接声道。
 */
public final class PortableJukeboxHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicLong SESSION_COUNTER = new AtomicLong(1L);

    public static final String TAG_ROOT = "carryOnBugFixJukebox";
    public static final String KEY_PLAYING = "playing";
    public static final String KEY_SESSION_ID = "sessionId";
    public static final String KEY_ORIGIN_X = "originX";
    public static final String KEY_ORIGIN_Y = "originY";
    public static final String KEY_ORIGIN_Z = "originZ";
    public static final String KEY_SONG_ID = "songId";
    public static final String KEY_START_GAME_TIME = "songStartGameTime";
    public static final String KEY_LENGTH_TICKS = "lengthTicks";
    public static final String KEY_DIMENSION = "dimension";

    private PortableJukeboxHandler() {
    }

    public static void onAfterSetBlock(ServerPlayer player, BlockPos originPos, BlockState state, @Nullable BlockEntity blockEntity, CarryOnData carry) {
        if (!CarryOnBugFixConfig.PORTABLE_JUKEBOX_ENABLED.get()) {
            return;
        }
        if (!(blockEntity instanceof JukeboxBlockEntity jukebox) || !jukebox.isRecordPlaying()) {
            return;
        }
        Item discItem = jukebox.getFirstItem().getItem();
        if (!(discItem instanceof RecordItem recordItem)) {
            return;
        }

        JukeboxBlockEntityAccessor accessor = (JukeboxBlockEntityAccessor) jukebox;
        long elapsed = Math.max(0L, accessor.carryOnBugFix$getTickCount() - accessor.carryOnBugFix$getRecordStartedTick());
        long lengthTicks = recordItem.getLengthInTicks() + 20L;
        if (elapsed >= lengthTicks) {
            return;
        }

        ResourceLocation discId = BuiltInRegistries.ITEM.getKey(recordItem);
        long sessionId = SESSION_COUNTER.getAndIncrement();
        long startGameTime = player.level().getGameTime() - elapsed;

        CompoundTag info = new CompoundTag();
        info.putBoolean(KEY_PLAYING, true);
        info.putLong(KEY_SESSION_ID, sessionId);
        info.putInt(KEY_ORIGIN_X, originPos.getX());
        info.putInt(KEY_ORIGIN_Y, originPos.getY());
        info.putInt(KEY_ORIGIN_Z, originPos.getZ());
        info.putString(KEY_SONG_ID, discId.toString());
        info.putLong(KEY_START_GAME_TIME, startGameTime);
        info.putLong(KEY_LENGTH_TICKS, lengthTicks);
        info.putString(KEY_DIMENSION, player.level().dimension().location().toString());

        CompoundTag internal = ((CarryOnDataAccessor) (Object) carry).carryOnBugFix$getInternalNbt();
        internal.put(TAG_ROOT, info);
        LOGGER.debug("Captured playing jukebox disc={} elapsed={} session={} at {}", discId, elapsed, sessionId, originPos);
    }

    public static void onCarryTick(ServerPlayer player) {
        if (!CarryOnBugFixConfig.PORTABLE_JUKEBOX_ENABLED.get()) {
            return;
        }
        CarryOnData carry = CarryOnDataManager.getCarryData(player);
        if (!carry.isCarrying(CarryType.BLOCK)) {
            return;
        }
        CompoundTag internal = ((CarryOnDataAccessor) (Object) carry).carryOnBugFix$getInternalNbt();
        if (!internal.contains(TAG_ROOT, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag info = internal.getCompound(TAG_ROOT);
        if (!info.getBoolean(KEY_PLAYING)) {
            return;
        }

        Level level = player.level();
        long startGameTime = info.getLong(KEY_START_GAME_TIME);
        long lengthTicks = info.getLong(KEY_LENGTH_TICKS);
        long elapsed = Math.max(0L, level.getGameTime() - startGameTime);

        if (elapsed >= lengthTicks) {
            info.putBoolean(KEY_PLAYING, false);
            if (internal.contains("tile", Tag.TAG_COMPOUND)) {
                CompoundTag tile = internal.getCompound("tile");
                tile.putBoolean("IsPlaying", false);
                tile.putLong("RecordStartTick", 0L);
                tile.putLong("TickCount", lengthTicks);
            }
            level.gameEvent(GameEvent.JUKEBOX_STOP_PLAY, player.blockPosition(), GameEvent.Context.of(player));
            CarryOnDataManager.setCarryData(player, new CarryOnData(carry.getNbt().copy()));
            return;
        }

        String currentDim = level.dimension().location().toString();
        if (!currentDim.equals(info.getString(KEY_DIMENSION))) {
            info.putString(KEY_DIMENSION, currentDim);
            CarryOnDataManager.setCarryData(player, new CarryOnData(carry.getNbt().copy()));
        }

        if (internal.contains("tile", Tag.TAG_COMPOUND)) {
            CompoundTag tile = internal.getCompound("tile");
            tile.putBoolean("IsPlaying", true);
            tile.putLong("RecordStartTick", 0L);
            tile.putLong("TickCount", elapsed);
        }

        if (elapsed % 20L == 0L && CarryOnBugFixConfig.PORTABLE_JUKEBOX_EMIT_PARTICLES.get() && level instanceof ServerLevel serverLevel) {
            level.gameEvent(GameEvent.JUKEBOX_PLAY, player.blockPosition(), GameEvent.Context.of(player));
            double px = player.getX();
            double py = player.getY() + player.getBbHeight() + 0.35D;
            double pz = player.getZ();
            float noteColor = (float) level.getRandom().nextInt(4) / 24.0F;
            serverLevel.sendParticles(ParticleTypes.NOTE, px, py, pz, 0, noteColor, 0.0D, 0.0D, 1.0D);
            for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, new AABB(player.blockPosition()).inflate(3.0D))) {
                nearby.setRecordPlayingNearby(player.blockPosition(), true);
            }
        }
    }

    @Nullable
    public static PendingHandoff preparePlacement(ServerPlayer player, BlockPos placePos, CarryOnData carry) {
        if (!CarryOnBugFixConfig.PORTABLE_JUKEBOX_ENABLED.get()) {
            return null;
        }
        CompoundTag internal = ((CarryOnDataAccessor) (Object) carry).carryOnBugFix$getInternalNbt();
        if (!internal.contains(TAG_ROOT, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag info = internal.getCompound(TAG_ROOT);
        if (!info.getBoolean(KEY_PLAYING)) {
            if (internal.contains("tile", Tag.TAG_COMPOUND)) {
                internal.getCompound("tile").putBoolean("IsPlaying", false);
            }
            return null;
        }

        long elapsed = Math.max(0L, player.level().getGameTime() - info.getLong(KEY_START_GAME_TIME));
        long lengthTicks = info.getLong(KEY_LENGTH_TICKS);
        if (elapsed >= lengthTicks) {
            info.putBoolean(KEY_PLAYING, false);
            if (internal.contains("tile", Tag.TAG_COMPOUND)) {
                internal.getCompound("tile").putBoolean("IsPlaying", false);
            }
            return null;
        }

        if (internal.contains("tile", Tag.TAG_COMPOUND)) {
            CompoundTag tile = internal.getCompound("tile");
            tile.putBoolean("IsPlaying", true);
            tile.putLong("RecordStartTick", 0L);
            tile.putLong("TickCount", elapsed);
        }
        return new PendingHandoff(
                player.getId(),
                info.getLong(KEY_SESSION_ID),
                placePos.immutable(),
                info.getString(KEY_SONG_ID),
                elapsed
        );
    }

    public static void sendHandoff(ServerPlayer player, PendingHandoff handoff) {
        if (handoff == null || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        CarryOnBugFixNetwork.sendToTrackingChunk(
                serverLevel,
                handoff.pos(),
                new JukeboxHandoffPacket(
                        handoff.carrierId(),
                        handoff.sessionId(),
                        handoff.pos(),
                        handoff.songId(),
                        handoff.elapsedTicks()
                )
        );
    }

    public record PendingHandoff(int carrierId, long sessionId, BlockPos pos, String songId, long elapsedTicks) {
    }
}
