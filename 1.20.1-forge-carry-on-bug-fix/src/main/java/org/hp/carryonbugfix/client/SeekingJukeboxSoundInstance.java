package org.hp.carryonbugfix.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * 1.20.1 支持起始进度跳转且可在“跟随搬运者”与“锚定落地方块”之间无缝切换的唱片机音源实例。
 */
public final class SeekingJukeboxSoundInstance extends AbstractTickableSoundInstance {
    private final long initialSkipTicks;
    private int carrierEntityId;
    @Nullable
    private BlockPos anchoredPos;

    public SeekingJukeboxSoundInstance(SoundEvent soundEvent, Vec3 initialPos, long initialSkipTicks, int carrierEntityId, @Nullable BlockPos anchoredPos) {
        super(soundEvent, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.volume = 4.0F;
        this.pitch = 1.0F;
        this.looping = false;
        this.delay = 0;
        this.attenuation = Attenuation.LINEAR;
        this.x = initialPos.x;
        this.y = initialPos.y;
        this.z = initialPos.z;
        this.initialSkipTicks = Math.max(0L, initialSkipTicks);
        this.carrierEntityId = carrierEntityId;
        this.anchoredPos = anchoredPos;
    }

    public void anchorToBlock(BlockPos pos) {
        this.anchoredPos = pos.immutable();
        this.carrierEntityId = -1;
        this.x = pos.getX() + 0.5D;
        this.y = pos.getY() + 0.5D;
        this.z = pos.getZ() + 0.5D;
    }

    public void followCarrier(int entityId, Vec3 pos) {
        this.anchoredPos = null;
        this.carrierEntityId = entityId;
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
    }

    public void stopImmediately() {
        this.stop();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return super.getStream(soundBuffers, sound, looping).thenApply(stream -> {
            if (this.initialSkipTicks > 0L) {
                return SeekingAudioStream.skipTicks(stream, this.initialSkipTicks);
            }
            return stream;
        });
    }

    @Override
    public void tick() {
        if (this.anchoredPos != null) {
            this.x = this.anchoredPos.getX() + 0.5D;
            this.y = this.anchoredPos.getY() + 0.5D;
            this.z = this.anchoredPos.getZ() + 0.5D;
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || this.carrierEntityId < 0) {
            this.stop();
            return;
        }
        Entity carrier = level.getEntity(this.carrierEntityId);
        if (carrier == null || carrier.isRemoved()) {
            this.stop();
            return;
        }
        this.x = carrier.getX();
        this.y = carrier.getY() + 1.0D;
        this.z = carrier.getZ();
    }
}
