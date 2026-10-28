package net.tuffclient.module;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

/**
 * Holds one of the game's own options at a module's value while the module is
 * on, and gives the player's value back when it turns off. If the player
 * changes the option in the game's settings meanwhile, that becomes the value
 * to give back. The player's value is also kept in Glade's config, so it
 * comes back even after the game closes (or crashes) with the module on.
 */
public final class OptionOverride<T> {
	private static final List<OptionOverride<?>> ALL = new ArrayList<>();

	private final String key;
	private final Function<Options, OptionInstance<T>> option;
	private final Function<JsonElement, T> parse;
	private T original;
	private T applied;

	public OptionOverride(String key, Function<Options, OptionInstance<T>> option, Function<JsonElement, T> parse) {
		this.key = key;
		this.option = option;
		this.parse = parse;
		ALL.add(this);
	}

	/** Sets the option to {@code value} (call every tick while on). */
	public void hold(T value) {
		OptionInstance<T> o = option.apply(Minecraft.getInstance().options);
		T now = o.get();
		// after a restart the option still has our value; the player's is the saved one
		if (applied == null && original != null) applied = now;
		if (applied == null || !now.equals(applied)) original = now;
		if (!now.equals(value)) o.set(value);
		applied = o.get();
	}

	/** Gives the player's own value back. */
	public void release() {
		if (original == null) return;
		OptionInstance<T> o = option.apply(Minecraft.getInstance().options);
		if (applied == null || o.get().equals(applied)) o.set(original);
		original = null;
		applied = null;
	}

	/** Puts every option back before the game saves its options on the way out. */
	public static void releaseAll() {
		for (OptionOverride<?> o : ALL) o.release();
	}

	public void save(JsonObject json) {
		if (original instanceof Number n) json.addProperty(key, n);
		else if (original instanceof Boolean b) json.addProperty(key, b);
	}

	public void load(JsonObject json) {
		if (json.has(key)) {
			try {
				original = parse.apply(json.get(key));
			} catch (RuntimeException e) {
				original = null;
			}
		}
	}
}
