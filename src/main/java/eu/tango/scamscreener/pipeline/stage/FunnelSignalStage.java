package eu.tango.scamscreener.pipeline.stage;

import eu.tango.scamscreener.pipeline.core.FunnelStore;
import eu.tango.scamscreener.pipeline.core.IntentTagger;
import eu.tango.scamscreener.pipeline.core.RuleConfig;
import eu.tango.scamscreener.pipeline.model.IntentTag;
import eu.tango.scamscreener.pipeline.model.MessageEvent;
import eu.tango.scamscreener.pipeline.model.Signal;
import eu.tango.scamscreener.pipeline.model.SignalSource;
import eu.tango.scamscreener.rules.ScamRules;

import java.util.List;

public final class FunnelSignalStage {
	private final RuleConfig ruleConfig;
	private final FunnelStore funnelStore;
	private final IntentTagger intentTagger;

	/**
	 * Evaluates per-player message funnels (offer/rep/redirect/instruction).
	 */
	public FunnelSignalStage(RuleConfig ruleConfig, FunnelStore funnelStore) {
		this.ruleConfig = ruleConfig;
		this.funnelStore = funnelStore;
		this.intentTagger = new IntentTagger(ruleConfig);
	}

	public List<Signal> collectSignals(MessageEvent event, List<Signal> existingSignals) {
		IntentTagger.TaggingResult tagging = intentTagger.tag(event, existingSignals);
		FunnelStore.FunnelEvaluation evaluation = funnelStore.evaluate(event, tagging);
		if (!ruleConfig.isEnabled(ScamRules.ScamRule.FUNNEL_SEQUENCE_PATTERN)) {
			return List.of();
		}
		if (evaluation.detail() == null || evaluation.bonusScore() <= 0) {
			return List.of();
		}

		String evidence = appendCurrentTags(evaluation.detail(), tagging.tags());
		return List.of(new Signal(
			ScamRules.ScamRule.FUNNEL_SEQUENCE_PATTERN.name(),
			SignalSource.FUNNEL,
			evaluation.bonusScore(),
			evidence,
			ScamRules.ScamRule.FUNNEL_SEQUENCE_PATTERN,
			evaluation.relatedMessages()
		));
	}

	private static String appendCurrentTags(String detail, java.util.Set<IntentTag> tags) {
		if (tags == null || tags.isEmpty()) {
			return detail;
		}
		List<String> current = tags.stream().map(Enum::name).sorted().toList();
		return detail + "\nCurrent intent tags=" + String.join(", ", current);
	}
}
