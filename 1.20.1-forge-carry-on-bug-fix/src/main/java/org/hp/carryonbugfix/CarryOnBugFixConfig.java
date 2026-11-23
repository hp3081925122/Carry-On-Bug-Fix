package org.hp.carryonbugfix;

import net.minecraftforge.common.ForgeConfigSpec;

public final class CarryOnBugFixConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue PASSENGER_STACK_ENABLED;
    public static final ForgeConfigSpec.IntValue MAX_PASSENGER_DEPTH;
    public static final ForgeConfigSpec.IntValue MAX_PASSENGER_ENTITIES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        PASSENGER_STACK_ENABLED = builder.define("passengerStack.enabled", true);
        MAX_PASSENGER_DEPTH = builder.defineInRange("passengerStack.maxDepth", 3, 1, 3);
        MAX_PASSENGER_ENTITIES = builder.defineInRange("passengerStack.maxEntities", 8, 1, 8);
        SPEC = builder.build();
    }

    private CarryOnBugFixConfig() {
    }
}
