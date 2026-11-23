package org.hp.carryonbugfix.mixin;

import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * 暴露 LevelEventHandler 的唱片播放映射表，便于抱起认领与放下交接活体声源。
 */
@Mixin(value = LevelEventHandler.class, remap = false)
public interface LevelEventHandlerAccessor {
    @Accessor("playingJukeboxSongs")
    Map<BlockPos, SoundInstance> carryOnBugFix$getPlayingJukeboxSongs();
}
