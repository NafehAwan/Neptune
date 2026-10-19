package net.tuffclient.module.util;

import net.tuffclient.gui.Gfx;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.tuffclient.gui.Theme;
import net.tuffclient.gui.Ui;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ColorSetting;
import net.tuffclient.setting.NumberSetting;

/**
 * After you take a screenshot: a small card on screen, and buttons in chat to
 * open it, show the folder, copy its path or delete it.
 */
public final class ScreenshotToolsModule extends Module {
	private final BoolSetting popup = add(new BoolSetting("popup", "Show a card on screen", true));
	private final NumberSetting seconds = add(new NumberSetting("seconds", "Card time (seconds)", 4, 1, 10, 1));
	private final BoolSetting chat = add(new BoolSetting("chat", "Buttons in chat", true));
	private final ColorSetting cardColor = add(new ColorSetting("card_color", "Card color", 0xD9101210, true));
	private final ColorSetting accent = add(new ColorSetting("accent", "Accent color", 0xFF4FC79A, false));
	private final Set<String> seen = new HashSet<>();
	private long lastScan;
	private boolean primed;
	private String shown;
	private long shownAt;

	public ScreenshotToolsModule() {
		super("screenshot_tools", "Screenshot Tools", "After F2: open, copy, show in folder or delete the screenshot.", Category.UTILITY, true);
	}

	public static File folder() {
		return new File(Minecraft.getInstance().gameDirectory, "screenshots");
	}

	@Override
	public void tick(Minecraft client) {
		long now = System.currentTimeMillis();
		if (now - lastScan < 500) return;
		lastScan = now;
		File[] files = folder().listFiles((d, n) -> n.endsWith(".png"));
		if (files == null) files = new File[0];
		if (!primed) {
			for (File f : files) seen.add(f.getName());
			primed = true;
			return;
		}
		for (File f : files) {
			if (seen.add(f.getName())) announce(client, f);
		}
	}

	private void announce(Minecraft client, File file) {
		shown = file.getName();
		shownAt = System.currentTimeMillis();
		if (!chat.get() || client.player == null) return;
		MutableComponent msg = Component.literal("Screenshot saved ").withStyle(ChatFormatting.GRAY);
		msg.append(button("Open", new ClickEvent.OpenFile(file), "Open the picture"));
		msg.append(button("Folder", new ClickEvent.OpenFile(folder()), "Show the screenshots folder"));
		msg.append(button("Copy path", new ClickEvent.CopyToClipboard(file.getAbsolutePath()), "Copy where it's saved"));
		msg.append(button("Delete", new ClickEvent.RunCommand("/glade screenshot delete " + file.getName()), "Delete this screenshot"));
		client.player.sendSystemMessage(msg);
	}

	private MutableComponent button(String label, ClickEvent click, String hover) {
		int color = accent.argb() & 0xFFFFFF;
		return Component.literal(" [" + label + "]").withStyle(s -> s.withColor(color).withClickEvent(click).withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}

	/** Deletes a screenshot by file name (from the chat button). */
	public static String delete(String name) {
		if (name.contains("/") || name.contains("\\\\") || name.startsWith(".")) return "That isn't a screenshot.";
		File f = new File(folder(), name);
		if (!f.isFile()) return "Already deleted.";
		return f.delete() ? "Deleted " + name : "Couldn't delete " + name;
	}

	/** The card in the corner after a screenshot. */
	public void render(Gfx g) {
		if (!popup.get() || shown == null) return;
		float t = (System.currentTimeMillis() - shownAt) / 1000f, life = seconds.get().floatValue();
		if (t > life) {
			shown = null;
			return;
		}
		float in = Math.min(1, t / 0.25f), out = Math.min(1, (life - t) / 0.4f), a = Math.min(in, out);
		var font = Minecraft.getInstance().font;
		String title = "Screenshot saved", sub = Ui.ellipsize(shown, Ui.Font.BODY, 220);
		int w = Math.max(font.width(title), font.width(shown)) + 34, h = 30;
		float x = g.guiWidth() - w - 10 + (1 - in) * 30, y = 10;
		Ui.round(g, x, y, w, h, 6, Theme.alpha(cardColor.argb(), a));
		Ui.round(g, x, y, 3, h, 1.5f, Theme.alpha(accent.argb(), a));
		Ui.lucide(g, "camera", x + 10, y + 9, 12, Theme.alpha(accent.argb(), a));
		g.push();
		g.translate(x + 27, y + 6);
		g.text(font, title, 0, 0, Theme.alpha(0xFFFFFFFF, a), false);
		g.text(font, shown, 0, 11, Theme.alpha(0xFFB8C2B9, a), false);
		g.pop();
	}
}
