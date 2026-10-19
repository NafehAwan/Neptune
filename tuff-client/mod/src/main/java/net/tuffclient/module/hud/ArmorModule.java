package net.tuffclient.module.hud;

import net.tuffclient.gui.Gfx;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tuffclient.gui.Theme;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ChoiceSetting;
import net.tuffclient.setting.ColorSetting;

/**
 * Your armor and held item with durability numbers, coloured by how much is
 * left: green, yellow, orange, red (pulsing when nearly broken).
 */
public final class ArmorModule extends HudModule {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private final ChoiceSetting number = add(new ChoiceSetting("number", "Number", 0, "Points", "Percent", "Both"));
	private final ChoiceSetting layout = add(new ChoiceSetting("layout", "Layout", 0, "Vertical", "Horizontal"));
	private final BoolSetting numbersLeft = add(new BoolSetting("numbers_left", "Numbers on the left", true));
	private final BoolSetting held = add(new BoolSetting("held", "Show held item", true));
	private final BoolSetting hideFull = add(new BoolSetting("hide_full", "Hide pieces at 100%", false));
	private final BoolSetting smooth = add(new BoolSetting("smooth", "Smooth colors", false));
	private final ColorSetting high = add(new ColorSetting("high", "75-100% color", 0xFF55FF55, true));
	private final ColorSetting mid = add(new ColorSetting("mid", "50-74% color", 0xFFFFE155, true));
	private final ColorSetting low = add(new ColorSetting("low", "25-49% color", 0xFFFFA040, true));
	private final ColorSetting critical = add(new ColorSetting("critical", "0-24% color", 0xFFFF5555, true));
	private final BoolSetting pulse = add(new BoolSetting("pulse", "Pulse under 10%", true));

	public ArmorModule() {
		super("armor", "Armor Status", "Your armor and held item, with durability colored from green to red.", 0.9, 0.62, true);
		// like the classic armor HUD: just the numbers and the items
		background.set(false);
	}

	private record Row(ItemStack stack, String label, int color) {
	}

	private int colorFor(int pct) {
		if (smooth.get()) {
			// blend between the middles of each band
			float[] at = {12.5f, 37.5f, 62.5f, 87.5f};
			int[] cs = {critical.argb(), low.argb(), mid.argb(), high.argb()};
			if (pct <= at[0]) return cs[0];
			for (int i = 0; i < 3; i++) {
				if (pct <= at[i + 1]) return Theme.mix(cs[i], cs[i + 1], (pct - at[i]) / (at[i + 1] - at[i]));
			}
			return cs[3];
		}
		return pct >= 75 ? high.argb() : pct >= 50 ? mid.argb() : pct >= 25 ? low.argb() : critical.argb();
	}

	private List<ItemStack> stacks(LocalPlayer player, boolean preview) {
		List<ItemStack> out = new ArrayList<>();
		if (player != null) {
			for (EquipmentSlot slot : ARMOR) {
				ItemStack s = player.getItemBySlot(slot);
				if (!s.isEmpty()) out.add(s);
			}
			if (held.get() && !player.getMainHandItem().isEmpty()) out.add(player.getMainHandItem());
		}
		if (out.isEmpty() && preview) {
			out.add(sample(new ItemStack(Items.DIAMOND_HELMET), 0.92f));
			out.add(sample(new ItemStack(Items.DIAMOND_CHESTPLATE), 0.6f));
			out.add(sample(new ItemStack(Items.DIAMOND_LEGGINGS), 0.35f));
			out.add(sample(new ItemStack(Items.DIAMOND_BOOTS), 0.08f));
		}
		return out;
	}

	private static ItemStack sample(ItemStack s, float left) {
		s.setDamageValue(Math.round(s.getMaxDamage() * (1 - left)));
		return s;
	}

	@Override
	public void render(Gfx g, Font font, boolean preview) {
		List<Row> rows = new ArrayList<>();
		float blink = (float) (0.55 + 0.45 * Math.sin(System.currentTimeMillis() / 160.0));
		for (ItemStack s : stacks(Minecraft.getInstance().player, preview)) {
			if (!s.isDamageableItem()) {
				rows.add(new Row(s, s.getCount() > 1 ? String.valueOf(s.getCount()) : "", textColor()));
				continue;
			}
			int left = s.getMaxDamage() - s.getDamageValue();
			int pct = Math.round(left * 100f / s.getMaxDamage());
			if (hideFull.get() && pct >= 100) continue;
			String label = switch (number.get()) {
				case 1 -> pct + "%";
				case 2 -> left + " · " + pct + "%";
				default -> String.valueOf(left);
			};
			int color = colorFor(pct);
			if (pulse.get() && pct < 10) color = Math.round((color >>> 24) * blink) << 24 | (color & 0xFFFFFF);
			rows.add(new Row(s, label, color));
		}
		if (rows.isEmpty()) {
			width = 0;
			height = 0;
			return;
		}
		int p = Math.max(1, pad() - 2);
		if (layout.get() == 1) {
			int w = 0;
			for (Row r : rows) w += 16 + (r.label().isEmpty() ? 0 : 2 + font.width(r.label())) + 6;
			box(g, w - 6 + p * 2, 16 + p * 2);
			int x = p;
			for (Row r : rows) {
				g.item(r.stack(), x, p);
				x += 16;
				if (!r.label().isEmpty()) {
					text(g, font, r.label(), x + 2, p + 4, r.color());
					x += 2 + font.width(r.label());
				}
				x += 6;
			}
			return;
		}
		int tw = 0;
		for (Row r : rows) tw = Math.max(tw, font.width(r.label()));
		int gap = tw > 0 ? 3 : 0;
		box(g, tw + gap + 16 + p * 2, rows.size() * 17 - 1 + p * 2);
		int y = p;
		for (Row r : rows) {
			int lw = font.width(r.label());
			if (numbersLeft.get()) {
				text(g, font, r.label(), p + tw - lw, y + 4, r.color());
				g.item(r.stack(), p + tw + gap, y);
			} else {
				g.item(r.stack(), p, y);
				text(g, font, r.label(), p + 16 + gap, y + 4, r.color());
			}
			y += 17;
		}
	}
}
