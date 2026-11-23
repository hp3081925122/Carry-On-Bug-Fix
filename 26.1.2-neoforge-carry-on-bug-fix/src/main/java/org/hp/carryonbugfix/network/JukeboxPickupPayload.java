package org.hp.carryonbugfix.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.hp.carryonbugfix.CarryOnBugFix;

import java.util.function.Consumer;

/**
 * 服务端到客户端的便携唱片机抱起预认领包：在原方块被移除并发送空气方块更新包前先抵达客户端，
 * 避免 26.x 客户端侧 JukeboxBlockEntity.setRemoved() 提前发送 1011 杀掉活体声道。
 */
public record JukeboxPickupPayload(
        int carrierEntityId,
        long sessionId,
        BlockPos originPos,
        String dimension
) implements CustomPacketPayload {

    private static volatile Consumer<JukeboxPickupPayload> clientHandler = payload -> {
    };

    public static final CustomPacketPayload.Type<JukeboxPickupPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CarryOnBugFix.MOD_ID, "jukebox_pickup"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JukeboxPickupPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            JukeboxPickupPayload::carrierEntityId,
            ByteBufCodecs.VAR_LONG,
            JukeboxPickupPayload::sessionId,
            BlockPos.STREAM_CODEC,
            JukeboxPickupPayload::originPos,
            ByteBufCodecs.STRING_UTF8,
            JukeboxPickupPayload::dimension,
            JukeboxPickupPayload::new
    );

    public static void setClientHandler(Consumer<JukeboxPickupPayload> handler) {
        clientHandler = handler;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(JukeboxPickupPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientHandler.accept(payload));
    }
}
