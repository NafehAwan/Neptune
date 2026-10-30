package net.tuffclient.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keybinds: a key the game passes on (in game, no screen open) also reaches the actions it's an extra key for. */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
	@Inject(method = "click(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V", at = @At("TAIL"))
	private static void glade$click(InputConstants.Key key, CallbackInfo ci) {
		if (TuffClient.modules() != null) TuffClient.modules().keybinds.click(key);
	}

	@Inject(method = "set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V", at = @At("TAIL"))
	private static void glade$set(InputConstants.Key key, boolean down, CallbackInfo ci) {
		if (TuffClient.modules() != null) TuffClient.modules().keybinds.set(key, down);
	}
}
