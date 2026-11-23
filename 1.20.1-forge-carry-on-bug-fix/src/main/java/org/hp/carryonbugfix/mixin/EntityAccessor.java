package org.hp.carryonbugfix.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露原版 Entity.canAddPassenger 座位容量判定方法。
 */
@Mixin(Entity.class)
public interface EntityAccessor {
    @Invoker("canAddPassenger")
    boolean carryOnBugFix$canAddPassenger(Entity passenger);
}
