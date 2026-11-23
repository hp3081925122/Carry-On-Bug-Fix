package org.hp.carryonbugfix;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.20.1 Forge 模组通用配置，控制乘客整组搬运、生物塞入载具、便携唱片机续播以及黑名单白名单豁免。
 */
public final class CarryOnBugFixConfig {
    public static final ForgeConfigSpec SPEC;

    // 整组乘客搬运配置
    public static final ForgeConfigSpec.BooleanValue PASSENGER_STACK_ENABLED;
    public static final ForgeConfigSpec.IntValue MAX_PASSENGER_DEPTH;
    public static final ForgeConfigSpec.IntValue MAX_PASSENGER_ENTITIES;

    // 抱着生物放入船与矿车配置
    public static final ForgeConfigSpec.BooleanValue VEHICLE_BOARDING_ENABLED;
    public static final ForgeConfigSpec.BooleanValue VEHICLE_BOARDING_REQUIRE_SHIFT;
    public static final ForgeConfigSpec.BooleanValue VEHICLE_BOARDING_ALLOW_BOATS;
    public static final ForgeConfigSpec.BooleanValue VEHICLE_BOARDING_ALLOW_MINECARTS;

    // 便携播放中唱片机配置
    public static final ForgeConfigSpec.BooleanValue PORTABLE_JUKEBOX_ENABLED;
    public static final ForgeConfigSpec.BooleanValue PORTABLE_JUKEBOX_EMIT_PARTICLES;

    // 黑名单与白名单豁免共存配置
    public static final ForgeConfigSpec.BooleanValue BLACKLIST_EXCEPTIONS_ENABLED;
    public static final ForgeConfigSpec.BooleanValue USE_CARRYON_WHITELIST_AS_EXCEPTIONS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCK_BLACKLIST_EXCEPTIONS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_BLACKLIST_EXCEPTIONS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> STACKING_BLACKLIST_EXCEPTIONS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        PASSENGER_STACK_ENABLED = builder
                .comment("是否启用整组乘客搬运（抱起船/矿车/实体时一并带走乘客）。 / Enable carrying vehicles and entity stacks together with all passengers.")
                .define("passengerStack.enabled", true);
        MAX_PASSENGER_DEPTH = builder
                .comment("整组搬运允许的最大嵌套层数（含最底层载具）。 / Maximum allowed riding depth including the root vehicle.")
                .defineInRange("passengerStack.maxDepth", 3, 1, 3);
        MAX_PASSENGER_ENTITIES = builder
                .comment("整组搬运允许的最大实体总数。 / Maximum total entities allowed in a carried stack.")
                .defineInRange("passengerStack.maxEntities", 8, 1, 8);

        builder.comment("抱着生物直接放入船或矿车的设置 / Settings for placing carried mobs directly into boats and minecarts")
                .push("vehicleBoarding");
        VEHICLE_BOARDING_ENABLED = builder
                .comment("是否允许抱着生物时右键空位船或空矿车直接将生物放入载具。 / Allow placing a carried mob directly into a boat or minecart with an empty seat.")
                .define("enabled", true);
        VEHICLE_BOARDING_REQUIRE_SHIFT = builder
                .comment("放入船或矿车时是否要求按住潜行键（Shift）；关闭后直接右键即可放入。 / Require holding Shift when right-clicking a boat or minecart to board the carried mob.")
                .define("requireShift", true);
        VEHICLE_BOARDING_ALLOW_BOATS = builder
                .comment("是否允许将抱起的生物放入船与运输船（遵循原版座位数与宽度限制）。 / Allow boarding carried mobs into boats and chest boats.")
                .define("allowBoats", true);
        VEHICLE_BOARDING_ALLOW_MINECARTS = builder
                .comment("是否允许将抱起的生物放入空载客矿车。 / Allow boarding carried mobs into empty rideable minecarts.")
                .define("allowMinecarts", true);
        builder.pop();

        builder.comment("抱起播放中唱片机持续放歌与放下续播设置 / Settings for keeping playing jukeboxes running while carried")
                .push("portableJukebox");
        PORTABLE_JUKEBOX_ENABLED = builder
                .comment("抱起正在播放的唱片机时是否保持音乐跟随玩家继续播放，并在放下后无缝续播。 / Keep jukebox music playing and following the carrier when picked up, resuming seamlessly when placed down.")
                .define("enabled", true);
        PORTABLE_JUKEBOX_EMIT_PARTICLES = builder
                .comment("携带播放中唱片机行走时是否在玩家头顶周期性生成音符粒子并触发游戏事件。 / Emit note particles and jukebox game events above the player while carrying a playing jukebox.")
                .define("emitNoteParticles", true);
        builder.pop();

        builder.comment("黑名单与白名单豁免共存设置（可在拉黑整组或整模组时放行指定方块/实体） / Blacklist exception settings allowing whitelist overrides in blacklist mode")
                .push("blacklistExceptions");
        BLACKLIST_EXCEPTIONS_ENABLED = builder
                .comment("是否在 Carry On 黑名单模式下启用白名单例外放行机制。 / Allow whitelist entries to act as exceptions when Carry On is in blacklist mode.")
                .define("enabled", true);
        USE_CARRYON_WHITELIST_AS_EXCEPTIONS = builder
                .comment("是否直接将 Carry On 自身配置中的 allowedBlocks/allowedEntities/allowedStacking 与 #carryon:*_whitelist 标签作为黑名单例外。 / Treat Carry On's own whitelist entries and #carryon:*_whitelist tags as blacklist exceptions.")
                .define("useCarryOnWhitelistAsExceptions", true);
        BLOCK_BLACKLIST_EXCEPTIONS = builder
                .comment("额外方块黑名单豁免列表（支持精确 ID、通配符如 modid:*crate*、方块标签如 #forge:chests）。 / Extra block blacklist exceptions (exact IDs, wildcards like modid:*crate*, or #tags).")
                .defineListAllowEmpty("blockExceptions", ArrayList::new, CarryOnBugFixConfig::isValidPatternEntry);
        ENTITY_BLACKLIST_EXCEPTIONS = builder
                .comment("额外实体黑名单豁免列表（支持精确 ID、通配符或 #实体标签）。 / Extra entity blacklist exceptions (exact IDs, wildcards, or #entity_tags).")
                .defineListAllowEmpty("entityExceptions", ArrayList::new, CarryOnBugFixConfig::isValidPatternEntry);
        STACKING_BLACKLIST_EXCEPTIONS = builder
                .comment("额外堆叠黑名单豁免列表（支持精确 ID、通配符或 #实体标签）。 / Extra stacking blacklist exceptions (exact IDs, wildcards, or #entity_tags).")
                .defineListAllowEmpty("stackingExceptions", ArrayList::new, CarryOnBugFixConfig::isValidPatternEntry);
        builder.pop();

        SPEC = builder.build();
    }

    private static boolean isValidPatternEntry(Object entry) {
        return entry instanceof String text && !text.trim().isEmpty();
    }

    private CarryOnBugFixConfig() {
    }
}
