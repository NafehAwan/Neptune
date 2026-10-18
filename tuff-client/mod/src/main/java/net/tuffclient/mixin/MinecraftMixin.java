package net.tuffclient.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.tuffclient.account.Accounts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** After an in-game account switch, singleplayer uses the new account's profile. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "getGameProfile", at = @At("HEAD"), cancellable = true)
	private void glade$switchedProfile(CallbackInfoReturnable<GameProfile> cir) {
		GameProfile profile = Accounts.switchedProfile();
		if (profile != null) cir.setReturnValue(profile);
	}
}
