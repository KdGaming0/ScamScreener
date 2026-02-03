package eu.tango.scamscreener;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.google.gson.Gson;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Team;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScamScreenerClient implements ClientModInitializer {
	private static final BlacklistManager BLACKLIST = new BlacklistManager();
	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	private static final Pattern TRADE_INCOMING_PATTERN = Pattern.compile("^([A-Za-z0-9_]{3,16}) has sent you a trade request\\.?$");
	private static final Pattern TRADE_OUTGOING_PATTERN = Pattern.compile("^You have sent a trade request to ([A-Za-z0-9_]{3,16})\\.?$");
	private static final Pattern TRADE_SESSION_PATTERN = Pattern.compile("^You are trading with ([A-Za-z0-9_]{3,16})\\.?$");

	private final Set<UUID> currentlyDetected = new HashSet<>();
	private final Set<String> warnedContexts = new HashSet<>();

	@Override
	public void onInitializeClient() {
		BLACKLIST.load();
		ScamRules.reloadConfig();
		registerCommands();
		registerHypixelMessageChecks();
		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
	}

	private void registerHypixelMessageChecks() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> handleHypixelMessage(message));
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) -> handleHypixelMessage(message));
	}

	private void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
				ClientCommandManager.literal("scamscreener")
					.then(ClientCommandManager.literal("add")
						.then(ClientCommandManager.argument("player", StringArgumentType.word())
							.executes(context -> {
								ResolvedTarget target = resolveTargetOrReply(StringArgumentType.getString(context, "player"));
								if (target == null) {
									return 0;
								}

								boolean added = BLACKLIST.add(target.uuid(), target.name(), 50, "manual-entry");
								reply(added
									? Messages.addedToBlacklist(target.name(), target.uuid())
									: Messages.alreadyBlacklisted(target.name(), target.uuid()));
								return 1;
							})
							.then(ClientCommandManager.argument("score", IntegerArgumentType.integer(0, 100))
								.executes(context -> {
									ResolvedTarget target = resolveTargetOrReply(StringArgumentType.getString(context, "player"));
									if (target == null) {
										return 0;
									}

									int score = IntegerArgumentType.getInteger(context, "score");
									boolean added = BLACKLIST.add(target.uuid(), target.name(), score, "manual-entry");
									reply(added
										? Messages.addedToBlacklistWithScore(target.name(), target.uuid(), score)
										: Messages.alreadyBlacklisted(target.name(), target.uuid()));
									return 1;
								})
								.then(ClientCommandManager.argument("reason", StringArgumentType.greedyString())
									.executes(context -> {
										ResolvedTarget target = resolveTargetOrReply(StringArgumentType.getString(context, "player"));
										if (target == null) {
											return 0;
										}

										int score = IntegerArgumentType.getInteger(context, "score");
										String reason = StringArgumentType.getString(context, "reason");
										boolean added = BLACKLIST.add(target.uuid(), target.name(), score, reason);
										reply(added
											? Messages.addedToBlacklistWithMetadata(target.name(), target.uuid())
											: Messages.alreadyBlacklisted(target.name(), target.uuid()));
										return 1;
									})))))
					.then(ClientCommandManager.literal("remove")
						.then(ClientCommandManager.argument("player", StringArgumentType.word())
							.suggests((context, builder) -> suggestBlacklistedPlayers(builder))
							.executes(context -> {
								ResolvedTarget target = resolveTargetOrReply(StringArgumentType.getString(context, "player"));
								if (target == null) {
									return 0;
								}

								boolean removed = BLACKLIST.remove(target.uuid());
								reply(removed
									? Messages.removedFromBlacklist(target.name(), target.uuid())
									: Messages.notOnBlacklist(target.name(), target.uuid()));
								currentlyDetected.remove(target.uuid());
								return 1;
							})))
					.then(ClientCommandManager.literal("list")
						.executes(context -> {
							if (BLACKLIST.isEmpty()) {
								reply(Messages.blacklistEmpty());
								return 1;
							}

							reply(Messages.blacklistHeader());
							for (BlacklistManager.ScamEntry entry : BLACKLIST.allEntries()) {
								reply(Messages.blacklistEntry(entry));
							}
							return 1;
						}))
			);
		});
	}

	private void onClientTick(Minecraft client) {
		if (client.player == null || client.getConnection() == null) {
			currentlyDetected.clear();
			warnedContexts.clear();
			return;
		}

		Team ownTeam = client.player.getTeam();
		if (ownTeam == null || BLACKLIST.isEmpty()) {
			currentlyDetected.clear();
			return;
		}

		Set<UUID> detectedNow = new HashSet<>();
		for (PlayerInfo entry : client.getConnection().getOnlinePlayers()) {
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
				sendWarning(client, playerName, playerUuid);
			}
		}

		currentlyDetected.clear();
		currentlyDetected.addAll(detectedNow);
	}

	private void handleHypixelMessage(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.getConnection() == null || BLACKLIST.isEmpty()) {
			return;
		}

		String plain = message.getString().trim();
		checkPatternAndWarn(plain, TRADE_INCOMING_PATTERN, "trade-incoming");
		checkPatternAndWarn(plain, TRADE_OUTGOING_PATTERN, "trade-outgoing");
		checkPatternAndWarn(plain, TRADE_SESSION_PATTERN, "trade-session");
	}

	private void checkPatternAndWarn(String message, Pattern pattern, String contextPrefix) {
		Matcher matcher = pattern.matcher(message);
		if (!matcher.matches()) {
			return;
		}

		String playerName = matcher.group(1);
		UUID uuid = findUuidByName(playerName);
		if (uuid == null || !BLACKLIST.contains(uuid)) {
			return;
		}

		String dedupeKey = contextPrefix + ":" + uuid;
		if (!warnedContexts.add(dedupeKey)) {
			return;
		}

		sendBlacklistWarning(playerName, uuid, humanReadableTrigger(contextPrefix));
	}

	private static String humanReadableTrigger(String contextPrefix) {
		return switch (contextPrefix) {
			case "trade-incoming" -> "incoming trade request";
			case "trade-outgoing" -> "outgoing trade request";
			case "trade-session" -> "active trade session";
			default -> "detected Hypixel interaction";
		};
	}

	private UUID findUuidByName(String playerName) {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null) {
			return null;
		}

		String target = playerName.toLowerCase(Locale.ROOT);
		for (PlayerInfo entry : client.getConnection().getOnlinePlayers()) {
			if (entry.getProfile().name().equalsIgnoreCase(target)) {
				return entry.getProfile().id();
			}
		}
		return null;
	}

	private String findNameByUuid(UUID uuid) {
		Minecraft client = Minecraft.getInstance();
		if (uuid == null || client.getConnection() == null) {
			return "unknown";
		}

		for (PlayerInfo entry : client.getConnection().getOnlinePlayers()) {
			if (uuid.equals(entry.getProfile().id())) {
				return entry.getProfile().name();
			}
		}
		return "unknown";
	}

	private static UUID parseUuid(String input) {
		try {
			return UUID.fromString(input.trim());
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private ResolvedTarget resolveTargetOrReply(String input) {
		UUID parsedUuid = parseUuid(input);
		if (parsedUuid != null) {
			return new ResolvedTarget(parsedUuid, findNameByUuid(parsedUuid));
		}

		UUID byName = findUuidByName(input);
		if (byName != null) {
			return new ResolvedTarget(byName, findNameByUuid(byName));
		}

		BlacklistManager.ScamEntry knownEntry = BLACKLIST.findByName(input);
		if (knownEntry != null) {
			return new ResolvedTarget(knownEntry.uuid(), knownEntry.name());
		}

		ResolvedTarget mojangResolved = lookupMojangProfile(input);
		if (mojangResolved != null) {
			return mojangResolved;
		}

		reply(Messages.unresolvedTarget(input));
		return null;
	}

	private void sendWarning(Minecraft client, String playerName, UUID uuid) {
		sendBlacklistWarning(playerName, uuid, "is in your team/party");
	}

	private void sendBlacklistWarning(String playerName, UUID uuid, String reason) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}

		BlacklistManager.ScamEntry entry = BLACKLIST.get(uuid);
		client.player.displayClientMessage(
			Messages.blacklistWarning(playerName, reason, entry),
			false
		);
	}

	private static void reply(Component text) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.displayClientMessage(text, false);
		}
	}

	private CompletableFuture<Suggestions> suggestBlacklistedPlayers(SuggestionsBuilder builder) {
		String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
		for (BlacklistManager.ScamEntry entry : BLACKLIST.allEntries()) {
			String name = entry.name();
			if (name != null && !name.isBlank() && name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
				builder.suggest(name);
			}
		}
		return builder.buildFuture();
	}

	private ResolvedTarget lookupMojangProfile(String input) {
		String trimmed = input == null ? "" : input.trim();
		if (trimmed.isEmpty()) {
			return null;
		}

		try {
			String encodedName = URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + encodedName))
				.timeout(Duration.ofSeconds(4))
				.GET()
				.build();

			HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200) {
				return null;
			}

			MojangProfile profile = GSON.fromJson(response.body(), MojangProfile.class);
			if (profile == null || profile.id == null || profile.id.length() != 32 || profile.name == null || profile.name.isBlank()) {
				return null;
			}

			UUID uuid = uuidFromUndashed(profile.id);
			return uuid == null ? null : new ResolvedTarget(uuid, profile.name);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static UUID uuidFromUndashed(String undashed) {
		String dashed = undashed.replaceFirst(
			"([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})",
			"$1-$2-$3-$4-$5"
		);
		try {
			return UUID.fromString(dashed);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private record ResolvedTarget(UUID uuid, String name) {
	}

	private static final class MojangProfile {
		String id;
		String name;
	}
}
