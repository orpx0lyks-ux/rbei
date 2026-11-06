package fr.max.rbei;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.datafixers.util.Either;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.CompositeEntryBase;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.entries.TagEntry;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;

/**
 * Lit les {@link LootTable} déjà chargées (après Fabric {@code LootTableEvents.MODIFY}).
 * Nécessaire pour les mods qui injectent des drops en code (ex. WardenGear → warden).
 */
public final class LiveLootWalker {
	private static final Map<ResourceKey<LootTable>, List<Drop>> CACHE = new HashMap<>();

	private LiveLootWalker() {}

	record Drop(Item item, Float chance) {}

	public static void invalidate() {
		CACHE.clear();
	}

	public static List<Drop> collect(MinecraftServer server, ResourceKey<LootTable> key) {
		if (server == null || key == null) return List.of();
		List<Drop> cached = CACHE.get(key);
		if (cached != null) return cached;
		LootTable table = server.reloadableRegistries().getLootTable(key);
		if (table == null || table == LootTable.EMPTY) {
			CACHE.put(key, List.of());
			return List.of();
		}
		Set<String> seen = new LinkedHashSet<>();
		List<Drop> out = new ArrayList<>();
		walkTable(server, table, out, seen, null, new LinkedHashSet<>());
		List<Drop> frozen = List.copyOf(out);
		CACHE.put(key, frozen);
		return frozen;
	}

	public static List<Drop> collect(MinecraftServer server, Identifier tableId) {
		if (tableId == null) return List.of();
		return collect(server, ResourceKey.create(Registries.LOOT_TABLE, tableId));
	}

	@SuppressWarnings("unchecked")
	private static void walkTable(MinecraftServer server, LootTable table, List<Drop> out,
			Set<String> seen, Float inheritedChance, Set<ResourceKey<LootTable>> visiting) {
		if (table == null || table == LootTable.EMPTY) return;
		List<LootPool> pools = (List<LootPool>) readField(table, "pools");
		if (pools == null) return;
		for (LootPool pool : pools) {
			if (pool == null) continue;
			Float poolChance = mergeChance(inheritedChance, chanceOf(pool.conditions));
			int totalWeight = 0;
			for (LootPoolEntryContainer entry : pool.entries) {
				totalWeight += Math.max(1, weightOf(entry));
			}
			for (LootPoolEntryContainer entry : pool.entries) {
				Float weightShare = totalWeight > 0
						? weightOf(entry) / (float) totalWeight
						: null;
				Float entryChance = mergeChance(poolChance, chanceOf(conditionsOf(entry)));
				// Prefer explicit random_chance; otherwise approximate via pool weight share
				Float chance = entryChance != null ? entryChance
						: (weightShare != null && pool.entries.size() > 1
								? mergeChance(poolChance, weightShare) : poolChance);
				walkEntry(server, entry, out, seen, chance, visiting);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static void walkEntry(MinecraftServer server, LootPoolEntryContainer entry, List<Drop> out,
			Set<String> seen, Float chance, Set<ResourceKey<LootTable>> visiting) {
		if (entry == null) return;

		if (entry instanceof LootItem lootItem) {
			Holder<Item> holder = (Holder<Item>) readField(lootItem, "item");
			if (holder != null) add(out, seen, holder.value(), chance);
			return;
		}

		if (entry instanceof TagEntry tagEntry) {
			TagKey<Item> tag = (TagKey<Item>) readField(tagEntry, "tag");
			if (tag != null) {
				for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
					add(out, seen, h.value(), chance);
				}
			}
			return;
		}

		if (entry instanceof NestedLootTable nested) {
			Either<ResourceKey<LootTable>, LootTable> contents =
					(Either<ResourceKey<LootTable>, LootTable>) readField(nested, "contents");
			if (contents == null) return;
			contents.ifLeft(key -> {
				if (!visiting.add(key)) return;
				LootTable ref = server.reloadableRegistries().getLootTable(key);
				walkTable(server, ref, out, seen, chance, visiting);
			});
			contents.ifRight(inline -> walkTable(server, inline, out, seen, chance, visiting));
			return;
		}

		if (entry instanceof CompositeEntryBase composite) {
			List<LootPoolEntryContainer> children =
					(List<LootPoolEntryContainer>) readField(composite, "children");
			if (children != null) {
				int total = 0;
				for (LootPoolEntryContainer child : children) total += Math.max(1, weightOf(child));
				for (LootPoolEntryContainer child : children) {
					Float share = total > 0 ? weightOf(child) / (float) total : null;
					walkEntry(server, child, out, seen, mergeChance(chance, share), visiting);
				}
			}
		}
	}

	private static void add(List<Drop> out, Set<String> seen, Item item, Float chance) {
		if (item == null || item == Items.AIR) return;
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		if (!seen.add(id)) return;
		out.add(new Drop(item, chance));
	}

	@SuppressWarnings("unchecked")
	private static List<LootItemCondition> conditionsOf(LootPoolEntryContainer entry) {
		Object c = readField(entry, "conditions");
		return c instanceof List<?> list ? (List<LootItemCondition>) list : null;
	}

	private static int weightOf(LootPoolEntryContainer entry) {
		Object w = readField(entry, "weight");
		if (w instanceof Integer i) return Math.max(1, i);
		if (w instanceof Number n) return Math.max(1, n.intValue());
		return 1;
	}

	private static Float chanceOf(List<LootItemCondition> conditions) {
		if (conditions == null) return null;
		Float found = null;
		for (LootItemCondition c : conditions) {
			if (c instanceof LootItemRandomChanceCondition rc) {
				NumberProvider np = rc.chance();
				if (np instanceof ConstantValue cv) {
					found = mergeChance(found, cv.value());
				}
			}
			// random_chance_with_looting — base chance field often named "chance"
			Float reflected = reflectChance(c);
			if (reflected != null) found = mergeChance(found, reflected);
		}
		return found;
	}

	private static Float reflectChance(LootItemCondition c) {
		if (c instanceof LootItemRandomChanceCondition) return null; // already handled
		Object np = readField(c, "chance");
		if (np instanceof ConstantValue cv) return cv.value();
		if (np instanceof Number n) return n.floatValue();
		return null;
	}

	private static Float mergeChance(Float a, Float b) {
		if (a == null) return b;
		if (b == null) return a;
		return a * b;
	}

	private static Object readField(Object obj, String name) {
		Class<?> c = obj.getClass();
		while (c != null && c != Object.class) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(obj);
			} catch (NoSuchFieldException e) {
				c = c.getSuperclass();
			} catch (ReflectiveOperationException e) {
				return null;
			}
		}
		return null;
	}
}
