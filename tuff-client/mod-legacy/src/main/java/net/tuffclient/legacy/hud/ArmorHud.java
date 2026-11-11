package net.tuffclient.legacy.hud;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.item.ItemStack;
import net.tuffclient.legacy.setting.BoolSetting;
import net.tuffclient.legacy.setting.ChoiceSetting;
import net.tuffclient.legacy.setting.ColorSetting;

/** Your armor and held item, with durability colored green, yellow, orange and red. */
public final class ArmorHud extends HudModule {
	public final ChoiceSetting number = add(new ChoiceSetting("number", "Show", 0, "Points", "Percent", "Both"));
	public final BoolSetting held = add(new BoolSetting("held", "Held item", true));
	public final ColorSetting high = add(new ColorSetting("high", "Over 60%", 0xFF55FF55));
	public final ColorSetting mid = add(new ColorSetting("mid", "30% to 60%", 0xFFFFE155));
	public final ColorSetting low = add(new ColorSetting("low", "10% to 30%", 0xFFFFA040));
	public final ColorSetting critical = add(new ColorSetting("critical", "Under 10%", 0xFFFF5555));

	public ArmorHud() {
		super("armor", "Armor Status", "Your armor and held item, with durability colored from green to red.", 0.9, 0.62, true);
		background.set(false);
	}

	private List<ItemStack> items() {
		List<ItemStack> list = new ArrayList<ItemStack>();
		if (mc().player == null) return list;
		for (int i = 3; i >= 0; i--) {
			ItemStack s = mc().player.inventory.armor[i];
			if (s != null) list.add(s);
		}
		ItemStack hand = mc().player.inventory.getMainHandStack();
		if (held.get() && hand != null && hand.isDamageable()) list.add(hand);
		return list;
	}

	private String label(ItemStack s) {
		if (!s.isDamageable()) return s.count > 1 ? String.valueOf(s.count) : "";
		int left = s.getMaxDamage() - s.getDamage();
		int pct = left * 100 / Math.max(1, s.getMaxDamage());
		switch (number.get()) {
			case 1: return pct + "%";
			case 2: return left + " " + pct + "%";
			default: return String.valueOf(left);
		}
	}

	private int color(ItemStack s) {
		if (!s.isDamageable()) return text.argb();
		float f = (s.getMaxDamage() - s.getDamage()) / (float) Math.max(1, s.getMaxDamage());
		int c = f > 0.6f ? high.argb() : f > 0.3f ? mid.argb() : f > 0.1f ? low.argb() : critical.argb();
		if (f <= 0.1f && (System.currentTimeMillis() / 400) % 2 == 0) c = (c & 0x00FFFFFF) | 0x80000000;
		return c;
	}

	@Override
	public int width() {
		int w = 40;
		for (ItemStack s : items()) w = Math.max(w, 20 + mc().textRenderer.getStringWidth(label(s)));
		return w + 4;
	}

	@Override
	public int height() {
		return Math.max(1, items().size()) * 18 + 2;
	}

	@Override
	protected void draw(boolean preview) {
		List<ItemStack> list = items();
		int y = 1;
		for (ItemStack s : list) {
			GlStateManager.pushMatrix();
			DiffuseLighting.enableNormally();
			mc().getItemRenderer().renderInGuiWithOverrides(s, 2, y);
			DiffuseLighting.disable();
			GlStateManager.popMatrix();
			mc().textRenderer.draw(label(s), 21, y + 5, color(s), shadow.get());
			y += 18;
		}
		if (list.isEmpty() && preview) mc().textRenderer.draw("Armor", 4, 5, text.argb(), shadow.get());
	}
}
