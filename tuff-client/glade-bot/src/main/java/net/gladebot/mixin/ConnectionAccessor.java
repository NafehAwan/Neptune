package net.gladebot.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the bot's connection have a channel that goes nowhere (see FakeConnection). */
@Mixin(Connection.class)
public interface ConnectionAccessor {
	@Accessor("channel")
	void gladebot$setChannel(Channel channel);
}
