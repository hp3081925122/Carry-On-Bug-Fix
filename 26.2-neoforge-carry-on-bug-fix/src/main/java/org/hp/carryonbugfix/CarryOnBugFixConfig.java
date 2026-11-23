package org.hp.carryonbugfix;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CarryOnBugFixConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue PASSENGER_STACK_ENABLED;
    public static final ModConfigSpec.IntValue MAX_PASSENGER_DEPTH;
    public static final ModConfigSpec.IntValue MAX_PASSENGER_ENTITIES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        PASSENGER_STACK_ENABLED = builder
                .comment("是否启用整组乘客搬运与手持渲染 / Enable carrying and rendering full passenger stacks")
                .define("passengerStack.enabled", true);
        MAX_PASSENGER_DEPTH = builder
                .comment("允许搬运与堆叠的最大乘骑层数 / Maximum passenger stack depth allowed")
                .defineInRange("passengerStack.maxDepth", 3, 1, 3);
        MAX_PASSENGER_ENTITIES = builder
                .comment("允许搬运与堆叠的最大实体总数 / Maximum total entity count in a carried stack")
                .defineInRange("passengerStack.maxEntities", 8, 1, 8);
        SPEC = builder.build();
    }

    private CarryOnBugFixConfig() {
    }
}
