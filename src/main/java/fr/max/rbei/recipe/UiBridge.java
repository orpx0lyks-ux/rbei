package fr.max.rbei.recipe;

import java.util.ArrayList;
import java.util.List;

import fr.max.rbei.RecipeIndex;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Pont vers le modèle d'affichage historique {@link RecipeIndex.Entry}.
 * Conserve le design actuel tout en exposant les nouvelles métadonnées.
 */
public final class UiBridge {
	private UiBridge() {}

	public static RecipeIndex.Entry toEntry(UniversalRecipe u) {
		List<RecipeIndex.Cell> cells = new ArrayList<>();
		for (RecipeSlot s : u.inputs()) {
			cells.add(new RecipeIndex.Cell(s.col(), s.row(), s.options()));
		}
		List<ItemStack> outputs = u.flatOutputs();
		List<Component> info = new ArrayList<>(u.info());
		appendDetails(info, u);

		RecipeIndex.OreInfo ore = u.ore() instanceof RecipeIndex.OreInfo o ? o : null;
		RecipeIndex.Details details = toDetails(u);

		return new RecipeIndex.Entry(
				u.kind(), u.cols(), u.rows(), cells, outputs, u.stations(),
				u.crafting(), u.shaped(), u.icon(), info, ore, details);
	}

	private static RecipeIndex.Details toDetails(UniversalRecipe u) {
		RecipeDetails d = u.details();
		List<Component> fluids = new ArrayList<>();
		for (RecipeFluid f : d.inputFluids()) fluids.add(Component.literal("↓ ").append(f.describe()));
		for (RecipeFluid f : d.outputFluids()) fluids.add(Component.literal("↑ ").append(f.describe()));
		// Prefer registry id; fall back to stable UniversalRecipe uid for extras/loot/ores
		String recipeId = u.id() != null ? u.id().toString() : u.uid();
		return new RecipeIndex.Details(
				recipeId,
				u.modId(),
				d.durationTicks(),
				d.energy(),
				d.experience(),
				fluids,
				d.conditions(),
				d.extras());
	}

	private static void appendDetails(List<Component> info, UniversalRecipe u) {
		RecipeDetails d = u.details();
		if (d.durationTicks() != null) {
			info.add(Component.translatable("rbei.info.time", d.durationTicks()));
		}
		if (d.experience() != null) {
			info.add(Component.translatable("rbei.info.xp", String.format(java.util.Locale.ROOT, "%.1f", d.experience())));
		}
		if (d.energy() != null) {
			info.add(Component.translatable("rbei.info.energy", d.energy()));
		}
		if (d.temperature() != null) {
			info.add(Component.translatable("rbei.info.temp", d.temperature()));
		}
		if (d.pressure() != null) {
			info.add(Component.translatable("rbei.info.pressure", d.pressure()));
		}
		for (RecipeFluid f : d.inputFluids()) {
			info.add(Component.translatable("rbei.meta.fluid_in").append(f.describe()));
		}
		for (RecipeFluid f : d.outputFluids()) {
			info.add(Component.translatable("rbei.meta.fluid_out").append(f.describe()));
		}
		for (Component c : d.conditions()) info.add(c);
		for (RecipeSlot s : u.outputs()) {
			if (s.chance() != null) {
				info.add(Component.translatable("rbei.info.chance", (int) (s.chance() * 100)));
			}
		}
		String mod = u.modId();
		if (mod != null && !mod.isEmpty() && !"rbei".equals(mod) && !"unknown".equals(mod)) {
			info.add(Component.translatable("rbei.info.mod", fr.max.rbei.ui.InfoFormat.modName(mod)));
		}
	}
}
