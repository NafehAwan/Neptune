package net.tuffclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.tuffclient.TuffClient;
import net.tuffclient.TuffConfig;
import net.tuffclient.module.Module;
import net.tuffclient.module.hud.HudModule;
import net.tuffclient.module.util.CrosshairModule;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ChoiceSetting;
import net.tuffclient.setting.ColorSetting;
import net.tuffclient.setting.KeybindSetting;
import net.tuffclient.setting.NumberSetting;
import net.tuffclient.setting.Setting;
import net.tuffclient.setting.TextSetting;

/** One module's options, one control per row, like the game's options screens. */
public final class ModuleScreen extends VanillaScreen {
	private static final String[] LOOKS = {"Default", "Clean", "Boxed", "Outline"};

	private final Module module;
	private KeybindSetting listening;
	private TextSetting typing;

	public ModuleScreen(Screen parent, Module module) {
		super(Component.literal(module.name), parent);
		this.module = module;
	}

	@Override
	protected void layout(Gfx g) {
		title(g);
		int w = 310, x = centerX() - w / 2;
		int y = beginList(g);
		y = note(g, module.description, x, y + 2, w);
		button(g, x, y, w, onOff("Enabled", module.isEnabled()), true, () -> module.setEnabled(!module.isEnabled()));
		y += ROW;
		HudModule hud = module instanceof HudModule h ? h : null;
		for (Setting<?> s : module.settings()) {
			if (s.visible() && (hud == null || !hud.isStyle(s))) y = row(g, s, x, y, w);
		}
		if (module instanceof CrosshairModule ch) y = crosshair(g, ch, x, y, w);
		if (hud != null) {
			y = heading(g, "Style", y + 4);
			int bw = (w - 3 * 4) / 4;
			for (int i = 0; i < LOOKS.length; i++) {
				String look = LOOKS[i];
				button(g, x + i * (bw + 4), y, bw, Component.literal(look), true, () -> hud.applyLook(look));
			}
			y += ROW;
			for (Setting<?> s : module.settings()) {
				if (s.visible() && hud.isStyle(s)) y = row(g, s, x, y, w);
			}
		}
		endList(g, y);
		button(g, centerX() - 100, height - FOOTER + 7, 200, Component.translatable("gui.done"), false, this::onClose);
	}

