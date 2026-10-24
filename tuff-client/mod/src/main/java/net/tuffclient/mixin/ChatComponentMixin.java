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
	/** Set while a line the chat filter hides is being added. */
	private boolean glade$drop;

	@ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true)
	private Component glade$chat(Component message) {
		if (TuffClient.modules() == null || message == null) return message;
		var chat = TuffClient.modules().chatPlus;
		glade$drop = chat.hidden(message.getString());
		if (!chat.isEnabled()) {
			AutoGGModule.onChat(message.getString());
			return message;
		}
		return chat.process(message, (ChatComponentAccessor) this);
	}

	@org.spongepowered.asm.mixin.injection.Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
	private void glade$filter(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (glade$drop) {
			glade$drop = false;
			ci.cancel();
		}
	}
}
