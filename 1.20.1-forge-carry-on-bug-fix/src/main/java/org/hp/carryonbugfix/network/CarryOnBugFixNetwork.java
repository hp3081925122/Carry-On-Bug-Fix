package org.hp.carryonbugfix.network;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.hp.carryonbugfix.CarryOnBugFix;

import java.util.Optional;

/**
 * 1.20.1 Forge 网络通道：可选兼容握手，不阻断单端安装时的联机连接。
 */
public final class CarryOnBugFixNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CarryOnBugFix.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            remote -> true,
            remote -> true
    );
    private static boolean registered = false;

    private CarryOnBugFixNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.registerMessage(
                0,
                JukeboxHandoffPacket.class,
                JukeboxHandoffPacket::encode,
                JukeboxHandoffPacket::decode,
                JukeboxHandoffPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    public static void sendToTrackingChunk(ServerLevel level, BlockPos pos, JukeboxHandoffPacket packet) {
        CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)), packet);
    }
}
