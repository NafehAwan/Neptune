package net.tuffclient.module.util;

import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.TextSetting;

/** Shows a different name for you on your own screen (chat, Tab list, nametags), for streaming and videos. */
public final class NickHiderModule extends Module {
	private final TextSetting nick = add(new TextSetting("nick", "Show me as", "You", 16));
	private final BoolSetting chat = add(new BoolSetting("chat", "In chat", true));
	private final BoolSetting tab = add(new BoolSetting("tab", "In the Tab list", true));
	private final BoolSetting tags = add(new BoolSetting("tags", "On nametags", true));

	public NickHiderModule() {
		super("nick_hider", "Nick Hider", "Shows a different name for you on your screen, for streams and videos.", Category.UTILITY, false);
	}

	public Component chat(Component c) {
		return isEnabled() && chat.get() ? hide(c) : c;
	}

	public Component tab(Component c) {
		return isEnabled() && tab.get() ? hide(c) : c;
	}

	public Component tag(Component c) {
		return isEnabled() && tags.get() ? hide(c) : c;
	}

	private Component hide(Component c) {
		if (c == null) return null;
		String me = Minecraft.getInstance().getUser().getName();
		String to = nick.get().isBlank() ? "You" : nick.get();
		if (me == null || me.isEmpty() || !c.getString().contains(me)) return c;
		return replace(c, me, to);
	}

	/** The same text with every {@code from} swapped for {@code to}, keeping colours, clicks and hovers. */
	public static Component replace(Component c, String from, String to) {
		MutableComponent out = Component.empty();
		c.visit((Style style, String text) -> {
			out.append(Component.literal(text.replace(from, to)).withStyle(style));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}
}
