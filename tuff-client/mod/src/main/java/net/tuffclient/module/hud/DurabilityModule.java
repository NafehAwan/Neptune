package net.tuffclient.module.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ColorSetting;
import net.tuffclient.setting.NumberSetting;

/** Warns when armor or a held tool is about to break. */
public final class DurabilityModule extends HudModule {
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
	private final NumberSetting threshold = add(new NumberSetting("threshold", "Warn below (%)", 10, 3, 50, 1));
	private final BoolSetting pulse = add(new BoolSetting("pulse", "Pulse", true));
	private final BoolSetting sound = add(new BoolSetting("sound", "Play a sound", true));
	private final ColorSetting warnColor = add(new ColorSetting("warn_color", "Warning color", 0xFFFF6B6B, false));
	private final java.util.Set<String> warned = new java.util.HashSet<>();

	public DurabilityModule() {
		super("durability", "Durability Warning", "Flashes when your armor or tools are about to break.", 0.44, 0.66, true);
	}

	private record Low(ItemStack stack, int percent) {
	}

	private List<Low> low(LocalPlayer player) {
		List<Low> out = new ArrayList<>();
		if (player == null) return out;
		for (EquipmentSlot slot : SLOTS) {
			ItemStack s = player.getItemBySlot(slot);
			if (s.isEmpty() || !s.isDamageableItem()) continue;
			int pct = Math.round((s.getMaxDamage() - s.getDamageValue()) * 100f / s.getMaxDamage());
			if (pct <= threshold.get()) out.add(new Low(s, pct));
		}
		return out;
	}

	@Override
	public void tick(Minecraft client) {
		if (!sound.get()) return;
		java.util.Set<String> now = new java.util.HashSet<>();
		for (Low l : low(client.player)) now.add(l.stack().getItem().toString());
		for (String id : now) {
			if (warned.add(id)) client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK, 1.4f));
		}
		warned.retainAll(now);
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font font, boolean preview) {
		List<Low> list = low(Minecraft.getInstance().player);
		if (list.isEmpty() && preview) list = List.of(new Low(new ItemStack(Items.DIAMOND_CHESTPLATE), 7));
		if (list.isEmpty()) {
			width = 0;
			height = 0;
			return;
		}
		float t = pulse.get() ? (float) (0.55 + 0.45 * Math.sin(System.currentTimeMillis() / 160.0)) : 1;
		int color = Math.round((warnColor.argb() >>> 24) * t) << 24 | (warnColor.argb() & 0xFFFFFF);
		int p = Math.max(1, pad() - 2), w = 0;
		for (Low l : list) w = Math.max(w, 20 + font.width(label(l)));
		box(g, w + p * 2, list.size() * 17 - 1 + p * 2);
		int y = p;
		for (Low l : list) {
			g.item(l.stack(), p, y);
			text(g, font, label(l), p + 19, y + 4, color);
			y += 17;
		}
	}

	private static String label(Low l) {
		return l.percent() + "% left";
	}
}
