package net.tuffclient.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.tuffclient.TuffClient;
import net.tuffclient.module.util.AutoGGModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Chat+ (timestamps, merged repeats, copy, highlights) and Auto GG read every chat line here. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true)
	private Component glade$chat(Component message) {
		if (TuffClient.modules() == null || message == null) return message;
		var chat = TuffClient.modules().chatPlus;
		if (!chat.isEnabled()) {
			AutoGGModule.onChat(message.getString());
			return message;
		}
		return chat.process(message, (ChatComponentAccessor) this);
	}
}
