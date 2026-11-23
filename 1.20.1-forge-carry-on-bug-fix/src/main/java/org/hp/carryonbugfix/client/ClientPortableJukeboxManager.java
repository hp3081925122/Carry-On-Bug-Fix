package org.hp.carryonbugfix.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.carryonbugfix.CarryOnBugFixConfig;
import org.hp.carryonbugfix.PortableJukeboxHandler;
import org.hp.carryonbugfix.mixin.LevelRendererAccessor;
import org.hp.carryonbugfix.mixin.SoundEngineAccessor;
import org.hp.carryonbugfix.mixin.SoundManagerAccessor;
import org.hp.carryonbugfix.network.JukeboxHandoffPacket;
import org.slf4j.Logger;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.carry.CarryOnData.CarryType;
import tschipp.carryon.common.carry.CarryOnDataManager;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * 1.20.1 客户端便携唱片机声源管理器：无断点认领原方块活体声道、跟随搬运者移动并在落地时交接回原版播放表。
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
                    SoundInstance restarted = startSeekingSound(soundManager, info.getString(PortableJukeboxHandler.KEY_SONG_ID), carrierPos, elapsed, entityId, null);
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
                SoundInstance claimed = claimOrStartSound(mc, soundManager, originPos, info.getString(PortableJukeboxHandler.KEY_SONG_ID), carrierPos, elapsed, entityId);
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

    public static void handleHandoff(JukeboxHandoffPacket payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !CarryOnBugFixConfig.PORTABLE_JUKEBOX_ENABLED.get()) {
            return;
        }
        SoundManager soundManager = mc.getSoundManager();
        Map<BlockPos, SoundInstance> playingMap = ((LevelRendererAccessor) mc.levelRenderer).carryOnBugFix$getPlayingRecords();
        BlockPos newPos = payload.pos().immutable();
        Vec3 blockCenter = Vec3.atCenterOf(newPos);

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
            handoffSound = startSeekingSound(soundManager, payload.songId(), blockCenter, payload.elapsedTicks(), -1, newPos);
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
            SoundManager soundManager,
            BlockPos originPos,
            String songId,
            Vec3 carrierPos,
            long elapsedTicks,
            int carrierEntityId
    ) {
        Map<BlockPos, SoundInstance> playingMap = ((LevelRendererAccessor) mc.levelRenderer).carryOnBugFix$getPlayingRecords();
        SoundInstance live = playingMap.remove(originPos);
        if (live != null && soundManager.isActive(live)) {
            if (live instanceof SeekingJukeboxSoundInstance seeking) {
                seeking.followCarrier(carrierEntityId, carrierPos);
            }
            updateLiveChannelPosition(soundManager, live, carrierPos);
            return live;
        }
        return startSeekingSound(soundManager, songId, carrierPos, elapsedTicks, carrierEntityId, null);
    }

    @Nullable
    private static SoundInstance startSeekingSound(
            SoundManager soundManager,
            String songId,
            Vec3 pos,
            long elapsedTicks,
            int carrierEntityId,
            @Nullable BlockPos anchoredPos
    ) {
        ResourceLocation loc = ResourceLocation.tryParse(songId);
        if (loc == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(loc);
        if (!(item instanceof RecordItem recordItem)) {
            return null;
        }
        SoundEvent soundEvent = recordItem.getSound();
        SeekingJukeboxSoundInstance instance = new SeekingJukeboxSoundInstance(soundEvent, pos, elapsedTicks, carrierEntityId, anchoredPos);
        soundManager.play(instance);
        return instance;
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
