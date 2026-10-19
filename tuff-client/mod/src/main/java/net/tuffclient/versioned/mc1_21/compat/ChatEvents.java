// glade: minecraft <1.21.5
package net.tuffclient.compat;

import java.io.File;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/** Click and hover actions for chat text (records since 1.21.5). */
public final class ChatEvents {
	private ChatEvents() {
	}

	public static ClickEvent copy(String text) {
		return new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text);
	}

	public static ClickEvent openFile(File file) {
		return new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath());
	}

	public static ClickEvent runCommand(String command) {
		return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
	}

	public static HoverEvent showText(Component text) {
		return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
	}
}
