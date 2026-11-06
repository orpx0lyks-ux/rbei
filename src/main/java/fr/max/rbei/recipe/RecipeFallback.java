package fr.max.rbei.recipe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * Reconstruit une recette quand {@link Recipe#display()} est vide
 * (cas fréquent des smithing tables / machines custom de mods).
 *
 * // [SPEC] Ne pas ignorer silencieusement les recettes sans RecipeDisplay.
 * // [WHY] Beaucoup de mods custom omettent display() tout en enregistrant un RecipeType.
 */
public final class RecipeFallback {
	private RecipeFallback() {}

	public static Optional<UniversalRecipe> fromHolder(RecipeHolder<?> holder, ContextMap ctx) {
		Recipe<?> recipe = holder.value();
		Identifier id = holder.id().identifier();

		if (recipe instanceof SmithingRecipe smithing) {
			UniversalRecipe u = fromSmithing(smithing, id, ctx);
			if (u != null) return Optional.of(u);
		}
		if (recipe instanceof SingleItemRecipe single) {
			UniversalRecipe u = fromSingle(single, id, ctx);
			if (u != null) return Optional.of(u);
		}
		Optional<UniversalRecipe> placement = fromPlacement(recipe, id, ctx);
		if (placement.isPresent()) return placement;
		// Filet automatique : réflexion sur tous les champs du mod
		return ReflectiveHarvester.fromRecipe(holder, ctx);
	}

	private static UniversalRecipe fromSmithing(SmithingRecipe recipe, Identifier id, ContextMap ctx) {
		List<RecipeSlot> cells = new ArrayList<>();
		addIngredient(cells, 0, 0, recipe.templateIngredient().orElse(null), ctx);
		addIngredient(cells, 1, 0, recipe.baseIngredient(), ctx);
		addIngredient(cells, 2, 0, recipe.additionIngredient().orElse(null), ctx);

		ItemStack result = extractResult(recipe);
		if (result.isEmpty()) return null;

		ItemStack station = guessSmithingStation(id);
		return UniversalRecipe.builder(id + "#smithing-fallback")
				.id(id).sourceId("rbei:fallback-smithing")
				.kind(station.getHoverName())
				.stations(List.of(station))
				.grid(3, 1)
				.inputs(cells)
				.outputStacks(List.of(result))
				.addInfo(Component.translatable("rbei.meta.fallback_smithing"))
				.build();
	}

	private static UniversalRecipe fromSingle(SingleItemRecipe recipe, Identifier id, ContextMap ctx) {
		List<RecipeSlot> cells = new ArrayList<>();
		addIngredient(cells, 0, 0, recipe.input(), ctx);
		ItemStack result = recipe.assemble(new SingleRecipeInput(ItemStack.EMPTY));
		if (result.isEmpty()) result = extractResult(recipe);
		if (result.isEmpty()) return null;
		ItemStack station = stationForType(recipe.getType(), id);
		return UniversalRecipe.builder(id + "#single-fallback")
				.id(id).sourceId("rbei:fallback-single")
				.kind(station.getHoverName())
				.stations(List.of(station))
				.grid(1, 1)
				.inputs(cells)
				.outputStacks(List.of(result))
				.build();
	}

	private static Optional<UniversalRecipe> fromPlacement(Recipe<?> recipe, Identifier id, ContextMap ctx) {
		PlacementInfo info;
		try {
			info = recipe.placementInfo();
		} catch (RuntimeException ex) {
			return Optional.empty();
		}
		if (info == null || info.isImpossibleToPlace()) return Optional.empty();

		List<Ingredient> ingredients = info.ingredients();
		if (ingredients == null || ingredients.isEmpty()) return Optional.empty();

		ItemStack result = extractResult(recipe);
		if (result.isEmpty()) return Optional.empty();

		List<RecipeSlot> cells = new ArrayList<>();
		for (int i = 0; i < ingredients.size(); i++) {
			addIngredient(cells, i % 3, i / 3, ingredients.get(i), ctx);
		}
		ItemStack station = stationForType(recipe.getType(), id);
		return Optional.of(UniversalRecipe.builder(id + "#placement-fallback")
				.id(id).sourceId("rbei:fallback-placement")
				.kind(station.getHoverName())
				.stations(List.of(station))
				.grid(3, Math.max(1, (ingredients.size() + 2) / 3))
				.inputs(cells)
				.outputStacks(List.of(result))
				.addInfo(Component.translatable("rbei.meta.fallback_generic",
						BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString()))
				.build());
	}

	private static void addIngredient(List<RecipeSlot> cells, int col, int row, Ingredient ingredient,
			ContextMap ctx) {
		if (ingredient == null || ingredient.isEmpty()) return;
		List<ItemStack> opts = stacksOf(ingredient, ctx);
		if (!opts.isEmpty()) cells.add(new RecipeSlot(col, row, opts));
	}

	public static List<ItemStack> stacksOf(Ingredient ingredient, ContextMap ctx) {
		List<ItemStack> out = new ArrayList<>();
		if (ingredient == null || ingredient.isEmpty()) return out;
		try {
			SlotDisplay display = ingredient.display();
			if (display != null) {
				for (ItemStack s : display.resolveForStacks(ctx)) {
					if (!s.isEmpty()) out.add(s.copy());
				}
			}
		} catch (RuntimeException ignored) {}
		if (!out.isEmpty()) return out;
		for (Holder<Item> holder : ingredient.items().toList()) {
			out.add(holder.value().getDefaultInstance());
		}
		return out;
	}

	private static ItemStack extractResult(Recipe<?> recipe) {
		if (recipe instanceof SingleItemRecipe single) {
			ItemStack s = single.assemble(new SingleRecipeInput(ItemStack.EMPTY));
			if (!s.isEmpty()) return s;
		}
		ItemStack reflected = reflectResult(recipe);
		if (!reflected.isEmpty()) return reflected;
		return ItemStack.EMPTY;
	}

	private static ItemStack reflectResult(Object recipe) {
		for (String name : new String[] {"result", "output", "results"}) {
			try {
				Field f = findField(recipe.getClass(), name);
				if (f == null) continue;
				f.setAccessible(true);
				Object v = f.get(recipe);
				ItemStack stack = toStack(v);
				if (!stack.isEmpty()) return stack;
			} catch (ReflectiveOperationException ignored) {}
		}
		for (String name : new String[] {"getResultItem", "getResult", "result", "assemble"}) {
			try {
				for (Method m : recipe.getClass().getMethods()) {
					if (!m.getName().equals(name) || m.getParameterCount() > 1) continue;
					m.setAccessible(true);
					Object v = m.getParameterCount() == 0 ? m.invoke(recipe) : null;
					if (v == null) continue;
					ItemStack stack = toStack(v);
					if (!stack.isEmpty()) return stack;
				}
			} catch (ReflectiveOperationException ignored) {}
		}
		return ItemStack.EMPTY;
	}

	private static Field findField(Class<?> type, String name) {
		Class<?> c = type;
		while (c != null && c != Object.class) {
			try {
				return c.getDeclaredField(name);
			} catch (NoSuchFieldException ex) {
				c = c.getSuperclass();
			}
		}
		return null;
	}

	private static ItemStack toStack(Object v) {
		if (v instanceof ItemStack stack) return stack.copy();
		if (v instanceof ItemStackTemplate template) return template.create();
		if (v instanceof Optional<?> opt && opt.isPresent()) return toStack(opt.get());
		if (v instanceof List<?> list && !list.isEmpty()) return toStack(list.get(0));
		return ItemStack.EMPTY;
	}

	private static ItemStack guessSmithingStation(Identifier recipeId) {
		// Cherche un item "*smithing*" dans le namespace du mod de la recette
		String ns = recipeId.getNamespace();
		if (!ns.equals("minecraft")) {
			for (Item item : BuiltInRegistries.ITEM) {
				Identifier key = BuiltInRegistries.ITEM.getKey(item);
				if (key.getNamespace().equals(ns) && key.getPath().contains("smithing")) {
					return item.getDefaultInstance();
				}
			}
		}
		return new ItemStack(Items.SMITHING_TABLE);
	}

	private static ItemStack stationForType(RecipeType<?> type, Identifier recipeId) {
		Identifier typeId = BuiltInRegistries.RECIPE_TYPE.getKey(type);
		if (typeId != null) {
			String path = typeId.getPath();
			ItemStack byType = itemNamed(typeId.getNamespace(), path);
			if (!byType.isEmpty()) return byType;
			if (path.contains("smith")) return guessSmithingStation(recipeId);
			if (path.contains("smelt") || path.contains("furnace")) return new ItemStack(Items.FURNACE);
			if (path.contains("blast")) return new ItemStack(Items.BLAST_FURNACE);
			if (path.contains("smok")) return new ItemStack(Items.SMOKER);
			if (path.contains("campfire")) return new ItemStack(Items.CAMPFIRE);
			if (path.contains("stonecut")) return new ItemStack(Items.STONECUTTER);
			if (path.contains("craft")) return new ItemStack(Items.CRAFTING_TABLE);
		}
		// Dernier recours : machine smithing du mod, sinon établi
		ItemStack modSmithing = guessSmithingStation(recipeId);
		if (!modSmithing.is(Items.SMITHING_TABLE) || recipeId.getNamespace().equals("minecraft")) {
			return modSmithing.is(Items.SMITHING_TABLE) ? new ItemStack(Items.CRAFTING_TABLE) : modSmithing;
		}
		return modSmithing;
	}

	private static ItemStack itemNamed(String ns, String path) {
		Identifier id = Identifier.tryBuild(ns, path);
		if (id == null) return ItemStack.EMPTY;
		return BuiltInRegistries.ITEM.getOptional(id).map(Item::getDefaultInstance).orElse(ItemStack.EMPTY);
	}
}
