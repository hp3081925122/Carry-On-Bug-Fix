package org.hp.carryonbugfix.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.carryonbugfix.CarryOnBugFixConfig;
import org.hp.carryonbugfix.PortableJukeboxHandler;
import org.hp.carryonbugfix.mixin.LevelRendererAccessor;
import org.hp.carryonbugfix.mixin.SoundEngineAccessor;
import org.hp.carryonbugfix.mixin.SoundManagerAccessor;
import org.hp.carryonbugfix.network.JukeboxHandoffPayload;
import org.slf4j.Logger;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.carry.CarryOnData.CarryType;
import tschipp.carryon.common.carry.CarryOnDataManager;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 客户端便携唱片机声源管理器：
 * 1. 抱起时优先直接认领原方块坐标正在播放的活体 OpenAL 声道，避免中断或重头播放；
 * 2. 携带期间每 tick 实时更新声道 3D 坐标跟随搬运者，并周期通知附近生物；
 * 3. 放下时收到落地交接包，将活体声道锚定回目标方块坐标并登记到原版播放表，支持正常退盘与破坏停歌。
 */
public final class ClientPortableJukeboxManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Integer, ActiveSession> ACTIVE_SESSIONS = new HashMap<>();
    private static final Set<Long> HANDED_OFF_SESSIONS = new HashSet<>();

    private ClientPortableJukeboxManager() {
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !CarryOnBugFixConfig.PORTABLE_JUKEBOX_ENABLED.get()) {
            if (!ACTIVE_SESSIONS.isEmpty()) {
                clearAll();
            }
            return;
        }

        SoundManager soundManager = mc.getSoundManager();
        Set<Integer> seenCarriers = new HashSet<>();

        for (Player player : level.players()) {
            CarryOnData carry = CarryOnDataManager.getCarryData(player);
            if (!carry.isCarrying(CarryType.BLOCK)) {
                continue;
            }
            CompoundTag nbt = carry.getNbt();
            if (!nbt.contains(PortableJukeboxHandler.TAG_ROOT, Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag info = nbt.getCompound(PortableJukeboxHandler.TAG_ROOT);
            if (!info.getBoolean(PortableJukeboxHandler.KEY_PLAYING)) {
                continue;
            }

            long sessionId = info.getLong(PortableJukeboxHandler.KEY_SESSION_ID);
            if (HANDED_OFF_SESSIONS.contains(sessionId)) {
                continue;
            }
            long startGameTime = info.getLong(PortableJukeboxHandler.KEY_START_GAME_TIME);
            long lengthTicks = info.getLong(PortableJukeboxHandler.KEY_LENGTH_TICKS);
            long elapsed = Math.max(0L, level.getGameTime() - startGameTime);
            if (elapsed >= lengthTicks) {
                continue;
            }

            int entityId = player.getId();
            seenCarriers.add(entityId);
            String dimension = info.getString(PortableJukeboxHandler.KEY_DIMENSION);
            Vec3 carrierPos = new Vec3(player.getX(), player.getY() + 1.0D, player.getZ());

            ActiveSession existing = ACTIVE_SESSIONS.get(entityId);
            if (existing != null && existing.sessionId == sessionId && existing.dimension.equals(dimension)) {
                if (!soundManager.isActive(existing.sound)) {
                    // 声道已停（例如远离后重新走近或音量恢复）且曲目未完时，从当前精确进度补播
                    SoundInstance restarted = startSeekingSound(level, soundManager, info.getString(PortableJukeboxHandler.KEY_SONG_ID), carrierPos, elapsed, entityId, null);
                    if (restarted != null) {
                        existing = new ActiveSession(sessionId, dimension, restarted);
                        ACTIVE_SESSIONS.put(entityId, existing);
                    }
                } else {
                    updateLiveChannelPosition(soundManager, existing.sound, carrierPos);
                }
            } else {
                if (existing != null) {
                    stopSoundInstance(soundManager, existing.sound);
                }
                BlockPos originPos = new BlockPos(
                        info.getInt(PortableJukeboxHandler.KEY_ORIGIN_X),
                        info.getInt(PortableJukeboxHandler.KEY_ORIGIN_Y),
                        info.getInt(PortableJukeboxHandler.KEY_ORIGIN_Z)
                );
                SoundInstance claimed = claimOrStartSound(mc, level, soundManager, originPos, info.getString(PortableJukeboxHandler.KEY_SONG_ID), carrierPos, elapsed, entityId);
                if (claimed != null) {
                    ACTIVE_SESSIONS.put(entityId, new ActiveSession(sessionId, dimension, claimed));
                }
            }

            if (elapsed % 20L == 0L) {
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(player.blockPosition()).inflate(3.0D))) {
                    entity.setRecordPlayingNearby(player.blockPosition(), true);
                }
            }
        }

        Iterator<Map.Entry<Integer, ActiveSession>> it = ACTIVE_SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ActiveSession> entry = it.next();
            if (!seenCarriers.contains(entry.getKey())) {
                stopSoundInstance(soundManager, entry.getValue().sound);
                it.remove();
            }
        }
    }

    /**
     * 收到服务端落地交接包时，将玩家怀中的活体声道无缝锚定到新方块坐标并放回原版播放表。
     */
    public static void handleHandoff(JukeboxHandoffPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !CarryOnBugFixConfig.PORTABLE_JUKEBOX_ENABLED.get()) {
            return;
        }
        SoundManager soundManager = mc.getSoundManager();
        Map<BlockPos, SoundInstance> playingMap = ((LevelRendererAccessor) mc.levelRenderer).carryOnBugFix$getPlayingJukeboxSongs();
        BlockPos newPos = payload.pos().immutable();
        Vec3 blockCenter = Vec3.atCenterOf(newPos);

        // 先停止新坐标可能存在的旧唱片音源
        SoundInstance previousAtPos = playingMap.remove(newPos);
        if (previousAtPos != null) {
            stopSoundInstance(soundManager, previousAtPos);
        }

        if (HANDED_OFF_SESSIONS.size() > 128) {
            HANDED_OFF_SESSIONS.clear();
        }
        HANDED_OFF_SESSIONS.add(payload.sessionId());

        SoundInstance handoffSound = null;
        ActiveSession session = ACTIVE_SESSIONS.get(payload.carrierEntityId());
        if (session != null && session.sessionId == payload.sessionId()) {
            ACTIVE_SESSIONS.remove(payload.carrierEntityId());
            if (soundManager.isActive(session.sound)) {
                handoffSound = session.sound;
                if (handoffSound instanceof SeekingJukeboxSoundInstance seeking) {
                    seeking.anchorToBlock(newPos);
                }
                updateLiveChannelPosition(soundManager, handoffSound, blockCenter);
            }
        }

        if (handoffSound == null) {
            handoffSound = startSeekingSound(level, soundManager, payload.songId(), blockCenter, payload.elapsedTicks(), -1, newPos);
        }

        if (handoffSound != null) {
            playingMap.put(newPos, handoffSound);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(newPos).inflate(3.0D))) {
                entity.setRecordPlayingNearby(newPos, true);
            }
            LOGGER.debug("Handed off portable jukebox session={} to block {}", payload.sessionId(), newPos);
        }
    }

    public static void clearAll() {
        Minecraft mc = Minecraft.getInstance();
        SoundManager soundManager = mc.getSoundManager();
        for (ActiveSession session : ACTIVE_SESSIONS.values()) {
            stopSoundInstance(soundManager, session.sound);
        }
        ACTIVE_SESSIONS.clear();
        HANDED_OFF_SESSIONS.clear();
    }

    @Nullable
    private static SoundInstance claimOrStartSound(
            Minecraft mc,
            ClientLevel level,
            SoundManager soundManager,
            BlockPos originPos,
            String songId,
            Vec3 carrierPos,
            long elapsedTicks,
            int carrierEntityId
    ) {
        Map<BlockPos, SoundInstance> playingMap = ((LevelRendererAccessor) mc.levelRenderer).carryOnBugFix$getPlayingJukeboxSongs();
        SoundInstance live = playingMap.remove(originPos);
        if (live != null && soundManager.isActive(live)) {
            if (live instanceof SeekingJukeboxSoundInstance seeking) {
                seeking.followCarrier(carrierEntityId, carrierPos);
            }
            updateLiveChannelPosition(soundManager, live, carrierPos);
            LOGGER.debug("Claimed live jukebox sound from {} for carrier {}", originPos, carrierEntityId);
            return live;
        }
        return startSeekingSound(level, soundManager, songId, carrierPos, elapsedTicks, carrierEntityId, null);
    }

    @Nullable
    private static SoundInstance startSeekingSound(
            ClientLevel level,
            SoundManager soundManager,
            String songId,
            Vec3 pos,
            long elapsedTicks,
            int carrierEntityId,
            @Nullable BlockPos anchoredPos
    ) {
        SoundEvent soundEvent = resolveSongSoundEvent(level, songId);
        if (soundEvent == null) {
            return null;
        }
        SeekingJukeboxSoundInstance instance = new SeekingJukeboxSoundInstance(soundEvent, pos, elapsedTicks, carrierEntityId, anchoredPos);
        soundManager.play(instance);
        return instance;
    }

    @Nullable
    private static SoundEvent resolveSongSoundEvent(ClientLevel level, String songId) {
        ResourceLocation loc = ResourceLocation.tryParse(songId);
        if (loc == null) {
            return null;
        }
        Optional<Registry<JukeboxSong>> regOpt = level.registryAccess().registry(Registries.JUKEBOX_SONG);
        if (regOpt.isEmpty()) {
            return null;
        }
        ResourceKey<JukeboxSong> key = ResourceKey.create(Registries.JUKEBOX_SONG, loc);
        Optional<Holder.Reference<JukeboxSong>> holder = regOpt.get().getHolder(key);
        return holder.map(h -> h.value().soundEvent().value()).orElse(null);
    }

    private static void updateLiveChannelPosition(SoundManager soundManager, SoundInstance instance, Vec3 pos) {
        SoundEngine engine = ((SoundManagerAccessor) soundManager).carryOnBugFix$getSoundEngine();
        Map<SoundInstance, ChannelAccess.ChannelHandle> map = ((SoundEngineAccessor) engine).carryOnBugFix$getInstanceToChannel();
        ChannelAccess.ChannelHandle handle = map.get(instance);
        if (handle != null) {
            handle.execute(channel -> channel.setSelfPosition(pos));
        }
    }

    private static void stopSoundInstance(SoundManager soundManager, SoundInstance instance) {
        if (instance instanceof SeekingJukeboxSoundInstance seeking) {
            seeking.stopImmediately();
        }
        soundManager.stop(instance);
    }

    private record ActiveSession(long sessionId, String dimension, SoundInstance sound) {
    }
}
