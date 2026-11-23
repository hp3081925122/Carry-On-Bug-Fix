package org.hp.carryonbugfix.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.carryonbugfix.CarryOnBugFixConfig;
import org.hp.carryonbugfix.PortableJukeboxHandler;
import org.hp.carryonbugfix.mixin.ClientLevelAccessor;
import org.hp.carryonbugfix.mixin.LevelEventHandlerAccessor;
import org.hp.carryonbugfix.mixin.SoundEngineAccessor;
import org.hp.carryonbugfix.mixin.SoundManagerAccessor;
import org.hp.carryonbugfix.network.JukeboxHandoffPayload;
import org.hp.carryonbugfix.network.JukeboxPickupPayload;
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
 * 26.x 客户端便携唱片机声源管理器：通过先序抱起预认领包在方块更新包前认领原方块活体声道、跟随移动并在落地时交接回原版播放表。
 */
public final class ClientPortableJukeboxManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long PICKUP_GRACE_TICKS = 10L;

    private static final Map<Integer, ActiveSession> ACTIVE_SESSIONS = new HashMap<>();
    private static final Set<Long> HANDED_OFF_SESSIONS = new HashSet<>();
    private static final Map<BlockPos, Long> SUPPRESSED_CLIENT_ORIGINS = new HashMap<>();

    private ClientPortableJukeboxManager() {
    }

    /**
     * 供客户端侧 JukeboxBlockEntity.setRemoved() 查询，抑制紧随预认领包到达的空气方块更新导致的 1011 停歌。
     */
    public static boolean shouldSuppressClientStop(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !CarryOnBugFixConfig.PORTABLE_JUKEBOX_ENABLED.get()) {
            return false;
        }
        Long expireAt = SUPPRESSED_CLIENT_ORIGINS.get(pos);
        return expireAt != null && level.getGameTime() <= expireAt;
    }

    /**
     * 收到服务端先序抱起预认领包时立即从原版播放表移出并认领活体声道，杜绝客户端 setRemoved() 竞态杀音。
     */
    public static void handlePickup(JukeboxPickupPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !CarryOnBugFixConfig.PORTABLE_JUKEBOX_ENABLED.get()) {
            return;
        }
        if (HANDED_OFF_SESSIONS.contains(payload.sessionId())) {
            return;
        }

        BlockPos originPos = payload.originPos().immutable();
        long expireAt = level.getGameTime() + PICKUP_GRACE_TICKS;
        SUPPRESSED_CLIENT_ORIGINS.put(originPos, expireAt);

        SoundManager soundManager = mc.getSoundManager();
        Map<BlockPos, SoundInstance> playingMap = getPlayingMap(level);
        SoundInstance live = playingMap.remove(originPos);
        if (live == null || !soundManager.isActive(live)) {
            return;
        }

        int carrierId = payload.carrierEntityId();
        Entity carrier = level.getEntity(carrierId);
        Vec3 carrierPos = carrier != null
                ? new Vec3(carrier.getX(), carrier.getY() + 1.0D, carrier.getZ())
                : Vec3.atCenterOf(originPos);

        ActiveSession previous = ACTIVE_SESSIONS.get(carrierId);
        if (previous != null && previous.sound != live) {
            stopSoundInstance(soundManager, previous.sound);
        }

        if (live instanceof SeekingJukeboxSoundInstance seeking) {
            seeking.followCarrier(carrierId, carrierPos);
        }
        updateLiveChannelPosition(soundManager, live, carrierPos);
        ACTIVE_SESSIONS.put(carrierId, new ActiveSession(payload.sessionId(), payload.dimension(), live, expireAt, false));
        LOGGER.debug("Claimed live jukebox sound via pickup packet from {} for carrier {} session={}", originPos, carrierId, payload.sessionId());
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !CarryOnBugFixConfig.PORTABLE_JUKEBOX_ENABLED.get()) {
            if (!ACTIVE_SESSIONS.isEmpty() || !SUPPRESSED_CLIENT_ORIGINS.isEmpty()) {
                clearAll();
            }
            return;
        }

        long now = level.getGameTime();
        if (!SUPPRESSED_CLIENT_ORIGINS.isEmpty()) {
            SUPPRESSED_CLIENT_ORIGINS.entrySet().removeIf(entry -> now > entry.getValue());
        }

        SoundManager soundManager = mc.getSoundManager();
        Set<Integer> seenCarriers = new HashSet<>();

        for (Player player : level.players()) {
            CarryOnData carry = CarryOnDataManager.getCarryData(player);
            if (!carry.isCarrying(CarryType.BLOCK)) {
                continue;
            }
            CompoundTag nbt = carry.getNbt();
            Optional<CompoundTag> infoOpt = nbt.getCompound(PortableJukeboxHandler.TAG_ROOT);
            if (infoOpt.isEmpty()) {
                continue;
            }
            CompoundTag info = infoOpt.get();
            if (!info.getBooleanOr(PortableJukeboxHandler.KEY_PLAYING, false)) {
                continue;
            }

            long sessionId = info.getLongOr(PortableJukeboxHandler.KEY_SESSION_ID, 0L);
            if (HANDED_OFF_SESSIONS.contains(sessionId)) {
                continue;
            }
            long startGameTime = info.getLongOr(PortableJukeboxHandler.KEY_START_GAME_TIME, now);
            long lengthTicks = info.getLongOr(PortableJukeboxHandler.KEY_LENGTH_TICKS, 0L);
            long elapsed = Math.max(0L, now - startGameTime);
            if (elapsed >= lengthTicks) {
                continue;
            }

            int entityId = player.getId();
            seenCarriers.add(entityId);
            String dimension = info.getStringOr(PortableJukeboxHandler.KEY_DIMENSION, "");
            String songId = info.getStringOr(PortableJukeboxHandler.KEY_SONG_ID, "");
            Vec3 carrierPos = new Vec3(player.getX(), player.getY() + 1.0D, player.getZ());

            ActiveSession existing = ACTIVE_SESSIONS.get(entityId);
            if (existing != null && existing.sessionId == sessionId && existing.dimension.equals(dimension)) {
                existing.seenInCarryData = true;
                if (!soundManager.isActive(existing.sound)) {
                    SoundInstance restarted = startSeekingSound(level, soundManager, songId, carrierPos, elapsed, entityId, null);
                    if (restarted != null) {
                        existing = new ActiveSession(sessionId, dimension, restarted, 0L, true);
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
                        info.getIntOr(PortableJukeboxHandler.KEY_ORIGIN_X, 0),
                        info.getIntOr(PortableJukeboxHandler.KEY_ORIGIN_Y, 0),
                        info.getIntOr(PortableJukeboxHandler.KEY_ORIGIN_Z, 0)
                );
                SoundInstance claimed = claimOrStartSound(level, soundManager, originPos, songId, carrierPos, elapsed, entityId);
                if (claimed != null) {
                    ACTIVE_SESSIONS.put(entityId, new ActiveSession(sessionId, dimension, claimed, 0L, true));
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
            ActiveSession session = entry.getValue();
            if (!seenCarriers.contains(entry.getKey())) {
                // 预认领包先到而 CarryOnData 尚未同步的短暂窗口内保留声道，等待同步包抵达
                if (!session.seenInCarryData && now <= session.graceUntilGameTime) {
                    Entity carrier = level.getEntity(entry.getKey());
                    if (carrier != null && !carrier.isRemoved() && soundManager.isActive(session.sound)) {
                        updateLiveChannelPosition(soundManager, session.sound, new Vec3(carrier.getX(), carrier.getY() + 1.0D, carrier.getZ()));
                        continue;
                    }
                }
                stopSoundInstance(soundManager, session.sound);
                it.remove();
            }
        }
    }

    public static void handleHandoff(JukeboxHandoffPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !CarryOnBugFixConfig.PORTABLE_JUKEBOX_ENABLED.get()) {
            return;
        }
        SoundManager soundManager = mc.getSoundManager();
        Map<BlockPos, SoundInstance> playingMap = getPlayingMap(level);
        BlockPos newPos = payload.pos().immutable();
        Vec3 blockCenter = Vec3.atCenterOf(newPos);

        SUPPRESSED_CLIENT_ORIGINS.remove(newPos);

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
        SUPPRESSED_CLIENT_ORIGINS.clear();
    }

    private static Map<BlockPos, SoundInstance> getPlayingMap(ClientLevel level) {
        LevelEventHandler handler = ((ClientLevelAccessor) level).carryOnBugFix$getLevelEventHandler();
        return ((LevelEventHandlerAccessor) handler).carryOnBugFix$getPlayingJukeboxSongs();
    }

    @Nullable
    private static SoundInstance claimOrStartSound(
            ClientLevel level,
            SoundManager soundManager,
            BlockPos originPos,
            String songId,
            Vec3 carrierPos,
            long elapsedTicks,
            int carrierEntityId
    ) {
        Map<BlockPos, SoundInstance> playingMap = getPlayingMap(level);
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
        Identifier loc = Identifier.tryParse(songId);
        if (loc == null) {
            return null;
        }
        Optional<Registry<JukeboxSong>> regOpt = level.registryAccess().lookup(Registries.JUKEBOX_SONG);
        if (regOpt.isEmpty()) {
            return null;
        }
        ResourceKey<JukeboxSong> key = ResourceKey.create(Registries.JUKEBOX_SONG, loc);
        Optional<Holder.Reference<JukeboxSong>> holder = regOpt.get().get(key);
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

    private static final class ActiveSession {
        final long sessionId;
        final String dimension;
        final SoundInstance sound;
        final long graceUntilGameTime;
        boolean seenInCarryData;

        ActiveSession(long sessionId, String dimension, SoundInstance sound, long graceUntilGameTime, boolean seenInCarryData) {
            this.sessionId = sessionId;
            this.dimension = dimension;
            this.sound = sound;
            this.graceUntilGameTime = graceUntilGameTime;
            this.seenInCarryData = seenInCarryData;
        }
    }
}