	private int row(Gfx g, Setting<?> s, int x, int y, int w) {
		switch (s) {
			case BoolSetting b -> button(g, x, y, w, onOff(b.name, b.get()), true, b::toggle);
			case ChoiceSetting c -> button(g, x, y, w, 20, Component.literal(c.name + ": " + c.choice()), true, true, click -> c.cycle(click.button() == 1 ? -1 : 1));
			case NumberSetting n -> slider(g, x, y, w, Component.literal(n.name + ": " + n.display()), n.fraction(), true, f -> n.set(n.min + f * (n.max - n.min)));
			case ColorSetting c -> {
				Component label = Component.literal(c.name + ": ")
						.append(Component.literal("██").withStyle(st -> st.withColor(c.argb() & 0xFFFFFF)))
						.append(Component.literal(" " + c.display()));
				button(g, x, y, w, label, true, () -> minecraft.setScreen(new ColorScreen(this, c)));
			}
			case KeybindSetting k -> {
				Component label = listening == k
						? Component.literal(k.name + ": ").append(Component.literal("> " + k.display() + " <").withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE))
						: Component.literal(k.name + ": " + k.display());
				button(g, x, y, w, label, true, () -> listening = k);
			}
			case TextSetting t -> {
				g.text(font, t.name, x, y + 1, GREY, true);
				y += 11;
				textField(g, x, y, w, t.get(), typing == t, true, () -> typing = t);
			}
		}
		return y + ROW;
	}

	// ------------------------------------------------------------- crosshair

	/** Previews (normal and aiming, on dark and light) and the drawing grids. */
	private int crosshair(Gfx g, CrosshairModule ch, int x, int y, int w) {
		y = heading(g, "Preview", y + 4);
		int half = (w - 8) / 2, ph = 60;
		String[] labels = {"Normal", "Aiming"};
		for (int i = 0; i < 2; i++) {
			int bx = x + i * (half + 8);
			g.fill(bx, y, bx + half / 2, y + ph, 0xFF3A3A3A);
			g.fill(bx + half / 2, y, bx + half, y + ph, 0xFFC6C6C6);
			g.outline(bx, y, half, ph, 0xFF000000);
			boolean aiming = i == 1;
			String shape = aiming && ch.onTarget.get() == 2 ? "Target" : ch.style.choice();
			g.push();
			g.translate(bx + half / 2, y + ph / 2);
			g.scale(3);
			ch.draw(g, 0, 0, shape, aiming && ch.onTarget.get() == 1);
			g.pop();
			g.text(font, labels[i], bx + 4, y + ph - 11, WHITE, true);
		}
		y += ph + 6;
		if (ch.style.choice().equals("Draw your own")) {
			y = grid(g, ch, ch.grid, "Draw your own", "Start from a plus", () -> CrosshairModule.resetGrid(ch.grid), x, y, w);
		}
		if (ch.onTarget.get() == 2 || ch.style.choice().equals("Target")) {
			y = grid(g, ch, ch.targetGrid, "Aiming (Target) crosshair", "Reset Target", ch::resetTarget, x, y, w);
		}
		return y;
	}

	/** A 15x15 grid to draw on: click or drag; starting on a set cell erases. */
	private int grid(Gfx g, CrosshairModule ch, boolean[] cells, String title, String resetLabel, Runnable reset, int x, int y, int w) {
		y = heading(g, title, y + 4);
		int n = CrosshairModule.GRID, cell = 10, size = cell * n;
		int gx = x + (w - size) / 2, gy = y;
		g.fill(gx - 1, gy - 1, gx + size + 1, gy + size + 1, 0xFF000000);
		for (int cy = 0; cy < n; cy++) {
			for (int cx = 0; cx < n; cx++) {
				boolean on = CrosshairModule.cell(cells, cx, cy), centre = cx == n / 2 && cy == n / 2;
				int col = on ? 0xFFFFFFFF : centre ? 0xFF6B6B6B : (cx + cy) % 2 == 0 ? 0xFF3A3A3A : 0xFF333333;
				g.fill(gx + cx * cell, gy + cy * cell, gx + (cx + 1) * cell, gy + (cy + 1) * cell, col);
			}
		}
		onClickAt(gx, gy, size, size, true, c -> {
			int cx = (int) ((c.x() - gx) / cell), cy = (int) ((c.y() - gy) / cell);
			if (cx < 0 || cy < 0 || cx >= n || cy >= n) return;
			boolean paintOn = !CrosshairModule.cell(cells, cx, cy);
			ch.paint(cells, cx, cy, paintOn);
			startDrag((mx, my) -> {
				int dx = (int) ((mx - gx) / cell), dy = (int) ((my - gy) / cell);
				if (dx >= 0 && dy >= 0 && dx < n && dy < n) ch.paint(cells, dx, dy, paintOn);
			});
		});
		y = gy + size + 6;
		int bw = (w - 8) / 3;
		button(g, x, y, bw, Component.literal("Clear"), true, () -> CrosshairModule.clear(cells));
		button(g, x + bw + 4, y, bw, Component.literal(resetLabel), true, reset);
		button(g, x + 2 * (bw + 4), y, bw, onOff("Mirror", ch.mirror), true, () -> ch.mirror = !ch.mirror);
		return y + ROW;
	}

	// ----------------------------------------------------------------- input

	@Override
	protected void clickedNothing() {
		typing = null;
	}

	@Override
	protected boolean onClick(Input.Click click, boolean doubleClick) {
		if (listening != null) {
			// a mouse button becomes the key, like the game's Controls screen
			listening.bind(InputConstants.Type.MOUSE.getOrCreate(click.button()));
			listening = null;
			return true;
		}
		typing = null;
		return super.onClick(click, doubleClick);
	}

	@Override
	protected boolean onKey(Input.Key key) {
		if (listening != null) {
			if (key.isEscape()) listening.unbind();
			else listening.bind(key.bound());
			listening = null;
			return true;
		}
		if (typing != null) {
			if (key.isEscape() || key.key() == InputConstants.KEY_RETURN) typing = null;
			else if (key.key() == InputConstants.KEY_BACKSPACE && !typing.get().isEmpty()) typing.set(typing.get().substring(0, typing.get().length() - 1));
			return true;
		}
		return super.onKey(key);
	}

	@Override
	protected boolean onType(Input.Typed typed) {
		if (typing != null) {
			if (typed.isAllowedChatCharacter()) typing.set(typing.get() + typed.codepointAsString());
			return true;
		}
		return super.onType(typed);
	}

	@Override
	public void onClose() {
		TuffConfig.save(TuffClient.modules());
		super.onClose();
	}
}
