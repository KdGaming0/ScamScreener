package eu.tango.scamscreener.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import eu.tango.scamscreener.ui.DebugRegistry;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DebugConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE_PATH = ScamScreenerPaths.inModConfigDir("scam-screener-debug.json");

	public Map<String, Boolean> states = new LinkedHashMap<>();

	public static DebugConfig loadOrCreate() {
		if (!Files.exists(FILE_PATH)) {
			DebugConfig defaults = new DebugConfig();
			save(defaults);
			return defaults;
		}

		DebugConfig loaded = loadFromPath(FILE_PATH);
		if (loaded == null) {
			loaded = new DebugConfig();
		}
		return loaded.normalize();
	}

	private static DebugConfig loadFromPath(Path path) {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return GSON.fromJson(reader, DebugConfig.class);
		} catch (IOException ignored) {
			return null;
		}
	}

	public static void save(DebugConfig config) {
		try {
			Files.createDirectories(FILE_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE_PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException ignored) {
		}
	}

	public boolean isEnabled(String key) {
		String normalized = DebugRegistry.normalize(key);
		return states != null && Boolean.TRUE.equals(states.get(normalized));
	}

	public void setEnabled(String key, boolean enabled) {
		String normalized = DebugRegistry.normalize(key);
		if (normalized.isBlank()) {
			return;
		}
		if (states == null) {
			states = new LinkedHashMap<>();
		}
		states.put(normalized, enabled);
	}

	public void setAll(boolean enabled) {
		if (states == null) {
			states = new LinkedHashMap<>();
		}
		for (String key : DebugRegistry.keys()) {
			states.put(key, enabled);
		}
	}

	public Map<String, Boolean> snapshot() {
		return DebugRegistry.withDefaults(states);
	}

	private DebugConfig normalize() {
		states = DebugRegistry.withDefaults(states);
		return this;
	}
}
