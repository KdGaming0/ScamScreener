package eu.tango.scamscreener.pipeline.stage;

import java.util.List;
import eu.tango.scamscreener.pipeline.core.AiScorer;
import eu.tango.scamscreener.pipeline.model.BehaviorAnalysis;
import eu.tango.scamscreener.pipeline.model.MessageEvent;
import eu.tango.scamscreener.pipeline.model.Signal;

public final class AiSignalStage {
	private final AiScorer aiScorer;

	/**
	 * Wraps {@link AiScorer} so the pipeline has a uniform "collectSignals" API.
	 */
	public AiSignalStage(AiScorer aiScorer) {
		this.aiScorer = aiScorer;
	}

	/**
	 * Returns AI signals, or an empty list if no trigger happened.
	 */
	public List<Signal> collectSignals(MessageEvent event, BehaviorAnalysis analysis, List<Signal> existingSignals) {
		return aiScorer.score(event, analysis, existingSignals);
	}

	public void reset() {
		aiScorer.reset();
	}
}
