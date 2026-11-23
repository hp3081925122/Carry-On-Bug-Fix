package org.hp.carryonbugfix.mixin;

import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import tschipp.carryon.common.carry.CarryOnData;

/**
 * 暴露 CarryOnData 内部 NBT 复合标签，便于附加随身唱片机元数据。
 */
@Mixin(value = CarryOnData.class, remap = false)
public interface CarryOnDataAccessor {
    @Accessor("nbt")
    CompoundTag carryOnBugFix$getInternalNbt();
}
