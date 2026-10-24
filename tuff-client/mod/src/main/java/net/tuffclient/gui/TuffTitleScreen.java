package net.tuffclient.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.realmsclient.RealmsMainScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
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
public final class TuffTitleScreen extends GladeScreen {
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
	/** The screen a click asked for: the card fades out first, then it opens. */
	private Screen pending;
	/** 0 shown, 1 faded out; fades back in when we return to this screen. */
	private float leave;
	private boolean switching;

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
		if (pending != null) return;
		if (!animate()) {
			net.tuffclient.Mc.setScreen(screen);
			return;
		}
		pending = screen;
	}

	/**
	 * Mods opens Mod Menu's mod list when it's installed (every Glade preset has
	 * it), otherwise Glade's own mods page. Glade's settings are on Right Shift.
	 */
	public void openMods() {
		if (FabricLoader.getInstance().isModLoaded("modmenu")) {
			try {
				// Mod Menu is optional, so it's looked up by name rather than linked against
				Screen mods = (Screen) Class.forName("com.terraformersmc.modmenu.gui.ModsScreen").getConstructor(Screen.class).newInstance(this);
				open(mods);
				return;
			} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
				TuffClient.LOGGER.warn("Couldn't open Mod Menu, showing Glade's mods instead", e);
			}
		}
		open(new TuffMenuScreen(this, TuffMenuScreen.Page.MODS));
	}

	/** How much of the card is showing while it fades out to another screen or back in. */
	private float shown() {
		float t = Math.max(0, Math.min(1, leave));
		return 1 - t * t * (3 - 2 * t);
	}

	private float zoom() {
		return Ui.mockup(height);
	}

	/** How far the 720-tall layout is moved down to sit in the middle of the (slightly taller) screen. */
	private float oy() {
		// on a whole screen pixel, or everything below would sit between two
		float p = zoom() * Ui.guiScale();
		return Math.round((height / zoom() - H) / 2 * p) / p;
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
		boxes.add(new Box(new Button("mods", "Mods", "grid", Button.FROST, this::openMods), bx, BTN_Y + 112, 145, 42));
		boxes.add(new Box(new Button("options", "Options", "settings", Button.FROST, () -> open(net.tuffclient.compat.Screens.options(this))), bx + 155, BTN_Y + 112, 145, 42));

		float right = vw() - 20;
		boxes.add(new Box(new Button("quit", "Quit game", "logout", Button.ROUND, minecraft::stop), right - 36, 18, 36, 36));
		boxes.add(new Box(new Button("settings", "Options", "settings", Button.ROUND, () -> open(net.tuffclient.compat.Screens.options(this))), right - 80, 18, 36, 36));
		boxes.add(new Box(new Button("realms", "Realms", "cloud", Button.ROUND, () -> open(new RealmsMainScreen(this))), right - 124, 18, 36, 36));
	}

	@Override
	protected void init() {
		Ui.clearTextCache();
		layout();
		// back from another screen: fade the card in again
		if (switching) {
			switching = false;
			leave = 1;
		}
		pending = null;
	}

	@Override
	protected void drawBackground(Gfx g, int mouseX, int mouseY, float partialTick) {
		// the glass blurs what's behind it, like the mockup's backdrop-filter
		float z = zoom(), lift = (1 - stage(0)) * 8;
		List<float[]> glass = new ArrayList<>();
		glass.add(new float[] {(vw() / 2 - CARD_W / 2) * z, (CARD_Y + lift) * z, CARD_W * z, CARD_H * z, 28 * z});
		accounts.glass(glass, z);
		for (float[] shape : glass) shape[1] += oy() * z;
		background.renderWithGlass(g, width, height, animate(), () -> blurBehind(g), glass);
	}

	private float stage(float delay) {
		float t = Math.max(0, Math.min(1, (intro - delay) / 0.5f));
		return (1 - (1 - t) * (1 - t) * (1 - t)) * shown();
	}

	@Override
	protected void draw(Gfx g, int guiMouseX, int guiMouseY, float partialTick) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		intro = animate() ? intro + dt : 5;
		if (pending != null) {
			leave = Math.min(1, leave + dt / 0.16f);
		} else {
			leave = Math.max(0, leave - dt / 0.22f);
		}
		float z = zoom();
		if (boxes.isEmpty() || Math.abs(boxes.getLast().x() + 36 - (vw() - 20)) > 0.5f) layout();
		float mouseX = guiMouseX / z, mouseY = guiMouseY / z - oy();
		boolean menuOpen = accounts.isOpen() || pending != null;
		float mx = menuOpen ? -1000 : mouseX, my = menuOpen ? -1000 : mouseY;

		Ui.zoom = z;
		g.push();
		g.scale(z, z);
		g.translate(0, oy());

		float cx = vw() / 2;
		float a = stage(0);
		float lift = (1 - a) * 8;

		// the frosted card
		Ui.frost(g, cx - CARD_W / 2, CARD_Y + lift, CARD_W, CARD_H, 28, 0x61FFFFFF, 0.18f, a);
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
			// buttons lift a little under the pointer
			float y = b.y() + (btn.kind() == Button.ROUND ? 0 : lift) - hv * 1.5f;
			switch (btn.kind()) {
				case Button.PRIMARY -> {
					Ui.shadowOutside(g, b.x(), y, b.w(), b.h(), 16, (0.12f + 0.2f * hv) * appear);
					Ui.round(g, b.x(), y, b.w(), b.h(), 12, Theme.alpha(Theme.mix(Theme.COPPER, Theme.COPPER_HI, hv), appear));
					Ui.round(g, b.x() + 6, y + 1, b.w() - 12, 1, 0.5f, Theme.alpha(0x40FFFFFF, appear));
					label(g, b, y, btn, Theme.alpha(Theme.INK, appear), Ui.Font.UI_15_SB, 15);
				}
				case Button.FROST -> {
					Ui.round(g, b.x(), y, b.w(), b.h(), 12, Theme.alpha(Theme.mix(0x73FFFFFF, 0xD9FFFFFF, hv), appear), Theme.alpha(0xBFFFFFFF, appear), 1);
					boolean big = b.h() > 44;
					label(g, b, y, btn, Theme.alpha(Theme.TEXT, appear), big ? Ui.Font.UI_15_SB : Ui.Font.UI_13_5_SB, big ? 15 : 13.5f);
				}
				default -> {
					Ui.shadowOutside(g, b.x(), y, b.w(), b.h(), 14, 0.12f * appear);
					Ui.round(g, b.x(), y, b.w(), b.h(), b.h() / 2, Theme.alpha(Theme.mix(0x61FFFFFF, 0xD9FFFFFF, hv), appear), Theme.alpha(0xB3FFFFFF, appear), 1);
					int color = btn.id().equals("quit") ? Theme.mix(Theme.TEXT, Theme.DANGER, hv) : Theme.TEXT;
					Ui.lucide(g, btn.icon(), b.x() + 10.5f, y + 10.5f, 15, Theme.alpha(color, appear));
					if (over) tooltip(g, btn.label(), b);
				}
			}
		}

		// footer
		float fa = stage(0.4f);
		Ui.textAt(g, version, 16, H + oy() - 14, Theme.alpha(0xD9FFFFFF, fa), Ui.Font.MONO_10_5);
		Ui.rightAt(g, "Not an official Minecraft product", vw() - 16, H + oy() - 14, Theme.alpha(0xD9FFFFFF, fa), Ui.Font.UI_11_5);

		// account chip and its menu go on top of everything
		accounts.render(g, 20, 18, mouseX, mouseY, dt, stage(0.05f));

		g.pop();
		Ui.zoom = 1f;
	}

	/** Icon and label centred together, 8px apart, like the mockup's buttons. */
	private void label(Gfx g, Box b, float y, Button btn, int color, Ui.Font font, float px) {
		float total = 15 + 8 + Ui.width(btn.label(), font);
		float x = b.x() + (b.w() - total) / 2;
		Ui.lucide(g, btn.icon(), x, y + (b.h() - 15) / 2, 15, color);
		Ui.textAt(g, btn.label(), x + 23, Ui.mid(y + b.h() / 2, px), color, font);
	}

	private void tooltip(Gfx g, String tip, Box b) {
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
	protected boolean onClick(Input.Click event, boolean doubleClick) {
		if (pending != null) return true;
		if (event.button() == 0) {
			float z = zoom();
			double x = event.x() / z, y = event.y() / z - oy();
			if (accounts.mouseClicked(x, y)) return true;
			for (Box b : boxes) {
				if (Theme.inside(x, y, b.x(), b.y(), b.x() + b.w(), b.y() + b.h())) {
					minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
					b.button().action().run();
					return true;
				}
			}
		}
		return super.onClick(event, doubleClick);
	}

	@Override
	protected boolean onKey(Input.Key event) {
		if (pending != null) return true;
		if (accounts.keyPressed(event)) return true;
		if (!accounts.isOpen() && keyMatches(TuffClient.menuKey())) {
			open(new TuffMenuScreen(this));
			return true;
		}
		return super.onKey(event);
	}

	@Override
	protected boolean onType(Input.Typed event) {
		if (accounts.charTyped(event)) return true;
		return super.onType(event);
	}

	@Override
	public void tick() {
		super.tick();
		// the card has faded out: open the screen that was asked for (between frames, not mid-draw)
		if (pending != null && leave >= 1 && !switching) {
			switching = true;
			net.tuffclient.Mc.setScreen(pending);
		}
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
