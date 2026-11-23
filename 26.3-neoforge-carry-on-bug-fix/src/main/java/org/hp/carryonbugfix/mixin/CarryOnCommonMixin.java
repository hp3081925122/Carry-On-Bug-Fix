package org.hp.carryonbugfix.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.hp.carryonbugfix.PortableJukeboxHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tschipp.carryon.CarryOnCommon;

/**
 * 在 Carry On 服务端搬运 tick 末尾推进便携唱片机播放进度与粒子/游戏事件。
 */
@Mixin(value = CarryOnCommon.class, remap = false)
public abstract class CarryOnCommonMixin {
    @Inject(method = "onCarryTick", at = @At("TAIL"))
    private static void carryOnBugFix$tickPortableJukebox(ServerPlayer player, CallbackInfo ci) {
        PortableJukeboxHandler.onCarryTick(player);
    }
}
