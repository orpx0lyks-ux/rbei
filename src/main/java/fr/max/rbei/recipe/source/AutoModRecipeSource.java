package fr.max.rbei.recipe.source;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.max.rbei.recipe.ReflectiveHarvester;
import fr.max.rbei.recipe.RecipeSource;
import fr.max.rbei.recipe.UniversalRecipe;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * Scan ciblé des registres propriétaires — uniquement les mods qui déclarent
 * un RecipeSerializer / RecipeType (évite Class.forName sur toute la liste Fabric).
 */
public final class AutoModRecipeSource implements RecipeSource {
	private static final Logger LOG = LoggerFactory.getLogger("rbei/auto");
	private static final Set<String> MISSING_CLASSES =
			java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

	private int added;
	private final List<String> notes = new ArrayList<>();

	@Override
	public String id() {
		return "rbei:auto-mods";
	}

	@Override
	public List<UniversalRecipe> collect(Minecraft mc) {
		added = 0;
		notes.clear();
		List<UniversalRecipe> out = new ArrayList<>();
		if (mc.level == null) return out;
		ContextMap ctx = SlotDisplayContext.fromLevel(mc.level);
		Set<String> seen = new HashSet<>();

		// Mods réellement liés aux recettes (serializers / types non-minecraft)
		Set<String> recipeMods = new HashSet<>();
		for (var e : BuiltInRegistries.RECIPE_SERIALIZER.entrySet()) {
			String ns = e.getKey().identifier().getNamespace();
			if (!ns.equals("minecraft")) recipeMods.add(ns);
		}
		for (var e : BuiltInRegistries.RECIPE_TYPE.entrySet()) {
			String ns = e.getKey().identifier().getNamespace();
			if (!ns.equals("minecraft")) recipeMods.add(ns);
		}
		// + mods tech connus même sans serializer listé tôt
		for (String known : List.of("create", "mekanism", "ae2", "techreborn",
				"modern_industrialization", "indrev", "botania", "farmersdelight")) {
			if (FabricLoader.getInstance().isModLoaded(known)) recipeMods.add(known);
		}

		for (String modId : recipeMods) {
			if (isSkippedMod(modId)) continue;
			if (!FabricLoader.getInstance().isModLoaded(modId)) continue;
			int before = out.size();
			probeMod(modId, ctx, out, seen);
			int delta = out.size() - before;
			if (delta > 0) {
				added += delta;
				notes.add(modId + " +" + delta);
				LOG.info("Auto-mod {}: {} proprietary recipes", modId, delta);
			}
		}
		return out;
	}

	private static boolean isSkippedMod(String modId) {
		return modId.equals("minecraft") || modId.equals("java") || modId.equals("fabricloader")
				|| modId.equals("mixinextras") || modId.startsWith("fabric-")
				|| modId.equals("rbei") || modId.equals("fabric-api");
	}

	private void probeMod(String modId, ContextMap ctx, List<UniversalRecipe> out, Set<String> seen) {
		String[] candidates = {
				modId + ".recipe.ModRecipes",
				modId + ".recipes.ModRecipes",
				modId + ".init.ModRecipes",
				modId + ".registry.ModRecipes",
				modId + ".RecipeTypes",
				modId + ".recipe.RecipeTypes",
				"com." + modId + ".recipe.ModRecipes",
				"com.simibubi.create.AllRecipeTypes",
				"com.simibubi.create.foundation.data.recipe.CreateRecipeProvider",
				"com.simibubi.create.content.processing.recipe.ProcessingRecipeType",
				"mekanism.common.recipe.MekanismRecipeType",
				"appeng.recipes.AERecipeTypes",
				"appeng.core.definitions.AERecipeTypes",
				"aztech.modern_industrialization.machines.recipe.MIRecipeTypes",
				"vectorwing.farmersdelight.common.registry.ModRecipeTypes",
				"vectorwing.farmersdelight.common.crafting.CookingPotRecipe",
				"vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe",
		};
		ClassLoader cl = AutoModRecipeSource.class.getClassLoader();
		for (String name : candidates) {
			if (MISSING_CLASSES.contains(name)) continue;
			try {
				Class<?> clazz = Class.forName(name, false, cl);
				harvestStatic(clazz, modId, ctx, out, seen);
			} catch (ClassNotFoundException ignored) {
				MISSING_CLASSES.add(name);
			} catch (Throwable t) {
				notes.add(modId + "/" + name + ":" + t.getClass().getSimpleName());
			}
		}

		for (var entry : BuiltInRegistries.RECIPE_SERIALIZER.entrySet()) {
			Identifier sid = entry.getKey().identifier();
			if (!sid.getNamespace().equals(modId)) continue;
			try {
				harvestStatic(entry.getValue().getClass(), modId, ctx, out, seen);
			} catch (Throwable ignored) {}
		}
	}

