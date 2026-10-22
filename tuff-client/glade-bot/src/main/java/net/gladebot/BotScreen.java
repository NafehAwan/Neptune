package net.gladebot;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Bot settings: tier, mend, kits, and start. Every button runs a /bot command. */
public final class BotScreen extends Screen {
	public BotScreen() {
		super(Component.literal("Glade Bot"));
	}

	@Override
	protected void init() {
		int w = 220, x = width / 2 - w / 2, y = height / 2 - 92;
		addRenderableWidget(new StringWidget(x, y, w, 12, Component.literal("Glade Bot · Neth Pot practice"), font));
		y += 20;
		addRenderableWidget(Button.builder(Component.literal("Tier: " + Settings.tier.label), b -> {
			Tier next = Tier.values()[(Settings.tier.ordinal() + 1) % Tier.values().length];
			command("bot tier " + next.name().toLowerCase());
			b.setMessage(Component.literal("Tier: " + next.label));
		}).bounds(x, y, w, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(Component.literal("Mend: " + (Settings.mend ? "On" : "Off")), b -> {
			boolean next = !Settings.mend;
			command("bot mend " + (next ? "on" : "off"));
			b.setMessage(Component.literal("Mend: " + (next ? "On" : "Off")));
		}).bounds(x, y, w, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(Component.literal("Save my inventory as my kit"), b -> command("bot kit save me")).bounds(x, y, w, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(Component.literal("Save my inventory as the bot's kit"), b -> command("bot kit save bot")).bounds(x, y, w, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(Component.literal("Reset both kits"), b -> command("bot kit reset")).bounds(x, y, w, 20).build());
		y += 32;
		addRenderableWidget(Button.builder(Component.literal("Start"), b -> {
			command("bot start");
			onClose();
		}).bounds(x, y, w / 2 - 2, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Stop"), b -> {
			command("bot stop");
			onClose();
		}).bounds(x + w / 2 + 2, y, w / 2 - 2, 20).build());
	}

	private void command(String command) {
		if (minecraft != null && minecraft.player != null) minecraft.player.connection.sendCommand(command);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
