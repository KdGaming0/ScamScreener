package eu.tango.scamscreener.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public final class ErrorMessages {
	private ErrorMessages() {
	}

	public static MutableComponent error(String prefix, int prefixColor, String summary, String code, String detail) {
		String safePrefix = prefix == null ? "" : prefix;
		String safeSummary = summary == null ? "Error." : summary;
		String safeCode = code == null || code.isBlank() ? "ERR-000" : code.trim();
		String safeDetail = detail == null || detail.isBlank() ? "unknown error" : detail;
		String hoverText = safeSummary + " (" + safeCode + ")\n" + safeDetail;

		return Component.literal(safePrefix)
			.withStyle(style -> style.withColor(prefixColor))
			.append(Component.literal(safeSummary + " ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal("[" + safeCode + "]")
				.withStyle(style -> style
					.withColor(ChatFormatting.YELLOW)
					.withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverText).withStyle(ChatFormatting.GRAY)))));
	}
}
