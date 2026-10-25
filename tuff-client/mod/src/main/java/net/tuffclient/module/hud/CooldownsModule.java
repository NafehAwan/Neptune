package net.tuffclient.module.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tuffclient.compat.Cooldowns;
import net.tuffclient.gui.Gfx;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ColorSetting;

/** Ender pearl, chorus fruit, wind charge and other item cooldowns, with the time left. */
public final class CooldownsModule extends HudModule {
	private final BoolSetting bar = add(new BoolSetting("bar", "Progress bar", true));
	private final ColorSetting barColor = add(new ColorSetting("bar_color", "Bar color", 0xFF4FD98A, true)).showIf(() -> bar.get());

	/** When each item's cooldown was first seen, and how much was left then. */
	private record Start(long tick, float left) {
	}

	private final Map<Item, Start> starts = new HashMap<>();
	private long ticks;

	public CooldownsModule() {
		super("cooldowns", "Cooldowns", "Ender pearl, chorus fruit and other item cooldowns, with the time left.", 0.006, 0.5, false);
	}

	@Override
	public void tick(Minecraft client) {
		ticks++;
	}

	/** Items in your inventory that are cooling down, one per kind. */
	private List<ItemStack> cooling(LocalPlayer p) {
		List<ItemStack> out = new ArrayList<>();
		java.util.Set<Item> seen = new java.util.HashSet<>();
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty() || !seen.add(s.getItem())) continue;
			if (Cooldowns.left(p, s) > 0) out.add(s);
		}
		starts.keySet().retainAll(out.stream().map(ItemStack::getItem).toList());
		return out;
	}

	/** Seconds left, worked out from how fast the cooldown has been going down. */
	private String timeLeft(Item item, float left) {
		Start st = starts.computeIfAbsent(item, k -> new Start(ticks, left));
		long n = ticks - st.tick();
		float done = st.left() - left;
		if (n < 2 || done <= 0.001f) return "";
		float totalTicks = n / done;
		return String.format("%.1fs", left * totalTicks / 20f);
	}

	@Override
	public void render(Gfx g, Font font, boolean preview) {
		LocalPlayer p = Minecraft.getInstance().player;
		List<ItemStack> items = new ArrayList<>();
		List<Float> lefts = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		if (p != null) {
			for (ItemStack s : cooling(p)) {
				float left = Cooldowns.left(p, s);
				items.add(s);
				lefts.add(left);
				labels.add(timeLeft(s.getItem(), left));
			}
		}
		if (items.isEmpty() && preview) {
			items.add(new ItemStack(Items.ENDER_PEARL));
			lefts.add(0.6f);
			labels.add("0.6s");
		}
		if (items.isEmpty()) {
			width = 0;
			height = 0;
			return;
		}
		int pd = Math.max(1, pad() - 2);
		int textW = 0;
		for (String l : labels) textW = Math.max(textW, width(font, l.isEmpty() ? "0.0s" : l));
		int rowW = 18 + Math.max(textW, bar.get() ? 30 : 0), rowH = 17;
		box(g, rowW + pd * 2, items.size() * rowH - 1 + pd * 2);
		for (int i = 0; i < items.size(); i++) {
			int x = pd, y = pd + i * rowH;
			g.item(items.get(i), x, y);
			value(g, font, labels.get(i), x + 18, y + (bar.get() ? 1 : 4));
			if (bar.get()) {
				int bw = rowW - 18;
				g.fill(x + 18, y + 12, x + 18 + bw, y + 14, 0x55000000);
				g.fill(x + 18, y + 12, x + 18 + Math.round(bw * lefts.get(i)), y + 14, barColor.argb());
			}
		}
	}
}
