package eu.tango.scamscreener.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

final class SettingsCommand {
	private SettingsCommand() {
	}

	static LiteralArgumentBuilder<FabricClientCommandSource> build(Runnable openSettingsHandler) {
		return ClientCommandManager.literal("settings")
			.executes(context -> {
				if (openSettingsHandler != null) {
					openSettingsHandler.run();
				}
				return 1;
			});
	}
}
