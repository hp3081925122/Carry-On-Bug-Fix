package org.hp.carryonbugfix.client;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.hp.carryonbugfix.PortableJukeboxHandler;
import org.hp.carryonbugfix.network.JukeboxHandoffPayload;
import org.hp.carryonbugfix.network.JukeboxPickupPayload;

/**
 * 客户端事件注册入口，独立隔离所有客户端专用类引用，确保专用服务端零客户端类加载。
 */
public final class CarryOnBugFixClient {
    private CarryOnBugFixClient() {
    }

    public static void init() {
        JukeboxHandoffPayload.setClientHandler(ClientPortableJukeboxManager::handleHandoff);
        JukeboxPickupPayload.setClientHandler(ClientPortableJukeboxManager::handlePickup);
        PortableJukeboxHandler.setClientStopSuppressor(ClientPortableJukeboxManager::shouldSuppressClientStop);
        NeoForge.EVENT_BUS.addListener(CarryOnBugFixClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(CarryOnBugFixClient::onClientLogout);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        ClientPortableJukeboxManager.onClientTick();
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPortableJukeboxManager.clearAll();
    }
}
