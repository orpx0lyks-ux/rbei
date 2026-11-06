package fr.max.rbei.recipe.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import fr.max.rbei.recipe.RecipeDatabase;
import fr.max.rbei.recipe.UniversalRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * API publique (Niveau 4) pour enregistrer des adaptateurs et interroger l'index.
 *
 * // [SPEC] Objectif : autres mods / datapacks-devs branchent leurs machines sans fork de RBEI.
 */
public final class RbeiRecipeApi {
	private static final List<RecipeAdapter> RUNTIME = new CopyOnWriteArrayList<>();

	private RbeiRecipeApi() {}

	/** Enregistre un adaptateur à chaud (en plus des entrypoints {@code rbei:recipe_adapter}). */
	public static void registerAdapter(RecipeAdapter adapter) {
		Objects.requireNonNull(adapter, "adapter");
		RUNTIME.add(adapter);
		RecipeDatabase.invalidate();
	}

	/** Adaptateurs enregistrés à runtime (hors entrypoints). */
	public static List<RecipeAdapter> runtimeAdapters() {
		return List.copyOf(RUNTIME);
	}

	public static List<UniversalRecipe> all(Minecraft mc) {
		return RecipeDatabase.get(mc).all();
	}

	public static List<UniversalRecipe> recipesFor(Minecraft mc, ItemStack stack) {
		return RecipeDatabase.get(mc).recipesFor(stack);
	}

	public static List<UniversalRecipe> usesOf(Minecraft mc, ItemStack stack) {
		return RecipeDatabase.get(mc).usesOf(stack);
	}

	/** Limitations déclarées par les adaptateurs (mods présents mais partiellement supportés). */
	public static List<String> limitations(Minecraft mc) {
		return new ArrayList<>(RecipeDatabase.get(mc).limitations());
	}
}
