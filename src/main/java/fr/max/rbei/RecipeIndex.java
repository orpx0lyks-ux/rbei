package fr.max.rbei;

import java.util.List;
import java.util.Map;

import fr.max.rbei.recipe.ItemKey;
import fr.max.rbei.recipe.RecipeDatabase;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Façade UI historique : charge l'index universel et expose des {@link Entry} affichables.
 * Solo / multi / datapacks : gérés par {@link RecipeDatabase}.
 */
public final class RecipeIndex {
	private RecipeIndex() {}

	public record Cell(int col, int row, List<ItemStack> options) {}

	/**
	 * Génération d'un minerai : hauteurs (Y) du filon et de la dimension, taille, essais par chunk,
	 * et meilleure couche pour le miner (bestMin = bestMax pour une couche précise, Integer.MIN_VALUE si inconnue).
	 */
	public record OreInfo(int minY, int maxY, int dimMin, int dimMax, int size, int count,
			int bestMin, int bestMax) {}

	/**
	 * Métadonnées enrichies (mod, durée, énergie, fluides, conditions).
	 * Conservées même si l'UI n'affiche qu'un sous-ensemble.
	 */
	public record Details(
			String recipeId,
			String modId,
			Integer durationTicks,
			Long energy,
			Float experience,
			List<Component> fluids,
			List<Component> conditions,
			Map<String, String> extras) {

		public static final Details EMPTY = new Details(null, null, null, null, null, List.of(), List.of(), Map.of());
	}

	/**
	 * crafting = recette d'établi (transférable dans une grille) ; shaped = forme fixe ;
	 * icon = icône de catégorie (butins, minerais) quand il n'y a pas de station ;
	 * info = lignes de texte sous la recette ; ore = hauteurs de génération (ou null).
	 */
	public record Entry(Component kind, int cols, int rows, List<Cell> cells,
			List<ItemStack> outputs, List<ItemStack> stations, boolean crafting, boolean shaped,
			ItemStack icon, List<Component> info, OreInfo ore, Details details) {

		public Entry(Component kind, int cols, int rows, List<Cell> cells,
				List<ItemStack> outputs, List<ItemStack> stations, boolean crafting, boolean shaped) {
			this(kind, cols, rows, cells, outputs, stations, crafting, shaped, null, List.of(), null, Details.EMPTY);
		}

		public Entry(Component kind, int cols, int rows, List<Cell> cells,
				List<ItemStack> outputs, List<ItemStack> stations, boolean crafting, boolean shaped,
				ItemStack icon, List<Component> info, OreInfo ore) {
			this(kind, cols, rows, cells, outputs, stations, crafting, shaped, icon, info, ore, Details.EMPTY);
		}
	}

	public static List<Entry> load(Minecraft mc) {
		return RecipeDatabase.get(mc).uiEntries();
	}

	/** Invalide le cache (sync recettes / leave world). */
	public static void invalidate() {
		RecipeDatabase.invalidate();
	}

	public static List<Entry> recipesFor(List<Entry> all, ItemStack stack) {
		// all est déjà le cache UI ; filtre local pour compatibilité historique
		List<Entry> out = new java.util.ArrayList<>();
		for (Entry e : all) {
			if (contains(e.outputs(), stack)) out.add(e);
		}
		return out;
	}

	public static List<Entry> usesOf(List<Entry> all, ItemStack stack) {
		List<Entry> out = new java.util.ArrayList<>();
		for (Entry e : all) {
			boolean used = contains(e.stations(), stack);
			for (Cell c : e.cells()) {
				if (used) break;
				used = contains(c.options(), stack);
			}
			if (used) out.add(e);
		}
		return out;
	}

	private static boolean contains(List<ItemStack> list, ItemStack stack) {
		for (ItemStack s : list) {
			if (same(s, stack)) return true;
		}
		return false;
	}

	/** Même objet ; pour les potions, aussi le même contenu (toutes les fioles sont le même objet). */
	public static boolean same(ItemStack a, ItemStack b) {
		return ItemKey.same(a, b);
	}
}
