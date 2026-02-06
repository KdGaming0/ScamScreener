package eu.tango.scamscreener;

import eu.tango.scamscreener.ai.LocalAiScorer;
import eu.tango.scamscreener.ai.ModelUpdateService;
import eu.tango.scamscreener.ai.TrainingDataService;
import eu.tango.scamscreener.ai.LocalAiTrainer;
import eu.tango.scamscreener.blacklist.BlacklistManager;
import eu.tango.scamscreener.commands.ScamScreenerCommands;
import eu.tango.scamscreener.config.DebugConfig;
import eu.tango.scamscreener.config.LocalAiModelConfig;
import eu.tango.scamscreener.chat.mute.MutePatternManager;
import eu.tango.scamscreener.chat.parser.ChatLineParser;
import eu.tango.scamscreener.chat.trigger.TriggerContext;
import eu.tango.scamscreener.pipeline.model.DetectionOutcome;
import eu.tango.scamscreener.pipeline.core.DetectionPipeline;
import eu.tango.scamscreener.pipeline.model.MessageEvent;
import eu.tango.scamscreener.pipeline.core.MessageEventParser;
import eu.tango.scamscreener.lookup.MojangProfileService;
import eu.tango.scamscreener.lookup.PlayerLookup;
import eu.tango.scamscreener.lookup.ResolvedTarget;
import eu.tango.scamscreener.rules.ScamRules;
import eu.tango.scamscreener.ui.Messages;
import eu.tango.scamscreener.ui.MessageFlagging;
import eu.tango.scamscreener.ui.Keybinds;
import eu.tango.scamscreener.ui.DebugMessages;
import eu.tango.scamscreener.ui.DebugRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.scores.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import eu.tango.scamscreener.security.EmailSafety;

