package net.tuffclient.module.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.tuffclient.mixin.ChatComponentAccessor;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ColorSetting;

/** Timestamps, merged repeats (x3), click to copy and your name highlighted. */
public final class ChatPlusModule extends Module {
	private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm");
	private final BoolSetting timestamps = add(new BoolSetting("timestamps", "Timestamps", true));
	private final BoolSetting twelve = add(new BoolSetting("12h", "12-hour timestamps", false)).showIf(() -> timestamps.get());
	private final ColorSetting timeColor = add(new ColorSetting("time_color", "Timestamp color", 0xFF8A938B, false)).showIf(() -> timestamps.get());
	private final BoolSetting merge = add(new BoolSetting("merge", "Merge repeated messages", true));
	private final BoolSetting copy = add(new BoolSetting("copy", "Click a message to copy it", true));
	private final BoolSetting highlight = add(new BoolSetting("highlight", "Highlight your name", true));
	private final ColorSetting highlightColor = add(new ColorSetting("highlight_color", "Highlight color", 0xFFE3B23C, false)).showIf(() -> highlight.get());
	private final BoolSetting ping = add(new BoolSetting("ping", "Sound when you're mentioned", false));

	private String lastText;
	private Component lastShown;
	private int repeats;

	public ChatPlusModule() {
		super("chat_plus", "Chat+", "Timestamps, merged repeats, click to copy and your name highlighted.", Category.UTILITY, true);
	}

	/** Rewrites a chat line before it's added (see ChatComponentMixin). */
	public Component process(Component message, ChatComponentAccessor chat) {
		String raw = message.getString();
		AutoGGModule.onChat(raw);
		Component body = message;
		Minecraft mc = Minecraft.getInstance();
		String name = mc.player == null ? null : mc.getUser().getName();
		boolean mentioned = false;
		if (highlight.get() && name != null && name.length() > 2 && raw.toLowerCase().contains(name.toLowerCase()) && !raw.startsWith("<" + name + ">")) {
			body = highlightName(message, name, highlightColor.argb() & 0xFFFFFF);
			mentioned = true;
		}
		if (mentioned && ping.get()) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.6f));

		MutableComponent out = Component.empty();
		if (timestamps.get()) {
			String time = LocalTime.now().format(twelve.get() ? H12 : H24);
			out.append(Component.literal("[" + time + "] ").withStyle(s -> s.withColor(timeColor.argb() & 0xFFFFFF)));
		}
		out.append(body);
		if (merge.get() && raw.equals(lastText) && lastShown != null) {
			List<GuiMessage> messages = chat.glade$messages();
			if (!messages.isEmpty() && messages.get(0).content() == lastShown) {
				messages.remove(0);
				chat.glade$refresh();
				repeats++;
				out.append(Component.literal(" (x" + repeats + ")").withStyle(s -> s.withColor(0x8A938B)));
			} else {
				repeats = 1;
			}
		} else {
			repeats = 1;
		}
		if (copy.get()) {
			MutableComponent wrapped = Component.empty().append(out);
			out = wrapped.withStyle(s -> s.withClickEvent(new ClickEvent.CopyToClipboard(raw)).withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy"))));
		}
		lastText = raw;
		lastShown = out;
		return out;
	}

	/** The same message with every case-insensitive match of {@code name} recoloured. */
	private static Component highlightName(Component message, String name, int color) {
		MutableComponent out = Component.empty();
		String lower = name.toLowerCase();
		message.visit((style, text) -> {
			String t = text, l = text.toLowerCase();
			int i;
			while ((i = l.indexOf(lower)) >= 0) {
				if (i > 0) out.append(Component.literal(t.substring(0, i)).setStyle(style));
				out.append(Component.literal(t.substring(i, i + name.length())).setStyle(style.withColor(color).withBold(true)));
				t = t.substring(i + name.length());
				l = l.substring(i + name.length());
			}
			if (!t.isEmpty()) out.append(Component.literal(t).setStyle(style));
			return Optional.<Object>empty();
		}, Style.EMPTY);
		return out;
	}
}
