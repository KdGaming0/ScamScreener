package eu.tango.scamscreener.chat.parser;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatLineParser {
	private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("\\u00A7.");
	private static final Pattern NPC_PREFIX_PATTERN = Pattern.compile("^\\[npc\\](?:\\s|$).*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern DIRECT_CHAT_PATTERN = Pattern.compile(
		"^(?:[^A-Za-z0-9_\\s]*\\s*)*(?:\\[[^\\]]+\\]\\s*)*([A-Za-z0-9_]{3,16})\\s*:\\s*(.+)$"
	);
	private static final Pattern CHANNEL_CHAT_PATTERN = Pattern.compile(
		"^(?:party|guild|officer|team)\\s*>\\s*(?:\\[[^\\]]+\\]\\s*)*([A-Za-z0-9_]{3,16})\\s*:\\s*(.+)$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern WHISPER_CHAT_PATTERN = Pattern.compile(
		"^(?:from|to|whisper from|whisper to)\\s+(?:\\[[^\\]]+\\]\\s*)*([A-Za-z0-9_]{3,16})\\s*:\\s*(.+)$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Set<String> SYSTEM_LABELS = Set.of(
		"profile", "area", "server", "gems", "fairy", "essence", "wither",
		"cookie", "active", "upgrades", "collection", "dungeons", "players", "info",
		"rng", "meter", "other", "bank", "interest", "unclaimed", "scamscreener",
		"auction", "bazaar", "rewards", "party", "guild", "friend", "friends",
		"booster", "store", "profileviewer", "warning", "note", "tip", "announcement"
	);
	private static final List<Pattern> SYSTEM_MESSAGE_PATTERNS = List.of(
		Pattern.compile("^you'?ll be partying with: [A-Za-z0-9_]{3,16}\\.?$", Pattern.CASE_INSENSITIVE),
		Pattern.compile("^party finder > [A-Za-z0-9_]{3,16} joined the dungeon group(?:!.*)?$", Pattern.CASE_INSENSITIVE),
		Pattern.compile("^[A-Za-z0-9_]{3,16} has sent you a trade request\\.?$", Pattern.CASE_INSENSITIVE),
		Pattern.compile("^you have sent a trade request to [A-Za-z0-9_]{3,16}\\.?$", Pattern.CASE_INSENSITIVE),
		Pattern.compile("^you are trading with [A-Za-z0-9_]{3,16}\\.?$", Pattern.CASE_INSENSITIVE),
		Pattern.compile(
			"^[A-Za-z0-9_]{3,16} (?:has )?(?:requested|asks|asked) to join your (?:skyblock )?co-?op!?$",
			Pattern.CASE_INSENSITIVE
		),
		Pattern.compile("^you invited [A-Za-z0-9_]{3,16} to your (?:skyblock )?co-?op!?$", Pattern.CASE_INSENSITIVE),
		Pattern.compile("^[A-Za-z0-9_]{3,16} joined your (?:skyblock )?co-?op!?$", Pattern.CASE_INSENSITIVE),
		Pattern.compile(
			"^actions\\s*:\\s*\\[legit\\].*\\[scam\\].*\\[blacklist\\].*$",
			Pattern.CASE_INSENSITIVE
		),
		Pattern.compile("^latest\\s+update\\s*:\\s*.+$", Pattern.CASE_INSENSITIVE),
		Pattern.compile("^update\\s*:\\s*.*\\b(?:click|v\\d+\\.\\d+\\.\\d+)\\b.*$", Pattern.CASE_INSENSITIVE)
	);

	private ChatLineParser() {
	}

	public static ParsedPlayerLine parsePlayerLine(String rawLine) {
		if (rawLine == null || rawLine.isBlank()) {
			return null;
		}

		String cleaned = COLOR_CODE_PATTERN.matcher(rawLine).replaceAll("").trim();
		if (isSystemLine(cleaned)) {
			return null;
		}

		Matcher matcher = matchPlayerChat(cleaned);
		if (matcher == null) {
			return null;
		}

		String playerName = matcher.group(1);
		String message = matcher.group(2);
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

	public static boolean isSystemLine(String rawLine) {
		if (rawLine == null || rawLine.isBlank()) {
			return false;
		}
		String cleaned = COLOR_CODE_PATTERN.matcher(rawLine).replaceAll("").trim();
		return isSystemLineCleaned(cleaned);
	}

	private static Matcher matchPlayerChat(String cleaned) {
		Matcher direct = DIRECT_CHAT_PATTERN.matcher(cleaned);
		if (direct.matches()) {
			return direct;
		}
		Matcher channel = CHANNEL_CHAT_PATTERN.matcher(cleaned);
		if (channel.matches()) {
			return channel;
		}
		Matcher whisper = WHISPER_CHAT_PATTERN.matcher(cleaned);
		if (whisper.matches()) {
			return whisper;
		}
		return null;
	}

	private static boolean isKnownSystemMessage(String cleaned) {
		if (cleaned == null || cleaned.isBlank()) {
			return false;
		}
		String normalized = cleaned.trim();
		for (Pattern pattern : SYSTEM_MESSAGE_PATTERNS) {
			if (pattern.matcher(normalized).matches()) {
				return true;
			}
		}
		return false;
	}

	private static boolean isSystemLineCleaned(String cleaned) {
		if (cleaned == null || cleaned.isBlank()) {
			return false;
		}
		if (cleaned.startsWith("[ScamScreener]")) {
			return true;
		}
		if (NPC_PREFIX_PATTERN.matcher(cleaned).matches()) {
			return true;
		}
		return isKnownSystemMessage(cleaned);
	}

	public record ParsedPlayerLine(String playerName, String message) {
	}
}
