package fr.max.rbei.recipe.api;

import java.util.Collection;
import java.util.List;

import fr.max.rbei.recipe.UniversalRecipe;
import net.minecraft.client.Minecraft;

/**
 * Niveau 3 / 4 — adaptateur de recettes (mod spécifique ou extension développeur).
 *
 * Enregistrer via l'entrypoint Fabric {@code rbei:recipe_adapter}, ou
 * {@link RbeiRecipeApi#registerAdapter(RecipeAdapter)}.
 *
 * // [SPEC] Objectif : chaque mod propriétaire a son adaptateur facultatif.
 * // [WHY] Soft-load : jamais de dépendance compile obligatoire.
 */
public interface RecipeAdapter {

	/** Identifiant stable (ex. {@code create}, {@code mekanism}, {@code my_mod}). */
	String id();

	/**
	 * Mod(s) Fabric requis. Si aucun n'est chargé, l'adaptateur est ignoré
	 * (sauf si la liste est vide = toujours actif).
	 */
	default Collection<String> requiredMods() {
		return List.of();
	}

	/** Priorité : plus élevé = exécuté plus tôt (défaut 0). */
	default int priority() {
		return 0;
	}

	/**
	 * Collecte les recettes. Ne doit jamais planter : encapsuler les erreurs.
	 *
	 * @return recettes normalisées ; liste vide si rien à ajouter
	 */
	List<UniversalRecipe> collect(Minecraft mc);

	/**
	 * Limitation connue quand le mod est présent mais non entièrement indexable.
	 * Affichée / loguée pour ne pas ignorer silencieusement.
	 */
	default String limitation() {
		return null;
	}
}
