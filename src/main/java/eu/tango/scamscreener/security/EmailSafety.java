package eu.tango.scamscreener.security;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class EmailSafety {
	private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b", Pattern.CASE_INSENSITIVE);
	private static final int MAX_PENDING = 5;

	private final Map<String, Pending> pending = new LinkedHashMap<>();

	public String blockIfEmail(String message, boolean isCommand) {
		if (message == null || message.isBlank()) {
			return null;
		}
		if (!EMAIL_PATTERN.matcher(message).find()) {
			return null;
		}
		String id = UUID.randomUUID().toString().replace("-", "");
		pending.put(id, new Pending(isCommand, message));
		trimPending();
		return id;
	}

	public Pending takePending(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		return pending.remove(id);
	}

	private void trimPending() {
		while (pending.size() > MAX_PENDING) {
			String oldest = pending.keySet().iterator().next();
			pending.remove(oldest);
		}
	}

	public record Pending(boolean command, String message) {
	}
}
