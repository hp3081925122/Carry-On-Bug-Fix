package org.hp.carryonbugfix;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import org.hp.carryonbugfix.mixin.CarryOnDataAccessor;
import org.hp.carryonbugfix.network.JukeboxHandoffPayload;
import org.slf4j.Logger;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.carry.CarryOnData.CarryType;
import tschipp.carryon.common.carry.CarryOnDataManager;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 服务端便携唱片机管理器：在搬运播放中的唱片机时保存/推进播放进度、生成音符粒子，并在放下时无缝交接回方块。
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

    /**
     * 在 Carry On 将方块写入 CarryOnData 后记录播放中唱片机的实时状态。
     */
    public static void onAfterSetBlock(ServerPlayer player, BlockPos originPos, BlockState state, @Nullable BlockEntity blockEntity, CarryOnData carry) {
        if (!CarryOnBugFixConfig.PORTABLE_JUKEBOX_ENABLED.get()) {
            return;
        }
        if (!(blockEntity instanceof JukeboxBlockEntity jukebox)) {
            return;
        }
        JukeboxSongPlayer songPlayer = jukebox.getSongPlayer();
        if (!songPlayer.isPlaying() || songPlayer.getSong() == null) {
            return;
        }
        ItemStack record = jukebox.getTheItem();
        Optional<Holder<JukeboxSong>> songHolderOpt = JukeboxSong.fromStack(player.level().registryAccess(), record);
        if (songHolderOpt.isEmpty()) {
            return;
        }
        Holder<JukeboxSong> songHolder = songHolderOpt.get();
        ResourceLocation songId = songHolder.unwrapKey().map(key -> key.location()).orElse(null);
        if (songId == null) {
            return;
        }

        long elapsed = Math.max(0L, songPlayer.getTicksSinceSongStarted());
        long lengthTicks = songHolder.value().lengthInTicks() + 20L;
        if (elapsed >= lengthTicks) {
            return;
        }

        long sessionId = SESSION_COUNTER.getAndIncrement();
        long startGameTime = player.level().getGameTime() - elapsed;

        CompoundTag info = new CompoundTag();
        info.putBoolean(KEY_PLAYING, true);
        info.putLong(KEY_SESSION_ID, sessionId);
        info.putInt(KEY_ORIGIN_X, originPos.getX());
        info.putInt(KEY_ORIGIN_Y, originPos.getY());
        info.putInt(KEY_ORIGIN_Z, originPos.getZ());
        info.putString(KEY_SONG_ID, songId.toString());
        info.putLong(KEY_START_GAME_TIME, startGameTime);
        info.putLong(KEY_LENGTH_TICKS, lengthTicks);
        info.putString(KEY_DIMENSION, player.level().dimension().location().toString());

        CompoundTag internal = ((CarryOnDataAccessor) (Object) carry).carryOnBugFix$getInternalNbt();
        internal.put(TAG_ROOT, info);
        LOGGER.debug("Captured playing jukebox song={} elapsed={} session={} at {}", songId, elapsed, sessionId, originPos);
    }

    /**
     * 每服务端 tick 推进手持播放中唱片机的状态、发射音符粒子与通知周围生物。
     */
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

        // 歌曲自然播完：清理播放标记并同步给客户端停歌
        if (elapsed >= lengthTicks) {
            info.putBoolean(KEY_PLAYING, false);
            if (internal.contains("tile", Tag.TAG_COMPOUND)) {
                internal.getCompound("tile").remove(JukeboxBlockEntity.TICKS_SINCE_SONG_STARTED_TAG_ID);
            }
            level.gameEvent(GameEvent.JUKEBOX_STOP_PLAY, player.blockPosition(), GameEvent.Context.of(player));
            CarryOnDataManager.setCarryData(player, carry.clone());
            LOGGER.debug("Portable jukebox finished playing for player {}", player.getScoreboardName());
            return;
        }

        // 跨维度同步更新维度标识，让客户端在目标维度续播
        String currentDim = level.dimension().location().toString();
        if (!currentDim.equals(info.getString(KEY_DIMENSION))) {
            info.putString(KEY_DIMENSION, currentDim);
            CarryOnDataManager.setCarryData(player, carry.clone());
        }

        // 保持 tile NBT 内进度与真实进度同步，便于保存退出或死亡克隆时直接保留
        if (internal.contains("tile", Tag.TAG_COMPOUND)) {
            internal.getCompound("tile").putLong(JukeboxBlockEntity.TICKS_SINCE_SONG_STARTED_TAG_ID, elapsed);
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

    /**
     * 在唱片机方块实体重新加载前写入精确已播放 tick，并生成待广播的交接信息。
     */
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
                internal.getCompound("tile").remove(JukeboxBlockEntity.TICKS_SINCE_SONG_STARTED_TAG_ID);
            }
            return null;
        }

        long elapsed = Math.max(0L, player.level().getGameTime() - info.getLong(KEY_START_GAME_TIME));
        long lengthTicks = info.getLong(KEY_LENGTH_TICKS);
        if (elapsed >= lengthTicks) {
            info.putBoolean(KEY_PLAYING, false);
            if (internal.contains("tile", Tag.TAG_COMPOUND)) {
                internal.getCompound("tile").remove(JukeboxBlockEntity.TICKS_SINCE_SONG_STARTED_TAG_ID);
            }
            return null;
        }

        if (internal.contains("tile", Tag.TAG_COMPOUND)) {
            internal.getCompound("tile").putLong(JukeboxBlockEntity.TICKS_SINCE_SONG_STARTED_TAG_ID, elapsed);
        }
        return new PendingHandoff(
                player.getId(),
                info.getLong(KEY_SESSION_ID),
                placePos.immutable(),
                info.getString(KEY_SONG_ID),
                elapsed
        );
    }

    /**
     * 方块实际落地且准备清空搬运数据前，通知追踪该区块的所有客户端将活体声道交接给方块坐标。
     */
    public static void sendHandoff(ServerPlayer player, PendingHandoff handoff) {
        if (handoff == null || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        JukeboxHandoffPayload payload = new JukeboxHandoffPayload(
                handoff.carrierId(),
                handoff.sessionId(),
                handoff.pos(),
                handoff.songId(),
                handoff.elapsedTicks()
        );
        PacketDistributor.sendToPlayersTrackingChunk(serverLevel, new ChunkPos(handoff.pos()), payload);
        LOGGER.debug("Sent portable jukebox handoff session={} song={} elapsed={} -> {}", handoff.sessionId(), handoff.songId(), handoff.elapsedTicks(), handoff.pos());
    }

    public record PendingHandoff(int carrierId, long sessionId, BlockPos pos, String songId, long elapsedTicks) {
    }
}
