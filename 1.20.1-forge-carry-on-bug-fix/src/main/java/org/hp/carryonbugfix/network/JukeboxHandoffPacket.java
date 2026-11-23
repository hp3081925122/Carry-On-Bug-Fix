package org.hp.carryonbugfix.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 1.20.1 Forge 服务端到客户端的便携唱片机落地交接网络包。
 */
public record JukeboxHandoffPacket(
        int carrierEntityId,
        long sessionId,
        BlockPos pos,
        String songId,
        long elapsedTicks
) {
    private static volatile Consumer<JukeboxHandoffPacket> clientHandler = packet -> {
    };

    public static void setClientHandler(Consumer<JukeboxHandoffPacket> handler) {
        clientHandler = handler;
    }

    public static void encode(JukeboxHandoffPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.carrierEntityId);
        buf.writeVarLong(msg.sessionId);
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.songId);
        buf.writeVarLong(msg.elapsedTicks);
    }

    public static JukeboxHandoffPacket decode(FriendlyByteBuf buf) {
        int carrierId = buf.readVarInt();
        long sessionId = buf.readVarLong();
        BlockPos pos = buf.readBlockPos();
        String songId = buf.readUtf();
        long elapsed = buf.readVarLong();
        return new JukeboxHandoffPacket(carrierId, sessionId, pos, songId, elapsed);
    }

    public static void handle(JukeboxHandoffPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> clientHandler.accept(msg));
        ctx.setPacketHandled(true);
    }
}
