package fr.max.rbei;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Sources de données en plus des recettes vanilla :
 * potions (alambic), butins (tables de loot) et génération des minerais (hauteurs Y).
 * Butins et minerais viennent des données du monde solo (le client n'a pas ces fichiers en multijoueur).
 */
public final class ExtraSources {
	private ExtraSources() {}

	private static final int NONE = Integer.MIN_VALUE;

	/** Meilleure couche pour miner un minerai (valeurs de référence), et la zone concernée. */
	private record Best(int min, int max, String zoneKey) {}

	private static final String OVERWORLD = "rbei.ore.dim.overworld";
	private static final String NETHER = "rbei.ore.dim.nether";
	private static final String MOUNTAINS = "rbei.ore.zone.mountains";
	private static final Map<String, Best> BEST = new HashMap<>();

	static {
		best("diamond", -59, -59, OVERWORLD);
		best("redstone", -59, -59, OVERWORLD);
		best("coal", 96, 96, OVERWORLD);
		best("copper", 48, 48, OVERWORLD);
		best("iron", 16, 16, OVERWORLD);
		best("gold", -16, -16, OVERWORLD);
		best("lapis", 0, 0, OVERWORLD);
		best("emerald", 232, 232, MOUNTAINS);
		BEST.put("minecraft:ancient_debris", new Best(15, 15, NETHER));
		BEST.put("minecraft:nether_quartz_ore", new Best(10, 117, NETHER));
		BEST.put("minecraft:nether_gold_ore", new Best(10, 117, NETHER));
	}

	/** Enregistre le minerai et sa variante deepslate. */
	private static void best(String name, int min, int max, String zoneKey) {
		BEST.put("minecraft:" + name + "_ore", new Best(min, max, zoneKey));
		BEST.put("minecraft:deepslate_" + name + "_ore", new Best(min, max, zoneKey));
	}

	// ------------------------------------------------------------------ potions

	private record Mix(ItemStack ingredient, ItemStack input, ItemStack result) {}

	/** Clé d'une potion : l'objet seul ne suffit pas, toutes les fioles sont le même objet. */
	private static String key(ItemStack s) {
		return BuiltInRegistries.ITEM.getKey(s.getItem()) + "#" + s.get(DataComponents.POTION_CONTENTS);
	}

	public static List<RecipeIndex.Entry> brewing(Minecraft mc) {
		PotionBrewing brewing = mc.level.potionBrewing();
		ItemStack stand = new ItemStack(Items.BREWING_STAND);

		List<ItemStack> ingredients = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack s = item.getDefaultInstance();
			if (brewing.isIngredient(s)) ingredients.add(s);
		}

		List<Mix> mixes = new ArrayList<>();
		List<Holder.Reference<Potion>> potions = BuiltInRegistries.POTION.listElements().toList();
		for (Item container : new Item[] {Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION}) {
			for (Holder.Reference<Potion> potion : potions) {
				ItemStack input = PotionContents.createItemStack(container, potion);
				for (ItemStack ing : ingredients) {
					if (!brewing.hasMix(input, ing)) continue;
					ItemStack result = brewing.mix(ing, input);
					if (result.isEmpty() || ItemStack.isSameItemSameComponents(result, input)) continue;
					mixes.add(new Mix(ing, input, result));
				}
			}
		}

		// nombre d'étapes depuis une fiole d'eau (parcours en largeur du graphe des mélanges)
		Map<String, List<String>> next = new HashMap<>();
		for (Mix m : mixes) {
			next.computeIfAbsent(key(m.input()), k -> new ArrayList<>()).add(key(m.result()));
		}
		Map<String, Integer> steps = new HashMap<>();
		Deque<String> queue = new ArrayDeque<>();
		String water = key(PotionContents.createItemStack(Items.POTION, Potions.WATER));
		steps.put(water, 0);
		queue.add(water);
		while (!queue.isEmpty()) {
			String cur = queue.poll();
			for (String n : next.getOrDefault(cur, List.of())) {
				if (!steps.containsKey(n)) {
					steps.put(n, steps.get(cur) + 1);
					queue.add(n);
				}
			}
		}

		List<RecipeIndex.Entry> out = new ArrayList<>();
		for (Mix m : mixes) {
			Integer n = steps.get(key(m.result()));
			List<Component> info = n == null ? List.of() : List.of(Component.translatable("rbei.brew.steps", n));
			List<RecipeIndex.Cell> cells = List.of(
					new RecipeIndex.Cell(0, 0, List.of(m.ingredient())),
					new RecipeIndex.Cell(0, 1, List.of(m.input())));
			out.add(new RecipeIndex.Entry(stand.getHoverName(), 1, 2, cells,
					List.of(m.result()), List.of(stand), false, false, null, info, null));
		}
		return out;
	}

	// ------------------------------------------------------------------ butins (tables LIVE + JSON)

	public static List<RecipeIndex.Entry> loot(Minecraft mc) {
		List<RecipeIndex.Entry> out = new ArrayList<>();
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null) return out;
		ResourceManager rm = server.getResourceManager();
		Set<String> seen = new LinkedHashSet<>();

		// 1) PRIORITÉ : loot tables en mémoire (après LootTableEvents.MODIFY des mods, ex. WardenGear)
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			Optional<ResourceKey<LootTable>> lootKey = type.getDefaultLootTable();
			if (lootKey.isEmpty()) {
				if (type.getCategory() == MobCategory.MISC) continue;
				Identifier eid = BuiltInRegistries.ENTITY_TYPE.getKey(type);
				lootKey = Optional.of(ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
						Identifier.fromNamespaceAndPath(eid.getNamespace(), "entities/" + eid.getPath())));
			}
			List<LiveLootWalker.Drop> drops = LiveLootWalker.collect(server, lootKey.get());
			if (drops.isEmpty()) continue;
			Identifier eid = BuiltInRegistries.ENTITY_TYPE.getKey(type);
			addEntityLiveDrops(out, seen, type, eid, drops);
		}

		// 2) Complément JSON : blocs / coffres / tables sans EntityType
		Map<Identifier, Resource> files = rm.listResources("loot_table", id -> id.getPath().endsWith(".json"));
		for (Map.Entry<Identifier, Resource> f : files.entrySet()) {
			Identifier fileId = f.getKey();
			String rel = lootRelPath(fileId.getPath());
			if (rel == null) continue;
			Identifier tableId = Identifier.fromNamespaceAndPath(fileId.getNamespace(), rel);

			// Entités : déjà couvertes en live ; on complète seulement si rien vu
			if (rel.startsWith("entities/") || rel.startsWith("entity/")) {
				String n = rel.substring(rel.indexOf('/') + 1);
				int slash = n.indexOf('/');
				if (slash >= 0) n = n.substring(0, slash);
				Identifier eid = Identifier.fromNamespaceAndPath(tableId.getNamespace(), n);
				String prefix = "entity:" + eid + "|";
				boolean already = seen.stream().anyMatch(s -> s.startsWith(prefix));
				if (already) continue;
				List<LiveLootWalker.Drop> live = LiveLootWalker.collect(server, tableId);
				if (!live.isEmpty()) {
					EntityType<?> et = BuiltInRegistries.ENTITY_TYPE.getOptional(eid).orElse(null);
					if (et != null) addEntityLiveDrops(out, seen, et, eid, live);
					continue;
				}
				Set<String> names = resolveLootItems(rm, tableId, new LinkedHashSet<>());
				if (names.isEmpty()) continue;
				EntityType<?> et = BuiltInRegistries.ENTITY_TYPE.getOptional(eid).orElse(null);
				Component label = et != null ? et.getDescription() : Component.literal(n);
				addLootEntries(out, seen, "entity:" + eid,
						Component.translatable("rbei.loot.dropped_by", label),
						item(eid.getNamespace() + ":" + eid.getPath() + "_spawn_egg")
								.map(Item::getDefaultInstance).orElse(new ItemStack(Items.BONE)),
						new ItemStack(Items.ROTTEN_FLESH),
						List.of(Component.translatable("rbei.info.source", label),
								Component.translatable("rbei.info.mod",
										fr.max.rbei.ui.InfoFormat.modName(eid.getNamespace()))),
						names, null);
				continue;
			}

			// Prefer live table (mods may modify); keep drop chances when available
			List<LiveLootWalker.Drop> liveDrops = LiveLootWalker.collect(server, tableId);
			Map<String, Float> chances = new HashMap<>();
			Set<String> names = new LinkedHashSet<>();
			if (!liveDrops.isEmpty()) {
				for (LiveLootWalker.Drop d : liveDrops) {
					String id = BuiltInRegistries.ITEM.getKey(d.item()).toString();
					names.add(id);
					if (d.chance() != null) chances.put(id, d.chance());
				}
			} else {
				names = resolveLootItems(rm, tableId, new LinkedHashSet<>());
			}
			if (names.isEmpty()) continue;

			String ns = tableId.getNamespace();
			ItemStack source;
			ItemStack icon;
			Component kind;
			List<Component> baseInfo = new ArrayList<>();
			String own = null;
			String dedupePrefix;
			if (rel.startsWith("blocks/")) {
				String n = rel.substring("blocks/".length());
				source = item(ns + ":" + n).map(Item::getDefaultInstance).orElse(new ItemStack(Items.BARRIER));
				icon = new ItemStack(Items.DIAMOND_PICKAXE);
				kind = Component.translatable("rbei.loot.block");
				own = ns + ":" + n;
				dedupePrefix = "block:" + own;
				baseInfo.add(Component.translatable("rbei.info.source", source.getHoverName().getString()));
				baseInfo.add(Component.translatable("rbei.info.mod", fr.max.rbei.ui.InfoFormat.modName(ns)));
			} else if (rel.startsWith("chests/") || rel.startsWith("chest/")) {
				source = new ItemStack(Items.CHEST);
				icon = new ItemStack(Items.CHEST);
				kind = Component.translatable("rbei.loot.chest");
				String chestName = fr.max.rbei.ui.InfoFormat.humanizePath(rel.substring(rel.indexOf('/') + 1));
				baseInfo.add(Component.translatable("rbei.info.source", chestName));
				baseInfo.add(Component.translatable("rbei.info.mod", fr.max.rbei.ui.InfoFormat.modName(ns)));
				dedupePrefix = "chest:" + tableId;
			} else {
				source = new ItemStack(rel.startsWith("archaeology/") ? Items.BRUSH : Items.FISHING_ROD);
				icon = source.copy();
				kind = Component.translatable("rbei.loot.other");
				baseInfo.add(Component.translatable("rbei.info.source", fr.max.rbei.ui.InfoFormat.humanizePath(rel)));
				baseInfo.add(Component.translatable("rbei.info.mod", fr.max.rbei.ui.InfoFormat.modName(ns)));
				dedupePrefix = "other:" + tableId;
			}
			addLootEntries(out, seen, dedupePrefix, kind, source, icon, baseInfo, names, own, chances);
		}
		return out;
	}

	/** Accepte `loot_table/entities/x.json` OU `entities/x.json` selon le ResourceManager. */
	private static String lootRelPath(String path) {
		if (path == null || !path.endsWith(".json")) return null;
		String p = path;
		if (p.startsWith("loot_table/")) p = p.substring("loot_table/".length());
		if (p.endsWith(".json")) p = p.substring(0, p.length() - ".json".length());
		return p.isEmpty() ? null : p;
	}

	private static void addEntityLiveDrops(List<RecipeIndex.Entry> out, Set<String> seen,
			EntityType<?> type, Identifier eid, List<LiveLootWalker.Drop> drops) {
		Component label = type.getDescription();
		ItemStack source = item(eid.getNamespace() + ":" + eid.getPath() + "_spawn_egg")
				.map(Item::getDefaultInstance)
				.orElse(new ItemStack(Items.BONE));
		ItemStack icon = new ItemStack(Items.ROTTEN_FLESH);
		Component kind = Component.translatable("rbei.loot.dropped_by", label);
		for (LiveLootWalker.Drop drop : drops) {
			if (!SurvivalFilter.isSurvivalItem(drop.item())) continue;
			String itemId = BuiltInRegistries.ITEM.getKey(drop.item()).toString();
			String key = "entity:" + eid + "|" + itemId;
			if (!seen.add(key)) continue;
			List<Component> info = new ArrayList<>();
			info.add(Component.translatable("rbei.info.source", label));
			info.add(Component.translatable("rbei.info.mod",
					fr.max.rbei.ui.InfoFormat.modName(eid.getNamespace())));
			if (drop.chance() != null) {
				info.add(Component.translatable("rbei.info.chance",
						Math.max(1, Math.round(drop.chance() * 100))));
			}
			out.add(new RecipeIndex.Entry(kind, 1, 1,
					List.of(new RecipeIndex.Cell(0, 0, List.of(source))),
					List.of(drop.item().getDefaultInstance()), List.of(), false, false,
					icon, info, null));
		}
	}

	private static void addLootEntries(List<RecipeIndex.Entry> out, Set<String> seen, String dedupePrefix,
			Component kind, ItemStack source, ItemStack icon, List<Component> baseInfo,
			Set<String> names, String skipSelf, Map<String, Float> chances) {
		for (String name : names) {
			if (skipSelf != null && name.equals(skipSelf)) continue;
			Optional<Item> drop = item(name);
			if (drop.isEmpty() || drop.get() == Items.AIR) continue;
			if (!SurvivalFilter.isSurvivalItem(drop.get())) continue;
			String key = dedupePrefix + "|" + name;
			if (!seen.add(key)) continue;
			List<Component> info = new ArrayList<>(baseInfo);
			Float chance = chances == null ? null : chances.get(name);
			if (chance != null) {
				info.add(Component.translatable("rbei.info.chance", Math.max(1, Math.round(chance * 100))));
			}
			out.add(new RecipeIndex.Entry(kind, 1, 1,
					List.of(new RecipeIndex.Cell(0, 0, List.of(source))),
					List.of(drop.get().getDefaultInstance()), List.of(), false, false,
					icon, info, null));
		}
	}

	private static void addLootEntries(List<RecipeIndex.Entry> out, Set<String> seen, String dedupePrefix,
			Component kind, ItemStack source, ItemStack icon, List<Component> info,
			Set<String> names, String skipSelf) {
		addLootEntries(out, seen, dedupePrefix, kind, source, icon, info, names, skipSelf, Map.of());
	}

	/**
	 * Résout récursivement une loot table (items, tags, tables imbriquées) — mods inclus.
	 */
	private static Set<String> resolveLootItems(ResourceManager rm, Identifier tableId, Set<Identifier> visiting) {
		Set<String> names = new LinkedHashSet<>();
		if (tableId == null || !visiting.add(tableId)) return names;
		Identifier file = Identifier.fromNamespaceAndPath(tableId.getNamespace(),
				"loot_table/" + tableId.getPath() + ".json");
		Optional<Resource> res = rm.getResource(file);
		if (res.isEmpty()) return names;
		JsonObject json = readJson(res.get());
		if (json == null) return names;
		collectFromLootJson(rm, json, names, visiting);
		return names;
	}

	private static void collectFromLootJson(ResourceManager rm, JsonObject json, Set<String> names,
			Set<Identifier> visiting) {
		if (json.has("pools") && json.get("pools").isJsonArray()) {
			for (JsonElement pool : json.getAsJsonArray("pools")) {
				if (!pool.isJsonObject()) continue;
				JsonObject p = pool.getAsJsonObject();
				if (p.has("entries") && p.get("entries").isJsonArray()) {
					walkEntries(rm, p.getAsJsonArray("entries"), names, visiting);
				}
			}
		}
		// Certaines tables mods n'ont qu'un champ "entries" racine
		if (json.has("entries") && json.get("entries").isJsonArray()) {
			walkEntries(rm, json.getAsJsonArray("entries"), names, visiting);
		}
	}

	private static void walkEntries(ResourceManager rm, JsonArray entries, Set<String> names,
			Set<Identifier> visiting) {
		for (JsonElement el : entries) {
			if (!el.isJsonObject()) continue;
			JsonObject e = el.getAsJsonObject();
			String type = stringField(e, "type");
			if (type.isEmpty()) type = "minecraft:item";

			if (type.equals("minecraft:item") || type.endsWith(":item")) {
				String name = stringField(e, "name");
				if (name.isEmpty()) name = stringField(e, "id");
				if (!name.isEmpty()) names.add(name);
			} else if (type.equals("minecraft:tag") || type.endsWith(":tag")) {
				String tag = stringField(e, "name");
				if (tag.isEmpty()) tag = stringField(e, "id");
				expandItemTag(tag, names);
			} else if (type.equals("minecraft:loot_table") || type.endsWith(":loot_table")
					|| type.equals("minecraft:reference") || type.endsWith(":reference")) {
				String ref = stringField(e, "value");
				if (ref.isEmpty()) ref = stringField(e, "name");
				if (ref.isEmpty()) ref = stringField(e, "id");
				Identifier nested = Identifier.tryParse(ref);
				if (nested != null) names.addAll(resolveLootItems(rm, nested, visiting));
			} else if (type.equals("minecraft:alternatives") || type.equals("minecraft:group")
					|| type.equals("minecraft:sequence") || type.endsWith(":alternatives")
					|| type.endsWith(":group") || type.endsWith(":sequence")) {
				if (e.has("children") && e.get("children").isJsonArray()) {
					walkEntries(rm, e.getAsJsonArray("children"), names, visiting);
				}
			}

			if (e.has("children") && e.get("children").isJsonArray()
					&& !(type.equals("minecraft:alternatives") || type.equals("minecraft:group")
					|| type.equals("minecraft:sequence"))) {
				walkEntries(rm, e.getAsJsonArray("children"), names, visiting);
			}
		}
	}

	private static void expandItemTag(String tagId, Set<String> names) {
		Identifier id = Identifier.tryParse(tagId);
		if (id == null) return;
		TagKey<Item> key = TagKey.create(net.minecraft.core.registries.Registries.ITEM, id);
		try {
			for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(key)) {
				names.add(BuiltInRegistries.ITEM.getKey(holder.value()).toString());
			}
		} catch (RuntimeException ignored) {}
	}

	private static String stringField(JsonObject o, String field) {
		if (!o.has(field) || !o.get(field).isJsonPrimitive()) return "";
		return o.get(field).getAsString();
	}

	// ------------------------------------------------------------------ minerais

	public static List<RecipeIndex.Entry> ores(Minecraft mc) {
		List<RecipeIndex.Entry> out = new ArrayList<>();
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null) return out;
		ResourceManager rm = server.getResourceManager();

		Map<Identifier, Resource> placed = rm.listResources("worldgen/placed_feature",
				id -> id.getPath().contains("ore") && id.getPath().endsWith(".json"));
		for (Map.Entry<Identifier, Resource> f : placed.entrySet()) {
			JsonObject p = readJson(f.getValue());
			if (p == null || !p.has("feature") || !p.get("feature").isJsonPrimitive()) continue;
			Identifier fid = Identifier.tryParse(p.get("feature").getAsString());
			if (fid == null) continue;

			Optional<Resource> cres = rm.getResource(Identifier.fromNamespaceAndPath(
					fid.getNamespace(), "worldgen/configured_feature/" + fid.getPath() + ".json"));
			if (cres.isEmpty()) continue;
			JsonObject cf = readJson(cres.get());
			if (cf == null) continue;
			String type = cf.has("type") && cf.get("type").isJsonPrimitive() ? cf.get("type").getAsString() : "";
			if (!type.endsWith("ore") || !cf.has("config") || !cf.get("config").isJsonObject()) continue;
			JsonObject cfg = cf.getAsJsonObject("config");

			int size = cfg.has("size") && cfg.get("size").isJsonPrimitive() ? cfg.get("size").getAsInt() : 0;
			Set<String> blocks = new LinkedHashSet<>();
			if (cfg.has("targets") && cfg.get("targets").isJsonArray()) {
				for (JsonElement t : cfg.getAsJsonArray("targets")) {
					if (!t.isJsonObject() || !t.getAsJsonObject().has("state")) continue;
					JsonElement st = t.getAsJsonObject().get("state");
					if (st.isJsonObject() && st.getAsJsonObject().has("Name")) {
						blocks.add(st.getAsJsonObject().get("Name").getAsString());
					}
				}
			}
			if (blocks.isEmpty()) continue;

			boolean nether = fid.getPath().contains("nether") || fid.getPath().contains("debris");
			int dimMin = nether ? 0 : -64;
			int dimMax = nether ? 127 : 319;

			int minY = NONE, maxY = NONE, count = 0;
			if (p.has("placement") && p.get("placement").isJsonArray()) {
				for (JsonElement pe : p.getAsJsonArray("placement")) {
					if (!pe.isJsonObject()) continue;
					JsonObject m = pe.getAsJsonObject();
					String mt = m.has("type") && m.get("type").isJsonPrimitive() ? m.get("type").getAsString() : "";
					if (mt.equals("minecraft:count") && m.has("count") && m.get("count").isJsonPrimitive()) {
						count = m.get("count").getAsInt();
					}
					if (mt.equals("minecraft:height_range") && m.has("height") && m.get("height").isJsonObject()) {
						JsonObject h = m.getAsJsonObject("height");
						minY = resolveY(h.get("min_inclusive"), dimMin, dimMax);
						maxY = resolveY(h.get("max_inclusive"), dimMin, dimMax);
					}
				}
			}
			if (minY == NONE || maxY == NONE) continue;
			minY = Math.max(minY, dimMin);
			maxY = Math.min(maxY, dimMax);

			for (String block : blocks) {
				Optional<Item> item = item(block);
				if (item.isEmpty()) continue;
				Best best = BEST.get(block);
				List<Component> info = new ArrayList<>();
				if (best != null) {
					Component y = best.min() == best.max()
							? Component.translatable("rbei.ore.best", best.min())
							: Component.translatable("rbei.ore.best_range", best.min(), best.max());
					info.add(y);
					info.add(Component.translatable("rbei.info.zone",
							Component.translatable(best.zoneKey()).getString()));
				}
				info.add(Component.translatable("rbei.info.dim",
						Component.translatable(nether ? "rbei.ore.dim.nether" : "rbei.ore.dim.overworld").getString()));
				info.add(count > 0
						? Component.translatable("rbei.ore.size_count", size, count)
						: Component.translatable("rbei.ore.size", size));
				out.add(new RecipeIndex.Entry(Component.translatable("rbei.ore.kind"), 1, 1, List.of(),
						List.of(item.get().getDefaultInstance()), List.of(), false, false,
						new ItemStack(nether ? Items.NETHERRACK : Items.GRASS_BLOCK), info,
						new RecipeIndex.OreInfo(minY, maxY, dimMin, dimMax, size, count,
								best != null ? best.min() : NONE, best != null ? best.max() : NONE)));
			}
		}
		return out;
	}

	/** Hauteur JSON : {"absolute": n}, {"above_bottom": n} ou {"below_top": n}. */
	private static int resolveY(JsonElement v, int bottom, int top) {
		if (v == null || !v.isJsonObject()) return NONE;
		JsonObject o = v.getAsJsonObject();
		if (o.has("absolute")) return o.get("absolute").getAsInt();
		if (o.has("above_bottom")) return bottom + o.get("above_bottom").getAsInt();
		if (o.has("below_top")) return top - o.get("below_top").getAsInt();
		return NONE;
	}

	// ------------------------------------------------------------------ outils

	private static Optional<Item> item(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(key);
	}

	private static JsonObject readJson(Resource resource) {
		try (BufferedReader reader = resource.openAsReader()) {
			JsonElement el = JsonParser.parseReader(reader);
			return el.isJsonObject() ? el.getAsJsonObject() : null;
		} catch (IOException | RuntimeException ex) {
			return null;
		}
	}
}
