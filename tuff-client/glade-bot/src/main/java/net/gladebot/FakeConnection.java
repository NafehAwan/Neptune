package net.gladebot;

import io.netty.channel.embedded.EmbeddedChannel;
import net.gladebot.mixin.ConnectionAccessor;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.PacketFlow;

/**
 * The bot's network connection: packets for it go into a channel that is
 * emptied every tick, so the server treats the bot like any other player.
 */
public final class FakeConnection extends Connection {
	private final EmbeddedChannel channel = new EmbeddedChannel();

	public FakeConnection() {
		super(PacketFlow.SERVERBOUND);
		((ConnectionAccessor) (Object) this).gladebot$setChannel(channel);
	}

	/** Throws away everything sent to the bot. */
	public void drain() {
		channel.releaseOutbound();
		channel.releaseInbound();
	}

	// Network setup the bot has no use for.

	public void setReadOnly() {
	}

	public void handleDisconnection() {
	}

	public void setListenerForServerboundHandshake(PacketListener listener) {
	}

	public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T listener) {
	}
}
