package fr.max.rbei.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

/**
 * Convertit les {@link RecipeDisplay} vanilla (et inconnus) en {@link UniversalRecipe}.
 *
 * // [SPEC] Niveau 1 : API native Minecraft 26.2 uniquement.
 * // [WHY] Les mods qui exposent un RecipeDisplay via Recipe.display() sont couverts automatiquement.
 * // [TRADE-OFF] Displays custom sans champs standards → résultat + station seulement + limitation.
 */
public final class DisplayConverter {
	private DisplayConverter() {}

	public static List<UniversalRecipe> fromHolder(RecipeHolder<?> holder, ContextMap ctx,
			List<String> limitations) {
		ResourceKey<Recipe<?>> key = holder.id();
		Identifier id = key.identifier();
		List<RecipeDisplay> displays = holder.value().display();
		List<UniversalRecipe> out = new ArrayList<>();

		if (displays == null || displays.isEmpty()) {
			Optional<UniversalRecipe> fallback = RecipeFallback.fromHolder(holder, ctx);
			if (fallback.isPresent()) {
				out.add(fallback.get());
				return out;
			}
			String typeId = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()).toString();
			limitations.add("Recette sans RecipeDisplay ni repli possible : " + id + " (type " + typeId + ")");
			return out;
		}

		int i = 0;
		for (RecipeDisplay d : displays) {
			UniversalRecipe u = convert(d, ctx, id, i++);
			if (u != null) {
				out.add(u);
			} else {
				Identifier displayType = BuiltInRegistries.RECIPE_DISPLAY.getKey(d.type());
				limitations.add("RecipeDisplay non converti : " + displayType + " pour " + id);
				UniversalRecipe fallback = genericFallback(d, ctx, id, i, displayType);
				if (fallback != null) out.add(fallback);
			}
		}
		return out;
	}

	public static UniversalRecipe convert(RecipeDisplay d, ContextMap ctx, Identifier recipeId, int index) {
		List<ItemStack> outputs = resolve(d.result(), ctx);
		List<ItemStack> stations = resolve(d.craftingStation(), ctx);
		Component kind = stations.isEmpty() ? Component.literal("?") : stations.get(0).getHoverName();
		String uid = (recipeId == null ? "anon" : recipeId.toString()) + "#" + index;

		if (d instanceof ShapedCraftingRecipeDisplay s) {
			List<RecipeSlot> cells = new ArrayList<>();
			List<SlotDisplay> ing = s.ingredients();
			for (int i = 0; i < ing.size(); i++) {
				List<ItemStack> opts = resolve(ing.get(i), ctx);
				if (!opts.isEmpty()) cells.add(new RecipeSlot(i % s.width(), i / s.width(), opts));
			}
			return UniversalRecipe.builder(uid)
					.id(recipeId).sourceId("rbei:native")
					.kind(kind).stations(stations)
					.grid(s.width(), s.height())
					.inputs(cells).outputStacks(outputs)
					.crafting(true).shaped(true)
					.build();
		}
		if (d instanceof ShapelessCraftingRecipeDisplay s) {
			List<RecipeSlot> cells = new ArrayList<>();
			List<SlotDisplay> ing = s.ingredients();
			for (int i = 0; i < ing.size(); i++) {
				List<ItemStack> opts = resolve(ing.get(i), ctx);
				if (!opts.isEmpty()) cells.add(new RecipeSlot(i % 3, i / 3, opts));
			}
			return UniversalRecipe.builder(uid)
					.id(recipeId).sourceId("rbei:native")
					.kind(kind).stations(stations)
					.grid(3, Math.max(1, (ing.size() + 2) / 3))
					.inputs(cells).outputStacks(outputs)
					.crafting(true).shaped(false)
					.build();
		}
		if (d instanceof FurnaceRecipeDisplay f) {
			List<RecipeSlot> cells = new ArrayList<>();
			List<ItemStack> ing = resolve(f.ingredient(), ctx);
			List<ItemStack> fuel = resolve(f.fuel(), ctx);
			if (!ing.isEmpty()) cells.add(new RecipeSlot(0, 0, ing));
			if (!fuel.isEmpty()) cells.add(new RecipeSlot(0, 1, fuel));
			RecipeDetails details = RecipeDetails.builder()
					.durationTicks(f.duration())
					.experience(f.experience())
					.build();
			List<Component> info = new ArrayList<>();
			info.add(Component.translatable("rbei.meta.duration", f.duration()));
			if (f.experience() > 0) {
				info.add(Component.translatable("rbei.meta.xp", String.format("%.1f", f.experience())));
			}
			return UniversalRecipe.builder(uid)
					.id(recipeId).sourceId("rbei:native")
					.kind(kind).stations(stations)
					.grid(1, 2).inputs(cells).outputStacks(outputs)
					.info(info).details(details)
					.build();
		}
		if (d instanceof StonecutterRecipeDisplay s) {
			List<RecipeSlot> cells = new ArrayList<>();
			List<ItemStack> input = resolve(s.input(), ctx);
			if (!input.isEmpty()) cells.add(new RecipeSlot(0, 0, input));
			return UniversalRecipe.builder(uid)
					.id(recipeId).sourceId("rbei:native")
					.kind(kind).stations(stations)
					.grid(1, 1).inputs(cells).outputStacks(outputs)
					.build();
		}
		if (d instanceof SmithingRecipeDisplay s) {
			List<RecipeSlot> cells = new ArrayList<>();
			addCell(cells, 0, 0, resolve(s.template(), ctx));
			addCell(cells, 1, 0, resolve(s.base(), ctx));
			addCell(cells, 2, 0, resolve(s.addition(), ctx));
			return UniversalRecipe.builder(uid)
					.id(recipeId).sourceId("rbei:native")
					.kind(kind).stations(stations)
					.grid(3, 1).inputs(cells).outputStacks(outputs)
					.build();
		}
		return null;
	}

	/** Repli : extraction auto des SlotDisplay custom, sinon au moins résultat + station. */
	private static UniversalRecipe genericFallback(RecipeDisplay d, ContextMap ctx, Identifier recipeId,
			int index, Identifier displayType) {
		UniversalRecipe auto = ReflectiveHarvester.fromUnknownDisplay(d, ctx, recipeId, index, displayType);
		if (auto != null && !auto.inputs().isEmpty()) return auto;

		List<ItemStack> outputs = resolve(d.result(), ctx);
		if (outputs.isEmpty()) {
			return auto; // éventuellement résultat seul déjà trouvé par réflexion
		}
		List<ItemStack> stations = resolve(d.craftingStation(), ctx);
		Component kind = stations.isEmpty()
				? Component.literal(displayType == null ? "?" : displayType.toString())
				: stations.get(0).getHoverName();
		String uid = (recipeId == null ? "anon" : recipeId.toString()) + "#fallback" + index;
		return UniversalRecipe.builder(uid)
				.id(recipeId).sourceId("rbei:native-fallback")
				.kind(kind).stations(stations)
				.outputStacks(outputs)
				.inputs(auto == null ? List.of() : auto.inputs())
				.addInfo(Component.translatable("rbei.meta.unknown_display",
						displayType == null ? "?" : displayType.toString()))
				.details(RecipeDetails.builder()
						.extra("display_type", displayType == null ? "?" : displayType.toString())
						.build())
				.build();
	}

	private static void addCell(List<RecipeSlot> cells, int col, int row, List<ItemStack> opts) {
		if (!opts.isEmpty()) cells.add(new RecipeSlot(col, row, opts));
	}

	public static List<ItemStack> resolve(SlotDisplay display, ContextMap ctx) {
		List<ItemStack> list = new ArrayList<>();
		if (display == null) return list;
		for (ItemStack s : display.resolveForStacks(ctx)) {
			if (!s.isEmpty()) list.add(s.copy());
		}
		return list;
	}
}
