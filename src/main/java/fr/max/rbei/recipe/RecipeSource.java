package fr.max.rbei.recipe;

import java.util.List;

import net.minecraft.client.Minecraft;

/**
 * Producteur de recettes (niveaux 1–3). Les sources natives sont enregistrées en dur ;
 * les adaptateurs tiers passent par {@link fr.max.rbei.recipe.api.RecipeAdapter}.
 */
public interface RecipeSource {

	String id();

	List<UniversalRecipe> collect(Minecraft mc);

	default String limitation() {
		return null;
	}
}
