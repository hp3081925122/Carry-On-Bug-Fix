package org.hp.carryonbugfix;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 仅在检测到 Carry On Extend 时应用投掷兼容 Mixin。
 */
public final class CarryOnExtendMixinPlugin implements IMixinConfigPlugin {
    private boolean carryOnExtendLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        FMLLoader loader = FMLLoader.getCurrentOrNull();
        LoadingModList list = loader == null ? null : loader.getLoadingModList();
        this.carryOnExtendLoaded = list != null && list.getModFileById("carryonextend") != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".EntityThrowHandlerMixin")) {
            return this.carryOnExtendLoaded;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
