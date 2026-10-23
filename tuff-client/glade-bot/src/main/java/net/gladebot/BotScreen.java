package net.gladebot;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The bot's settings on one panel: tier, fight type (Agro or Mend), style,
 * sliders for crit chance, jump reset chance, strafing and when it pots, the
 * other switches, kits, and start. Buttons run /bot commands; sliders change
 * the settings directly (singleplayer shares them with the server) so dragging
 * doesn't spam the chat.
 */
public final class BotScreen extends Screen {
	private static final int PANEL = 0xE0141A16, EDGE = 0xFF2C3A30, GREEN = 0xFF4FB07F, TEXT = 0xFFFFFFFF, MUTED = 0xFFA9B7AD;
	private static final int W = 300, H = 324;
	private Tier tier = Settings.tier;
	private String status = "";
	private final Button[] tierButtons = new Button[Tier.values().length];

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
		int x = left() + 12, y = top() + 40, w = W - 24, hw = (w - 4) / 2;
		// tier
		int tw = (w - 8) / 3;
		for (Tier t : Tier.values()) {
			tierButtons[t.ordinal()] = addRenderableWidget(Button.builder(tierLabel(t), b -> {
				tier = t;
				Settings.jumpReset = true;
				command("bot tier " + t.name().toLowerCase());
				status = "Tier " + t.label + (anyTuning() ? " (your sliders still apply; Reset to use its own)" : "");
				for (Tier u : Tier.values()) tierButtons[u.ordinal()].setMessage(tierLabel(u));
				rebuildWidgets();
			}).bounds(x + t.ordinal() * (tw + 4), y, tw, 20).build());
		}
		y += 24;
		// fight type and style
		addRenderableWidget(Button.builder(fightLabel(), b -> {
			boolean mend = !Settings.mend;
			command("bot set fight " + (mend ? "mend" : "agro"));
			Settings.mend = mend;
			b.setMessage(fightLabel());
			status = mend ? "Mend: XP in the kits, defensive opening, mends its armour" : "Agro: no XP, a durability war: strafe, crit, few gaps";
		}).bounds(x, y, hw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Style: " + Settings.style.label), b -> {
			Settings.Style v = Settings.Style.values()[(Settings.style.ordinal() + 1) % Settings.Style.values().length];
			command("bot set style " + v.name().toLowerCase());
			b.setMessage(Component.literal("Style: " + v.label));
			status = v == Settings.Style.RANDOM ? "Style: changes as the fight goes" : "Style: " + v.label;
		}).bounds(x + hw + 4, y, hw, 20).build());
		y += 30;
		// sliders
		addRenderableWidget(slider(x, y, hw, "Crit", () -> Settings.critPct >= 0 ? Settings.critPct : Math.round(tier.critChance * 100), 100,
				v -> Settings.critPct = v, v -> v + "%"));
		addRenderableWidget(slider(x + hw + 4, y, hw, "Jump reset", () -> Settings.jumpResetPct >= 0 ? Settings.jumpResetPct : Math.round(Settings.jumpResetChance(tier) * 100), 100,
				v -> Settings.jumpResetPct = v, v -> v + "%"));
		y += 24;
		addRenderableWidget(slider(x, y, hw, "Strafe", () -> Settings.strafePct >= 0 ? Settings.strafePct : Math.round(tier.strafe * 100), 100,
				v -> Settings.strafePct = v, v -> v + "%"));
		addRenderableWidget(slider(x + hw + 4, y, hw, "Pot at", () -> Settings.potAtHearts > 0 ? Settings.potAtHearts : Math.round(tier.healAt / 2), 10,
				v -> Settings.potAtHearts = Math.max(1, v), v -> Math.max(1, v) + " hearts"));
		y += 24;
		addRenderableWidget(slider(x, y, hw, "Mend rate", () -> Settings.mendRate, 10,
				v -> Settings.mendRate = Math.max(1, v), v -> Math.max(1, v) + "/s"));
		addRenderableWidget(slider(x + hw + 4, y, hw, "Miss", () -> Settings.missPct, 100,
				v -> Settings.missPct = v, v -> v + "%"));
		y += 24;
		addRenderableWidget(Button.builder(Component.literal("Reset sliders to " + tier.label), b -> {
			command("bot set reset");
			Settings.resetTuning();
			status = "Sliders back to " + tier.label + "'s own values";
			rebuildWidgets();
		}).bounds(x, y, w, 20).build());
		y += 30;
		// switches
		addRenderableWidget(Button.builder(onOff("W-tap", Settings.wtap), b -> {
			boolean v = !Settings.wtap;
			command("bot set wtap " + (v ? "on" : "off"));
			b.setMessage(onOff("W-tap", v));
		}).bounds(x, y, hw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Deivi pop: " + Settings.DEIVI[Settings.deivi]), b -> {
			int v = (Settings.deivi + 1) % Settings.DEIVI.length;
			command("bot set deivi " + Settings.DEIVI[v].toLowerCase());
			b.setMessage(Component.literal("Deivi pop: " + Settings.DEIVI[v]));
		}).bounds(x + hw + 4, y, hw, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(onOff("Clumps", Settings.clumps), b -> {
			boolean v = !Settings.clumps;
			command("bot set clumps " + (v ? "on" : "off"));
			b.setMessage(onOff("Clumps", v));
		}).bounds(x, y, hw, 20).build());
		addRenderableWidget(Button.builder(onOff("Jump reset", Settings.jumpReset), b -> {
			boolean v = !Settings.jumpReset;
			command("bot set jumpreset " + (v ? "on" : "off"));
			b.setMessage(onOff("Jump reset", v));
		}).bounds(x + hw + 4, y, hw, 20).build());
		y += 30;
		// kits
		int kw = (w - 8) / 3;
		addRenderableWidget(Button.builder(Component.literal("Save my kit"), b -> {
			command("bot kit save me");
			status = "Your inventory is now your kit";
		}).bounds(x, y, kw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Save bot's kit"), b -> {
			command("bot kit save bot");
			status = "Your inventory is now the bot's kit";
		}).bounds(x + kw + 4, y, kw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Reset kits"), b -> {
			command("bot kit reset");
			status = "Both kits are back to the default Neth Pot kit";
		}).bounds(x + 2 * (kw + 4), y, kw, 20).build());
		y += 28;
		addRenderableWidget(Button.builder(Component.literal("Start fight").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), b -> {
			Settings.save();
			command("bot start");
			onClose();
		}).bounds(x, y, w - 64, 24).build());
		addRenderableWidget(Button.builder(Component.literal("Stop"), b -> {
			command("bot stop");
			status = "Match stopped";
		}).bounds(x + w - 60, y, 60, 24).build());
	}

	private static boolean anyTuning() {
		return Settings.critPct >= 0 || Settings.jumpResetPct >= 0 || Settings.strafePct >= 0 || Settings.potAtHearts > 0;
	}

	/** A 0..max slider that writes a setting as it moves and saves when let go. */
	private AbstractSliderButton slider(int x, int y, int w, String name, IntSupplier get, int max, IntConsumer set, IntFunction<String> show) {
		return new AbstractSliderButton(x, y, w, 20, Component.empty(), get.getAsInt() / (double) max) {
			{
				updateMessage();
			}

			private int current() {
				return (int) Math.round(value * max);
			}

			@Override
			protected void updateMessage() {
				setMessage(Component.literal(name + ": " + show.apply(current())));
			}

			@Override
			protected void applyValue() {
				set.accept(current());
				Settings.save();
			}
		};
	}

	private static Component onOff(String name, boolean on) {
		return Component.literal(name + ": ").append(on ? Component.literal("On").withStyle(ChatFormatting.GREEN) : Component.literal("Off").withStyle(ChatFormatting.RED));
	}

	private static Component fightLabel() {
		return Component.literal("Fight: ").append(Settings.mend ? Component.literal("Mend").withStyle(ChatFormatting.AQUA) : Component.literal("Agro").withStyle(ChatFormatting.RED));
	}

	private Component tierLabel(Tier t) {
		return t == tier ? Component.literal(t.label).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD) : Component.literal(t.label);
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
		g.drawString(font, "Tuning", x, y + 84, MUTED);
		g.drawString(font, "Kits (from your inventory)", x, y + 236, MUTED);
		Button chosen = tierButtons[tier.ordinal()];
		if (chosen != null) frame(g, chosen.getX() - 1, chosen.getY() - 1, chosen.getWidth() + 2, chosen.getHeight() + 2, GREEN);
		String line = status.isEmpty() ? "B opens this screen.  /bot works too." : status;
		g.drawCenteredString(font, line, width / 2, top() + H - 14, status.isEmpty() ? MUTED : GREEN);
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
	public void onClose() {
		Settings.save();
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
