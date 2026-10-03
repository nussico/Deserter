package io.github.nussico.deserter;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Turns config messages like {@code "&c{player} deserted"} into components. */
public final class TextFormat {
	private TextFormat() {
	}

	/**
	 * @param placeholders alternating keys and values, e.g. {@code "player", name, "seconds", 5}
	 */
	public static MutableComponent format(String template, Object... placeholders) {
		String text = template;
		for (int i = 0; i + 1 < placeholders.length; i += 2) {
			text = text.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
		}
		// The client renders legacy § color codes in literal text.
		return Component.literal(text.replace('&', '§'));
	}
}
