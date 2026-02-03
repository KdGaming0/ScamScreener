package eu.tango.scamscreener;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Messages {
	private static final String PREFIX = "[ScamScreener] ";
	private static final int PREFIX_LIGHT_RED = 0xFF5555;
	private static final int LABEL_WIDTH = 20;
	private static final int VALUE_WIDTH = 36;
	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

	private Messages() {
	}

	public static MutableComponent addedToBlacklist(String name, UUID uuid) {
		return Component.literal(PREFIX)
			.withStyle(style -> style.withColor(PREFIX_LIGHT_RED))
			.append(Component.literal(name).withStyle(ChatFormatting.AQUA))
			.append(Component.literal(" was added to the blacklist."));
	}

	public static MutableComponent addedToBlacklistWithScore(String name, UUID uuid, int score) {
		return Component.literal(PREFIX)
			.withStyle(style -> style.withColor(PREFIX_LIGHT_RED))
			.append(Component.literal(name).withStyle(ChatFormatting.AQUA))
			.append(Component.literal(" was added with score "))
			.append(Component.literal(String.valueOf(score)).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
			.append(Component.literal("."));
	}

	public static MutableComponent addedToBlacklistWithMetadata(String name, UUID uuid) {
		return Component.literal(PREFIX)
			.withStyle(style -> style.withColor(PREFIX_LIGHT_RED))
			.append(Component.literal(name).withStyle(ChatFormatting.AQUA))
			.append(Component.literal(" was added with metadata."));
	}

	public static MutableComponent alreadyBlacklisted(String name, UUID uuid) {
		return Component.literal(PREFIX)
			.withStyle(style -> style.withColor(PREFIX_LIGHT_RED))
			.append(Component.literal(name).withStyle(ChatFormatting.AQUA))
			.append(Component.literal(" is already blacklisted."));
	}

	public static MutableComponent removedFromBlacklist(String name, UUID uuid) {
		return Component.literal(PREFIX)
			.withStyle(style -> style.withColor(PREFIX_LIGHT_RED))
			.append(Component.literal(name).withStyle(ChatFormatting.AQUA))
			.append(Component.literal(" was removed from the blacklist."));
	}

	public static MutableComponent notOnBlacklist(String name, UUID uuid) {
		return Component.literal(PREFIX)
			.withStyle(style -> style.withColor(PREFIX_LIGHT_RED))
			.append(Component.literal(name).withStyle(ChatFormatting.AQUA))
			.append(Component.literal(" is not on the blacklist."));
	}

	public static MutableComponent blacklistEmpty() {
		return Component.literal(PREFIX)
			.withStyle(style -> style.withColor(PREFIX_LIGHT_RED))
			.append(Component.literal("The blacklist is empty."));
	}

	public static MutableComponent blacklistHeader() {
		return Component.literal(PREFIX)
			.withStyle(style -> style.withColor(PREFIX_LIGHT_RED))
			.append(Component.literal("Blacklist entries:"));
	}

	public static Component blacklistEntry(BlacklistManager.ScamEntry entry) {
		return Component.literal(entry.name()).withStyle(ChatFormatting.AQUA)
			.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
			.append(Component.literal(String.valueOf(entry.score())).withStyle(ChatFormatting.DARK_RED))
			.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
			.append(Component.literal(entry.reason()).withStyle(ChatFormatting.YELLOW))
			.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
			.append(Component.literal(formatTimestamp(entry.addedAt())).withStyle(ChatFormatting.GREEN));
	}

	public static MutableComponent unresolvedTarget(String input) {
		return Component.literal(PREFIX)
			.withStyle(style -> style.withColor(PREFIX_LIGHT_RED))
			.append(Component.literal("Could not resolve '"))
			.append(Component.literal(input).withStyle(ChatFormatting.YELLOW))
			.append(Component.literal("'. Tried UUID, online player list, local blacklist, and Mojang lookup."));
	}

	public static MutableComponent blacklistWarning(String playerName, String triggerReason, BlacklistManager.ScamEntry entry) {
		String score = entry == null ? "n/a" : String.valueOf(entry.score());
		String reason = entry == null ? "n/a" : entry.reason();
		String addedAt = entry == null ? "n/a" : formatTimestamp(entry.addedAt());
		String border = "+" + repeat('-', LABEL_WIDTH + 2) + "+" + repeat('-', VALUE_WIDTH + 2) + "+";

		MutableComponent table = Component.literal("SCAM WARNING\n" + border + "\n");
		appendRow(table, "Player", playerName, LABEL_WIDTH, VALUE_WIDTH);
		appendRow(table, "Score", score, LABEL_WIDTH, VALUE_WIDTH);
		appendRow(table, "Reason", reason, LABEL_WIDTH, VALUE_WIDTH);
		appendRow(table, "Added At", addedAt, LABEL_WIDTH, VALUE_WIDTH);
		appendRow(table, "Trigger", triggerReason, LABEL_WIDTH, VALUE_WIDTH);
		table.append(Component.literal(border));

		return Component.literal(PREFIX)
			.withStyle(style -> style.withColor(PREFIX_LIGHT_RED))
			.append(table);
	}

	private static void appendRow(MutableComponent builder, String label, String value, int labelWidth, int valueWidth) {
		List<String> chunks = wrap(value == null ? "n/a" : value, valueWidth);
		builder.append(Component.literal("| " + padRight(label, labelWidth) + " | "));
		builder.append(Component.literal(padRight(chunks.getFirst(), valueWidth)).withStyle(styleForLabel(label)));
		builder.append(Component.literal(" |\n"));
		for (int i = 1; i < chunks.size(); i++) {
			builder.append(Component.literal("| " + padRight("", labelWidth) + " | "));
			builder.append(Component.literal(padRight(chunks.get(i), valueWidth)).withStyle(styleForLabel(label)));
			builder.append(Component.literal(" |\n"));
		}
	}

	private static ChatFormatting styleForLabel(String label) {
		return switch (label) {
			case "Player" -> ChatFormatting.AQUA;
			case "Score" -> ChatFormatting.DARK_RED;
			case "Reason" -> ChatFormatting.YELLOW;
			case "Added At" -> ChatFormatting.GREEN;
			case "Trigger" -> ChatFormatting.GOLD;
			default -> ChatFormatting.WHITE;
		};
	}

	private static List<String> wrap(String input, int width) {
		List<String> lines = new ArrayList<>();
		String remaining = input == null ? "" : input;
		while (remaining.length() > width) {
			int split = remaining.lastIndexOf(' ', width);
			if (split <= 0) {
				split = width;
			}
			lines.add(remaining.substring(0, split).trim());
			remaining = remaining.substring(split).trim();
		}
		lines.add(remaining.isEmpty() ? "-" : remaining);
		return lines;
	}

	private static String padRight(String value, int width) {
		if (value.length() >= width) {
			return value.substring(0, width);
		}
		return value + " ".repeat(width - value.length());
	}

	private static String repeat(char c, int count) {
		return String.valueOf(c).repeat(Math.max(0, count));
	}

	private static String formatTimestamp(String input) {
		if (input == null || input.isBlank()) {
			return "n/a";
		}
		try {
			return TIMESTAMP_FORMATTER.format(Instant.parse(input));
		} catch (Exception ignored) {
			return input;
		}
	}
}
