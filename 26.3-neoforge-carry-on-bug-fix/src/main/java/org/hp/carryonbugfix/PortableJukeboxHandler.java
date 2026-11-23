package org.hp.carryonbugfix;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
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
import org.hp.carryonbugfix.network.JukeboxPickupPayload;
import org.slf4j.Logger;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.carry.CarryOnData.CarryType;
import tschipp.carryon.common.carry.CarryOnDataManager;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * 服务端便携唱片机管理器：在搬运播放中的唱片机时先发包预认领客户端活体声道、抑制双端 setRemoved 停歌事件、推进播放进度，并在放下时无缝交接声源。
 */
public final class PortableJukeboxHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicLong SESSION_COUNTER = new AtomicLong(1L);
    private static final ThreadLocal<Boolean> SUPPRESS_STOP_EVENT = new ThreadLocal<>();
    private static volatile Predicate<BlockPos> clientStopSuppressor = pos -> false;

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

    public static void setClientStopSuppressor(Predicate<BlockPos> suppressor) {
        clientStopSuppressor = suppressor;
    }

    public static boolean shouldSuppressStopEvent(@Nullable Level level, BlockPos pos) {
        if (level != null && level.isClientSide()) {
            return clientStopSuppressor.test(pos);
        }
        return Boolean.TRUE.equals(SUPPRESS_STOP_EVENT.get());
    }

    public static void clearSuppressStopEvent() {
        SUPPRESS_STOP_EVENT.remove();
    }

    /**
     * 在 Carry On 将方块写入 CarryOnData 后记录播放中唱片机的实时状态，
     * 先于 removeBlock 发出客户端预认领包并标记抑制紧随其后的 setRemoved 停歌广播。
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
        Optional<Holder<JukeboxSong>> songHolderOpt = JukeboxSong.fromStack(record);
        if (songHolderOpt.isEmpty()) {
            return;
        }
        Holder<JukeboxSong> songHolder = songHolderOpt.get();
        Identifier songId = songHolder.unwrapKey().map(key -> key.identifier()).orElse(null);
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
        String dimension = player.level().dimension().identifier().toString();

        CompoundTag info = new CompoundTag();
        info.putBoolean(KEY_PLAYING, true);
        info.putLong(KEY_SESSION_ID, sessionId);
        info.putInt(KEY_ORIGIN_X, originPos.getX());
        info.putInt(KEY_ORIGIN_Y, originPos.getY());
        info.putInt(KEY_ORIGIN_Z, originPos.getZ());
        info.putString(KEY_SONG_ID, songId.toString());
        info.putLong(KEY_START_GAME_TIME, startGameTime);
        info.putLong(KEY_LENGTH_TICKS, lengthTicks);
        info.putString(KEY_DIMENSION, dimension);

        CompoundTag internal = ((CarryOnDataAccessor) (Object) carry).carryOnBugFix$getInternalNbt();
        internal.put(TAG_ROOT, info);
        SUPPRESS_STOP_EVENT.set(Boolean.TRUE);

        // 必须在 level.removeBlock 发送空气方块更新包前发出，保证 TCP 严格先序到达客户端完成活体声道认领
        if (player.level() instanceof ServerLevel serverLevel) {
            ChunkPos chunkPos = new ChunkPos(originPos.getX() >> 4, originPos.getZ() >> 4);
            PacketDistributor.sendToPlayersTrackingChunk(
                    serverLevel,
                    chunkPos,
                    new JukeboxPickupPayload(player.getId(), sessionId, originPos.immutable(), dimension)
            );
        }
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
        Optional<CompoundTag> infoOpt = internal.getCompound(TAG_ROOT);
        if (infoOpt.isEmpty()) {
            return;
        }
        CompoundTag info = infoOpt.get();
        if (!info.getBooleanOr(KEY_PLAYING, false)) {
            return;
        }

        Level level = player.level();
        long startGameTime = info.getLongOr(KEY_START_GAME_TIME, level.getGameTime());
        long lengthTicks = info.getLongOr(KEY_LENGTH_TICKS, 0L);
        long elapsed = Math.max(0L, level.getGameTime() - startGameTime);

        if (elapsed >= lengthTicks) {
            info.putBoolean(KEY_PLAYING, false);
            internal.getCompound("tile").ifPresent(tile -> tile.remove(JukeboxBlockEntity.TICKS_SINCE_SONG_STARTED_TAG_ID));
            level.gameEvent(GameEvent.JUKEBOX_STOP_PLAY, player.blockPosition(), GameEvent.Context.of(player));
            CarryOnDataManager.setCarryData(player, carry.clone());
            LOGGER.debug("Portable jukebox finished playing for player {}", player.getScoreboardName());
            return;
        }

        String currentDim = level.dimension().identifier().toString();
        if (!currentDim.equals(info.getStringOr(KEY_DIMENSION, ""))) {
            info.putString(KEY_DIMENSION, currentDim);
            CarryOnDataManager.setCarryData(player, carry.clone());
        }

        internal.getCompound("tile").ifPresent(tile -> tile.putLong(JukeboxBlockEntity.TICKS_SINCE_SONG_STARTED_TAG_ID, elapsed));

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
        Optional<CompoundTag> infoOpt = internal.getCompound(TAG_ROOT);
        if (infoOpt.isEmpty()) {
            return null;
        }
        CompoundTag info = infoOpt.get();
        if (!info.getBooleanOr(KEY_PLAYING, false)) {
            internal.getCompound("tile").ifPresent(tile -> tile.remove(JukeboxBlockEntity.TICKS_SINCE_SONG_STARTED_TAG_ID));
            return null;
        }

        long elapsed = Math.max(0L, player.level().getGameTime() - info.getLongOr(KEY_START_GAME_TIME, player.level().getGameTime()));
        long lengthTicks = info.getLongOr(KEY_LENGTH_TICKS, 0L);
        if (elapsed >= lengthTicks) {
            info.putBoolean(KEY_PLAYING, false);
            internal.getCompound("tile").ifPresent(tile -> tile.remove(JukeboxBlockEntity.TICKS_SINCE_SONG_STARTED_TAG_ID));
            return null;
        }

        internal.getCompound("tile").ifPresent(tile -> tile.putLong(JukeboxBlockEntity.TICKS_SINCE_SONG_STARTED_TAG_ID, elapsed));
        return new PendingHandoff(
                player.getId(),
                info.getLongOr(KEY_SESSION_ID, 0L),
                placePos.immutable(),
                info.getStringOr(KEY_SONG_ID, ""),
                elapsed
        );
    }

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
        ChunkPos chunkPos = new ChunkPos(handoff.pos().getX() >> 4, handoff.pos().getZ() >> 4);
        PacketDistributor.sendToPlayersTrackingChunk(serverLevel, chunkPos, payload);
        LOGGER.debug("Sent portable jukebox handoff session={} song={} elapsed={} -> {}", handoff.sessionId(), handoff.songId(), handoff.elapsedTicks(), handoff.pos());
    }

    public record PendingHandoff(int carrierId, long sessionId, BlockPos pos, String songId, long elapsedTicks) {
    }
}
