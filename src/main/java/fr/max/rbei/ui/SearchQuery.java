package fr.max.rbei.ui;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Recherche : texte, @mod, #tag. */
public final class SearchQuery {
	private SearchQuery() {}

	public static boolean matches(ItemStack stack, String rawQuery, String cachedName) {
		if (stack == null || stack.isEmpty()) return false;
		String q = rawQuery.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) return true;
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (q.startsWith("@")) {
			return id.getNamespace().contains(q.substring(1));
		}
		if (q.startsWith("#")) {
			return matchesTag(stack.getItem(), q.substring(1));
		}
		String name = cachedName != null ? cachedName
				: stack.getHoverName().getString().toLowerCase(Locale.ROOT);
		return name.contains(q) || id.getPath().contains(q) || id.toString().contains(q);
	}

	private static boolean matchesTag(Item item, String tagQuery) {
		if (tagQuery.isEmpty()) return false;
		String q = tagQuery.toLowerCase(Locale.ROOT);
		Holder.Reference<Item> holder = item.builtInRegistryHolder();

		AtomicBoolean hit = new AtomicBoolean(false);
		BuiltInRegistries.ITEM.getTags().forEach(named -> {
			if (hit.get()) return;
			Identifier loc = named.key().location();
			String path = loc.getPath();
			String full = loc.toString();
			if (!(path.contains(q) || full.contains(q) || path.equals(q) || full.equals(q))) return;
			if (named.contains(holder)) hit.set(true);
		});
		if (hit.get()) return true;

		try {
			Identifier tid = q.contains(":")
					? Identifier.parse(q)
					: Identifier.fromNamespaceAndPath("minecraft", q);
			TagKey<Item> key = TagKey.create(Registries.ITEM, tid);
			return BuiltInRegistries.ITEM.get(key)
					.map(set -> set.contains(holder))
					.orElse(false);
		} catch (RuntimeException ignored) {
			return false;
		}
	}
}
