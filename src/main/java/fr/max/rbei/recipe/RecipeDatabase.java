package fr.max.rbei.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.max.rbei.LiveLootWalker;
import fr.max.rbei.RecipeIndex;
import fr.max.rbei.SurvivalFilter;
import fr.max.rbei.recipe.adapter.SoftAdapters;
import fr.max.rbei.recipe.api.RecipeAdapter;
import fr.max.rbei.recipe.api.RbeiRecipeApi;
import fr.max.rbei.recipe.source.AutoModRecipeSource;
import fr.max.rbei.recipe.source.ComposterSource;
import fr.max.rbei.recipe.source.ExtraDataSource;
import fr.max.rbei.recipe.source.NativeRecipeSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Base interne indexée (par résultat / par ingrédient), avec déduplication et cache monde.
 */
public final class RecipeDatabase {
	private static final Logger LOG = LoggerFactory.getLogger("rbei/recipes");

	private static volatile RecipeDatabase instance;
	private static volatile Object cacheKey;
	private static volatile boolean rebuildScheduled;

	private final List<UniversalRecipe> all;
	private final List<RecipeIndex.Entry> uiEntries;
	private final Map<String, List<RecipeIndex.Entry>> uiByOutput = new HashMap<>();
	private final Map<String, List<RecipeIndex.Entry>> uiByInput = new HashMap<>();
	private final Map<String, List<UniversalRecipe>> byOutput = new HashMap<>();
	private final Map<String, List<UniversalRecipe>> byInput = new HashMap<>();
	private final List<String> limitations;

	private RecipeDatabase(List<UniversalRecipe> all, List<String> limitations) {
		this.all = List.copyOf(all);
		this.limitations = List.copyOf(limitations);
		List<RecipeIndex.Entry> ui = new ArrayList<>(all.size());
		for (UniversalRecipe u : all) {
			RecipeIndex.Entry entry = UiBridge.toEntry(u);
			ui.add(entry);
			index(u, entry);
		}
		this.uiEntries = List.copyOf(ui);
		// Figer les listes d'index pour éviter les reallocs en lecture
		freezeIndex(uiByOutput);
		freezeIndex(uiByInput);
		freezeIndex(byOutput);
		freezeIndex(byInput);
	}

	private static <T> void freezeIndex(Map<String, List<T>> map) {
		for (Map.Entry<String, List<T>> e : map.entrySet()) {
			e.setValue(List.copyOf(e.getValue()));
		}
	}

	public static void invalidate() {
		instance = null;
		cacheKey = null;
		SurvivalFilter.invalidate();
		LiveLootWalker.invalidate();
		rebuildScheduled = false;
	}

	/** Invalide puis reconstruit à l'avance au prochain tick client (évite le lag à l'ouverture). */
	public static void invalidateAndWarm(Minecraft mc) {
		invalidate();
		if (mc == null || rebuildScheduled) return;
		rebuildScheduled = true;
		mc.execute(() -> {
			rebuildScheduled = false;
			if (mc.level != null && mc.player != null) {
				get(mc);
			}
		});
	}

	public static RecipeDatabase get(Minecraft mc) {
		Object key = cacheKeyOf(mc);
		RecipeDatabase db = instance;
		if (db != null && key != null && key.equals(cacheKey)) return db;
		synchronized (RecipeDatabase.class) {
			db = instance;
			if (db != null && key != null && key.equals(cacheKey)) return db;
			db = rebuild(mc);
			instance = db;
			cacheKey = key;
			return db;
		}
	}

	private static Object cacheKeyOf(Minecraft mc) {
		if (mc == null) return null;
		return System.identityHashCode(mc.level)
				+ ":" + (mc.getSingleplayerServer() != null)
				+ ":" + (mc.player != null ? System.identityHashCode(mc.player.connection) : 0);
	}

