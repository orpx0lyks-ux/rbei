package fr.max.rbei.recipe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * Extraction automatique par réflexion — couvre les recettes / displays custom des mods
 * sans connaître leur API à l'avance.
 *
 * // [SPEC] Objectif : récupérer crafts des mods installés automatiquement.
 * // [WHY] Les mods enregistrent souvent Ingredient/ItemStack dans des champs privés
 * //       sans fournir de RecipeDisplay exploitable.
 * // [TRADE-OFF] Heuristique : mieux vaut une recette partielle qu'aucune.
 */
public final class ReflectiveHarvester {
	private static final Set<String> INPUT_HINTS = Set.of(
			"input", "inputs", "ingredient", "ingredients", "catalyst", "extra",
			"addition", "template", "base", "fuel", "reagent", "item", "items",
			"slot", "primary", "secondary", "left", "right", "top", "bottom");
	private static final Set<String> OUTPUT_HINTS = Set.of(
			"output", "outputs", "result", "results", "product", "products",
			"mainoutput", "secondaryoutput", "byproduct", "reward");
	private static final Set<String> STATION_HINTS = Set.of(
			"station", "machine", "block", "catalystblock", "craftingstation", "icon");
	private static final Set<String> DURATION_HINTS = Set.of(
			"duration", "time", "cooktime", "processtime", "ticks", "cookingtime");
	private static final Set<String> ENERGY_HINTS = Set.of(
			"energy", "eu", "fe", "rf", "power", "cost", "energyCost", "energyPerTick");
	private static final Set<String> CHANCE_HINTS = Set.of(
			"chance", "probability", "rate");

	private ReflectiveHarvester() {}

	/** Reconstruit une UniversalRecipe depuis n'importe quel Recipe<?> via ses champs. */
	public static Optional<UniversalRecipe> fromRecipe(RecipeHolder<?> holder, ContextMap ctx) {
		Recipe<?> recipe = holder.value();
		Identifier id = holder.id().identifier();
		Extracted ex = extract(recipe, ctx);
		if (ex.outputs.isEmpty()) return Optional.empty();

		ItemStack station = ex.stations.isEmpty()
				? stationFor(recipe.getType(), id)
				: ex.stations.get(0);

		List<RecipeSlot> inputs = toSlots(ex.inputs, false);
		List<RecipeSlot> outputs = toSlots(ex.outputs, true);

		RecipeDetails.Builder details = RecipeDetails.builder();
		if (ex.duration != null) details.durationTicks(ex.duration);
		if (ex.energy != null) details.energy(ex.energy);
		List<Component> info = new ArrayList<>();
		info.add(Component.translatable("rbei.info.mod", fr.max.rbei.ui.InfoFormat.modName(id.getNamespace())));
		if (ex.duration != null) info.add(Component.translatable("rbei.info.time", ex.duration));
		if (ex.energy != null) info.add(Component.translatable("rbei.info.energy", ex.energy));

		Identifier typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
		return Optional.of(UniversalRecipe.builder(id + "#auto")
				.id(id).sourceId("rbei:auto:" + id.getNamespace())
				.kind(station.getHoverName())
				.stations(List.of(station.copy()))
				.grid(Math.max(1, Math.min(3, Math.max(1, inputs.size()))),
						Math.max(1, (inputs.size() + 2) / 3))
				.inputs(inputs)
				.outputs(outputs)
				.info(info)
				.details(details.extra("recipe_type", typeId == null ? "?" : typeId.toString())
						.extra("auto", "true")
						.build())
				.build());
	}

