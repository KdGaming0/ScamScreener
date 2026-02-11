package eu.tango.scamscreener.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TextUtil {
	private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("\\u00A7.");
	private static final Pattern AT_NAME_PATTERN = Pattern.compile("@[A-Za-z0-9_]{3,16}");
	private static final Pattern COMMAND_TARGET_PATTERN = Pattern.compile(
		"(?i)(\\b(?:/msg|/w|/tell|/party\\s+invite|/p\\s+invite|/f\\s+add|/coopadd|/visit)\\s+)([A-Za-z0-9_]{3,16})\\b"
	);
	private static final Pattern MIXED_NAME_TOKEN_PATTERN = Pattern.compile(
		"\\b(?=[A-Za-z0-9_]{5,16}\\b)(?=(?:.*_.*|.*[A-Z].*|(?:.*\\d){2,}))[A-Za-z0-9_]+\\b"
	);

	private TextUtil() {
	}

	public static String normalizeForMatch(String input) {
		if (input == null) {
			return "";
		}
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
	}

	public static String normalizeCommand(String input, boolean isCommand) {
		if (!isCommand || input == null) {
			return input;
		}
		String trimmed = input.trim();
		if (trimmed.startsWith("/")) {
			return trimmed.substring(1);
		}
		return trimmed;
	}

	public static String anonymizeForAi(String input, String playerNameHint) {
		if (input == null || input.isBlank()) {
			return "";
		}
		String sanitized = COLOR_CODE_PATTERN.matcher(input).replaceAll(" ");
		sanitized = AT_NAME_PATTERN.matcher(sanitized).replaceAll("@player");
		sanitized = COMMAND_TARGET_PATTERN.matcher(sanitized).replaceAll("$1player");
		sanitized = MIXED_NAME_TOKEN_PATTERN.matcher(sanitized).replaceAll("player");

		if (playerNameHint != null && !playerNameHint.isBlank()) {
			String escaped = Pattern.quote(playerNameHint.trim());
			sanitized = sanitized.replaceAll("(?i)\\b" + escaped + "\\b", "player");
		}
		return sanitized.replaceAll("\\s+", " ").trim();
	}

	public static String anonymizedSpeakerKey(String playerName) {
		if (playerName == null || playerName.isBlank()) {
			return "speaker-unknown";
		}
		String normalized = playerName.trim().toLowerCase(Locale.ROOT);
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
			return "speaker-" + toHex(hashed, 8);
		} catch (NoSuchAlgorithmException ignored) {
			return "speaker-" + Integer.toUnsignedString(normalized.hashCode(), 36);
		}
	}

	private static String toHex(byte[] bytes, int maxBytes) {
		StringBuilder out = new StringBuilder(maxBytes * 2);
		int length = Math.min(maxBytes, bytes.length);
		for (int i = 0; i < length; i++) {
			out.append(String.format(Locale.ROOT, "%02x", bytes[i]));
		}
		return out.toString();
	}
}
