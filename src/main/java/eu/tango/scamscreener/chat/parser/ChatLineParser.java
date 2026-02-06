package eu.tango.scamscreener.chat.parser;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatLineParser {
	private static final Pattern CHAT_LINE_PATTERN = Pattern.compile("^.*?([A-Za-z0-9_]{3,16})\\s*:\\s*(.+)$");
	private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("Â§.");
	private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
	private static final Set<String> SYSTEM_LABELS = Set.of(
		"profile", "area", "server", "gems", "fairy", "essence", "wither",
		"cookie", "active", "upgrades", "collection", "dungeons", "players", "info",
		"rng", "meter", "other", "bank", "interest", "unclaimed", "scamscreener"
	);

	private ChatLineParser() {
	}

	public static ParsedPlayerLine parsePlayerLine(String rawLine) {
		if (rawLine == null || rawLine.isBlank()) {
			return null;
		}

		String cleaned = COLOR_CODE_PATTERN.matcher(rawLine).replaceAll("").trim();
		if (cleaned.startsWith("[ScamScreener]")) {
			return null;
		}

		String playerName = null;
		String message = null;
		Matcher matcher = CHAT_LINE_PATTERN.matcher(cleaned);
		if (matcher.matches()) {
			playerName = matcher.group(1);
			message = matcher.group(2);
		} else {
			int colonIndex = cleaned.indexOf(':');
			if (colonIndex > 0 && colonIndex + 1 < cleaned.length()) {
				String before = cleaned.substring(0, colonIndex).trim();
				String after = cleaned.substring(colonIndex + 1).trim();
				if (!after.isBlank()) {
					playerName = extractNameToken(before);
					message = after;
				}
			}
		}
		if (playerName == null || message == null) {
			return null;
		}

		String normalizedName = playerName.trim().toLowerCase(Locale.ROOT);
		if (normalizedName.isBlank() || SYSTEM_LABELS.contains(normalizedName)) {
			return null;
		}

		String trimmedMessage = message.trim();
		if (trimmedMessage.isBlank()) {
			return null;
		}

		return new ParsedPlayerLine(playerName.trim(), trimmedMessage);
	}

	private static String extractNameToken(String prefix) {
		if (prefix == null || prefix.isBlank()) {
			return null;
		}
		String[] tokens = prefix.split("\\s+");
		for (int i = tokens.length - 1; i >= 0; i--) {
			String token = tokens[i];
			if (token == null || token.isBlank()) {
				continue;
			}
			String cleaned = token.replaceAll("^[^A-Za-z0-9_]+|[^A-Za-z0-9_]+$", "");
			if (cleaned.isBlank()) {
				continue;
			}
			if (NAME_PATTERN.matcher(cleaned).matches()) {
				return cleaned;
			}
		}
		return null;
	}

	public record ParsedPlayerLine(String playerName, String message) {
	}
}
