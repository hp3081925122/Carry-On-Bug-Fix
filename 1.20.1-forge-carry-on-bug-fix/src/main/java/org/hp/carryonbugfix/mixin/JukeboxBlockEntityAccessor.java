package org.hp.carryonbugfix.mixin;

import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 1.20.1 原版唱片机方块实体的播放起始与当前计时字段。
 */
@Mixin(JukeboxBlockEntity.class)
public interface JukeboxBlockEntityAccessor {
    @Accessor("recordStartedTick")
    long carryOnBugFix$getRecordStartedTick();

    @Accessor("tickCount")
    long carryOnBugFix$getTickCount();
}
