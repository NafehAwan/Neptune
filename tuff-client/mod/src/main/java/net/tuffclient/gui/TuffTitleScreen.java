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
 * The Glade title screen, drawn to the Grove Glass mockup: the animated grove
 * behind a frosted card with the leaf, the wordmark and the main buttons; the
 * account chip top left and round buttons top right.
 * <p>
 * Everything is laid out in the mockup's 1280x720 space (see {@link Ui#mockup})
 * with the mockup's own sizes, so it looks the same at any window size.
 */
public final class TuffTitleScreen extends Screen {
	private static final Identifier EMBLEM = Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "textures/gui/title/emblem.png");
	private static final float H = 720;

	private record Button(String id, String label, String icon, int kind, Runnable action) {
		static final int PRIMARY = 0, FROST = 1, ROUND = 2;
	}

	private record Box(Button button, float x, float y, float w, float h) {
	}

	private final GroveBackground background = new GroveBackground();
	private final AccountMenu accounts = new AccountMenu(this);
	private final List<Box> boxes = new ArrayList<>();
	private final Map<String, Float> hover = new HashMap<>();
	private final String version;
	private float intro;
	private long last = System.nanoTime();

	public TuffTitleScreen() {
		super(Component.literal("Glade"));
		String mc = FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		String glade = FabricLoader.getInstance().getModContainer("tuff").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		if (glade.endsWith(".0") && glade.chars().filter(ch -> ch == '.').count() == 2) glade = glade.substring(0, glade.length() - 2);
		version = "Glade " + glade + " · Minecraft " + mc;
	}

	private boolean animate() {
		return TuffClient.modules().titleScreen.animate.get();
	}

	private void open(Screen screen) {
		minecraft.gui.setScreen(screen);
	}

	private float zoom() {
		return Ui.mockup(height);
	}

	/** Width of the mockup space for this window (1280 at 16:9). */
	private float vw() {
		return width / zoom();
	}

	// card: 420 wide at top 92, padding 30 40 34; buttons 300 wide
	private static final float CARD_W = 420, CARD_Y = 92;
	private static final float LEAF_Y = CARD_Y + 30, WORD_TOP = LEAF_Y + 56 + 6;
	private static final float TAG_TOP = WORD_TOP + 96 + 14 + 4;
	private static final float BTN_Y = TAG_TOP + 14 * 1.26f + 34;
	private static final float CARD_H = BTN_Y + 46 + 10 + 46 + 10 + 42 + 34 - CARD_Y;

	private void layout() {
		boxes.clear();
		float cx = vw() / 2, bx = cx - 150;
		boxes.add(new Box(new Button("single", "Singleplayer", "play", Button.PRIMARY, () -> open(new SelectWorldScreen(this))), bx, BTN_Y, 300, 46));
		boxes.add(new Box(new Button("multi", "Multiplayer", "globe", Button.FROST, () -> open(new JoinMultiplayerScreen(this))), bx, BTN_Y + 56, 300, 46));
		boxes.add(new Box(new Button("mods", "Mods", "grid", Button.FROST, () -> open(new TuffMenuScreen(this))), bx, BTN_Y + 112, 145, 42));
		boxes.add(new Box(new Button("options", "Options", "settings", Button.FROST, () -> open(new OptionsScreen(this, minecraft.options))), bx + 155, BTN_Y + 112, 145, 42));

		float right = vw() - 20;
		boxes.add(new Box(new Button("quit", "Quit game", "logout", Button.ROUND, minecraft::stop), right - 36, 18, 36, 36));
		boxes.add(new Box(new Button("settings", "Options", "settings", Button.ROUND, () -> open(new OptionsScreen(this, minecraft.options))), right - 80, 18, 36, 36));
		boxes.add(new Box(new Button("realms", "Realms", "cloud", Button.ROUND, () -> open(new RealmsMainScreen(this))), right - 124, 18, 36, 36));
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
	public void extractRenderState(GuiGraphicsExtractor g, int guiMouseX, int guiMouseY, float partialTick) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		intro = animate() ? intro + dt : 5;
		float z = zoom();
		if (boxes.isEmpty() || Math.abs(boxes.getLast().x() + 36 - (vw() - 20)) > 0.5f) layout();
		float mouseX = guiMouseX / z, mouseY = guiMouseY / z;
		boolean menuOpen = accounts.isOpen();
		float mx = menuOpen ? -1000 : mouseX, my = menuOpen ? -1000 : mouseY;

		Ui.zoom = z;
		g.pose().pushMatrix();
		g.pose().scale(z, z);

		float cx = vw() / 2;
		float a = stage(0);
		float lift = (1 - a) * 8;

		// the frosted card
		Ui.frost(g, cx - CARD_W / 2, CARD_Y + lift, CARD_W, CARD_H, 28, 0x80FFFFFF, 0.18f, a);
		Ui.image(g, EMBLEM, cx - 28, LEAF_Y + lift, 56, 56, 512, 512, Theme.alpha(0xFFFFFFFF, a));
		// 96px Fraunces italic in a line-height 1 box: the baseline sits 0.86 of the size down
		Ui.centeredAt(g, "Glade", cx, WORD_TOP + 96 * 0.8615f + lift, Theme.alpha(Theme.TEXT, a), Ui.Font.HEAD_96);
		Ui.centeredAt(g, "A calmer way to play.", cx, TAG_TOP + 14 * 1.038f + lift, Theme.alpha(Theme.TAGLINE, a), Ui.Font.UI_14);

		// buttons
		for (int i = 0; i < boxes.size(); i++) {
			Box b = boxes.get(i);
			Button btn = b.button();
			boolean over = Theme.inside(mx, my, b.x(), b.y(), b.x() + b.w(), b.y() + b.h());
			float hv = Theme.approach(hover.getOrDefault(btn.id(), 0f), over ? 1 : 0, dt, 14);
			hover.put(btn.id(), hv);
			float appear = stage(btn.kind() == Button.ROUND ? 0.1f : 0.12f + i * 0.04f);
			float y = b.y() + (btn.kind() == Button.ROUND ? 0 : lift);
			switch (btn.kind()) {
				case Button.PRIMARY -> {
					Ui.round(g, b.x(), y, b.w(), b.h(), 12, Theme.alpha(Theme.mix(Theme.COPPER, Theme.COPPER_HI, hv), appear));
					label(g, b, y, btn, Theme.alpha(Theme.INK, appear), Ui.Font.UI_15_SB, 15);
				}
				case Button.FROST -> {
					Ui.round(g, b.x(), y, b.w(), b.h(), 12, Theme.alpha(Theme.mix(0x73FFFFFF, 0xD9FFFFFF, hv), appear), Theme.alpha(0xBFFFFFFF, appear), 1);
					boolean big = b.h() > 44;
					label(g, b, y, btn, Theme.alpha(Theme.TEXT, appear), big ? Ui.Font.UI_15_SB : Ui.Font.UI_13_5_SB, big ? 15 : 13.5f);
				}
				default -> {
					Ui.shadow(g, b.x(), y, b.w(), b.h(), 14, 0.12f * appear);
					Ui.round(g, b.x(), y, b.w(), b.h(), b.h() / 2, Theme.alpha(Theme.mix(0x8CFFFFFF, 0xE6FFFFFF, hv), appear), Theme.alpha(0xB3FFFFFF, appear), 1);
					int color = btn.id().equals("quit") ? Theme.mix(Theme.TEXT, Theme.DANGER, hv) : Theme.TEXT;
					Ui.lucide(g, btn.icon(), b.x() + 10.5f, y + 10.5f, 15, Theme.alpha(color, appear));
					if (over) tooltip(g, btn.label(), b);
				}
			}
		}

		// footer
		float fa = stage(0.4f);
		Ui.textAt(g, version, 20, H - 19, Theme.alpha(0xD9FFFFFF, fa), Ui.Font.MONO_11_5);
		Ui.rightAt(g, "Not an official Minecraft product", vw() - 20, H - 19, Theme.alpha(0xD9FFFFFF, fa), Ui.Font.UI_11_5);

		// account chip and its menu go on top of everything
		accounts.render(g, 20, 18, mouseX, mouseY, dt, stage(0.05f));

		g.pose().popMatrix();
		Ui.zoom = 1f;
	}

	/** Icon and label centred together, 8px apart, like the mockup's buttons. */
	private void label(GuiGraphicsExtractor g, Box b, float y, Button btn, int color, Ui.Font font, float px) {
		float total = 15 + 8 + Ui.width(btn.label(), font);
		float x = b.x() + (b.w() - total) / 2;
		Ui.lucide(g, btn.icon(), x, y + (b.h() - 15) / 2, 15, color);
		Ui.textAt(g, btn.label(), x + 23, Ui.mid(y + b.h() / 2, px), color, font);
	}

	private void tooltip(GuiGraphicsExtractor g, String tip, Box b) {
		float tw = Ui.width(tip, Ui.Font.UI_12_5_M) + 20;
		float tx = Math.min(vw() - tw - 8, b.x() + b.w() / 2 - tw / 2), ty = b.y() + b.h() + 8;
		Ui.round(g, tx, ty, tw, 26, 8, 0xF2FFFFFF, Theme.alpha(0xFF1F2A22, 0.08f), 1);
		Ui.textAt(g, tip, tx + 10, Ui.mid(ty + 13, 12.5f), Theme.TEXT, Ui.Font.UI_12_5_M);
	}

	/** Opens or closes the account menu (used by tests). */
	public void setAccountMenuOpen(boolean open) {
		accounts.setOpen(open);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			float z = zoom();
			double x = event.x() / z, y = event.y() / z;
			if (accounts.mouseClicked(x, y)) return true;
			for (Box b : boxes) {
				if (Theme.inside(x, y, b.x(), b.y(), b.x() + b.w(), b.y() + b.h())) {
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
