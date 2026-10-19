package net.tuffclient.module.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.tuffclient.gui.Ui;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.NumberSetting;

/** The resource packs you have on, top first. */
public final class PackDisplayModule extends HudModule {
	private final NumberSetting count = add(new NumberSetting("count", "Packs shown", 1, 1, 5, 1));
	private final BoolSetting icon = add(new BoolSetting("icon", "Icon", true));
	private List<String> cached = List.of();
	private long cachedAt;

	public PackDisplayModule() {
		super("pack_display", "Pack Display", "Shows the resource pack you're using.", 0.006, 0.94, false);
	}

	private List<String> titles() {
		long now = System.currentTimeMillis();
		if (now - cachedAt < 2000) return cached;
		cachedAt = now;
		PackRepository repo = Minecraft.getInstance().getResourcePackRepository();
		List<String> ids = new ArrayList<>(repo.getSelectedIds());
		java.util.Collections.reverse(ids);
		List<String> out = new ArrayList<>();
		for (String id : ids) {
			if (!id.startsWith("file/")) continue; // only packs the player added
			Pack pack = repo.getPack(id);
			out.add(pack == null ? id.substring(5) : pack.getTitle().getString().replaceAll("\\.zip$", ""));
		}
		cached = out;
		return out;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font font, boolean preview) {
		List<String> list = titles();
		if (list.isEmpty()) {
			if (!preview) {
				width = 0;
				height = 0;
				return;
			}
			list = List.of("Faithful 32x");
		}
		list = list.subList(0, Math.min(list.size(), count.get().intValue()));
		int p = pad(), iw = icon.get() ? 12 : 0, w = 0;
		for (String s : list) w = Math.max(w, font.width(s));
		box(g, iw + w + p * 2, linesHeight(list.size()));
		if (icon.get()) Ui.lucide(g, "package", p, p - 1, 9, labelColor());
		int y = p;
		for (String s : list) {
			value(g, font, s, p + iw, y);
			y += 10;
		}
	}
}
