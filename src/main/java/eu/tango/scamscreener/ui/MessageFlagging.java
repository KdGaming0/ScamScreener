package eu.tango.scamscreener.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class MessageFlagging {
	private static final String CLICK_PREFIX = "scamscreener:msg:";
	private static final int MAX_ENTRIES = 200;
	private static final Map<String, String> RECENT = new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
			return size() > MAX_ENTRIES;
		}
	};

	private MessageFlagging() {
	}

	public static String registerMessage(String message) {
		if (message == null || message.isBlank()) {
			return "";
		}
		String id = UUID.randomUUID().toString().replace("-", "");
		RECENT.put(id, message);
		return id;
	}

	public static String clickValue(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		return CLICK_PREFIX + id;
	}

	public static String messageById(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		return RECENT.get(id);
	}
}
