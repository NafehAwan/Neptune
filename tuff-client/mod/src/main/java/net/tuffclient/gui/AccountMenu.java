package net.tuffclient.gui;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.tuffclient.account.Accounts;
import net.tuffclient.account.SkinTextures;

/**
 * The account chip on the title screen and the menu it opens, drawn to the
 * Grove Glass mockup in mockup pixels: a turning 3D head of the current
 * account, every account with its own 3D head, and a form to add an offline
 * account.
 */
public final class AccountMenu {
	private record Hit(float x1, float y1, float x2, float y2, Runnable action) {
	}

	private static final float W = 320;
	/** The launcher frames big heads from further away (camera distance 37 instead of 30). */
	private static final float FAR = 30f / 37f;

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private boolean open;
	private float openAnim;
	private float chipHover;
	private boolean adding;
	private float addAnim;
	private String name = "";
	private String message;
	private float messageTime;
	private float spin;
	private float chipX1, chipY1, chipX2, chipY2;
	private float menuX1, menuY1, menuX2, menuY2;

	public AccountMenu(Screen parent) {
		this.parent = parent;
	}

	/** The chip and, while open, the menu: glass shapes {x, y, w, h, radius} in GUI units. */
	public void glass(List<float[]> out, float zoom) {
		if (chipX2 > chipX1) out.add(new float[] {chipX1 * zoom, chipY1 * zoom, (chipX2 - chipX1) * zoom, (chipY2 - chipY1) * zoom, (chipY2 - chipY1) / 2 * zoom});
		if (openAnim > 0.05f && menuY2 > menuY1) out.add(new float[] {menuX1 * zoom, menuY1 * zoom, (menuX2 - menuX1) * zoom, (menuY2 - menuY1) * zoom, 20 * zoom});
	}

	public boolean isOpen() {
		return open;
	}

	public void setOpen(boolean open) {
		this.open = open;
		if (!open) adding = false;
	}

	private static void click() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	private void hit(float x1, float y1, float x2, float y2, Runnable action) {
		hits.add(new Hit(x1, y1, x2, y2, action));
	}

	/** Draws the chip at (x, y) and, when open, the menu under it. All in mockup pixels. */
	public void render(GuiGraphicsExtractor g, float x, float y, float mouseX, float mouseY, float dt, float alpha) {
		hits.clear();
		openAnim = Theme.approach(openAnim, open ? 1 : 0, dt, 18);
		addAnim = Theme.approach(addAnim, adding ? 1 : 0, dt, 18);
		spin += dt * 42;
		if (message != null && (messageTime -= dt) <= 0) message = null;

		// chip: 36 tall, head 26, gap 10, name, 7px dot, chevron
		Accounts.Account current = Accounts.current();
		String label = current.name();
		float nameW = Ui.width(label, Ui.Font.UI_13_SB);
		float w = 5 + 26 + 10 + nameW + 10 + 7 + 10 + 16 + 14, h = 36;
		boolean over = Theme.inside(mouseX, mouseY, x, y, x + w, y + h);
		chipHover = Theme.approach(chipHover, over || open ? 1 : 0, dt, 16);
		chipX1 = x;
		chipY1 = y;
		chipX2 = x + w;
		chipY2 = y + h;
		Ui.shadowOutside(g, x, y, w, h, 16, 0.12f * alpha);
		Ui.round(g, x, y, w, h, h / 2, Theme.alpha(Theme.mix(0x61FFFFFF, 0xB3FFFFFF, chipHover), alpha), Theme.alpha(0xB3FFFFFF, alpha), 1);
		SkinHead.draw(g, SkinTextures.of(current), x + 5 + 13, y + h / 2, 26, -32, -20, alpha);
		float tx = x + 5 + 26 + 10;
		Ui.textAt(g, label, tx, Ui.mid(y + h / 2, 13), Theme.alpha(Theme.TEXT, alpha), Ui.Font.UI_13_SB);
		Ui.circle(g, tx + nameW + 10 + 3.5f, y + h / 2, 3.5f, Theme.alpha(current.microsoft() ? Theme.ONLINE : Theme.OFFLINE, alpha));
		Ui.lucide(g, "chevron", tx + nameW + 10 + 7 + 10, y + h / 2 - 8, 16, Theme.alpha(Theme.MUTED, alpha));
		hit(x, y, x + w, y + h, () -> {
			click();
			setOpen(!open);
		});

		if (openAnim > 0.01f) menu(g, x, 64 - (1 - openAnim) * 8, mouseX, mouseY, current, alpha * openAnim);
	}

