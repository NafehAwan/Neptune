package net.tuffclient.module.hud;

import net.tuffclient.gui.Gfx;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ChoiceSetting;

/** Totals for the items you care about, across your whole inventory. */
public final class ItemCounterModule extends HudModule {
	private record Tracked(BoolSetting on, Item[] items, int sample) {
	}

	private final List<Tracked> tracked = new ArrayList<>();
	private final ChoiceSetting layout = add(new ChoiceSetting("layout", "Layout", 0, "Column", "Row"));
	private final BoolSetting hideZero = add(new BoolSetting("hide_zero", "Hide items you have none of", true));

	public ItemCounterModule() {
		super("item_counter", "Item Counter", "How many arrows, totems, golden apples and pearls you're carrying.", 0.9, 0.42, false);
		track("arrows", "Arrows", true, 64, Items.ARROW, Items.SPECTRAL_ARROW, Items.TIPPED_ARROW);
		track("totems", "Totems", true, 2, Items.TOTEM_OF_UNDYING);
		track("gapples", "Golden apples", true, 12, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE);
		track("pearls", "Ender pearls", true, 16, Items.ENDER_PEARL);
		track("xp", "Bottles o' Enchanting", false, 32, Items.EXPERIENCE_BOTTLE);
		track("fireworks", "Firework rockets", false, 48, Items.FIREWORK_ROCKET);
		track("food", "Steak and bread", false, 24, Items.COOKED_BEEF, Items.BREAD, Items.GOLDEN_CARROT);
	}

	private void track(String id, String name, boolean on, int sample, Item... items) {
		tracked.add(new Tracked(add(new BoolSetting(id, name, on)), items, sample));
	}

	private static int count(Inventory inv, Item[] items) {
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			for (Item item : items) {
				if (s.is(item)) {
					n += s.getCount();
					break;
				}
			}
		}
		return n;
	}

	@Override
	public void render(Gfx g, Font font, boolean preview) {
		LocalPlayer player = Minecraft.getInstance().player;
		List<ItemStack> icons = new ArrayList<>();
		List<String> counts = new ArrayList<>();
		for (Tracked t : tracked) {
			if (!t.on().get()) continue;
			int n = player == null ? t.sample() : count(player.getInventory(), t.items());
			if (n == 0 && hideZero.get() && !preview) continue;
			icons.add(new ItemStack(t.items()[0]));
			counts.add(String.valueOf(n));
		}
		if (icons.isEmpty()) {
			if (preview) {
				box(g, width(font, "Items") + pad() * 2, linesHeight(1));
				label(g, font, "Items", pad(), pad());
			} else {
				width = 0;
				height = 0;
			}
			return;
		}
		int p = Math.max(1, pad() - 2);
		boolean row = layout.get() == 1;
		int cell = 0;
		for (String c : counts) cell = Math.max(cell, 19 + width(font, c));
		int w = row ? icons.size() * (cell + 4) - 4 : cell, h = row ? 16 : icons.size() * 17 - 1;
		box(g, w + p * 2, h + p * 2);
		for (int i = 0; i < icons.size(); i++) {
			int x = p + (row ? i * (cell + 4) : 0), y = p + (row ? 0 : i * 17);
			g.item(icons.get(i), x, y);
			if (counts.get(i).equals("0")) label(g, font, counts.get(i), x + 18, y + 4);
			else value(g, font, counts.get(i), x + 18, y + 4);
		}
	}
}
