package eu.tango.scamscreener.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class GUI extends Screen {
	protected static final int ROW_HEIGHT = 24;
	protected static final int TITLE_Y = 14;
	protected static final int CONTENT_START_Y = 36;
	protected static final int FOOTER_Y_OFFSET = 28;
	protected static final int ON_LIGHT_GREEN = 0x55FF55;
	protected static final int OFF_LIGHT_RED = 0xFF5555;

	protected final Screen parent;

	protected GUI(Component title, Screen parent) {
		super(title);
		this.parent = parent;
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.setScreen(parent);
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		// Avoid vanilla blur background here because some client stacks already apply blur once per frame.
		// Calling renderBackground again can crash with "Can only blur once per frame".
		guiGraphics.fill(0, 0, this.width, this.height, 0x90000000);
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
	}

	protected int defaultButtonWidth() {
		return Math.min(320, Math.max(180, this.width - 40));
	}

	protected int centeredX(int widgetWidth) {
		return (this.width - widgetWidth) / 2;
	}

	protected Button addCenteredButton(int y, int buttonWidth, Component label, Button.OnPress onPress) {
		return this.addRenderableWidget(Button.builder(label, onPress)
			.bounds(centeredX(buttonWidth), y, buttonWidth, 20)
			.build());
	}

	protected Button addCenteredButton(int y, Component label, Button.OnPress onPress) {
		return addCenteredButton(y, defaultButtonWidth(), label, onPress);
	}

	protected ColumnLayout defaultColumnLayout() {
		int width = defaultButtonWidth();
		return new ColumnLayout(width, centeredX(width), CONTENT_START_Y);
	}

	protected Button addBackButton(int buttonWidth) {
		return addFooterButton(buttonWidth, Component.literal("Back"));
	}

	protected Button addCloseButton(int buttonWidth) {
		return addFooterButton(buttonWidth, Component.literal("Close"));
	}

	protected Button addFooterButton(int buttonWidth, Component label) {
		return addCenteredButton(this.height - FOOTER_Y_OFFSET, buttonWidth, label, button -> this.onClose());
	}

	protected void openScreen(Screen screen) {
		if (this.minecraft != null) {
			this.minecraft.setScreen(screen);
		}
	}

	protected static String onOff(boolean enabled) {
		return enabled ? "ON" : "OFF";
	}

	protected static Component onOffComponent(boolean enabled) {
		int color = enabled ? ON_LIGHT_GREEN : OFF_LIGHT_RED;
		return Component.literal(onOff(enabled)).withStyle(style -> style.withColor(color));
	}

	protected static Component onOffLine(String label, boolean enabled) {
		return Component.literal(label).append(onOffComponent(enabled));
	}

	protected record ColumnLayout(int width, int x, int startY) {
	}
}