	private void menu(GuiGraphicsExtractor g, float x, float y, float mouseX, float mouseY, Accounts.Account current, float a) {
		List<Accounts.Account> accounts = Accounts.list();
		float h = 8 + 112 + 7 + 23.65f + accounts.size() * 46 + 13 + 80 + addAnim * 52 + (message != null ? 26 : 0) + 8;
		menuX1 = x;
		menuY1 = y;
		menuX2 = x + W;
		menuY2 = y + h;
		Ui.frost(g, x, y, W, h, 20, 0x61FFFFFF, 0.18f, a);
		// clicks inside the panel never fall through to the screen
		hit(x, y, x + W, y + h, () -> { });
		float ix = x + 8, iw = W - 16;

		// hero: 112 tall, the head turning in an 84px box, then name, kind and two chips
		float hy = y + 8;
		SkinHead.draw(g, SkinTextures.of(current), ix + 14 + 42, hy + 56, 84 * FAR, -28 + spin, -18, a);
		float textX = ix + 14 + 84 + 16;
		float top = hy + 20;
		Ui.textAt(g, Ui.ellipsize(current.name(), Ui.Font.HEAD_22, (int) (x + W - 14 - textX)), textX, top + 22 * 0.978f, Theme.alpha(Theme.TEXT, a), Ui.Font.HEAD_22);
		top += 22 * 1.233f + 2;
		Ui.textAt(g, current.microsoft() ? "Microsoft account · skin from Minecraft" : "Offline account · default skin", textX, top + 12 * 1.038f, Theme.alpha(Theme.MUTED, a), Ui.Font.UI_12);
		top += 12 * 1.26f + 8;
		float chipX = textX;
		chipX = pill(g, chipX, top, "Change skin", mouseX, mouseY, a, () -> Minecraft.getInstance().gui.setScreen(new SkinCustomizationScreen(parent, Minecraft.getInstance().options)));
		pill(g, chipX + 6, top, "Profile", mouseX, mouseY, a, () -> say(current.microsoft() ? "Edit your profile on minecraft.net or in the Glade launcher" : "Offline accounts have no online profile"));

		float cy = hy + 112;
		line(g, ix + 6, cy, iw - 12, a);
		cy += 7;
		Ui.spaced(g, "ACCOUNTS", ix + 10, cy + 4 + 10.5f * 1.005f - 7, Theme.alpha(Theme.MUTED, a), Ui.Font.MONO_10_5, 1.26f);
		cy += 4 + 13.65f + 6;

		for (Accounts.Account account : accounts) {
			boolean active = Accounts.isCurrent(account);
			boolean over = Theme.inside(mouseX, mouseY, ix, cy, ix + iw, cy + 46);
			if (active || over) Ui.round(g, ix, cy, iw, 46, 12, Theme.alpha(active ? 0x80FFFFFF : 0x4DFFFFFF, a));
			SkinHead.draw(g, SkinTextures.of(account), ix + 10 + 14, cy + 23, 28, -32, -20, a);
			float nx = ix + 10 + 28 + 12;
			String kind = account.microsoft() ? "Microsoft" : "Offline · singleplayer and offline servers";
			Ui.textAt(g, Ui.ellipsize(account.name(), Ui.Font.UI_13_5_SB, (int) (iw - 90)), nx, cy + 7.25f + 13.5f * 1.038f, Theme.alpha(Theme.TEXT, a), Ui.Font.UI_13_5_SB);
			Ui.textAt(g, Ui.ellipsize(kind, Ui.Font.UI_11_5, (int) (iw - 90)), nx, cy + 7.25f + 17 + 11.5f * 1.038f, Theme.alpha(Theme.MUTED, a), Ui.Font.UI_11_5);
			if (active) {
				Ui.lucide(g, "check", ix + iw - 10 - 16, cy + 15, 16, Theme.alpha(Theme.COPPER, a));
			} else if (account.addedInGame() && over) {
				float bx = ix + iw - 10 - 18, by = cy + 14;
				boolean overDel = Theme.inside(mouseX, mouseY, bx, by, bx + 18, by + 18);
				Ui.lucide(g, "trash", bx + 2, by + 2, 14, Theme.alpha(overDel ? Theme.DANGER : Theme.MUTED, a));
				hit(bx, by, bx + 18, by + 18, () -> {
					click();
					Accounts.remove(account);
				});
			}
			final float rowY = cy;
			if (!active) hits.add(hits.size() - (account.addedInGame() && over ? 1 : 0), new Hit(ix, rowY, ix + iw, rowY + 46, () -> {
				click();
				String why = Accounts.switchTo(account);
				if (why != null) say(why);
			}));
			cy += 46;
		}

		cy += 6;
		line(g, ix + 6, cy, iw - 12, a);
		cy += 7;
		cy = action(g, mouseX, mouseY, ix, cy, iw, "plus", "Add Microsoft account", false, a, () -> say("Add Microsoft accounts in the Glade launcher"));
		cy = action(g, mouseX, mouseY, ix, cy, iw, "user_plus", "Add offline account", adding, a, () -> {
			adding = !adding;
			name = "";
		});
		if (addAnim > 0.02f) {
			float fa = a * addAnim;
			float fy = cy + 6, fx = ix + 6;
			float bw = 16 + Ui.width("Add", Ui.Font.UI_13_5_SB) + 16;
			float fw = iw - 12 - 8 - bw;
			boolean valid = Accounts.validName(name);
			Ui.round(g, fx, fy, fw, 36, 10, Theme.alpha(0xA6FFFFFF, fa), Theme.alpha(Theme.alpha(Theme.COPPER, 0.45f), fa), 1);
			boolean blink = (System.nanoTime() / 530_000_000L) % 2 == 0;
			if (name.isEmpty()) Ui.textAt(g, "Username", fx + 12, Ui.mid(fy + 18, 12.5f), Theme.alpha(Theme.MUTED, fa), Ui.Font.UI_12_5);
			else Ui.textAt(g, name, fx + 12, Ui.mid(fy + 18, 12.5f), Theme.alpha(Theme.TEXT, fa), Ui.Font.UI_12_5);
			if (blink && adding) {
				float caretX = fx + 12 + Ui.width(name, Ui.Font.UI_12_5) + 1;
				Ui.round(g, caretX, fy + 10.5f, 1.5f, 15, 0.5f, Theme.alpha(Theme.TEXT, fa));
			}
			float bx = fx + fw + 8;
			boolean overAdd = valid && Theme.inside(mouseX, mouseY, bx, fy, bx + bw, fy + 36);
			Ui.round(g, bx, fy, bw, 36, 10, Theme.alpha(valid ? (overAdd ? Theme.COPPER_HI : Theme.COPPER) : Theme.alpha(Theme.COPPER, 0.45f), fa));
			Ui.centeredAt(g, "Add", bx + bw / 2, Ui.mid(fy + 18, 13.5f), Theme.alpha(Theme.INK, fa), Ui.Font.UI_13_5_SB);
			if (valid && adding) hit(bx, fy, bx + bw, fy + 36, this::submit);
			cy += 52 * addAnim;
		}
		if (message != null) {
			cy += 6;
			Ui.textAt(g, Ui.ellipsize(message, Ui.Font.UI_12, (int) (iw - 20)), ix + 10, cy + 12 * 1.038f, Theme.alpha(Theme.MUTED, a), Ui.Font.UI_12);
		}
	}

