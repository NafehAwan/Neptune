package net.tuffclient.gui;

import java.util.List;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.tuffclient.TuffClient;
import net.tuffclient.TuffConfig;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;

/**
 * Glade's menu (Right Shift), laid out like the game's options screens: every
 * module as an ON/OFF button with an Options button beside it, grouped by
 * category, and Edit HUD and Done at the bottom.
 */
public final class GladeMenuScreen extends VanillaScreen {
	private static final String[][] GROUPS = {{"HUD", "HUD"}, {"VISUAL", "PvP & Visual"}, {"UTILITY", "Utility"}};

	public GladeMenuScreen(Screen parent) {
		super(Component.literal("Glade"), parent);
	}

	@Override
	protected void layout(Gfx g) {
		title(g);
		int w = 310, x = centerX() - w / 2;
		int y = beginList(g);
		String hint = null;
		for (String[] group : GROUPS) {
			List<Module> mods = TuffClient.modules().visible().stream().filter(m -> m.category == Category.valueOf(group[0])).toList();
			if (mods.isEmpty()) continue;
			y = heading(g, group[1], y);
			for (Module m : mods) {
				boolean options = m.settings().stream().anyMatch(s -> s.visible());
				int bw = options ? w - 84 : w;
				button(g, x, y, bw, onOff(m.name, m.isEnabled()), true, () -> {
					m.setEnabled(!m.isEnabled());
					TuffConfig.save(TuffClient.modules());
				});
				if (options) button(g, x + bw + 4, y, 80, Component.literal("Options..."), true, () -> minecraft.setScreen(new ModuleScreen(this, m)));
				if (hovered(x, y, bw, 20) && hovered(0, HEADER, width, height - HEADER - FOOTER)) hint = m.description;
				y += ROW;
			}
		}
		endList(g, y);
		int by = height - FOOTER + 7;
		if (minecraft.level != null) button(g, centerX() - 154, by, 150, Component.literal("Edit HUD Layout..."), false, () -> minecraft.setScreen(new HudLayoutScreen(this)));
		else button(g, centerX() - 154, by, 150, 20, Component.literal("Edit HUD Layout..."), false, false, c -> {
		});
		button(g, centerX() + 4, by, 150, Component.translatable("gui.done"), false, this::onClose);
		if (hint != null) tooltip(g, hint);
	}

	@Override
	public void onClose() {
		TuffConfig.save(TuffClient.modules());
		super.onClose();
	}
}
