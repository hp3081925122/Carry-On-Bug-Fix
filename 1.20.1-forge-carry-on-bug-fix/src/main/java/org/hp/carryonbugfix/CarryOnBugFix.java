package org.hp.carryonbugfix;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

@Mod(CarryOnBugFix.MOD_ID)
public final class CarryOnBugFix {
    public static final String MOD_ID = "carry_on_bug_fix";

    public CarryOnBugFix(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, CarryOnBugFixConfig.SPEC);
    }
}