	/** Enrichit un RecipeDisplay inconnu : récupère tous les SlotDisplay du record/classe. */
	public static UniversalRecipe fromUnknownDisplay(RecipeDisplay display, ContextMap ctx,
			Identifier recipeId, int index, Identifier displayType) {
		Extracted ex = extract(display, ctx);
		List<ItemStack> resultStacks = DisplayConverter.resolve(display.result(), ctx);
		List<ItemStack> stationStacks = DisplayConverter.resolve(display.craftingStation(), ctx);
		for (ItemStack s : resultStacks) pushStack(ex.outputs, s, null);
		for (ItemStack s : stationStacks) pushStack(ex.stations, s, null);

		if (ex.outputs.isEmpty()) return null;

		ItemStack station = ex.stations.isEmpty()
				? new ItemStack(Items.CRAFTING_TABLE)
				: ex.stations.get(0);
		Component kind = station.getHoverName();
		if (displayType != null && !ex.stations.isEmpty()) {
			kind = station.getHoverName();
		} else if (displayType != null) {
			kind = Component.literal(displayType.toString());
		}

		String uid = (recipeId == null ? "anon" : recipeId.toString()) + "#auto-display" + index;
		List<Component> info = new ArrayList<>();
		if (recipeId != null) {
			info.add(Component.translatable("rbei.info.mod",
					fr.max.rbei.ui.InfoFormat.modName(recipeId.getNamespace())));
		}

		return UniversalRecipe.builder(uid)
				.id(recipeId).sourceId("rbei:auto-display")
				.kind(kind).stations(ex.stations.isEmpty() ? List.of(station) : List.copyOf(ex.stations))
				.grid(Math.max(1, Math.min(3, Math.max(1, ex.inputs.size()))),
						Math.max(1, (ex.inputs.size() + 2) / 3))
				.inputs(toSlots(ex.inputs, false))
				.outputs(toSlots(ex.outputs, true))
				.info(info)
				.build();
	}

	private static Extracted extract(Object root, ContextMap ctx) {
		Extracted ex = new Extracted();
		walk(root, ctx, ex, 0, "", new LinkedHashSet<>());
		return ex;
	}

	private static void walk(Object obj, ContextMap ctx, Extracted ex, int depth, String path,
			Set<Object> seen) {
		if (obj == null || depth > 4) return;
		if (obj instanceof ItemStack || obj instanceof Ingredient || obj instanceof Item
				|| obj instanceof ItemStackTemplate || obj instanceof SlotDisplay
				|| obj instanceof Number || obj instanceof String || obj instanceof Identifier
				|| obj instanceof Character || obj instanceof Boolean) {
			consumeLeaf(obj, ctx, ex, path);
			return;
		}
		if (!seen.add(System.identityHashCode(obj) + ":" + obj.getClass().getName())) return;

		if (obj instanceof Optional<?> opt) {
			opt.ifPresent(v -> walk(v, ctx, ex, depth + 1, path, seen));
			return;
		}
		if (obj instanceof Collection<?> col) {
			int i = 0;
			for (Object e : col) walk(e, ctx, ex, depth + 1, path + "[" + (i++) + "]", seen);
			return;
		}
		if (obj instanceof Map<?, ?> map) {
			for (Map.Entry<?, ?> e : map.entrySet()) {
				walk(e.getValue(), ctx, ex, depth + 1, path + "." + e.getKey(), seen);
			}
			return;
		}
		if (obj instanceof Object[] arr) {
			for (int i = 0; i < arr.length; i++) walk(arr[i], ctx, ex, depth + 1, path + "[" + i + "]", seen);
			return;
		}

		// Getters 0-arg utiles
		for (Method m : obj.getClass().getMethods()) {
			if (m.getParameterCount() != 0 || m.getDeclaringClass() == Object.class) continue;
			if (Modifier.isStatic(m.getModifiers())) continue;
			String name = m.getName();
			if (!(name.startsWith("get") || name.startsWith("is") || name.equals("result")
					|| name.equals("ingredient") || name.equals("input") || name.equals("output")
					|| name.equals("ingredients") || name.equals("display"))) continue;
			if (name.equals("getClass") || name.equals("hashCode") || name.equals("toString")) continue;
			try {
				m.setAccessible(true);
				Object v = m.invoke(obj);
				if (v != null && v != obj) walk(v, ctx, ex, depth + 1, path + "." + name, seen);
			} catch (Throwable ignored) {}
		}

		Class<?> c = obj.getClass();
		while (c != null && c != Object.class) {
			for (Field f : c.getDeclaredFields()) {
				if (Modifier.isStatic(f.getModifiers())) continue;
				try {
					f.setAccessible(true);
					Object v = f.get(obj);
					if (v != null && v != obj) {
						walk(v, ctx, ex, depth + 1, path + "." + f.getName(), seen);
					}
				} catch (Throwable ignored) {}
			}
			c = c.getSuperclass();
		}
	}

