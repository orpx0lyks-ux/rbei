package fr.max.rbei.recipe.adapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import fr.max.rbei.recipe.UniversalRecipe;
import fr.max.rbei.recipe.api.RecipeAdapter;
import net.minecraft.client.Minecraft;

/**
 * Soft adapters (no mandatory Maven deps). Document remaining limits when a mod is present.
 */
public final class SoftAdapters {
	private SoftAdapters() {}

	public static List<RecipeAdapter> builtin() {
		List<RecipeAdapter> list = new ArrayList<>();
		list.add(limit("create", List.of("create"),
				"Create: RecipeTypes + AllRecipeTypes/ProcessingRecipeType scan; "
						+ "purely internal basin sequences may be missing."));
		list.add(limit("mekanism", List.of("mekanism"),
				"Mekanism: registered types indexed automatically; complex fluids/gases may be incomplete."));
		list.add(limit("ae2", List.of("ae2", "appliedenergistics2"),
				"AE2: AERecipeTypes + scan; special circuits/patterns may be missing."));
		list.add(limit("techreborn", List.of("techreborn"),
				"Tech Reborn: auto-index via RecipeType + scan."));
		list.add(limit("modern_industrialization", List.of("modern_industrialization"),
				"MI: auto-index via RecipeType + scan."));
		list.add(limit("indrev", List.of("indrev"),
				"Industrial Revolution: auto-index via RecipeType."));
		list.add(limit("farmersdelight", List.of("farmersdelight"),
				"Farmer's Delight: ModRecipeTypes + CookingPot/CuttingBoard scan."));
		list.add(limit("botania", List.of("botania"),
				"Botania: RecipeType crafts indexed; pure mana rituals may be missing."));
		list.add(limit("occultism", List.of("occultism"),
				"Occultism: auto-index if RecipeType; special rituals may be missing."));
		list.add(limit("ad_astra", List.of("ad_astra"),
				"Ad Astra: auto-index via RecipeType."));
		list.add(limit("powah", List.of("powah"),
				"Powah: auto-index via RecipeType."));
		list.add(limit("mythicmetals", List.of("mythicmetals"),
				"Mythic Metals: auto-index via RecipeType."));
		list.add(limit("emi", List.of("emi"),
				"EMI present: RBEI indexes independently (no EMI dependency)."));
		list.add(limit("roughlyenoughitems", List.of("roughlyenoughitems", "rei"),
				"REI present: RBEI indexes independently."));
		list.add(limit("jei", List.of("jei"),
				"JEI present: RBEI indexes independently."));
		return list;
	}

	private static RecipeAdapter limit(String id, Collection<String> mods, String limitation) {
		return new RecipeAdapter() {
			@Override public String id() { return id; }
			@Override public Collection<String> requiredMods() { return mods; }
			@Override public String limitation() { return limitation; }
			@Override public List<UniversalRecipe> collect(Minecraft mc) {
				return List.of();
			}
		};
	}
}
