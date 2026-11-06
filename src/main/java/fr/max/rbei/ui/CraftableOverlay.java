package fr.max.rbei.ui;

import fr.max.rbei.config.RbeiConfig;
import fr.max.rbei.mixin.ContainerScreenAccessor;
import fr.max.rbei.recipe.RecipeDatabase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Surbrille les slots dont l'objet a une recette craftable avec l'inventaire actuel. */
public final class CraftableOverlay {
	private CraftableOverlay() {}

	public static void render(Minecraft mc, AbstractContainerScreen<?> screen, GuiGraphicsExtractor g) {
		if (!RbeiConfig.get().craftableOverlay()) return;
		if (mc.player == null || mc.level == null) return;
		ContainerScreenAccessor acc = (ContainerScreenAccessor) screen;
		int left = acc.rbei$getLeftPos();
		int top = acc.rbei$getTopPos();
		RecipeDatabase db = RecipeDatabase.get(mc);
		RbeiTheme theme = RbeiTheme.current();

		for (Slot slot : screen.getMenu().slots) {
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			var recipes = db.uiRecipesFor(stack);
			boolean ok = false;
			for (var e : recipes) {
				if (e.crafting() && RecipeTransfer.hasIngredients(screen.getMenu(), e)) {
					ok = true;
					break;
				}
			}
			if (!ok) continue;
			int x = left + slot.x;
			int y = top + slot.y;
			g.fill(x, y, x + 16, y + 16, theme.overlay);
		}
	}
}
