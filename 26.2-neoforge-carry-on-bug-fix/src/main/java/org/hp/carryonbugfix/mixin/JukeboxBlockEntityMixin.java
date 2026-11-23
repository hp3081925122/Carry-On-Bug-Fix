package org.hp.carryonbugfix.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import org.hp.carryonbugfix.PortableJukeboxHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 阻止 Carry On 抱起播放中唱片机执行 removeBlockEntity 时触发 setRemoved 广播 1011 停歌事件，保护客户端活体声道无缝认领。
 */
@Mixin(value = JukeboxBlockEntity.class, remap = false)
public abstract class JukeboxBlockEntityMixin {
    @Redirect(
            method = "setRemoved",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V"
            )
    )
    private void carryOnBugFix$suppressStopGameEvent(Level level, Holder<GameEvent> event, BlockPos pos, GameEvent.Context context) {
        if (!PortableJukeboxHandler.isSuppressingStopEvent()) {
            level.gameEvent(event, pos, context);
        }
    }

    @Redirect(
            method = "setRemoved",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;levelEvent(ILnet/minecraft/core/BlockPos;I)V"
            )
    )
    private void carryOnBugFix$suppressStopLevelEvent(Level level, int type, BlockPos pos, int data) {
        if (!PortableJukeboxHandler.isSuppressingStopEvent()) {
            level.levelEvent(type, pos, data);
        }
    }
}
