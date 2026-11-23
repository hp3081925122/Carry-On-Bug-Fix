package org.hp.carryonbugfix.client;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.hp.carryonbugfix.network.JukeboxHandoffPacket;

/**
 * 1.20.1 Forge 客户端事件注册入口，独立隔离客户端专用类。
 */
public final class CarryOnBugFixClient {
    private CarryOnBugFixClient() {
    }

    public static void init() {
        JukeboxHandoffPacket.setClientHandler(ClientPortableJukeboxManager::handleHandoff);
        MinecraftForge.EVENT_BUS.addListener(CarryOnBugFixClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(CarryOnBugFixClient::onClientLogout);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientPortableJukeboxManager.onClientTick();
        }
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPortableJukeboxManager.clearAll();
    }
}
