package org.hp.carryonbugfix;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.hp.carryonbugfix.network.JukeboxHandoffPayload;

/**
 * 模组入口：注册配置、可选网络通道、客户端处理器及开发环境 GameTest。
 */
@Mod(CarryOnBugFix.MOD_ID)
public final class CarryOnBugFix {
    public static final String MOD_ID = "carry_on_bug_fix";

    public CarryOnBugFix(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.LOCAL, CarryOnBugFixConfig.SPEC);
        modEventBus.addListener(this::onRegisterPayloads);
        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigReload);
        if (FMLEnvironment.getDist().isClient()) {
            initClient();
        }
        if (!FMLEnvironment.isProduction()) {
            registerDevGameTests(modEventBus);
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

    private void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == CarryOnBugFixConfig.SPEC) {
            BlacklistExceptionMatcher.markDirty();
        }
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == CarryOnBugFixConfig.SPEC) {
            BlacklistExceptionMatcher.rebuild();
        }
    }

    private static void initClient() {
        try {
            Class.forName("org.hp.carryonbugfix.client.CarryOnBugFixClient")
                    .getMethod("init")
                    .invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to initialize CarryOnBugFixClient", e);
        }
    }

    private static void registerDevGameTests(IEventBus modEventBus) {
        try {
            Class.forName("org.hp.carryonbugfix.gametest.CarryOnBugFixGameTests")
                    .getMethod("register", IEventBus.class)
                    .invoke(null, modEventBus);
        } catch (ClassNotFoundException ignored) {
            // 生产或精简环境下允许不包含测试类
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to register CarryOnBugFixGameTests", e);
        }
    }
}