	private static void line(GuiGraphicsExtractor g, float x, float y, float w, float a) {
		Ui.round(g, x, y, w, 1, 0.5f, Theme.alpha(0xB3FFFFFF, a));
	}

	/** The small chips under the name: 11px semibold, 3px 8px padding. */
	private float pill(GuiGraphicsExtractor g, float x, float y, String label, float mouseX, float mouseY, float a, Runnable run) {
		float w = 16 + Ui.width(label, Ui.Font.UI_11_SB), h = 6 + 11 * 1.26f;
		boolean over = Theme.inside(mouseX, mouseY, x, y, x + w, y + h);
		Ui.round(g, x, y, w, h, h / 2, Theme.alpha(over ? 0xE6FFFFFF : 0x99FFFFFF, a));
		Ui.textAt(g, label, x + 8, y + 3 + 11 * 1.038f, Theme.alpha(Theme.TEXT, a), Ui.Font.UI_11_SB);
		hit(x, y, x + w, y + h, () -> {
			click();
			run.run();
		});
		return x + w;
	}

	/** Action rows: 40 tall, a 28px tinted tile with the icon, then the label. */
	private float action(GuiGraphicsExtractor g, float mouseX, float mouseY, float x, float y, float w, String icon, String label, boolean on, float a, Runnable run) {
		boolean over = Theme.inside(mouseX, mouseY, x, y, x + w, y + 40);
		if (over || on) Ui.round(g, x, y, w, 40, 10, Theme.alpha(0x73FFFFFF, a));
		Ui.round(g, x + 10, y + 6, 28, 28, 8, Theme.alpha(0x1F2F6B4F, a));
		Ui.lucide(g, icon, x + 16, y + 12, 16, Theme.alpha(Theme.COPPER, a));
		Ui.textAt(g, label, x + 10 + 28 + 12, Ui.mid(y + 20, 13), Theme.alpha(Theme.TEXT, a), Ui.Font.UI_13_SB);
		hit(x, y, x + w, y + 40, () -> {
			click();
			run.run();
		});
		return y + 40;
	}

