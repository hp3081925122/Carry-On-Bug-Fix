package org.hp.carryonbugfix.mixin;

import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import tschipp.carryon.common.carry.CarryOnData;

/**
 * 暴露 CarryOnData 内部可变 NBT，用于存取便携唱片机会话状态并复用 Carry On 自带同步机制。
 */
@Mixin(value = CarryOnData.class, remap = false)
public interface CarryOnDataAccessor {
    @Accessor("nbt")
    CompoundTag carryOnBugFix$getInternalNbt();
}
