package org.hp.carryonbugfix.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.hp.carryonbugfix.PortableJukeboxHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tschipp.carryon.CarryOnCommon;

/**
 * 在 Carry On 搬运循环 tick 尾部推进便携唱片机的服务端播放计时、音符粒子与播完收尾。
 */
@Mixin(value = CarryOnCommon.class, remap = false)
public abstract class CarryOnCommonMixin {
    @Inject(method = "onCarryTick", at = @At("TAIL"))
    private static void carryOnBugFix$tickPortableJukebox(ServerPlayer player, CallbackInfo ci) {
        PortableJukeboxHandler.onCarryTick(player);
    }
}