	private static RecipeDatabase rebuild(Minecraft mc) {
		long t0 = System.nanoTime();
		// Précharge le filtre survie une seule fois
		SurvivalFilter.current();

		List<UniversalRecipe> recipes = new ArrayList<>(4096);
		List<String> limitations = new ArrayList<>();
		Set<String> seen = new HashSet<>(4096);

		for (RecipeSource source : builtInSources()) {
			collectSafe(source.id(), () -> source.collect(mc), recipes, seen, limitations);
			String lim = source.limitation();
			if (lim != null) limitations.add(lim);
		}

		List<RecipeAdapter> adapters = loadAdapters();
		adapters.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
		for (RecipeAdapter adapter : adapters) {
			if (!modsPresent(adapter)) continue;
			String lim = adapter.limitation();
			if (lim != null) limitations.add("[" + adapter.id() + "] " + lim);
			collectSafe(adapter.id(), () -> adapter.collect(mc), recipes, seen, limitations);
		}

		RecipeDatabase db = new RecipeDatabase(recipes, limitations);
		long ms = (System.nanoTime() - t0) / 1_000_000L;
		LOG.info("RBEI - Core index: {} recipes, {} out/in keys, {} ms",
				db.all.size(), db.byOutput.size() + "/" + db.byInput.size(), ms);
		return db;
	}

	private static List<RecipeSource> builtInSources() {
		return List.of(
				new NativeRecipeSource(),
				new AutoModRecipeSource(),
				new ExtraDataSource(),
				new ComposterSource());
	}

	private static List<RecipeAdapter> loadAdapters() {
		List<RecipeAdapter> list = new ArrayList<>();
		list.addAll(SoftAdapters.builtin());
		list.addAll(RbeiRecipeApi.runtimeAdapters());
		try {
			list.addAll(FabricLoader.getInstance()
					.getEntrypoints("rbei:recipe_adapter", RecipeAdapter.class));
		} catch (RuntimeException ex) {
			LOG.warn("Failed loading rbei:recipe_adapter entrypoints", ex);
		}
		return list;
	}

	private static boolean modsPresent(RecipeAdapter adapter) {
		var required = adapter.requiredMods();
		if (required == null || required.isEmpty()) return true;
		FabricLoader loader = FabricLoader.getInstance();
		for (String mod : required) {
			if (loader.isModLoaded(mod)) return true;
		}
		return false;
	}

	private static void collectSafe(String id, RecipeSupplier supplier,
			List<UniversalRecipe> out, Set<String> seen, List<String> limitations) {
		try {
			List<UniversalRecipe> got = supplier.get();
			if (got == null) return;
			for (UniversalRecipe u : got) {
				if (u == null) continue;
				List<ItemStack> outputs = u.flatOutputs();
				if (outputs.isEmpty()) continue;
				if (!hasSurvivalOutput(outputs)) continue;
				if (!seen.add(u.uid())) continue;
				out.add(u);
			}
		} catch (Throwable t) {
			limitations.add("Source '" + id + "' failed: " + t.getClass().getSimpleName()
					+ " — " + t.getMessage());
			LOG.warn("Recipe source '{}' failed", id, t);
		}
	}

	@FunctionalInterface
	private interface RecipeSupplier {
		List<UniversalRecipe> get();
	}

	private static boolean hasSurvivalOutput(List<ItemStack> outputs) {
		for (ItemStack out : outputs) {
			if (SurvivalFilter.isSurvivalItem(out)) return true;
		}
		return false;
	}

	private void index(UniversalRecipe u, RecipeIndex.Entry entry) {
		for (ItemStack out : u.flatOutputs()) {
			String k = ItemKey.of(out);
			byOutput.computeIfAbsent(k, x -> new ArrayList<>()).add(u);
			uiByOutput.computeIfAbsent(k, x -> new ArrayList<>()).add(entry);
		}
		for (RecipeSlot slot : u.inputs()) {
			for (ItemStack opt : slot.options()) {
				String k = ItemKey.of(opt);
				byInput.computeIfAbsent(k, x -> new ArrayList<>()).add(u);
				uiByInput.computeIfAbsent(k, x -> new ArrayList<>()).add(entry);
			}
		}
		for (ItemStack st : u.stations()) {
			String k = ItemKey.of(st);
			byInput.computeIfAbsent(k, x -> new ArrayList<>()).add(u);
			uiByInput.computeIfAbsent(k, x -> new ArrayList<>()).add(entry);
		}
	}

