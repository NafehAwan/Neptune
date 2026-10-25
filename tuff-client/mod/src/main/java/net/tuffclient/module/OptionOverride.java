package net.tuffclient.module;

import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

/**
 * Holds one of the game's own options at a module's value while the module is
 * on, and gives the player's value back when it turns off. If the player
 * changes the option in the game's settings meanwhile, that becomes the value
 * to give back.
 */
public final class OptionOverride<T> {
	private final Function<Options, OptionInstance<T>> option;
	private T original;
	private T applied;

	public OptionOverride(Function<Options, OptionInstance<T>> option) {
		this.option = option;
	}

	/** Sets the option to {@code value} (call every tick while on). */
	public void hold(T value) {
		OptionInstance<T> o = option.apply(Minecraft.getInstance().options);
		T now = o.get();
		if (applied == null || !now.equals(applied)) original = now;
		if (!now.equals(value)) o.set(value);
		applied = o.get();
	}

	/** Gives the player's own value back. */
	public void release() {
		if (original == null) return;
		OptionInstance<T> o = option.apply(Minecraft.getInstance().options);
		if (applied != null && o.get().equals(applied)) o.set(original);
		original = null;
		applied = null;
	}
}
