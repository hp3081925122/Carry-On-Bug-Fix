package org.hp.carryonbugfix.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.hp.carryonbugfix.CarryOnBugFix;

import java.util.function.Consumer;

/**
 * 服务端到客户端的便携唱片机落地交接包，将移动中的声道无断点绑定回落地方块坐标。
 */
public record JukeboxHandoffPayload(
        int carrierEntityId,
        long sessionId,
        BlockPos pos,
        String songId,
        long elapsedTicks
) implements CustomPacketPayload {

    private static volatile Consumer<JukeboxHandoffPayload> clientHandler = payload -> {
    };

    public static final CustomPacketPayload.Type<JukeboxHandoffPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CarryOnBugFix.MOD_ID, "jukebox_handoff"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JukeboxHandoffPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            JukeboxHandoffPayload::carrierEntityId,
            ByteBufCodecs.VAR_LONG,
            JukeboxHandoffPayload::sessionId,
            BlockPos.STREAM_CODEC,
            JukeboxHandoffPayload::pos,
            ByteBufCodecs.STRING_UTF8,
            JukeboxHandoffPayload::songId,
            ByteBufCodecs.VAR_LONG,
            JukeboxHandoffPayload::elapsedTicks,
            JukeboxHandoffPayload::new
    );

    public static void setClientHandler(Consumer<JukeboxHandoffPayload> handler) {
        clientHandler = handler;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(JukeboxHandoffPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientHandler.accept(payload));
    }
}
