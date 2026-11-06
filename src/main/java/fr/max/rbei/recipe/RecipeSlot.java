package fr.max.rbei.recipe;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/**
 * Case d'entrée/sortie : plusieurs options possibles (tags), quantité, chance optionnelle.
 *
 * // [CLAUDE-REVIEW] Vérifier : stacks vides filtrés ; chance null = certain (1.0 implicite).
 */
public record RecipeSlot(int col, int row, List<ItemStack> options, int count, Float chance) {

	public RecipeSlot(int col, int row, List<ItemStack> options) {
		this(col, row, options, 1, null);
	}

	public boolean isEmpty() {
		return options == null || options.isEmpty();
	}
}
