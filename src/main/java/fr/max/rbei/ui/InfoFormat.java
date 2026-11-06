package fr.max.rbei.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import fr.max.rbei.RecipeIndex;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;

/**
 * Turns messy recipe info into a few scannable lines (Source / Mod / Chance / …).
 */
public final class InfoFormat {
	private InfoFormat() {}

	public static String modName(String modId) {
		if (modId == null || modId.isEmpty()) return "";
		if ("minecraft".equals(modId)) return "Minecraft";
		Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(modId);
		if (mod.isPresent()) {
			return mod.get().getMetadata().getName();
		}
		return humanizeToken(modId);
	}

	/** mob/sealing_ghast → Sealing Ghast */
	public static String humanizePath(String path) {
		if (path == null || path.isEmpty()) return "";
		String leaf = path;
		int slash = leaf.lastIndexOf('/');
		if (slash >= 0) leaf = leaf.substring(slash + 1);
		return humanizeToken(leaf);
	}

	public static String humanizeToken(String raw) {
		if (raw == null || raw.isEmpty()) return "";
		String s = raw.replace('-', '_').replace('.', '_');
		StringBuilder out = new StringBuilder();
		for (String part : s.split("_")) {
			if (part.isEmpty()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) out.append(part.substring(1).toLowerCase(Locale.ROOT));
		}
		return out.toString();
	}

	/**
	 * Prefer structured details + cleaned info; drop raw paths / duplicate mod lines.
	 */
	public static List<Component> lines(RecipeIndex.Entry e) {
		List<Component> out = new ArrayList<>();
		LinkedHashSet<String> seen = new LinkedHashSet<>();

		RecipeIndex.OreInfo ore = e.ore();
		if (ore != null) {
			if (ore.bestMin() != Integer.MIN_VALUE) {
				if (ore.bestMin() == ore.bestMax()) {
					add(out, seen, Component.translatable("rbei.ore.best", ore.bestMin()));
				} else {
					add(out, seen, Component.translatable("rbei.ore.best_range", ore.bestMin(), ore.bestMax()));
				}
			}
			add(out, seen, Component.translatable("rbei.ore.range", ore.minY(), ore.maxY()));
			if (ore.count() > 0) {
				add(out, seen, Component.translatable("rbei.ore.size_count", ore.size(), ore.count()));
			} else if (ore.size() > 0) {
				add(out, seen, Component.translatable("rbei.ore.size", ore.size()));
			}
		}

		RecipeIndex.Details d = e.details();
		if (d != null && d != RecipeIndex.Details.EMPTY) {
			if (d.durationTicks() != null) {
				add(out, seen, Component.translatable("rbei.info.time", d.durationTicks()));
			}
			if (d.experience() != null) {
				add(out, seen, Component.translatable("rbei.info.xp", String.format(Locale.ROOT, "%.1f", d.experience())));
			}
			if (d.energy() != null) {
				add(out, seen, Component.translatable("rbei.info.energy", d.energy()));
			}
			for (Component f : d.fluids()) {
				add(out, seen, f);
			}
		}

		for (Component raw : e.info()) {
			String text = raw.getString().trim();
			if (text.isEmpty()) continue;
			if (looksLikeRawPath(text)) {
				add(out, seen, Component.translatable("rbei.info.source", humanizePath(text)));
				continue;
			}
			String mod = extractModId(text);
			if (mod != null) {
				add(out, seen, Component.translatable("rbei.info.mod", modName(mod)));
				continue;
			}
			// Skip redundant ore lines already covered by OreInfo
			if (ore != null && (text.toLowerCase(Locale.ROOT).startsWith("best")
					|| text.toLowerCase(Locale.ROOT).startsWith("y ")
					|| text.toLowerCase(Locale.ROOT).startsWith("vein"))) {
				continue;
			}
			add(out, seen, raw);
		}

		if (d != null && d.modId() != null && !d.modId().isEmpty()
				&& !"rbei".equals(d.modId()) && !"unknown".equals(d.modId())) {
			add(out, seen, Component.translatable("rbei.info.mod", modName(d.modId())));
		}

		if (out.size() > 5) return new ArrayList<>(out.subList(0, 5));
		return out;
	}

	private static void add(List<Component> out, LinkedHashSet<String> seen, Component line) {
		String key = line.getString().toLowerCase(Locale.ROOT);
		if (key.startsWith("mod:")) key = "mod";
		if (key.startsWith("source:")) key = "source";
		if (key.startsWith("chance:")) key = "chance";
		if (key.startsWith("best")) key = "best";
		if (key.startsWith("y ")) key = "range";
		if (!seen.add(key)) return;
		out.add(line);
	}

	private static boolean looksLikeRawPath(String text) {
		if (text.contains(" ") || text.contains(":")) return false;
		return text.contains("/") || text.matches("[a-z0-9_]+");
	}

	/** Only rewrite if the mod part still looks like a raw id (no spaces). */
	private static String extractModId(String text) {
		String lower = text.toLowerCase(Locale.ROOT);
		if (!lower.startsWith("mod:")) return null;
		String rest = text.substring(4).trim();
		int paren = rest.indexOf('(');
		if (paren > 0) rest = rest.substring(0, paren).trim();
		if (rest.isEmpty() || rest.contains(" ")) return null; // already display name
		return rest;
	}
}
