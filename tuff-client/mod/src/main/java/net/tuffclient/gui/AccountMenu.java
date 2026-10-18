package net.tuffclient.gui;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.tuffclient.account.Accounts;
import net.tuffclient.account.SkinTextures;

/**
 * The account chip on the title screen and the menu it opens: a spinning 3D
 * head of the current account, every account with its own 3D head, and a
 * form to add an offline account.
 */
public final class AccountMenu {
	private record Hit(float x1, float y1, float x2, float y2, Runnable action) {
	}

	private static final float W = 210;

	private final List<Hit> hits = new ArrayList<>();
	private boolean open;
	private float openAnim;
	private float chipHover;
	private boolean adding;
	private String name = "";
	private String message;
	private float messageTime;
	private float spin;
	private float chipX1, chipY1, chipX2, chipY2;
	private float menuX1, menuY1, menuX2, menuY2;

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

	/** Draws the chip at (x, y) and, when open, the menu under it. */
	public void render(GuiGraphicsExtractor g, float x, float y, int mouseX, int mouseY, float dt, float alpha) {
		hits.clear();
		openAnim = Theme.approach(openAnim, open ? 1 : 0, dt, 16);
		spin += dt * 40;
		if (message != null && (messageTime -= dt) <= 0) message = null;

		Accounts.Account current = Accounts.current();
		String label = current.name();
		float w = 44 + Ui.width(label, Ui.Font.SEMIBOLD), h = 22;
		boolean over = Theme.inside(mouseX, mouseY, x, y, x + w, y + h);
		chipHover = Theme.approach(chipHover, over || open ? 1 : 0, dt, 16);
		chipX1 = x;
		chipY1 = y;
		chipX2 = x + w;
		chipY2 = y + h;
		Ui.shadow(g, x, y, w, h, 10, 0.3f * alpha);
		Ui.round(g, x, y, w, h, h / 2, Theme.alpha(Theme.mix(0xC81B1D1B, 0xE62A2E2B, chipHover), alpha), Theme.alpha(Theme.mix(0x26FFFFFF, Theme.COPPER, chipHover * 0.6f), alpha), 1);
		SkinHead.draw(g, SkinTextures.of(current), x + 12, y + h / 2, 21, -32, -20, alpha);
		Ui.text(g, label, x + 23, y + 7, Theme.alpha(Theme.TEXT, alpha), Ui.Font.SEMIBOLD);
		float dotX = x + 23 + Ui.width(label, Ui.Font.SEMIBOLD) + 6;
		Ui.circle(g, dotX, y + h / 2, 2, Theme.alpha(current.microsoft() ? Theme.ONLINE : 0xFFE2B34A, alpha));
		Ui.lucide(g, "chevron", dotX + 5, y + h / 2 - 3.5f, 7, Theme.alpha(Theme.MUTED, alpha));
		hit(x, y, x + w, y + h, () -> {
			click();
			setOpen(!open);
		});

		if (openAnim > 0.01f) menu(g, x, y + h + 6 - (1 - openAnim) * 6, mouseX, mouseY, current, alpha * openAnim);
	}

