package fr.max.rbei.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Clé d'indexation stable pour un ItemStack (objet + contenu de potion).
 * Alignée sur {@link fr.max.rbei.RecipeIndex#same}.
 */
public final class ItemKey {
	private ItemKey() {}

	public static String of(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return "";
		String base = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
		return potion == null ? base : base + "#" + potion;
	}

	public static boolean same(ItemStack a, ItemStack b) {
		if (!ItemStack.isSameItem(a, b)) return false;
		PotionContents pa = a.get(DataComponents.POTION_CONTENTS);
		PotionContents pb = b.get(DataComponents.POTION_CONTENTS);
		return pa == null ? pb == null : pa.equals(pb);
	}
}
