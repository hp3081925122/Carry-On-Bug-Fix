package org.hp.carryonbugfix;

import com.mojang.logging.LogUtils;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import org.hp.carryonbugfix.client.CarryOnBugFixClient;
import org.hp.carryonbugfix.network.CarryOnBugFixNetwork;
import org.slf4j.Logger;

/**
 * 1.20.1 Forge 模组主入口：注册配置、网络通道、客户端监听与开发环境回归测试。
 */
@Mod(CarryOnBugFix.MOD_ID)
public final class CarryOnBugFix {
    public static final String MOD_ID = "carry_on_bug_fix";
    private static final Logger LOGGER = LogUtils.getLogger();

    public CarryOnBugFix(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, CarryOnBugFixConfig.SPEC);
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigReload);
        CarryOnBugFixNetwork.register();

        if (FMLEnvironment.dist.isClient()) {
            CarryOnBugFixClient.init();
        }

        if (!FMLLoader.isProduction()) {
            modEventBus.addListener(this::onRegisterGameTests);
        }
    }

    private void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == CarryOnBugFixConfig.SPEC) {
            BlacklistExceptionMatcher.markDirty();
        }
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == CarryOnBugFixConfig.SPEC) {
            BlacklistExceptionMatcher.markDirty();
        }
    }

    private void onRegisterGameTests(RegisterGameTestsEvent event) {
        try {
            Class<?> testClass = Class.forName("org.hp.carryonbugfix.gametest.CarryOnBugFixGameTests");
            event.register(testClass);
        } catch (ClassNotFoundException ex) {
            LOGGER.warn("Failed to locate GameTest class", ex);
        }
    }
}