	private static void consumeLeaf(Object obj, ContextMap ctx, Extracted ex, String path) {
		String leaf = leafName(path).toLowerCase();
		boolean asOutput = hintsMatch(leaf, OUTPUT_HINTS);
		boolean asInput = hintsMatch(leaf, INPUT_HINTS) || (!asOutput && !hintsMatch(leaf, STATION_HINTS));
		boolean asStation = hintsMatch(leaf, STATION_HINTS);

		if (obj instanceof Number num) {
			if (hintsMatch(leaf, DURATION_HINTS) && ex.duration == null) {
				ex.duration = num.intValue();
			} else if (hintsMatch(leaf, ENERGY_HINTS) && ex.energy == null) {
				ex.energy = num.longValue();
			} else if (hintsMatch(leaf, CHANCE_HINTS) && ex.lastChance == null) {
				float c = num.floatValue();
				ex.lastChance = c > 1 ? c / 100f : c;
			}
			return;
		}

		if (obj instanceof SlotDisplay slot) {
			List<ItemStack> stacks = DisplayConverter.resolve(slot, ctx);
			for (ItemStack s : stacks) {
				if (asStation) pushStack(ex.stations, s, null);
				else if (asOutput) pushStack(ex.outputs, s, ex.lastChance);
				else if (asInput) pushStack(ex.inputs, s, null);
			}
			ex.lastChance = null;
			return;
		}
		if (obj instanceof Ingredient ing) {
			List<ItemStack> stacks = RecipeFallback.stacksOf(ing, ctx);
			for (ItemStack s : stacks) {
				if (asOutput) pushStack(ex.outputs, s, ex.lastChance);
				else pushStack(ex.inputs, s, null);
			}
			ex.lastChance = null;
			return;
		}
		if (obj instanceof ItemStack stack) {
			if (stack.isEmpty()) return;
			if (asStation) pushStack(ex.stations, stack, null);
			else if (asOutput) pushStack(ex.outputs, stack, ex.lastChance);
			else pushStack(ex.inputs, stack, null);
			ex.lastChance = null;
			return;
		}
		if (obj instanceof ItemStackTemplate template) {
			ItemStack stack = template.create();
			if (stack.isEmpty()) return;
			if (asOutput) pushStack(ex.outputs, stack, ex.lastChance);
			else if (asStation) pushStack(ex.stations, stack, null);
			else pushStack(ex.inputs, stack, null);
			ex.lastChance = null;
			return;
		}
		if (obj instanceof Item item && item != Items.AIR) {
			ItemStack stack = item.getDefaultInstance();
			if (asStation) pushStack(ex.stations, stack, null);
			else if (asOutput) pushStack(ex.outputs, stack, ex.lastChance);
			else pushStack(ex.inputs, stack, null);
			ex.lastChance = null;
			return;
		}
		if (obj instanceof Holder<?> holder) {
			Object v = holder.value();
			if (v instanceof Item item) consumeLeaf(item, ctx, ex, path);
		}
	}

	private static void pushStack(List<ItemStack> list, ItemStack stack, Float chance) {
		if (stack == null || stack.isEmpty()) return;
		// évite les doublons exacts
		for (ItemStack existing : list) {
			if (ItemStack.isSameItemSameComponents(existing, stack)) return;
		}
		list.add(stack.copy());
	}

