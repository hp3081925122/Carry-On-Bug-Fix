package org.hp.carryonbugfix.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * 暴露 1.21.1 LevelRenderer 的唱片机播放表，用于拾取认领与落地交接活体声道。
 */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("playingJukeboxSongs")
    Map<BlockPos, SoundInstance> carryOnBugFix$getPlayingJukeboxSongs();
}
