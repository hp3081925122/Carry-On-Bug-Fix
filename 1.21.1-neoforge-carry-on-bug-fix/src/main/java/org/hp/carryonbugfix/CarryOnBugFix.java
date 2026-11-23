package org.hp.carryonbugfix;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.hp.carryonbugfix.client.CarryOnBugFixClient;
import org.hp.carryonbugfix.network.JukeboxHandoffPayload;
import org.slf4j.Logger;

/**
 * 1.21.1 NeoForge 模组主入口：注册配置、网络包、客户端事件以及开发环境回归测试。
 */
@Mod(CarryOnBugFix.MOD_ID)
public final class CarryOnBugFix {
    public static final String MOD_ID = "carry_on_bug_fix";
    private static final Logger LOGGER = LogUtils.getLogger();

    public CarryOnBugFix(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, CarryOnBugFixConfig.SPEC);
        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigReload);
        modEventBus.addListener(this::onRegisterPayloads);

        if (FMLEnvironment.dist.isClient()) {
            CarryOnBugFixClient.init();
        }

        if (!FMLEnvironment.production) {
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

    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(
                JukeboxHandoffPayload.TYPE,
                JukeboxHandoffPayload.STREAM_CODEC,
                JukeboxHandoffPayload::handle
        );
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