	private void menu(GuiGraphicsExtractor g, float x, float y, int mouseX, int mouseY, Accounts.Account current, float a) {
		List<Accounts.Account> accounts = Accounts.list();
		float rowH = 26;
		float h = 70 + 14 + accounts.size() * rowH + 8 + 2 * 24 + (adding ? 26 : 0) + (message != null ? 14 : 0) + 6;
		menuX1 = x;
		menuY1 = y;
		menuX2 = x + W;
		menuY2 = y + h;
		Ui.shadow(g, x, y, W, h, 18, 0.5f * a);
		Ui.round(g, x, y, W, h, 8, Theme.alpha(0xF51B1D1B, a), Theme.alpha(0x26FFFFFF, a), 1);
		// clicks inside the panel never fall through to the screen
		hit(x, y, x + W, y + h, () -> { });

		// hero: the current account's head, turning slowly
		SkinHead.draw(g, SkinTextures.of(current), x + 34, y + 36, 58, -30 + (float) Math.sin(Math.toRadians(spin)) * 40, -16, a);
		Ui.text(g, Ui.ellipsize(current.name(), Ui.Font.BOLD, (int) (W - 80)), x + 70, y + 24, Theme.alpha(Theme.TEXT, a), Ui.Font.BOLD);
		Ui.text(g, current.microsoft() ? "Microsoft account · skin from Minecraft" : "Offline account · default skin",
				x + 70, y + 39, Theme.alpha(Theme.MUTED, a), Ui.Font.SMALL);
		float cy = y + 70;
		g.fill(Math.round(x + 8), Math.round(cy), Math.round(x + W - 8), Math.round(cy + 1), Theme.alpha(0x1AFFFFFF, a));
		cy += 6;
		Ui.text(g, "ACCOUNTS", x + 10, cy, Theme.alpha(Theme.FAINT, a), Ui.Font.TINY);
		cy += 9;

		for (Accounts.Account account : accounts) {
			boolean active = Accounts.isCurrent(account);
			boolean over = Theme.inside(mouseX, mouseY, x + 4, cy, x + W - 4, cy + rowH - 2);
			if (active || over) Ui.round(g, x + 4, cy, W - 8, rowH - 2, 7, Theme.alpha(over && !active ? 0x12FFFFFF : 0x24000000 | (Theme.COPPER & 0xFFFFFF), a));
			SkinHead.draw(g, SkinTextures.of(account), x + 16, cy + rowH / 2 - 1, 20, -32, -20, a);
			Ui.text(g, Ui.ellipsize(account.name(), Ui.Font.SEMIBOLD, (int) (W - 70)), x + 31, cy + 4, Theme.alpha(Theme.TEXT, a), Ui.Font.SEMIBOLD);
			String kind = account.microsoft() ? "Microsoft" : "Offline · singleplayer and offline servers";
			Ui.text(g, Ui.ellipsize(kind, Ui.Font.TINY, (int) (W - 70)), x + 31, cy + 14, Theme.alpha(Theme.MUTED, a), Ui.Font.TINY);
			if (active) Ui.lucide(g, "check", x + W - 20, cy + rowH / 2 - 5, 9, Theme.alpha(Theme.COPPER, a));
			else if (account.addedInGame() && over) {
				float bx = x + W - 22, by = cy + rowH / 2 - 7;
				boolean overDel = Theme.inside(mouseX, mouseY, bx, by, bx + 14, by + 14);
				Ui.lucide(g, "trash", bx + 2.5f, by + 2.5f, 9, Theme.alpha(overDel ? Theme.DANGER : Theme.FAINT, a));
				hit(bx, by, bx + 14, by + 14, () -> {
					click();
					Accounts.remove(account);
				});
			}
			final float rowY = cy;
			if (!active) hits.add(hits.size() - (account.addedInGame() && over ? 1 : 0), new Hit(x + 4, rowY, x + W - 4, rowY + rowH - 2, () -> {
				click();
				String why = Accounts.switchTo(account);
				if (why != null) say(why);
			}));
			cy += rowH;
		}

		cy += 2;
		g.fill(Math.round(x + 8), Math.round(cy), Math.round(x + W - 8), Math.round(cy + 1), Theme.alpha(0x1AFFFFFF, a));
		cy += 5;
		cy = action(g, mouseX, mouseY, x, cy, "plus", "Add Microsoft account", false, a, () -> say("Add Microsoft accounts in the Glade launcher"));
		cy = action(g, mouseX, mouseY, x, cy, "user_plus", "Add offline account", adding, a, () -> {
			adding = !adding;
			name = "";
		});
		if (adding) {
			float fx = x + 8, fw = W - 16 - 40;
			boolean valid = Accounts.validName(name);
			Ui.round(g, fx, cy + 2, fw, 18, 5, Theme.alpha(0xFF141614, a), Theme.alpha(Theme.alpha(Theme.COPPER, 0.7f), a), 1);
			boolean blink = (System.nanoTime() / 530_000_000L) % 2 == 0;
			String shown = name.isEmpty() ? "Username" : name + (blink ? "|" : "");
			Ui.text(g, shown, fx + 6, cy + 7.5f, Theme.alpha(name.isEmpty() ? Theme.FAINT : Theme.TEXT, a), Ui.Font.MEDIUM);
			float bx = fx + fw + 4;
			boolean overAdd = valid && Theme.inside(mouseX, mouseY, bx, cy + 2, bx + 36, cy + 20);
			Ui.round(g, bx, cy + 2, 36, 18, 6, Theme.alpha(valid ? (overAdd ? Theme.COPPER_HI : Theme.COPPER) : Theme.alpha(Theme.COPPER, 0.4f), a));
			Ui.centered(g, "Add", bx + 18, cy + 7.5f, Theme.alpha(Theme.INK, a), Ui.Font.SEMIBOLD);
			if (valid) hit(bx, cy + 2, bx + 36, cy + 20, this::submit);
			cy += 26;
		}
		if (message != null) Ui.text(g, Ui.ellipsize(message, Ui.Font.SMALL, (int) (W - 20)), x + 10, cy + 2, Theme.alpha(Theme.MUTED, a), Ui.Font.SMALL);
	}

	private float action(GuiGraphicsExtractor g, int mouseX, int mouseY, float x, float y, String icon, String label, boolean on, float a, Runnable run) {
		boolean over = Theme.inside(mouseX, mouseY, x + 4, y, x + W - 4, y + 22);
		if (over || on) Ui.round(g, x + 4, y, W - 8, 22, 6, Theme.alpha(0x12FFFFFF, a));
		Ui.round(g, x + 9, y + 3, 16, 16, 5, Theme.alpha(0x1F000000 | (Theme.COPPER & 0xFFFFFF), a));
		Ui.lucide(g, icon, x + 12.5f, y + 6.5f, 9, Theme.alpha(Theme.COPPER, a));
		Ui.text(g, label, x + 31, y + 7, Theme.alpha(Theme.TEXT, a), Ui.Font.SEMIBOLD);
		hit(x + 4, y, x + W - 4, y + 22, () -> {
			click();
			run.run();
		});
		return y + 24;
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

	/** True when the click was used by the chip or the menu. */
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
