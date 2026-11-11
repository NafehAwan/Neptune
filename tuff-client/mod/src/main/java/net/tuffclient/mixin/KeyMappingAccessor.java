package net.tuffclient.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Keybinds count a press for an action reached through an extra key. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("clickCount")
	int glade$getClickCount();

	@Accessor("clickCount")
	void glade$setClickCount(int count);
}
