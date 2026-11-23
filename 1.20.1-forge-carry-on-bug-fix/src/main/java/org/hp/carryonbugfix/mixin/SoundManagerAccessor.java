package org.hp.carryonbugfix.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 SoundManager 内部的 SoundEngine 字段，用于动态更新活体声源位置。
 */
@Mixin(SoundManager.class)
public interface SoundManagerAccessor {
    @Accessor("soundEngine")
    SoundEngine carryOnBugFix$getSoundEngine();
}
