package eu.tango.scamscreener.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import eu.tango.scamscreener.ui.Messages;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

final class EmailBypassCommand {
	private EmailBypassCommand() {
	}

	static LiteralArgumentBuilder<FabricClientCommandSource> build(
		ScamScreenerCommands.EmailBypassHandler handler,
		Consumer<Component> reply
	) {
		return ClientCommandManager.literal("bypass")
			.then(ClientCommandManager.argument("id", StringArgumentType.word())
				.executes(context -> {
					String id = StringArgumentType.getString(context, "id");
					if (handler == null) {
						reply.accept(Messages.emailBypassExpired());
						return 0;
					}
					return handler.bypass(id);
				}));
	}
}