	public List<UniversalRecipe> all() {
		return all;
	}

	public List<RecipeIndex.Entry> uiEntries() {
		return uiEntries;
	}

	public List<String> limitations() {
		return limitations;
	}

	/** Entrées UI indexées — zéro reconversion à l'ouverture d'un objet. */
	public List<RecipeIndex.Entry> uiRecipesFor(ItemStack stack) {
		return filterUi(uiByOutput.getOrDefault(ItemKey.of(stack), List.of()), stack, true);
	}

	public List<RecipeIndex.Entry> uiUsesOf(ItemStack stack) {
		return filterUi(uiByInput.getOrDefault(ItemKey.of(stack), List.of()), stack, false);
	}

	private static List<RecipeIndex.Entry> filterUi(List<RecipeIndex.Entry> list, ItemStack stack, boolean asOutput) {
		if (list.isEmpty()) return List.of();
		List<RecipeIndex.Entry> out = new ArrayList<>();
		Set<RecipeIndex.Entry> added = asOutput ? null : new HashSet<>();
		for (RecipeIndex.Entry e : list) {
			if (asOutput) {
				if (contains(e.outputs(), stack)) out.add(e);
			} else {
				if (added != null && !added.add(e)) continue;
				if (contains(e.stations(), stack) || cellsContain(e, stack)) out.add(e);
			}
		}
		return out;
	}

	private static boolean cellsContain(RecipeIndex.Entry e, ItemStack stack) {
		for (RecipeIndex.Cell c : e.cells()) {
			if (contains(c.options(), stack)) return true;
		}
		return false;
	}

	private static boolean contains(List<ItemStack> list, ItemStack stack) {
		for (ItemStack s : list) {
			if (ItemKey.same(s, stack)) return true;
		}
		return false;
	}

	public List<UniversalRecipe> recipesFor(ItemStack stack) {
		List<UniversalRecipe> list = byOutput.getOrDefault(ItemKey.of(stack), List.of());
		if (list.isEmpty()) return List.of();
		List<UniversalRecipe> out = new ArrayList<>();
		for (UniversalRecipe u : list) {
			for (ItemStack o : u.flatOutputs()) {
				if (ItemKey.same(o, stack)) {
					out.add(u);
					break;
				}
			}
		}
		return out;
	}

	public List<UniversalRecipe> usesOf(ItemStack stack) {
		List<UniversalRecipe> list = byInput.getOrDefault(ItemKey.of(stack), List.of());
		if (list.isEmpty()) return List.of();
		List<UniversalRecipe> out = new ArrayList<>();
		Set<String> added = new HashSet<>();
		for (UniversalRecipe u : list) {
			if (!added.add(u.uid())) continue;
			if (matchesUse(u, stack)) out.add(u);
		}
		return out;
	}

	private static boolean matchesUse(UniversalRecipe u, ItemStack stack) {
		for (ItemStack st : u.stations()) {
			if (ItemKey.same(st, stack)) return true;
		}
		for (RecipeSlot slot : u.inputs()) {
			for (ItemStack opt : slot.options()) {
				if (ItemKey.same(opt, stack)) return true;
			}
		}
		return false;
	}

	public int outputKeyCount() {
		return byOutput.size();
	}

	public int inputKeyCount() {
		return byInput.size();
	}

	public static RecipeDatabase fromRecipesForTest(List<UniversalRecipe> recipes) {
		return new RecipeDatabase(recipes, List.of());
	}

	public Map<String, Integer> stats() {
		Map<String, Integer> m = new LinkedHashMap<>();
		m.put("recipes", all.size());
		m.put("outputKeys", byOutput.size());
		m.put("inputKeys", byInput.size());
		m.put("limitations", limitations.size());
		return Collections.unmodifiableMap(m);
	}
}
