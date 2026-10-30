package net.tuffclient.gui;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.tuffclient.module.util.KeybindsModule;

/** Every action in the game's Controls, to pick one for an extra key. */
public final class ActionPickerScreen extends VanillaScreen {
	private final KeybindsModule.Bind bind;

	public ActionPickerScreen(Screen parent, KeybindsModule.Bind bind) {
		super(Component.literal("Pick an action"), parent);
		this.bind = bind;
	}

	@Override
	protected void layout(Gfx g) {
		title(g);
		int w = 310, x = centerX() - w / 2;
		int y = beginList(g);
		button(g, x, y, w, Component.literal("None (clear this slot)"), true, () -> {
			bind.action = "";
			onClose();
		});
		y += ROW;
		List<KeyMapping> all = Arrays.stream(minecraft.options.keyMappings)
				.sorted(Comparator.comparing(k -> Component.translatable(k.getName()).getString()))
				.toList();
		for (KeyMapping k : all) {
			boolean current = k.getName().equals(bind.action);
			Component name = Component.translatable(k.getName());
			button(g, x, y, w, current ? Component.literal("> ").append(name).append(" <") : name, true, () -> {
				bind.action = k.getName();
				onClose();
			});
			y += ROW;
		}
		endList(g, y);
		button(g, centerX() - 100, height - FOOTER + 7, 200, Component.translatable("gui.cancel"), false, this::onClose);
	}
}
