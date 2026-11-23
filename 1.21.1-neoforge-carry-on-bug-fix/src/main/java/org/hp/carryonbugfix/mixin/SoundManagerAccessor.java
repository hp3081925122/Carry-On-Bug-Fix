package org.hp.carryonbugfix.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 SoundManager 内部 SoundEngine，用于更新移动中唱片机的活体 OpenAL 声道位置。
 */
@Mixin(SoundManager.class)
public interface SoundManagerAccessor {
    @Accessor("soundEngine")
    SoundEngine carryOnBugFix$getSoundEngine();
}
