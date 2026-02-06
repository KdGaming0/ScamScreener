package eu.tango.scamscreener.pipeline.core;

import eu.tango.scamscreener.rules.ScamRules;
import eu.tango.scamscreener.pipeline.model.DetectionLevel;

public final class DetectionScoring {
	private DetectionScoring() {
	}

	public static DetectionLevel mapLevel(double score) {
		if (score >= ScamRules.levelCriticalThreshold()) {
			return DetectionLevel.CRITICAL;
		}
		if (score >= ScamRules.levelHighThreshold()) {
			return DetectionLevel.HIGH;
		}
		if (score >= ScamRules.levelMediumThreshold()) {
			return DetectionLevel.MEDIUM;
		}
		return DetectionLevel.LOW;
	}

	public static ScamRules.ScamRiskLevel toScamRiskLevel(DetectionLevel level) {
		if (level == null) {
			return ScamRules.ScamRiskLevel.LOW;
		}
		return switch (level) {
			case LOW -> ScamRules.ScamRiskLevel.LOW;
			case MEDIUM -> ScamRules.ScamRiskLevel.MEDIUM;
			case HIGH -> ScamRules.ScamRiskLevel.HIGH;
			case CRITICAL -> ScamRules.ScamRiskLevel.CRITICAL;
		};
	}
}
