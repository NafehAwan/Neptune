package net.tuffclient.gui;

import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.tuffclient.TuffClient;

/** The Tuff title screen: animated chamber, pixel wordmark and a clean button stack. */
public final class TuffTitleScreen extends Screen {
	private record Entry(String label, boolean primary, Runnable action) {
	}

	private final ChamberBackground background = new ChamberBackground();
	private final String version;
	private List<Entry> entries = List.of();
	private float[] hover = new float[0];
	private float fadeIn;
	private long last = System.nanoTime();

	public TuffTitleScreen() {
		super(Component.literal("Tuff Client"));
		String mc = FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		String tuff = FabricLoader.getInstance().getModContainer("tuff").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		version = "Tuff Client " + tuff + " · Minecraft " + mc;
	}

	@Override
	protected void init() {
		entries = List.of(
				new Entry("Singleplayer", true, () -> minecraft.gui.setScreen(new SelectWorldScreen(this))),
				new Entry("Multiplayer", false, () -> minecraft.gui.setScreen(new JoinMultiplayerScreen(this))),
				new Entry("Tuff Mods", false, () -> minecraft.gui.setScreen(new TuffMenuScreen(this))),
				new Entry("Options", false, () -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options))),
				new Entry("Quit", false, minecraft::stop));
		hover = new float[entries.size()];
	}

	private int buttonWidth() {
		return Math.min(200, width - 40);
	}

	private static final int BUTTON_H = 20;

	private int gap() {
		return height < 300 ? 4 : 6;
	}

	/** Keeps the whole button stack clear of the footer on short screens. */
	private int buttonsTop() {
		int stack = entries.size() * BUTTON_H + (entries.size() - 1) * gap();
		return Math.max(height / 4 + 30, Math.min(height / 2 + 4, height - 20 - stack));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		background.render(g, width, height, TuffClient.modules().titleScreen.animate.get());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		fadeIn = Math.min(1, fadeIn + dt * 2.5f);
		float bob = (float) Math.sin(now / 1e9 * 0.9) * 2;

		// Wordmark: the game font scaled up stays pixel-perfect.
		// Fit the wordmark into the space above the buttons.
		int room = buttonsTop() - 12;
		int scale = Math.max(2, Math.min(6, (room - 22) / 9));
		String word = "TUFF";
		int wordWidth = font.width(word) * scale;
		int wordHeight = 8 * scale + 15;
		int wx = width / 2 - wordWidth / 2;
		int wy = Math.round(Math.max(4, (room - wordHeight) / 2f) + bob);
		g.pose().pushMatrix();
		g.pose().translate(wx, wy);
		g.pose().scale(scale);
		g.text(font, word, 1, 1, Theme.alpha(Theme.COPPER_LO, fadeIn), false);
		g.text(font, word, 0, 0, Theme.alpha(0xFFEFE9DF, fadeIn), false);
		g.pose().popMatrix();
		String sub = "C L I E N T";
		g.text(font, sub, width / 2 - font.width(sub) / 2, wy + 8 * scale + 6, Theme.alpha(Theme.COPPER_HI, fadeIn), false);

		int bw = buttonWidth(), bh = BUTTON_H, gap = gap();
		int x = width / 2 - bw / 2;
		int y = buttonsTop();
		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
			int top = y + i * (bh + gap);
			boolean over = Theme.inside(mouseX, mouseY, x, top, x + bw, top + bh);
			hover[i] = Theme.approach(hover[i], over ? 1 : 0, dt, 16);
			float appear = Math.max(0, Math.min(1, fadeIn * 1.6f - i * 0.12f));
			int slide = Math.round((1 - appear) * 10);
			if (e.primary()) {
				Theme.pixelButton(g, x, top + slide, x + bw, top + bh + slide, hover[i]);
				Theme.centered(g, font, e.label().toUpperCase(), width / 2, top + slide + 6, Theme.COPPER_INK);
			} else {
				int fill = Theme.mix(0xD01E211F, 0xF02F3430, hover[i]);
				int border = Theme.mix(Theme.BORDER_STRONG, Theme.COPPER, hover[i] * 0.8f);
				Theme.panel(g, x, top + slide, x + bw, top + bh + slide, Theme.alpha(fill, appear), Theme.alpha(border, appear));
				Theme.centered(g, font, e.label(), width / 2, top + slide + 6, Theme.alpha(Theme.mix(Theme.TEXT, Theme.COPPER_HI, hover[i]), appear));
			}
		}

		g.text(font, version, 6, height - 12, Theme.alpha(Theme.MUTED, fadeIn));
		String legal = width - font.width(version) - 20 > font.width("Copyright Mojang AB. Do not distribute!") ? "Copyright Mojang AB. Do not distribute!" : "© Mojang AB";
		g.text(font, legal, width - font.width(legal) - 6, height - 12, Theme.alpha(Theme.FAINT, fadeIn));
		String hint = "Right Shift opens the Tuff menu in game";
		if (width > font.width(hint) * 2 + 40) g.text(font, hint, width - font.width(hint) - 6, 6, Theme.alpha(Theme.FAINT, fadeIn));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			int bw = buttonWidth(), bh = BUTTON_H, gap = gap();
			int x = width / 2 - bw / 2;
			for (int i = 0; i < entries.size(); i++) {
				int top = buttonsTop() + i * (bh + gap);
				if (Theme.inside(event.x(), event.y(), x, top, x + bw, top + bh)) {
					minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
					entries.get(i).action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
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
