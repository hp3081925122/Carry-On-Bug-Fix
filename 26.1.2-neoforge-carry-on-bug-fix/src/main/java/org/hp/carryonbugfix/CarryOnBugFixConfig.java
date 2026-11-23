package org.hp.carryonbugfix;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * 模组通用配置：覆盖整组乘客搬运、载具直放、便携唱片机与黑名单白名单豁免共存四项能力。
 */
public final class CarryOnBugFixConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue PASSENGER_STACK_ENABLED;
    public static final ModConfigSpec.IntValue MAX_PASSENGER_DEPTH;
    public static final ModConfigSpec.IntValue MAX_PASSENGER_ENTITIES;

    public static final ModConfigSpec.BooleanValue VEHICLE_BOARDING_ENABLED;
    public static final ModConfigSpec.BooleanValue VEHICLE_BOARDING_REQUIRE_SHIFT;
    public static final ModConfigSpec.BooleanValue VEHICLE_BOARDING_ALLOW_BOATS;
    public static final ModConfigSpec.BooleanValue VEHICLE_BOARDING_ALLOW_MINECARTS;

    public static final ModConfigSpec.BooleanValue PORTABLE_JUKEBOX_ENABLED;
    public static final ModConfigSpec.BooleanValue PORTABLE_JUKEBOX_EMIT_PARTICLES;

    public static final ModConfigSpec.BooleanValue BLACKLIST_EXCEPTIONS_ENABLED;
    public static final ModConfigSpec.BooleanValue USE_CARRYON_WHITELIST_AS_EXCEPTIONS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCK_BLACKLIST_EXCEPTIONS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENTITY_BLACKLIST_EXCEPTIONS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STACKING_BLACKLIST_EXCEPTIONS;

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

        VEHICLE_BOARDING_ENABLED = builder
                .comment("是否允许抱着生物/玩家时直接右键放入空船或矿车 / Allow placing carried entities directly into boats or minecarts")
                .define("vehicleBoarding.enabled", true);
        VEHICLE_BOARDING_REQUIRE_SHIFT = builder
                .comment("放入船或矿车时是否需要按住潜行键 / Require sneaking when placing carried entities into vehicles")
                .define("vehicleBoarding.requireShift", true);
        VEHICLE_BOARDING_ALLOW_BOATS = builder
                .comment("是否允许将抱起的生物直接放入船与运输船 / Allow placing carried entities into boats and chest boats")
                .define("vehicleBoarding.allowBoats", true);
        VEHICLE_BOARDING_ALLOW_MINECARTS = builder
                .comment("是否允许将抱起的生物直接放入普通载客矿车 / Allow placing carried entities into rideable minecarts")
                .define("vehicleBoarding.allowMinecarts", true);

        PORTABLE_JUKEBOX_ENABLED = builder
                .comment("是否启用便携唱片机：抱起播放中的唱片机边走边放歌，放下后无缝续播 / Keep jukebox music playing while carried and resume seamlessly when placed")
                .define("portableJukebox.enabled", true);
        PORTABLE_JUKEBOX_EMIT_PARTICLES = builder
                .comment("搬运播放中的唱片机时是否周期发射音符粒子与通知附近生物 / Emit note particles and jukebox game events while carrying a playing jukebox")
                .define("portableJukebox.emitParticles", true);

        BLACKLIST_EXCEPTIONS_ENABLED = builder
                .comment("是否在 Carry On 黑名单模式下启用白名单豁免放行（黑名单+白名单共存） / Allow whitelist exceptions to override blacklists in Carry On's blacklist mode")
                .define("blacklistExceptions.enabled", true);
        USE_CARRYON_WHITELIST_AS_EXCEPTIONS = builder
                .comment("是否直接将 Carry On 原自带的 allowedBlocks/allowedEntities/allowedStacking 与 #carryon:*_whitelist 作为黑名单例外 / Treat Carry On's own whitelist entries and tags as blacklist exceptions")
                .define("blacklistExceptions.useCarryOnWhitelist", true);
        BLOCK_BLACKLIST_EXCEPTIONS = builder
                .comment("额外方块黑名单豁免列表（支持精确 ID、* 通配符、#标签，如 minecraft:chest 或 #c:chests） / Extra block blacklist exceptions supporting exact IDs, * wildcards, and #tags")
                .defineListAllowEmpty("blacklistExceptions.blocks", List.of(), () -> "", CarryOnBugFixConfig::isValidEntry);
        ENTITY_BLACKLIST_EXCEPTIONS = builder
                .comment("额外实体黑名单豁免列表（支持精确 ID、* 通配符、#标签） / Extra entity blacklist exceptions supporting exact IDs, * wildcards, and #tags")
                .defineListAllowEmpty("blacklistExceptions.entities", List.of(), () -> "", CarryOnBugFixConfig::isValidEntry);
        STACKING_BLACKLIST_EXCEPTIONS = builder
                .comment("额外堆叠黑名单豁免列表（支持精确 ID、* 通配符、#标签） / Extra stacking blacklist exceptions supporting exact IDs, * wildcards, and #tags")
                .defineListAllowEmpty("blacklistExceptions.stacking", List.of(), () -> "", CarryOnBugFixConfig::isValidEntry);

        SPEC = builder.build();
    }

    private CarryOnBugFixConfig() {
    }

    private static boolean isValidEntry(Object value) {
        return value instanceof String s && !s.isBlank();
    }
}