	private void harvestStatic(Class<?> clazz, String modId, ContextMap ctx,
			List<UniversalRecipe> out, Set<String> seen) {
		for (Field f : clazz.getDeclaredFields()) {
			if (!Modifier.isStatic(f.getModifiers())) continue;
			try {
				f.setAccessible(true);
				drain(f.get(null), modId, ctx, out, seen, 0);
			} catch (Throwable ignored) {}
		}
		for (Method m : clazz.getDeclaredMethods()) {
			if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 0) continue;
			String n = m.getName().toLowerCase();
			if (!(n.contains("recipe") || n.contains("all") || n.contains("values")
					|| n.contains("entries") || n.equals("get") || n.startsWith("get"))) continue;
			try {
				m.setAccessible(true);
				drain(m.invoke(null), modId, ctx, out, seen, 0);
			} catch (Throwable ignored) {}
		}
		Object[] constants = clazz.getEnumConstants();
		if (constants != null) {
			for (Object c : constants) {
				try {
					for (Method m : c.getClass().getMethods()) {
						if (m.getParameterCount() != 0) continue;
						String n = m.getName().toLowerCase();
						if (n.contains("recipe") || n.contains("getall") || n.equals("getrecipes")) {
							try {
								drain(m.invoke(c), modId, ctx, out, seen, 0);
							} catch (Throwable ignored) {}
						}
					}
				} catch (Throwable ignored) {}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void drain(Object v, String modId, ContextMap ctx, List<UniversalRecipe> out,
			Set<String> seen, int depth) {
		if (v == null || depth > 4) return;
		if (v instanceof RecipeHolder<?> holder) {
			String id = holder.id().identifier().toString();
			if (!seen.add(id)) return;
			ReflectiveHarvester.fromRecipe(holder, ctx).ifPresent(out::add);
			return;
		}
		if (v instanceof Recipe<?> recipe) {
			String uid = modId + ":prop:" + recipe.getClass().getName() + "@"
					+ Integer.toHexString(System.identityHashCode(recipe));
			if (!seen.add(uid)) return;
			try {
				Identifier id = Identifier.fromNamespaceAndPath(modId,
						"auto/" + Integer.toHexString(System.identityHashCode(recipe)));
				RecipeHolder<Recipe<?>> fake = new RecipeHolder<>(
						ResourceKey.create(Registries.RECIPE, id), recipe);
				ReflectiveHarvester.fromRecipe(fake, ctx).ifPresent(out::add);
			} catch (Throwable ignored) {}
			return;
		}
		if (v instanceof Optional<?> opt) {
			opt.ifPresent(o -> drain(o, modId, ctx, out, seen, depth + 1));
			return;
		}
		if (v instanceof Collection<?> col) {
			for (Object e : col) drain(e, modId, ctx, out, seen, depth + 1);
			return;
		}
		if (v instanceof Map<?, ?> map) {
			for (Object e : map.values()) drain(e, modId, ctx, out, seen, depth + 1);
			return;
		}
		if (v instanceof Registry<?> reg) {
			for (Object e : reg) drain(e, modId, ctx, out, seen, depth + 1);
		}
	}

	@Override
	public String limitation() {
		if (added == 0 && notes.isEmpty()) {
			return "Targeted mod scan (serializers/types): 0 extra proprietary recipes.";
		}
		if (notes.isEmpty()) return "Mod scan: +" + added + " proprietary recipes.";
		return "Mod scan (+" + added + "): " + String.join(", ",
				notes.subList(0, Math.min(5, notes.size())));
	}
}