public class ScamScreenerClient implements ClientModInitializer {
	private static final BlacklistManager BLACKLIST = new BlacklistManager();
	private static final Logger LOGGER = LoggerFactory.getLogger(ScamScreenerClient.class);
	private static final ScheduledExecutorService WARNING_SOUND_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "scamscreener-warning-sound");
		thread.setDaemon(true);
		return thread;
	});
	private static final int LEGIT_LABEL = 0;
	private static final int SCAM_LABEL = 1;
	private final PlayerLookup playerLookup = new PlayerLookup();
	private final MojangProfileService mojangProfileService = new MojangProfileService();
	private final TrainingDataService trainingDataService = new TrainingDataService();
	private final LocalAiTrainer localAiTrainer = new LocalAiTrainer();
	private final ModelUpdateService modelUpdateService = new ModelUpdateService();
	private final MutePatternManager mutePatternManager = new MutePatternManager();
	private final DetectionPipeline detectionPipeline = new DetectionPipeline(mutePatternManager, new LocalAiScorer());
	private final Set<UUID> currentlyDetected = new HashSet<>();
	private final Set<String> warnedContexts = new HashSet<>();
	private final EmailSafety emailSafety = new EmailSafety();
	private DebugConfig debugConfig;
	private boolean checkedModelUpdate;
	private volatile boolean trainingInProgress;

	@Override
	public void onInitializeClient() {
		BLACKLIST.load();
		ScamRules.reloadConfig();
		mutePatternManager.load();
		loadDebugConfig();
		registerCommands();
		Keybinds.register();
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> Keybinds.register());
		registerHypixelMessageChecks();
		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
	}

	private void registerCommands() {
		ScamScreenerCommands commands = new ScamScreenerCommands(
			BLACKLIST,
			this::resolveTargetOrReply,
			mutePatternManager,
			this::captureChatAsTrainingData,
			this::captureMessageById,
			this::captureBulkLegit,
			this::migrateTrainingData,
			this::handleModelUpdateCommand,
			this::handleModelUpdateCheck,
			this::handleEmailBypass,
			this::setAllDebug,
			this::setDebugKey,
			this::debugStateSnapshot,
			this::trainLocalAiModel,
			this::resetLocalAiModel,
			trainingDataService::lastCapturedLine,
			currentlyDetected::remove,
			ScamScreenerClient::reply
		);
		commands.register();
	}

	private void registerHypixelMessageChecks() {
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !mutePatternManager.shouldBlock(message.getString()));
		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, timestamp) -> handleChatAllow(message));
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> handleHypixelMessage(message));
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) -> handleHypixelMessage(message));
		ClientSendMessageEvents.ALLOW_CHAT.register(this::handleOutgoingChat);
		ClientSendMessageEvents.ALLOW_COMMAND.register(this::handleOutgoingCommand);
	}

	private void onClientTick(Minecraft client) {
		updateHoveredFlagTarget(client);
		handleFlagKeybinds(client);
		if (client.player == null || client.getConnection() == null) {
			currentlyDetected.clear();
			warnedContexts.clear();
			detectionPipeline.reset();
			checkedModelUpdate = false;
			return;
		}
		if (!checkedModelUpdate) {
			checkedModelUpdate = true;
			modelUpdateService.checkForUpdateAsync(ScamScreenerClient::reply);
		}

		maybeNotifyBlockedMessages(client);

		if (BLACKLIST.isEmpty()) {
			currentlyDetected.clear();
			return;
		}

		Team ownTeam = client.player.getTeam();
		if (ownTeam == null) {
			currentlyDetected.clear();
			return;
		}

		Set<UUID> detectedNow = new HashSet<>();
		for (PlayerInfo entry : playerLookup.onlinePlayers()) {
			String playerName = entry.getProfile().name();
			UUID playerUuid = entry.getProfile().id();
			if (!BLACKLIST.contains(playerUuid)) {
				continue;
			}

			Team otherTeam = entry.getTeam();
			if (otherTeam == null) {
				continue;
			}

			if (!ownTeam.getName().equals(otherTeam.getName())) {
				continue;
			}

			detectedNow.add(playerUuid);
			if (!currentlyDetected.contains(playerUuid)) {
				sendWarning(playerName, playerUuid);
			}
		}

		currentlyDetected.clear();
		currentlyDetected.addAll(detectedNow);
	}

	private void handleHypixelMessage(Component message) {
		String plain = message.getString().trim();
		trainingDataService.recordChatLine(plain);

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.getConnection() == null) {
			return;
		}

		MessageEvent event = MessageEventParser.parse(plain, System.currentTimeMillis());
		if (event != null) {
			detectionPipeline.process(event, ScamScreenerClient::reply, ScamScreenerClient::playWarningTone)
				.ifPresent(this::autoAddFlaggedMessageToTrainingData);
		}
		if (BLACKLIST.isEmpty()) {
			return;
		}

		for (TriggerContext context : TriggerContext.values()) {
			checkTriggerAndWarn(plain, context);
		}
	}

	private boolean handleChatAllow(Component message) {
		String plain = message == null ? "" : message.getString();
		if (mutePatternManager.shouldBlock(plain)) {
			debugMute("blocked chat: " + plain);
			return false;
		}
		ChatLineParser.ParsedPlayerLine parsed = ChatLineParser.parsePlayerLine(plain);
		if (parsed == null) {
			return true;
		}

		boolean blacklisted = isBlacklisted(parsed.playerName());
		debugChatColor("line player=" + parsed.playerName() + " blacklisted=" + blacklisted);
		Component decorated = blacklisted
			? rebuildChatMessage(message, parsed)
			: decorateChatMessage(message, parsed.message(), false);
		Minecraft client = Minecraft.getInstance();
		if (client != null) {
			client.execute(() -> {
				if (client.gui != null) {
					client.gui.getChat().addMessage(decorated);
				}
			});
		}

		handleHypixelMessage(message);
		return false;
	}

	private static Component decorateChatMessage(Component message, String rawMessage, boolean blacklisted) {
		String safe = rawMessage == null ? "" : rawMessage.trim();
		String id = MessageFlagging.registerMessage(safe);
		MutableComponent hover = Component.literal("CTRL+Y = legit\nCTRL+N = scam").withStyle(ChatFormatting.YELLOW);
		MutableComponent wrapped = message == null ? Component.empty() : message.copy();
		Style extra = Style.EMPTY
			.withHoverEvent(new HoverEvent.ShowText(hover))
			.withClickEvent(new ClickEvent.CopyToClipboard(MessageFlagging.clickValue(id)));
		applyStyleRecursive(wrapped, extra, blacklisted ? safe : null, blacklisted ? ChatFormatting.RED : null);
		return wrapped;
	}

	private static Component rebuildChatMessage(Component message, ChatLineParser.ParsedPlayerLine parsed) {
		String safe = parsed == null || parsed.message() == null ? "" : parsed.message().trim();
		String id = MessageFlagging.registerMessage(safe);
		MutableComponent hover = Component.literal("CTRL+Y = legit\nCTRL+N = scam").withStyle(ChatFormatting.YELLOW);
		Style clickStyle = Style.EMPTY
			.withHoverEvent(new HoverEvent.ShowText(hover))
			.withClickEvent(new ClickEvent.CopyToClipboard(MessageFlagging.clickValue(id)));

		List<StyledSegment> segments = flattenSegments(message);
		MutableComponent out = Component.empty();
		boolean seenColon = false;
		for (StyledSegment segment : segments) {
			String text = segment.text();
			if (text == null || text.isEmpty()) {
				continue;
			}
			Style base = segment.style()
				.withHoverEvent(clickStyle.getHoverEvent())
				.withClickEvent(clickStyle.getClickEvent());

			if (!seenColon) {
				int colon = text.indexOf(':');
				if (colon < 0) {
					out.append(Component.literal(text).withStyle(base));
					continue;
				}
				String before = text.substring(0, colon + 1);
				out.append(Component.literal(before).withStyle(base));
				String after = text.substring(colon + 1);
				if (!after.isEmpty()) {
					Style red = base.withColor(ChatFormatting.RED);
					out.append(Component.literal(after).withStyle(red));
				}
				seenColon = true;
				continue;
			}

			out.append(Component.literal(text).withStyle(base.withColor(ChatFormatting.RED)));
		}
		return out;
	}

	private static List<StyledSegment> flattenSegments(Component component) {
		if (component == null) {
			return List.of();
		}
		List<StyledSegment> segments = new java.util.ArrayList<>();
		component.visit((style, text) -> {
			if (text != null && !text.isEmpty()) {
				segments.add(new StyledSegment(text, style));
			}
			return java.util.Optional.empty();
		}, Style.EMPTY);
		return segments;
	}

	private record StyledSegment(String text, Style style) {
	}

	private static void applyStyleRecursive(MutableComponent component, Style extra, String messageText, ChatFormatting defaultColor) {
		if (component == null) {
			return;
		}
		Style merged = component.getStyle()
			.withHoverEvent(extra.getHoverEvent())
			.withClickEvent(extra.getClickEvent());
		if (defaultColor != null) {
			if (shouldColorMessagePart(component, messageText)) {
				merged = merged.withColor(defaultColor);
			} else if (merged.getColor() == null) {
				merged = merged.withColor(defaultColor);
			}
		}
		component.setStyle(merged);
		for (Component sibling : component.getSiblings()) {
			if (sibling instanceof MutableComponent mutable) {
				applyStyleRecursive(mutable, extra, messageText, defaultColor);
			}
		}
	}

	private static boolean shouldColorMessagePart(MutableComponent component, String messageText) {
		if (messageText == null || messageText.isBlank()) {
			return false;
		}
		if (!component.getSiblings().isEmpty()) {
			return false;
		}
		String piece = component.getString();
		if (piece == null || piece.isBlank()) {
			return false;
		}
		String normalizedMessage = normalizeForMatch(messageText);
		String normalizedPiece = normalizeForMatch(piece);
		if (normalizedPiece.isBlank()) {
			return false;
		}
		return normalizedMessage.contains(normalizedPiece);
	}

	private static String normalizeForMatch(String input) {
		if (input == null) {
			return "";
		}
		return input.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
	}

	private boolean isBlacklisted(String playerName) {
		if (playerName == null || playerName.isBlank()) {
			return false;
		}
		UUID uuid = playerLookup.findUuidByName(playerName);
		if (uuid != null) {
			return BLACKLIST.contains(uuid);
		}
		return BLACKLIST.findByName(playerName) != null;
	}

	private void updateHoveredFlagTarget(Minecraft client) {
		MessageFlagging.clearHovered();
		if (!(client.screen instanceof ChatScreen)) {
			return;
		}
		ChatComponent chat = client.gui.getChat();
		if (chat == null) {
			return;
		}
		double mouseX = scaledMouseX(client);
		double mouseY = scaledMouseY(client);
		Style style = chat.getClickedComponentStyleAt(mouseX, mouseY);
		if (style == null) {
			return;
		}
		ClickEvent clickEvent = style.getClickEvent();
		String id = MessageFlagging.extractId(clickEvent);
		if (id != null) {
			MessageFlagging.setHoveredId(id);
		}
	}

	private static double scaledMouseX(Minecraft client) {
		double raw = client.mouseHandler.xpos();
		return raw * client.getWindow().getGuiScaledWidth() / client.getWindow().getScreenWidth();
	}

	private static double scaledMouseY(Minecraft client) {
		double raw = client.mouseHandler.ypos();
		return raw * client.getWindow().getGuiScaledHeight() / client.getWindow().getScreenHeight();
	}

	private void handleFlagKeybinds(Minecraft client) {
		if (client.player == null || client.screen == null) {
			return;
		}
		if (!ChatScreen.class.isInstance(client.screen)) {
			return;
		}
		if (!Keybinds.isControlDown(client)) {
			return;
		}
		if (Keybinds.consumeFlagLegit()) {
			flagHoveredMessage(LEGIT_LABEL);
		}
		if (Keybinds.consumeFlagScam()) {
			flagHoveredMessage(SCAM_LABEL);
		}
	}

	private void flagHoveredMessage(int label) {
		String message = MessageFlagging.hoveredMessage();
		if (message == null || message.isBlank()) {
			reply(Messages.noChatToCapture());
			return;
		}
		try {
			trainingDataService.appendRows(List.of(message), label);
			reply(Messages.trainingSampleFlagged(label == LEGIT_LABEL ? "legit" : "scam"));
		} catch (IOException e) {
			LOGGER.warn("Failed to save training sample from hover", e);
			// Code: TR-SAVE-002
			reply(Messages.trainingSamplesSaveFailed(trainingErrorDetail(e, trainingDataService.trainingDataPath())));
		}
	}


	private void maybeNotifyBlockedMessages(Minecraft client) {
		long now = System.currentTimeMillis();
		if (!mutePatternManager.shouldNotifyNow(now)) {
			return;
		}

		int blocked = mutePatternManager.consumeBlockedCount(now);
		if (blocked <= 0 || client.player == null) {
			return;
		}
		client.player.displayClientMessage(Messages.blockedMessagesSummary(blocked, mutePatternManager.notifyIntervalSeconds()), false);
	}

	private int captureChatAsTrainingData(String playerName, int label, int count) {
		List<String> lines = trainingDataService.recentLinesForPlayer(playerName, count);
		if (lines.isEmpty()) {
			reply(Messages.noChatToCapture());
			return 0;
		}

		try {
			trainingDataService.appendRows(lines, label);
			if (lines.size() == 1) {
				reply(Messages.trainingSampleSaved(trainingDataService.trainingDataPath().toString(), label));
			} else {
				reply(Messages.trainingSamplesSaved(trainingDataService.trainingDataPath().toString(), label, lines.size()));
			}
			return 1;
		} catch (IOException e) {
			LOGGER.warn("Failed to save training samples", e);
			// Code: TR-SAVE-002
			reply(Messages.trainingSamplesSaveFailed(trainingErrorDetail(e, trainingDataService.trainingDataPath())));
			return 0;
		}
	}

	private int captureMessageById(String messageId, int label) {
		String message = MessageFlagging.messageById(messageId);
		if (message == null || message.isBlank()) {
			reply(Messages.noChatToCapture());
			return 0;
		}
		try {
			trainingDataService.appendRows(List.of(message), label);
			reply(Messages.trainingSampleFlagged(label == LEGIT_LABEL ? "legit" : "scam"));
			return 1;
		} catch (IOException e) {
			LOGGER.warn("Failed to save training sample from message id", e);
			// Code: TR-SAVE-002
			reply(Messages.trainingSamplesSaveFailed(trainingErrorDetail(e, trainingDataService.trainingDataPath())));
			return 0;
		}
	}

	private int captureBulkLegit(int count) {
		List<String> lines = trainingDataService.recentLines(count);
		if (lines.isEmpty()) {
			reply(Messages.noChatToCapture());
			return 0;
		}
		try {
			trainingDataService.appendRows(lines, LEGIT_LABEL);
			reply(Messages.trainingSamplesSaved(trainingDataService.trainingDataPath().toString(), LEGIT_LABEL, lines.size()));
			return 1;
		} catch (IOException e) {
			LOGGER.warn("Failed to save bulk legit samples", e);
			// Code: TR-SAVE-002
			reply(Messages.trainingSamplesSaveFailed(trainingErrorDetail(e, trainingDataService.trainingDataPath())));
			return 0;
		}
	}

	private int downloadModelUpdate(String id) {
		return modelUpdateService.download(id, ScamScreenerClient::reply);
	}

	private int acceptModelUpdate(String id) {
		return modelUpdateService.accept(id, ScamScreenerClient::reply);
	}

	private int mergeModelUpdate(String id) {
		return modelUpdateService.merge(id, ScamScreenerClient::reply);
	}

	private int ignoreModelUpdate(String id) {
		return modelUpdateService.ignore(id, ScamScreenerClient::reply);
	}

	private int handleModelUpdateCommand(String action, String id) {
		return switch (action) {
			case "download" -> downloadModelUpdate(id);
			case "accept" -> acceptModelUpdate(id);
			case "merge" -> mergeModelUpdate(id);
			case "ignore" -> ignoreModelUpdate(id);
			default -> 0;
		};
	}

	private int handleModelUpdateCheck(boolean force) {
		modelUpdateService.checkForUpdateAndDownloadAsync(ScamScreenerClient::reply, force);
		return 1;
	}

	private int handleEmailBypass(String id) {
		if (id == null || id.isBlank()) {
			reply(Messages.emailBypassExpired());
			return 0;
		}
		EmailSafety.Pending pending = emailSafety.takePending(id);
		if (pending == null || pending.message() == null || pending.message().isBlank()) {
			reply(Messages.emailBypassExpired());
			return 0;
		}
		if (pending.command()) {
			sendCommand(pending.message());
		} else {
			sendChatMessage(pending.message());
		}
		reply(Messages.emailBypassSent());
		return 1;
	}

	private boolean handleOutgoingChat(String message) {
		if (message == null || message.isBlank()) {
			return true;
		}
		String id = emailSafety.blockIfEmail(message, false);
		if (id == null) {
			return true;
		}
		reply(Messages.emailSafetyBlocked(id));
		return false;
	}

	private boolean handleOutgoingCommand(String command) {
		if (command == null || command.isBlank()) {
			return true;
		}
		String id = emailSafety.blockIfEmail(command, true);
		if (id == null) {
			return true;
		}
		reply(Messages.emailSafetyBlocked(id));
		return false;
	}

	private static void sendChatMessage(String message) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.getConnection() == null) {
			return;
		}
		client.getConnection().sendChat(message);
	}

	private static void sendCommand(String command) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.getConnection() == null) {
			return;
		}
		client.getConnection().sendCommand(command);
	}


	private void setAllDebug(boolean enabled) {
		modelUpdateService.setDebugEnabled(enabled);
		debugConfig.setAll(enabled);
		updateDebugConfig();
	}

	private void setDebugKey(String key, boolean enabled) {
		if (key == null) {
			return;
		}
		String normalized = DebugRegistry.normalize(key);
		if (normalized.isBlank()) {
			return;
		}
		if ("updater".equals(normalized)) {
			modelUpdateService.setDebugEnabled(enabled);
		}
		debugConfig.setEnabled(normalized, enabled);
		updateDebugConfig();
	}

	private java.util.Map<String, Boolean> debugStateSnapshot() {
		java.util.Map<String, Boolean> states = debugConfig.snapshot();
		states.put("updater", modelUpdateService.isDebugEnabled());
		return states;
	}

	private void loadDebugConfig() {
		debugConfig = DebugConfig.loadOrCreate();
		if (debugConfig == null) {
			debugConfig = new DebugConfig();
		}
		modelUpdateService.setDebugEnabled(debugConfig.isEnabled("updater"));
	}

	private void updateDebugConfig() {
		DebugConfig.save(debugConfig);
	}

	private int migrateTrainingData() {
		try {
			int updated = trainingDataService.migrateTrainingData();
			if (updated <= 0) {
				reply(Messages.trainingDataUpToDate());
			} else {
				reply(Messages.trainingDataMigrated(updated));
			}
			return 1;
		} catch (IOException e) {
			LOGGER.warn("Failed to migrate training data", e);
			// Code: TR-SAVE-002
			reply(Messages.trainingSamplesSaveFailed(trainingErrorDetail(e, trainingDataService.trainingDataPath())));
			return 0;
		}
	}

	private int trainLocalAiModel() {
		if (trainingInProgress) {
			reply(Messages.trainingAlreadyRunning());
			return 0;
		}
		trainingInProgress = true;
		Thread thread = new Thread(() -> {
			try {
				LocalAiTrainer.TrainingResult result = localAiTrainer.trainAndSave(trainingDataService.trainingDataPath());
				ScamRules.reloadConfig();
				reply(Messages.trainingCompleted(
					result.sampleCount(),
					result.positiveCount(),
					result.archivedDataPath().getFileName().toString()
				));
				if (result.ignoredUnigrams() > 0) {
					reply(Messages.trainingUnigramsIgnored(result.ignoredUnigrams()));
				}
			} catch (IOException e) {
				LOGGER.warn("Failed to train local AI model", e);
				// Code: TR-TRAIN-001
				reply(Messages.trainingFailed(trainingErrorDetail(e, trainingDataService.trainingDataPath())));
			} finally {
				trainingInProgress = false;
			}
		}, "scamscreener-train");
		thread.setDaemon(true);
		thread.start();
		return 1;
	}

	private static String trainingErrorDetail(IOException error, Path trainingPath) {
		if (error == null) {
			return "unknown error";
		}
		if (error instanceof NoSuchFileException missing) {
			String file = missing.getFile();
			return "Training data file not found: " + (file == null ? "unknown" : file);
		}
		if (error instanceof AccessDeniedException denied) {
			String file = denied.getFile();
			return "Access denied while reading training data: " + (file == null ? "unknown" : file);
		}
		String message = error.getMessage();
		if (message == null || message.isBlank()) {
			return error.getClass().getSimpleName();
		}
		String trimmed = message.trim();
		if (trainingPath != null && trimmed.equals(trainingPath.toString())) {
			return "Training data file not found: " + trimmed;
		}
		return trimmed;
	}

	private void autoAddFlaggedMessageToTrainingData(DetectionOutcome outcome) {
		if (outcome == null || outcome.result() == null || !outcome.result().shouldCapture()) {
			return;
		}
		MessageEvent event = outcome.event();
		if (event == null || event.rawMessage() == null || event.rawMessage().isBlank()) {
			return;
		}

		try {
			trainingDataService.appendRows(List.of(event.rawMessage()), 1);
		} catch (IOException e) {
			LOGGER.debug("Failed to auto-save flagged message as training sample", e);
		}
	}

	private int resetLocalAiModel() {
		LocalAiModelConfig.save(new LocalAiModelConfig());
		ScamRules.reloadConfig();
		reply(Messages.localAiModelReset());
		return 1;
	}

	private static UUID parseUuid(String input) {
		if (input == null || input.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(input.trim());
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private ResolvedTarget resolveTargetOrReply(String input) {
		UUID parsedUuid = parseUuid(input);
		if (parsedUuid != null) {
			return new ResolvedTarget(parsedUuid, playerLookup.findNameByUuid(parsedUuid));
		}

		UUID byName = playerLookup.findUuidByName(input);
		if (byName != null) {
			return new ResolvedTarget(byName, playerLookup.findNameByUuid(byName));
		}

		BlacklistManager.ScamEntry knownEntry = BLACKLIST.findByName(input);
		if (knownEntry != null) {
			return new ResolvedTarget(knownEntry.uuid(), knownEntry.name());
		}

		ResolvedTarget mojangResolved = mojangProfileService.lookupCached(input);
		if (mojangResolved != null) {
			return mojangResolved;
		}

		mojangProfileService.lookupAsync(input).thenAccept(resolved -> {
			if (resolved == null) {
				reply(Messages.unresolvedTarget(input));
				return;
			}
			reply(Messages.mojangLookupCompleted(input, resolved.name()));
		});
		reply(Messages.mojangLookupStarted(input));
		return null;
	}

	private void checkTriggerAndWarn(String message, TriggerContext context) {
		String playerName = context.matchPlayerName(message);
		if (playerName == null) {
			return;
		}

		UUID uuid = playerLookup.findUuidByName(playerName);
		debugTrade("trade trigger " + context.name().toLowerCase(java.util.Locale.ROOT) + " player=" + playerName + " blacklisted=" + (uuid != null && BLACKLIST.contains(uuid)));
		if (uuid == null || !BLACKLIST.contains(uuid)) {
			return;
		}

		String dedupeKey = context.dedupeKey(uuid);
		if (!warnedContexts.add(dedupeKey)) {
			debugTrade("trade trigger already warned: " + playerName);
			return;
		}

		sendBlacklistWarning(playerName, uuid, context.triggerReason());
	}

	private void debugTrade(String message) {
		if (!debugConfig.isEnabled("trade")) {
			return;
		}
		reply(DebugMessages.debug("Trade", message));
	}

	private void debugMute(String message) {
		if (!debugConfig.isEnabled("mute")) {
			return;
		}
		reply(DebugMessages.debug("Mute", message));
	}

	private void debugChatColor(String message) {
		if (!debugConfig.isEnabled("chatcolor")) {
			return;
		}
		reply(DebugMessages.debug("ChatColor", message));
	}

	private void sendWarning(String playerName, UUID uuid) {
		sendBlacklistWarning(playerName, uuid, "is in your team");
	}

	private void sendBlacklistWarning(String playerName, UUID uuid, String reason) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}

		BlacklistManager.ScamEntry entry = BLACKLIST.get(uuid);
		client.player.displayClientMessage(Messages.blacklistWarning(playerName, reason, entry), false);
		playWarningTone();
	}

	private static void reply(Component text) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			if (client.player != null) {
				client.player.displayClientMessage(text, false);
			}
		});
	}

	private static void playWarningTone() {
		Minecraft client = Minecraft.getInstance();
		for (int i = 0; i < 3; i++) {
			long delayMs = i * 120L;
			WARNING_SOUND_EXECUTOR.schedule(() -> client.execute(() -> {
				if (client.player != null) {
					client.player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 0.8F, 1.2F);
				}
			}), delayMs, TimeUnit.MILLISECONDS);
		}
	}

}
