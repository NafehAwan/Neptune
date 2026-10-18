package net.tuffclient.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.realmsclient.RealmsMainScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.tuffclient.TuffClient;

/**
 * The Glade title screen: the animated grove behind a frosted glass card with
 * the leaf, the wordmark and the main buttons; the account chip top left and
 * round buttons top right.
 */
public final class TuffTitleScreen extends Screen {
	private static final Identifier EMBLEM = Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "textures/gui/title/emblem.png");

	private record Button(String id, String label, String icon, int kind, Runnable action) {
		static final int ACCENT = 0, GLASS = 1, ROUND = 2;
	}

	private record Box(Button button, float x, float y, float w, float h) {
	}

	private final GroveBackground background = new GroveBackground();
	private final AccountMenu accounts = new AccountMenu();
	private final List<Box> boxes = new ArrayList<>();
	private final Map<String, Float> hover = new HashMap<>();
	private final String version;
	private float cardX, cardY, cardW, cardH;
	private float intro;
	private long last = System.nanoTime();

	public TuffTitleScreen() {
		super(Component.literal("Glade"));
		String mc = FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		String glade = FabricLoader.getInstance().getModContainer("tuff").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		version = "Glade " + glade + "  ·  Minecraft " + mc;
	}

	private boolean animate() {
		return TuffClient.modules().titleScreen.animate.get();
	}

	private void open(Screen screen) {
		minecraft.gui.setScreen(screen);
	}

	private void layout() {
		boxes.clear();
		boolean compact = height < 300;
		float bw = Math.max(160, Math.min(210, width * 0.3f));
		float bh = compact ? 20 : 22, gap = compact ? 4 : 5;
		float head = compact ? 70 : 88;
		cardW = bw + 44;
		cardH = 14 + head + 12 + bh * 3 + gap * 2 + 18;
		cardX = width / 2f - cardW / 2;
		cardY = Math.max(36, (height - cardH) / 2 - 4);
		float bx = width / 2f - bw / 2, by = cardY + 14 + head + 12;
		boxes.add(new Box(new Button("single", "Singleplayer", "play", Button.ACCENT, () -> open(new SelectWorldScreen(this))), bx, by, bw, bh));
		boxes.add(new Box(new Button("multi", "Multiplayer", "globe", Button.GLASS, () -> open(new JoinMultiplayerScreen(this))), bx, by + bh + gap, bw, bh));
		float half = (bw - gap) / 2;
		boxes.add(new Box(new Button("mods", "Mods", "grid", Button.GLASS, () -> open(new TuffMenuScreen(this))), bx, by + 2 * (bh + gap), half, bh));
		boxes.add(new Box(new Button("options", "Options", "settings", Button.GLASS, () -> open(new OptionsScreen(this, minecraft.options))), bx + half + gap, by + 2 * (bh + gap), half, bh));

		float r = 22, top = 10;
		boxes.add(new Box(new Button("quit", "Quit game", "logout", Button.ROUND, minecraft::stop), width - 10 - r, top, r, r));
		boxes.add(new Box(new Button("settings", "Options", "settings", Button.ROUND, () -> open(new OptionsScreen(this, minecraft.options))), width - 10 - 2 * r - 6, top, r, r));
		boxes.add(new Box(new Button("realms", "Realms", "cloud", Button.ROUND, () -> open(new RealmsMainScreen(this))), width - 10 - 3 * r - 12, top, r, r));
	}

	@Override
	protected void init() {
		layout();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		background.render(g, width, height, animate());
	}

	private float stage(float delay) {
		float t = Math.max(0, Math.min(1, (intro - delay) / 0.5f));
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		intro = animate() ? intro + dt : 5;
		if (boxes.isEmpty()) layout();
		boolean menuOpen = accounts.isOpen();
		int mx = menuOpen ? -1000 : mouseX, my = menuOpen ? -1000 : mouseY;

		// the glass card, rising into place
		float a = stage(0);
		float lift = (1 - a) * 10;
		Ui.glass(g, cardX, cardY + lift, cardW, cardH, 14, 0x80FFFFFF, a);

		// leaf and wordmark, breathing very slightly
		float breathe = (float) Math.sin(now / 1e9 * 0.8) * 0.8f;
		boolean compact = height < 300;
		float emblem = compact ? 22 : 28;
		float ey = cardY + lift + 14 + breathe;
		Ui.image(g, EMBLEM, width / 2f - emblem / 2, ey, emblem, emblem, 512, 512, Theme.alpha(0xFFFFFFFF, a));
		// the display font draws its glyphs well above the anchor, so the anchor sits low
		float wordY = ey + emblem + (compact ? 18 : 21);
		Ui.centered(g, "Glade", width / 2f, wordY, Theme.alpha(Theme.TEXT, a), Ui.Font.DISPLAY);
		Ui.centered(g, "A calmer way to play.", width / 2f, wordY + 14, Theme.alpha(Theme.MUTED, a), Ui.Font.BODY);

		// buttons
		Box hovered = null;
		for (int i = 0; i < boxes.size(); i++) {
			Box b = boxes.get(i);
			Button btn = b.button();
			boolean over = Theme.inside(mx, my, b.x(), b.y(), b.x() + b.w(), b.y() + b.h());
			if (over) hovered = b;
			float hv = Theme.approach(hover.getOrDefault(btn.id(), 0f), over ? 1 : 0, dt, 14);
			hover.put(btn.id(), hv);
			float appear = stage(btn.kind() == Button.ROUND ? 0.1f : 0.15f + i * 0.05f);
			float y = b.y() + (btn.kind() == Button.ROUND ? 0 : lift) + (1 - appear) * 6;
			switch (btn.kind()) {
				case Button.ACCENT -> {
					Ui.shadow(g, b.x(), y, b.w(), b.h(), 10, (0.18f + hv * 0.1f) * appear);
					Ui.accentButton(g, b.x(), y, b.w(), b.h(), hv, appear);
					label(g, b, y, btn, Theme.alpha(Theme.INK, appear));
				}
				case Button.GLASS -> {
					Ui.glassButton(g, b.x(), y, b.w(), b.h(), hv, appear);
					label(g, b, y, btn, Theme.alpha(Theme.mix(Theme.TEXT, Theme.COPPER, hv * 0.6f), appear));
				}
				default -> {
					Ui.shadow(g, b.x(), y, b.w(), b.h(), 8, 0.12f * appear);
					Ui.round(g, b.x(), y, b.w(), b.h(), b.h() / 2, Theme.alpha(Theme.mix(0x9CFFFFFF, 0xEBFFFFFF, hv), appear), Theme.alpha(Theme.GLASS_RIM, appear), 1);
					int color = btn.id().equals("quit") ? Theme.mix(Theme.MUTED, Theme.DANGER, hv) : Theme.mix(Theme.MUTED, Theme.TEXT, hv);
					Ui.lucide(g, btn.icon(), b.x() + (b.w() - 10) / 2, y + (b.h() - 10) / 2, 10, Theme.alpha(color, appear));
				}
			}
		}

		// tooltip for the round buttons
		if (hovered != null && hovered.button().kind() == Button.ROUND) {
			String tip = hovered.button().label();
			float tw = Ui.width(tip, Ui.Font.MEDIUM) + 12;
			float tx = Math.max(4, Math.min(width - tw - 4, hovered.x() + hovered.w() / 2 - tw / 2));
			float ty = hovered.y() + hovered.h() + 5;
			Ui.round(g, tx, ty, tw, 15, 5, 0xF5FFFFFF, Theme.LINE, 1);
			Ui.text(g, tip, tx + 6, ty + 4, Theme.TEXT, Ui.Font.MEDIUM);
		}

		float fa = stage(0.4f);
		Ui.text(g, version, 10, height - 14, Theme.alpha(0xE6FFFFFF, fa), Ui.Font.SMALL);
		String legal = "Copyright Mojang AB. Do not distribute!";
		if (Ui.width(version, Ui.Font.SMALL) + Ui.width(legal, Ui.Font.SMALL) + 30 > width) legal = "© Mojang AB";
		Ui.right(g, legal, width - 10, height - 14, Theme.alpha(0xCCFFFFFF, fa), Ui.Font.SMALL);

		// account chip and menu go on top of everything
		accounts.render(g, 10, 10, mouseX, mouseY, dt, stage(0.05f));
	}

	private void label(GuiGraphicsExtractor g, Box b, float y, Button btn, int color) {
		float textW = Ui.width(btn.label(), Ui.Font.SEMIBOLD);
		float total = 10 + 6 + textW;
		float x = b.x() + (b.w() - total) / 2;
		Ui.lucide(g, btn.icon(), x, y + (b.h() - 10) / 2, 10, color);
		Ui.text(g, btn.label(), x + 16, y + (b.h() - 9) / 2f, color, Ui.Font.SEMIBOLD);
	}

	/** Opens or closes the account menu (used by tests). */
	public void setAccountMenuOpen(boolean open) {
		accounts.setOpen(open);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			if (accounts.mouseClicked(event.x(), event.y())) return true;
			for (Box b : boxes) {
				if (Theme.inside(event.x(), event.y(), b.x(), b.y(), b.x() + b.w(), b.y() + b.h())) {
					minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
					b.button().action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (accounts.keyPressed(event)) return true;
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (accounts.charTyped(event)) return true;
		return super.charTyped(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}
