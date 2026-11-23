package org.hp.carryonbugfix.mixin;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import org.hp.carryonbugfix.BlacklistExceptionMatcher;
import org.hp.carryonbugfix.CarryOnBugFixConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tschipp.carryon.Constants;
import tschipp.carryon.common.config.ListHandler;

import java.util.List;
import java.util.Set;

/**
 * 在 Carry On 黑名单模式下注入白名单豁免判定，实现“黑名单 + 白名单例外共存”。
 */
@Mixin(value = ListHandler.class, remap = false)
public abstract class ListHandlerMixin {
    @Shadow
    private static Set<String> ALLOWED_TILES;
    @Shadow
    private static List<TagKey<Block>> ALLOWED_TILES_TAGS;
    @Shadow
    private static Set<String> ALLOWED_ENTITIES;
    @Shadow
    private static List<TagKey<EntityType<?>>> ALLOWED_ENTITIES_TAGS;
    @Shadow
    private static Set<String> ALLOWED_STACKING;
    @Shadow
    private static List<TagKey<EntityType<?>>> ALLOWED_STACKING_TAGS;

    @Shadow
    private static boolean doCheck(Block block, Set<String> regular, List<TagKey<Block>> tags) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean doCheck(Entity entity, Set<String> regular, List<TagKey<EntityType<?>>> tags) {
        throw new AssertionError();
    }

    @Inject(method = "initConfigLists", at = @At("TAIL"))
    private static void carryOnBugFix$rebuildExceptions(CallbackInfo ci) {
        BlacklistExceptionMatcher.rebuild();
    }

    @Inject(method = "isPermitted(Lnet/minecraft/world/level/block/Block;)Z", at = @At("RETURN"), cancellable = true)
    private static void carryOnBugFix$allowBlockBlacklistException(Block block, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || Constants.COMMON_CONFIG.settings.useWhitelistBlocks) {
            return;
        }
        if (!CarryOnBugFixConfig.SPEC.isLoaded() || !CarryOnBugFixConfig.BLACKLIST_EXCEPTIONS_ENABLED.get()) {
            return;
        }
        if (CarryOnBugFixConfig.USE_CARRYON_WHITELIST_AS_EXCEPTIONS.get() && doCheck(block, ALLOWED_TILES, ALLOWED_TILES_TAGS)) {
            cir.setReturnValue(true);
            return;
        }
        if (BlacklistExceptionMatcher.isBlockException(block)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isPermitted(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"), cancellable = true)
    private static void carryOnBugFix$allowEntityBlacklistException(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || Constants.COMMON_CONFIG.settings.useWhitelistEntities) {
            return;
        }
        if (!CarryOnBugFixConfig.SPEC.isLoaded() || !CarryOnBugFixConfig.BLACKLIST_EXCEPTIONS_ENABLED.get()) {
            return;
        }
        if (CarryOnBugFixConfig.USE_CARRYON_WHITELIST_AS_EXCEPTIONS.get() && doCheck(entity, ALLOWED_ENTITIES, ALLOWED_ENTITIES_TAGS)) {
            cir.setReturnValue(true);
            return;
        }
        if (BlacklistExceptionMatcher.isEntityException(entity)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isStackingPermitted(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"), cancellable = true)
    private static void carryOnBugFix$allowStackingBlacklistException(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || Constants.COMMON_CONFIG.settings.useWhitelistStacking) {
            return;
        }
        if (!CarryOnBugFixConfig.SPEC.isLoaded() || !CarryOnBugFixConfig.BLACKLIST_EXCEPTIONS_ENABLED.get()) {
            return;
        }
        if (CarryOnBugFixConfig.USE_CARRYON_WHITELIST_AS_EXCEPTIONS.get() && doCheck(entity, ALLOWED_STACKING, ALLOWED_STACKING_TAGS)) {
            cir.setReturnValue(true);
            return;
        }
        if (BlacklistExceptionMatcher.isStackingException(entity)) {
            cir.setReturnValue(true);
        }
    }
}
