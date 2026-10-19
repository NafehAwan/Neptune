package net.tuffclient.legacy.mixin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.tuffclient.legacy.Keys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds Glade's keys to the Controls screen (and options.txt) before the options load. */
@Mixin(GameOptions.class)
public abstract class GameOptionsMixin {
	@Shadow
	public KeyBinding[] allKeys;

	@Inject(method = "load()V", at = @At("HEAD"))
	private void glade$keys(CallbackInfo ci) {
		List<KeyBinding> list = new ArrayList<KeyBinding>(Arrays.asList(allKeys));
		for (KeyBinding k : Keys.ALL) if (!list.contains(k)) list.add(k);
		allKeys = list.toArray(new KeyBinding[0]);
	}
}
