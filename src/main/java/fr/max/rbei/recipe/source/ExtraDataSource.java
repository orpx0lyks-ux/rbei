package fr.max.rbei.recipe.source;

import java.util.ArrayList;
import java.util.List;

import fr.max.rbei.ExtraSources;
import fr.max.rbei.RecipeIndex;
import fr.max.rbei.recipe.ItemKey;
import fr.max.rbei.recipe.RecipeSlot;
import fr.max.rbei.recipe.RecipeSource;
import fr.max.rbei.recipe.UniversalRecipe;
import net.minecraft.client.Minecraft;

/**
 * Pont vers ExtraSources existant (potions, butins, minerais) — conserve le comportement actuel.
 */
public final class ExtraDataSource implements RecipeSource {

	@Override
	public String id() {
		return "rbei:extra";
	}

	@Override
	public List<UniversalRecipe> collect(Minecraft mc) {
		List<UniversalRecipe> out = new ArrayList<>();
		List<RecipeIndex.Entry> entries = new ArrayList<>();
		try { entries.addAll(ExtraSources.brewing(mc)); } catch (RuntimeException ignored) {}
		try { entries.addAll(ExtraSources.loot(mc)); } catch (RuntimeException ignored) {}
		try { entries.addAll(ExtraSources.ores(mc)); } catch (RuntimeException ignored) {}

		for (RecipeIndex.Entry e : entries) {
			List<RecipeSlot> inputs = new ArrayList<>();
			for (RecipeIndex.Cell c : e.cells()) {
				inputs.add(new RecipeSlot(c.col(), c.row(), c.options()));
			}
			String uid = fingerprint(e);
			out.add(UniversalRecipe.builder(uid.isEmpty() ? "extra:anon" : uid)
					.sourceId("rbei:extra")
					.kind(e.kind())
					.icon(e.icon())
					.stations(e.stations())
					.grid(e.cols(), e.rows())
					.inputs(inputs)
					.outputStacks(e.outputs())
					.crafting(e.crafting())
					.shaped(e.shaped())
					.info(e.info())
					.ore(e.ore())
					.build());
		}
		return out;
	}

	/** Stable content id — ignores translated kind text. */
	private static String fingerprint(RecipeIndex.Entry e) {
		StringBuilder sb = new StringBuilder("fp:");
		if (!e.outputs().isEmpty()) sb.append(ItemKey.of(e.outputs().get(0)));
		sb.append('|');
		for (RecipeIndex.Cell c : e.cells()) {
			if (!c.options().isEmpty()) {
				sb.append(ItemKey.of(c.options().get(0))).append(',');
			}
		}
		sb.append('|');
		if (!e.stations().isEmpty()) sb.append(ItemKey.of(e.stations().get(0)));
		else if (e.icon() != null && !e.icon().isEmpty()) sb.append(ItemKey.of(e.icon()));
		if (e.ore() != null) {
			sb.append("|ore:").append(e.ore().minY()).append('-').append(e.ore().maxY())
					.append(':').append(e.ore().bestMin());
		}
		sb.append("|c").append(e.crafting() ? 1 : 0).append(e.shaped() ? 1 : 0);
		return sb.toString();
	}

	@Override
	public String limitation() {
		return "Drops: live loot tables (incl. LootTableEvents.MODIFY) + JSON. "
				+ "Singleplayer only. Anvil/Loom/Grindstone/Cartography not indexed.";
	}
}
