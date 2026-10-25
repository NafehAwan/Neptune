package net.tuffclient.mixin;

import net.minecraft.world.item.ItemStack;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shiny Pots: potions get the enchantment glint. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Inject(method = "hasFoil", at = @At("HEAD"), cancellable = true)
	private void glade$shinyPots(CallbackInfoReturnable<Boolean> cir) {
		var modules = TuffClient.modules();
		if (modules == null || !modules.shinyPots.isEnabled()) return;
		ItemStack self = (ItemStack) (Object) this;
		if (modules.shinyPots.shines(self)) cir.setReturnValue(true);
	}
}
