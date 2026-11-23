package org.hp.carryonbugfix;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(CarryOnBugFix.MOD_ID)
public final class CarryOnBugFix {
    public static final String MOD_ID = "carry_on_bug_fix";

    public CarryOnBugFix(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.LOCAL, CarryOnBugFixConfig.SPEC);
    }
}
