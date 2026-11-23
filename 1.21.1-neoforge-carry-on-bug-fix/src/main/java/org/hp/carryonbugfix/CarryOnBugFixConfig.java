package org.hp.carryonbugfix;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CarryOnBugFixConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue PASSENGER_STACK_ENABLED;
    public static final ModConfigSpec.IntValue MAX_PASSENGER_DEPTH;
    public static final ModConfigSpec.IntValue MAX_PASSENGER_ENTITIES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        PASSENGER_STACK_ENABLED = builder.define("passengerStack.enabled", true);
        MAX_PASSENGER_DEPTH = builder.defineInRange("passengerStack.maxDepth", 3, 1, 3);
        MAX_PASSENGER_ENTITIES = builder.defineInRange("passengerStack.maxEntities", 8, 1, 8);
        SPEC = builder.build();
    }

    private CarryOnBugFixConfig() {
    }
}
