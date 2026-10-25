package net.tuffclient.module.visual;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.tuffclient.TuffClient;
import net.tuffclient.compat.Images;
import net.tuffclient.compat.TextureWrite;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.ColorSetting;

/**
 * Recolours the enchantment glint. The glint textures are repainted in place
 * in the chosen colour (keeping their brightness pattern), and put back when
 * the module turns off. Redone after the game reloads its resources.
 */
public final class GlintColorModule extends Module {
	private static final String[] TEXTURES = {"textures/misc/enchanted_glint_item.png", "textures/misc/enchanted_glint_entity.png", "textures/misc/enchanted_glint_armor.png"};
	private final ColorSetting color = add(new ColorSetting("color", "Glint color", 0xFF4FA8FF, false));

	private record Painted(Identifier id, AbstractTexture texture, NativeImage original) {
	}

	private final List<Painted> painted = new ArrayList<>();
	private int paintedColor;
	private boolean wasLoading;

	public GlintColorModule() {
		super("glint_color", "Glint Color", "Changes the color of the enchantment glint.", Category.VISUAL, false);
	}

	@Override
	public void tick(Minecraft mc) {
		boolean loading = net.tuffclient.Mc.overlay() != null;
		// a resource reload replaces the textures, so paint again once it's done
		if (wasLoading && !loading) forget();
		wasLoading = loading;
		if (loading) return;
		int c = color.argb();
		if (!painted.isEmpty() && c == paintedColor && stillOurs(mc)) return;
		paint(mc, c);
	}

	private boolean stillOurs(Minecraft mc) {
		for (Painted p : painted) if (mc.getTextureManager().getTexture(p.id()) != p.texture()) return false;
		return true;
	}

	private void paint(Minecraft mc, int argb) {
		restore();
		for (String path : TEXTURES) {
			Identifier id = Identifier.withDefaultNamespace(path);
			if (mc.getResourceManager().getResource(id).isEmpty()) continue;
			try (InputStream in = mc.getResourceManager().open(id)) {
				NativeImage original = NativeImage.read(in);
				AbstractTexture tex = mc.getTextureManager().getTexture(id);
				NativeImage tinted = tint(original, argb);
				TextureWrite.write(tex, tinted);
				tinted.close();
				painted.add(new Painted(id, tex, original));
			} catch (Exception e) {
				TuffClient.LOGGER.warn("Couldn't recolor {}", id, e);
			}
		}
		paintedColor = argb;
	}

	/** The glint's brightness pattern in a new colour. */
	private static NativeImage tint(NativeImage src, int argb) {
		int w = src.getWidth(), h = src.getHeight();
		NativeImage out = new NativeImage(w, h, false);
		float max = 1;
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) max = Math.max(max, lum(Images.getPixel(src, x, y)));
		int tr = argb >> 16 & 0xFF, tg = argb >> 8 & 0xFF, tb = argb & 0xFF;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int p = Images.getPixel(src, x, y);
				float k = lum(p) / max;
				int r = Math.min(255, Math.round(tr * k)), g = Math.min(255, Math.round(tg * k)), b = Math.min(255, Math.round(tb * k));
				Images.setPixel(out, x, y, (p & 0xFF000000) | r << 16 | g << 8 | b);
			}
		}
		return out;
	}

	private static float lum(int p) {
		return (p >> 16 & 0xFF) * 0.3f + (p >> 8 & 0xFF) * 0.59f + (p & 0xFF) * 0.11f;
	}

	/** Puts the original glint back. */
	private void restore() {
		Minecraft mc = Minecraft.getInstance();
		for (Painted p : painted) {
			try {
				if (mc.getTextureManager().getTexture(p.id()) == p.texture()) TextureWrite.write(p.texture(), p.original());
			} catch (Exception e) {
				TuffClient.LOGGER.warn("Couldn't restore {}", p.id(), e);
			}
			p.original().close();
		}
		painted.clear();
	}

	/** After a reload the game has fresh textures; drop ours without writing. */
	private void forget() {
		for (Painted p : painted) p.original().close();
		painted.clear();
	}

	@Override
	protected void onDisable() {
		restore();
	}
}
