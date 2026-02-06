package eu.tango.scamscreener.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import eu.tango.scamscreener.ui.UiPreview;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class PreviewCommand {
	private PreviewCommand() {
	}

	static LiteralArgumentBuilder<FabricClientCommandSource> build(
		Consumer<Component> reply,
		Supplier<String> lastCapturedChatSupplier
	) {
		return ClientCommandManager.literal("preview")
			.executes(context -> runDryRun(reply, lastCapturedChatSupplier));
	}

	private static int runDryRun(Consumer<Component> reply, Supplier<String> lastCapturedChatSupplier) {
		reply.accept(Component.literal("[ScamScreener] Preview dry run started."));
		for (Component component : UiPreview.buildAll(lastCapturedChatSupplier)) {
			reply.accept(component);
		}
		reply.accept(Component.literal("[ScamScreener] Preview dry run finished."));
		return 1;
	}
}
