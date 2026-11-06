package fr.max.rbei;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Seul point de contact avec l'API de dessin de Minecraft.
 * Si une méthode est renommée dans une future version, il suffit de corriger ce fichier.
 */
final class Gfx {
	private Gfx() {}

	static void fill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
		g.fill(x1, y1, x2, y2, color);
	}

	static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
		g.text(font, s, x, y, color, false);
	}

	static void text(GuiGraphicsExtractor g, Font font, Component c, int x, int y, int color) {
		g.text(font, c, x, y, color, false);
	}

	static void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
		g.item(stack, x, y);
	}

	static void tooltip(GuiGraphicsExtractor g, Font font, ItemStack stack, int x, int y) {
		g.setTooltipForNextFrame(font, stack, x, y);
	}

	static void tooltip(GuiGraphicsExtractor g, Font font, Component c, int x, int y) {
		g.setTooltipForNextFrame(font, c, x, y);
	}
}
