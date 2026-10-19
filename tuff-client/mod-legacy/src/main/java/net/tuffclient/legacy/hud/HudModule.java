package net.tuffclient.legacy.hud;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.tuffclient.legacy.gui.Draw;
import net.tuffclient.legacy.module.Category;
import net.tuffclient.legacy.module.Module;
import net.tuffclient.legacy.setting.BoolSetting;
import net.tuffclient.legacy.setting.ColorSetting;
import net.tuffclient.legacy.setting.NumberSetting;

/** A HUD element: placed anywhere in the HUD editor, styled on its own. */
public abstract class HudModule extends Module {
	/** Position as a fraction of the screen, so it survives window resizes. */
	public double x, y;
	private final double defaultX, defaultY;
	public final NumberSetting scale = add(new NumberSetting("scale", "Size", 1, 0.5, 2.5, 0.05));
	public final ColorSetting text = add(new ColorSetting("text", "Text color", 0xFFFFFFFF));
	public final ColorSetting label = add(new ColorSetting("label", "Label color", 0xFFB8C2B9));
	public final BoolSetting shadow = add(new BoolSetting("shadow", "Text shadow", true));
	public final BoolSetting background = add(new BoolSetting("background", "Background", true));
	public final ColorSetting backgroundColor = add(new ColorSetting("background_color", "Background color", 0x66000000));

	protected HudModule(String id, String name, String description, double x, double y, boolean enabled) {
		super(id, name, description, Category.HUD, enabled);
		this.x = this.defaultX = x;
		this.y = this.defaultY = y;
	}

	protected static MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}

	/** Size in unscaled pixels. */
	public abstract int width();

	public abstract int height();

	/** Draws at (0, 0); preview is true in the HUD editor (show sample values). */
	protected abstract void draw(boolean preview);

	public boolean showBackground() {
		return background.get();
	}

	public void resetPosition() {
		x = defaultX;
		y = defaultY;
	}

	/** Top-left corner on screen, kept inside it. */
	public float[] bounds(Window window) {
		float s = scale.get().floatValue();
		float w = width() * s, h = height() * s;
		float sw = (float) window.getScaledWidth(), sh = (float) window.getScaledHeight();
		float px = Math.max(0, Math.min(sw - w, (float) (x * sw)));
		float py = Math.max(0, Math.min(sh - h, (float) (y * sh)));
		return new float[] {px, py, w, h};
	}

	public void render(Window window, boolean preview) {
		float[] b = bounds(window);
		GlStateManager.pushMatrix();
		GlStateManager.translate(b[0], b[1], 0);
		GlStateManager.scale(scale.get().floatValue(), scale.get().floatValue(), 1);
		if (showBackground()) Draw.round(0, 0, width(), height(), 3, backgroundColor.argb());
		draw(preview);
		GlStateManager.popMatrix();
	}

	/** "Label value" on one line, each in its own color. */
	protected void pair(String labelText, String value, int px, int py) {
		int lw = labelText.isEmpty() ? 0 : Draw.width(labelText + " ");
		if (!labelText.isEmpty()) mc().textRenderer.draw(labelText, px, py, label.argb(), shadow.get());
		mc().textRenderer.draw(value, px + lw, py, text.argb(0.1f), shadow.get());
	}

	protected static int pairWidth(String labelText, String value) {
		return (labelText.isEmpty() ? 0 : Draw.width(labelText + " ")) + Draw.width(value);
	}

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		json.addProperty("x", x);
		json.addProperty("y", y);
		return json;
	}

	@Override
	public void load(JsonObject json) {
		super.load(json);
		if (json.has("x")) x = json.get("x").getAsDouble();
		if (json.has("y")) y = json.get("y").getAsDouble();
	}
}