	private static List<RecipeSlot> toSlots(List<ItemStack> stacks, boolean outputs) {
		List<RecipeSlot> slots = new ArrayList<>();
		int i = 0;
		for (ItemStack s : stacks) {
			slots.add(new RecipeSlot(i % 3, i / 3, List.of(s.copy()), s.getCount(), null));
			i++;
			if (!outputs && i >= 9) break;
			if (outputs && i >= 6) break;
		}
		return slots;
	}

	private static boolean hintsMatch(String leaf, Set<String> hints) {
		for (String h : hints) {
			if (leaf.contains(h.toLowerCase())) return true;
		}
		return false;
	}

	private static String leafName(String path) {
		int dot = path.lastIndexOf('.');
		String leaf = dot >= 0 ? path.substring(dot + 1) : path;
		int br = leaf.indexOf('[');
		return br >= 0 ? leaf.substring(0, br) : leaf;
	}

	static ItemStack stationFor(RecipeType<?> type, Identifier recipeId) {
		Identifier typeId = BuiltInRegistries.RECIPE_TYPE.getKey(type);
		if (typeId != null) {
			ItemStack exact = item(typeId);
			if (!exact.isEmpty()) return exact;
			// même path dans le namespace du mod
			ItemStack same = item(Identifier.fromNamespaceAndPath(typeId.getNamespace(), typeId.getPath()));
			if (!same.isEmpty()) return same;
			String path = typeId.getPath();
			for (Item item : BuiltInRegistries.ITEM) {
				Identifier key = BuiltInRegistries.ITEM.getKey(item);
				if (!key.getNamespace().equals(typeId.getNamespace())) continue;
				if (path.contains(key.getPath()) || key.getPath().contains(path)
						|| sharesToken(path, key.getPath())) {
					return item.getDefaultInstance();
				}
			}
			if (path.contains("smith")) return new ItemStack(Items.SMITHING_TABLE);
			if (path.contains("furnace") || path.contains("smelt")) return new ItemStack(Items.FURNACE);
			if (path.contains("blast")) return new ItemStack(Items.BLAST_FURNACE);
			if (path.contains("smok")) return new ItemStack(Items.SMOKER);
			if (path.contains("campfire")) return new ItemStack(Items.CAMPFIRE);
			if (path.contains("stonecut")) return new ItemStack(Items.STONECUTTER);
			if (path.contains("craft")) return new ItemStack(Items.CRAFTING_TABLE);
		}
		if (recipeId != null && !recipeId.getNamespace().equals("minecraft")) {
			for (Item item : BuiltInRegistries.ITEM) {
				Identifier key = BuiltInRegistries.ITEM.getKey(item);
				if (key.getNamespace().equals(recipeId.getNamespace())
						&& (key.getPath().contains("machine") || key.getPath().contains("furnace")
						|| key.getPath().contains("smith") || key.getPath().contains("table")
						|| key.getPath().contains("crusher") || key.getPath().contains("alloy"))) {
					return item.getDefaultInstance();
				}
			}
		}
		return new ItemStack(Items.CRAFTING_TABLE);
	}

	private static boolean sharesToken(String a, String b) {
		String[] ta = a.split("[_/]");
		String[] tb = b.split("[_/]");
		for (String x : ta) {
			if (x.length() < 4) continue;
			for (String y : tb) {
				if (x.equals(y)) return true;
			}
		}
		return false;
	}

	private static ItemStack item(Identifier id) {
		if (id == null) return ItemStack.EMPTY;
		return BuiltInRegistries.ITEM.getOptional(id).map(Item::getDefaultInstance).orElse(ItemStack.EMPTY);
	}

	private static final class Extracted {
		final List<ItemStack> inputs = new ArrayList<>();
		final List<ItemStack> outputs = new ArrayList<>();
		final List<ItemStack> stations = new ArrayList<>();
		Integer duration;
		Long energy;
		Float lastChance;
	}
}
