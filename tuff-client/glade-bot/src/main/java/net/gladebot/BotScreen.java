package net.gladebot;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The bot's settings on one panel: pick a tier, mend on or off, save kits,
 * and start. Every button runs a /bot command, so it all works the same as typing them.
 */
public final class BotScreen extends Screen {
	private static final int PANEL = 0xE0141A16, EDGE = 0xFF2C3A30, GREEN = 0xFF4FB07F, TEXT = 0xFFFFFFFF, MUTED = 0xFFA9B7AD;
	private static final int W = 264, H = 280;
	private Tier tier = Settings.tier;
	private boolean mend = Settings.mend;
	private String status = "";
	private final Button[] tierButtons = new Button[Tier.values().length];
	private Button mendButton;

	public BotScreen() {
		super(Component.literal("Glade Bot"));
	}

	private int left() {
		return width / 2 - W / 2;
	}

	private int top() {
		return height / 2 - H / 2;
	}

	@Override
	protected void init() {
		int x = left() + 12, y = top() + 46, w = W - 24;
		// tier: three buttons, the chosen one in green
		int tw = (w - 8) / 3;
		for (Tier t : Tier.values()) {
			int i = t.ordinal();
			tierButtons[i] = addRenderableWidget(Button.builder(tierLabel(t), b -> {
				tier = t;
				command("bot tier " + t.name().toLowerCase());
				status = "Tier set to " + t.label;
				refreshLabels();
			}).bounds(x + i * (tw + 4), y, tw, 20).build());
		}
		y += 38;
		mendButton = addRenderableWidget(Button.builder(mendLabel(), b -> {
			mend = !mend;
			command("bot mend " + (mend ? "on" : "off"));
			status = mend ? "Mend on: XP in the kits, the bot repairs its armour" : "Mend off: no XP";
			refreshLabels();
		}).bounds(x, y, w, 20).build());
		y += 24;
		// play settings, two per row
		int hw = (w - 4) / 2;
		addRenderableWidget(Button.builder(onOff("Jump reset", Settings.jumpReset), b -> {
			boolean v = !Settings.jumpReset;
			command("bot set jumpreset " + (v ? "on" : "off"));
			b.setMessage(onOff("Jump reset", v));
			status = v ? "Jump reset on: it jumps as it gets hit" : "Jump reset off";
		}).bounds(x, y, hw, 20).build());
		addRenderableWidget(Button.builder(onOff("W-tap", Settings.wtap), b -> {
			boolean v = !Settings.wtap;
			command("bot set wtap " + (v ? "on" : "off"));
			b.setMessage(onOff("W-tap", v));
			status = v ? "W-tap on" : "W-tap off";
		}).bounds(x + hw + 4, y, hw, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(Component.literal("Deivi pop: " + Settings.DEIVI[Settings.deivi]), b -> {
			int v = (Settings.deivi + 1) % Settings.DEIVI.length;
			command("bot set deivi " + Settings.DEIVI[v].toLowerCase());
			b.setMessage(Component.literal("Deivi pop: " + Settings.DEIVI[v]));
			status = "Deivi pop: " + Settings.DEIVI[v];
		}).bounds(x, y, hw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Style: " + Settings.style.label), b -> {
			Settings.Style v = Settings.Style.values()[(Settings.style.ordinal() + 1) % Settings.Style.values().length];
			command("bot set style " + v.name().toLowerCase());
			b.setMessage(Component.literal("Style: " + v.label));
			status = v == Settings.Style.RANDOM ? "Style: changes as the fight goes" : "Style: " + v.label;
		}).bounds(x + hw + 4, y, hw, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(onOff("Clumps", Settings.clumps), b -> {
			boolean v = !Settings.clumps;
			command("bot set clumps " + (v ? "on" : "off"));
			b.setMessage(onOff("Clumps", v));
			status = v ? "Clumps on: XP is picked up all at once" : "Clumps off";
		}).bounds(x, y, hw, 20).build());
		y += 38;
		int kw = (w - 8) / 3;
		addRenderableWidget(Button.builder(Component.literal("Save mine"), b -> {
			command("bot kit save me");
			status = "Your inventory is now your kit";
		}).bounds(x, y, kw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Save bot's"), b -> {
			command("bot kit save bot");
			status = "Your inventory is now the bot's kit";
		}).bounds(x + kw + 4, y, kw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
			command("bot kit reset");
			status = "Both kits are back to the default Neth Pot kit";
		}).bounds(x + 2 * (kw + 4), y, kw, 20).build());
		y += 34;
		addRenderableWidget(Button.builder(Component.literal("Start fight").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), b -> {
			command("bot start");
			onClose();
		}).bounds(x, y, w - 64, 24).build());
		addRenderableWidget(Button.builder(Component.literal("Stop"), b -> {
			command("bot stop");
			status = "Match stopped";
		}).bounds(x + w - 60, y, 60, 24).build());
	}

	private static Component onOff(String name, boolean on) {
		return Component.literal(name + ": ").append(on ? Component.literal("On").withStyle(ChatFormatting.GREEN) : Component.literal("Off").withStyle(ChatFormatting.RED));
	}

	private Component tierLabel(Tier t) {
		return t == tier ? Component.literal(t.label).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD) : Component.literal(t.label);
	}

	private Component mendLabel() {
		return Component.literal("Mend: ").append(mend ? Component.literal("On").withStyle(ChatFormatting.GREEN) : Component.literal("Off").withStyle(ChatFormatting.RED));
	}

	private void refreshLabels() {
		for (Tier t : Tier.values()) tierButtons[t.ordinal()].setMessage(tierLabel(t));
		mendButton.setMessage(mendLabel());
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		int x = left(), y = top();
		g.fill(x - 1, y - 1, x + W + 1, y + H + 1, EDGE);
		g.fill(x, y, x + W, y + H, PANEL);
		g.fill(x, y, x + W, y + 2, GREEN);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int x = left() + 12, y = top();
		g.drawString(font, Component.literal("Glade Bot").withStyle(ChatFormatting.BOLD), x, y + 10, TEXT);
		g.drawString(font, "Neth Pot practice", x, y + 22, MUTED);
		g.drawString(font, "Tier", x, y + 36, MUTED);
		g.drawString(font, "Mend and play", x, y + 74, MUTED);
		g.drawString(font, "Kits (from your inventory)", x, y + 184, MUTED);
		// the chosen tier gets a green frame
		Button chosen = tierButtons[tier.ordinal()];
		if (chosen != null) frame(g, chosen.getX() - 1, chosen.getY() - 1, chosen.getWidth() + 2, chosen.getHeight() + 2, GREEN);
		String line = status.isEmpty() ? "B opens this screen.  /bot works too." : status;
		g.drawCenteredString(font, line, width / 2, top() + H - 16, status.isEmpty() ? MUTED : GREEN);
	}

	private static void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	private void command(String command) {
		if (minecraft != null && minecraft.player != null) minecraft.player.connection.sendCommand(command);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
