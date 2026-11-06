package fr.max.rbei.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Config client persistée : thème, sons, favoris, récents.
 * // [SPEC] Pas de Cloth Config — JSON simple dans config/rbei-client.json.
 */
public final class RbeiConfig {
	private static final Logger LOG = LoggerFactory.getLogger("rbei/config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int MAX_RECENT = 24;
	private static final int MAX_FAVORITES = 256;

	private static RbeiConfig instance;

	private boolean darkTheme;
	private boolean uiSounds = true;
	private boolean craftableOverlay = true;
	private final LinkedHashSet<String> favorites = new LinkedHashSet<>();
	private final ArrayList<String> recent = new ArrayList<>();

	private RbeiConfig() {}

	public static RbeiConfig get() {
		if (instance == null) instance = load();
		return instance;
	}

	public static void reload() {
		instance = load();
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("rbei-client.json");
	}

	private static RbeiConfig load() {
		RbeiConfig cfg = new RbeiConfig();
		Path p = path();
		if (!Files.isRegularFile(p)) {
			cfg.save();
			return cfg;
		}
		try (Reader r = Files.newBufferedReader(p)) {
			JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
			cfg.darkTheme = root.has("darkTheme") && root.get("darkTheme").getAsBoolean();
			cfg.uiSounds = !root.has("uiSounds") || root.get("uiSounds").getAsBoolean();
			cfg.craftableOverlay = !root.has("craftableOverlay") || root.get("craftableOverlay").getAsBoolean();
			if (root.has("favorites") && root.get("favorites").isJsonArray()) {
				for (var e : root.getAsJsonArray("favorites")) {
					cfg.favorites.add(e.getAsString());
				}
			}
			if (root.has("recent") && root.get("recent").isJsonArray()) {
				for (var e : root.getAsJsonArray("recent")) {
					cfg.recent.add(e.getAsString());
				}
			}
		} catch (Exception ex) {
			LOG.warn("Failed reading rbei-client.json, reset", ex);
		}
		return cfg;
	}

	public synchronized void save() {
		JsonObject root = new JsonObject();
		root.addProperty("darkTheme", darkTheme);
		root.addProperty("uiSounds", uiSounds);
		root.addProperty("craftableOverlay", craftableOverlay);
		JsonArray fav = new JsonArray();
		for (String s : favorites) fav.add(s);
		root.add("favorites", fav);
		JsonArray rec = new JsonArray();
		for (String s : recent) rec.add(s);
		root.add("recent", rec);
		try {
			Files.createDirectories(path().getParent());
			try (Writer w = Files.newBufferedWriter(path())) {
				GSON.toJson(root, w);
			}
		} catch (IOException ex) {
			LOG.warn("Failed writing rbei-client.json", ex);
		}
	}

	public boolean darkTheme() { return darkTheme; }
	public void setDarkTheme(boolean v) { darkTheme = v; save(); }
	public void toggleTheme() { setDarkTheme(!darkTheme); }

	public boolean uiSounds() { return uiSounds; }
	public void setUiSounds(boolean v) { uiSounds = v; save(); }

	public boolean craftableOverlay() { return craftableOverlay; }
	public void setCraftableOverlay(boolean v) { craftableOverlay = v; save(); }

	public Set<String> favorites() { return favorites; }

	public boolean isFavorite(ItemStack stack) {
		return favorites.contains(idOf(stack));
	}

	public void toggleFavorite(ItemStack stack) {
		String id = idOf(stack);
		if (id.isEmpty()) return;
		if (!favorites.remove(id)) {
			if (favorites.size() >= MAX_FAVORITES) {
				var it = favorites.iterator();
				if (it.hasNext()) { it.next(); it.remove(); }
			}
			favorites.add(id);
		}
		save();
	}

	public void pushRecent(ItemStack stack) {
		String id = idOf(stack);
		if (id.isEmpty()) return;
		recent.remove(id);
		recent.add(0, id);
		while (recent.size() > MAX_RECENT) recent.remove(recent.size() - 1);
		save();
	}

	public List<ItemStack> favoriteStacks() {
		return resolve(favorites);
	}

	public List<ItemStack> recentStacks(int limit) {
		List<String> slice = recent.subList(0, Math.min(limit, recent.size()));
		return resolve(slice);
	}

	private static List<ItemStack> resolve(Iterable<String> ids) {
		List<ItemStack> out = new ArrayList<>();
		for (String id : ids) {
			ItemStack s = stackOf(id);
			if (!s.isEmpty()) out.add(s);
		}
		return out;
	}

	public static String idOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return "";
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	public static ItemStack stackOf(String id) {
		if (id == null || id.isEmpty()) return ItemStack.EMPTY;
		try {
			Identifier key = Identifier.parse(id);
			Item item = BuiltInRegistries.ITEM.getValue(key);
			if (item == null || item == Items.AIR) return ItemStack.EMPTY;
			return item.getDefaultInstance();
		} catch (RuntimeException ex) {
			return ItemStack.EMPTY;
		}
	}
}
