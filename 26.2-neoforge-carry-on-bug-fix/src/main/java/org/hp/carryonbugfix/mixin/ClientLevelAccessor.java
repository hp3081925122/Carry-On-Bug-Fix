package org.hp.carryonbugfix.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 ClientLevel 内部的 LevelEventHandler 字段，用于访问原版唱片播放映射表。
 */
@Mixin(value = ClientLevel.class, remap = false)
public interface ClientLevelAccessor {
    @Accessor("levelEventHandler")
    LevelEventHandler carryOnBugFix$getLevelEventHandler();
}
