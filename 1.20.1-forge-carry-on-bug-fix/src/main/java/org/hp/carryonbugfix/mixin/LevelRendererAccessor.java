package org.hp.carryonbugfix.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * 暴露 1.20.1 LevelRenderer 的唱片播放映射表，便于抱起认领与放下交接活体声源。
 */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("playingRecords")
    Map<BlockPos, SoundInstance> carryOnBugFix$getPlayingRecords();
}