	private void say(String text) {
		message = text;
		messageTime = 4;
	}

	private void submit() {
		if (!Accounts.validName(name)) return;
		click();
		String why = Accounts.switchTo(Accounts.addOffline(name));
		if (why != null) say(why);
		adding = false;
		name = "";
	}

	/** True when the click was used by the chip or the menu. Coordinates in mockup pixels. */
	public boolean mouseClicked(double mx, double my) {
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (Theme.inside(mx, my, h.x1(), h.y1(), h.x2(), h.y2())) {
				h.action().run();
				return true;
			}
		}
		if (open && !Theme.inside(mx, my, menuX1, menuY1, menuX2, menuY2) && !Theme.inside(mx, my, chipX1, chipY1, chipX2, chipY2)) {
			setOpen(false);
			return true;
		}
		return false;
	}

	public boolean keyPressed(KeyEvent event) {
		if (!open) return false;
		if (event.isEscape()) {
			setOpen(false);
			return true;
		}
		if (adding && event.key() == InputConstants.KEY_BACKSPACE && !name.isEmpty()) {
			name = name.substring(0, name.length() - 1);
			return true;
		}
		if (adding && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
			submit();
			return true;
		}
		return adding;
	}

	public boolean charTyped(CharacterEvent event) {
		if (!open || !adding) return false;
		String c = event.codepointAsString();
		if (c.matches("[A-Za-z0-9_]") && name.length() < 16) name += c;
		return true;
	}
}
