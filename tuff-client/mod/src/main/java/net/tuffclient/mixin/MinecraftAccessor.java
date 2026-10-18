package net.tuffclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the in-game account menu switch to an offline account. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Mutable
	@Accessor("user")
	void glade$setUser(User user);
}
