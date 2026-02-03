package eu.tango.scamscreener;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ScamRulesConfig {
	public static final String DEFAULT_LINK_PATTERN = "(https?://|www\\.|discord\\.gg/|t\\.me/)";
	public static final String DEFAULT_URGENCY_PATTERN = "\\b(now|quick|fast|urgent|sofort|jetzt)\\b";
	public static final String DEFAULT_PAYMENT_FIRST_PATTERN = "\\b(pay first|first payment|vorkasse|send first)\\b";
	public static final String DEFAULT_ACCOUNT_DATA_PATTERN = "\\b(password|passwort|2fa|code|email login)\\b";
	public static final String DEFAULT_TOO_GOOD_PATTERN = "\\b(free coins|free rank|dupe|100% safe|garantiert)\\b";
	public static final String DEFAULT_TRUST_BAIT_PATTERN = "\\b(trust me|vertrau mir|legit)\\b";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve("scam-screener-rules.json");

	public String linkPattern = DEFAULT_LINK_PATTERN;
	public String urgencyPattern = DEFAULT_URGENCY_PATTERN;
	public String paymentFirstPattern = DEFAULT_PAYMENT_FIRST_PATTERN;
	public String accountDataPattern = DEFAULT_ACCOUNT_DATA_PATTERN;
	public String tooGoodPattern = DEFAULT_TOO_GOOD_PATTERN;
	public String trustBaitPattern = DEFAULT_TRUST_BAIT_PATTERN;

	public static ScamRulesConfig loadOrCreate() {
		if (!Files.exists(FILE_PATH)) {
			ScamRulesConfig defaults = new ScamRulesConfig();
			save(defaults);
			return defaults;
		}

		try (Reader reader = Files.newBufferedReader(FILE_PATH, StandardCharsets.UTF_8)) {
			ScamRulesConfig loaded = GSON.fromJson(reader, ScamRulesConfig.class);
			if (loaded == null) {
				ScamRulesConfig defaults = new ScamRulesConfig();
				save(defaults);
				return defaults;
			}
			return loaded.withDefaults();
		} catch (IOException ignored) {
			return new ScamRulesConfig();
		}
	}

	public static void save(ScamRulesConfig config) {
		try {
			Files.createDirectories(FILE_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE_PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException ignored) {
		}
	}

	private ScamRulesConfig withDefaults() {
		if (isBlank(linkPattern)) {
			linkPattern = DEFAULT_LINK_PATTERN;
		}
		if (isBlank(urgencyPattern)) {
			urgencyPattern = DEFAULT_URGENCY_PATTERN;
		}
		if (isBlank(paymentFirstPattern)) {
			paymentFirstPattern = DEFAULT_PAYMENT_FIRST_PATTERN;
		}
		if (isBlank(accountDataPattern)) {
			accountDataPattern = DEFAULT_ACCOUNT_DATA_PATTERN;
		}
		if (isBlank(tooGoodPattern)) {
			tooGoodPattern = DEFAULT_TOO_GOOD_PATTERN;
		}
		if (isBlank(trustBaitPattern)) {
			trustBaitPattern = DEFAULT_TRUST_BAIT_PATTERN;
		}
		return this;
	}

	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}
}
