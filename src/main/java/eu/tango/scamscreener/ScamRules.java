package eu.tango.scamscreener;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ScamRules {
	private static PatternSet patterns = PatternSet.from(ScamRulesConfig.loadOrCreate());

	private ScamRules() {
	}

	public static void reloadConfig() {
		patterns = PatternSet.from(ScamRulesConfig.loadOrCreate());
	}

	public static ScamAssessment assess(BehaviorContext context) {
		EnumSet<ScamRule> triggered = EnumSet.noneOf(ScamRule.class);
		int score = 0;

		String message = normalize(context.message());
		if (!message.isEmpty()) {
			if (patterns.link().matcher(message).find()) {
				triggered.add(ScamRule.SUSPICIOUS_LINK);
				score += 20;
			}
			if (patterns.urgency().matcher(message).find()) {
				triggered.add(ScamRule.PRESSURE_AND_URGENCY);
				score += 15;
			}
			if (patterns.paymentFirst().matcher(message).find()) {
				triggered.add(ScamRule.UPFRONT_PAYMENT);
				score += 25;
			}
			if (patterns.accountData().matcher(message).find()) {
				triggered.add(ScamRule.ACCOUNT_DATA_REQUEST);
				score += 35;
			}
			if (patterns.tooGood().matcher(message).find()) {
				triggered.add(ScamRule.TOO_GOOD_TO_BE_TRUE);
				score += 15;
			}
			if (patterns.trustBait().matcher(message).find()) {
				triggered.add(ScamRule.TRUST_MANIPULATION);
				score += 10;
			}
		}

		if (context.pushesExternalPlatform()) {
			triggered.add(ScamRule.EXTERNAL_PLATFORM_PUSH);
			score += 15;
		}
		if (context.demandsUpfrontPayment()) {
			triggered.add(ScamRule.UPFRONT_PAYMENT);
			score += 25;
		}
		if (context.requestsSensitiveData()) {
			triggered.add(ScamRule.ACCOUNT_DATA_REQUEST);
			score += 35;
		}
		if (context.claimsTrustedMiddlemanWithoutProof()) {
			triggered.add(ScamRule.FAKE_MIDDLEMAN_CLAIM);
			score += 20;
		}
		if (context.repeatedContactAttempts() >= 3) {
			triggered.add(ScamRule.SPAMMY_CONTACT_PATTERN);
			score += 10;
		}

		return new ScamAssessment(score, mapLevel(score), triggered);
	}

	private static ScamRiskLevel mapLevel(int score) {
		if (score >= 70) {
			return ScamRiskLevel.CRITICAL;
		}
		if (score >= 40) {
			return ScamRiskLevel.HIGH;
		}
		if (score >= 20) {
			return ScamRiskLevel.MEDIUM;
		}
		return ScamRiskLevel.LOW;
	}

	private static String normalize(String message) {
		return message == null ? "" : message.toLowerCase(Locale.ROOT);
	}

	public enum ScamRule {
		SUSPICIOUS_LINK,
		PRESSURE_AND_URGENCY,
		UPFRONT_PAYMENT,
		ACCOUNT_DATA_REQUEST,
		EXTERNAL_PLATFORM_PUSH,
		FAKE_MIDDLEMAN_CLAIM,
		TOO_GOOD_TO_BE_TRUE,
		TRUST_MANIPULATION,
		SPAMMY_CONTACT_PATTERN
	}

	public enum ScamRiskLevel {
		LOW,
		MEDIUM,
		HIGH,
		CRITICAL
	}

	public record BehaviorContext(
		String message,
		boolean pushesExternalPlatform,
		boolean demandsUpfrontPayment,
		boolean requestsSensitiveData,
		boolean claimsTrustedMiddlemanWithoutProof,
		int repeatedContactAttempts
	) {
	}

	public record ScamAssessment(int riskScore, ScamRiskLevel riskLevel, Set<ScamRule> triggeredRules) {
		public boolean shouldWarn() {
			return riskLevel == ScamRiskLevel.HIGH || riskLevel == ScamRiskLevel.CRITICAL;
		}
	}

	private record PatternSet(
		Pattern link,
		Pattern urgency,
		Pattern paymentFirst,
		Pattern accountData,
		Pattern tooGood,
		Pattern trustBait
	) {
		private static PatternSet from(ScamRulesConfig config) {
			return new PatternSet(
				compileOrDefault(config.linkPattern, ScamRulesConfig.DEFAULT_LINK_PATTERN),
				compileOrDefault(config.urgencyPattern, ScamRulesConfig.DEFAULT_URGENCY_PATTERN),
				compileOrDefault(config.paymentFirstPattern, ScamRulesConfig.DEFAULT_PAYMENT_FIRST_PATTERN),
				compileOrDefault(config.accountDataPattern, ScamRulesConfig.DEFAULT_ACCOUNT_DATA_PATTERN),
				compileOrDefault(config.tooGoodPattern, ScamRulesConfig.DEFAULT_TOO_GOOD_PATTERN),
				compileOrDefault(config.trustBaitPattern, ScamRulesConfig.DEFAULT_TRUST_BAIT_PATTERN)
			);
		}
	}

	private static Pattern compileOrDefault(String candidate, String fallback) {
		try {
			return Pattern.compile(candidate);
		} catch (PatternSyntaxException ignored) {
			return Pattern.compile(fallback);
		}
	}
}
