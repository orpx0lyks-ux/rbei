package fr.max.rbei.recipe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.network.chat.Component;

/**
 * Métadonnées optionnelles d'une recette (durée, énergie, fluides, conditions, extras bruts).
 * Tout ce qui ne rentre pas dans la grille d'affichage est conservé ici.
 *
 * // [SPEC] Objectif : ne rien perdre même si l'UI n'affiche qu'un sous-ensemble.
 */
public record RecipeDetails(
		Integer durationTicks,
		Float experience,
		Long energy,
		Float temperature,
		Float pressure,
		List<RecipeFluid> inputFluids,
		List<RecipeFluid> outputFluids,
		List<Component> conditions,
		Map<String, String> extras) {

	public static final RecipeDetails EMPTY = new RecipeDetails(
			null, null, null, null, null, List.of(), List.of(), List.of(), Map.of());

	public static Builder builder() {
		return new Builder();
	}

	public boolean isEmpty() {
		return durationTicks == null && experience == null && energy == null
				&& temperature == null && pressure == null
				&& inputFluids.isEmpty() && outputFluids.isEmpty()
				&& conditions.isEmpty() && extras.isEmpty();
	}

	public static final class Builder {
		private Integer durationTicks;
		private Float experience;
		private Long energy;
		private Float temperature;
		private Float pressure;
		private List<RecipeFluid> inputFluids = List.of();
		private List<RecipeFluid> outputFluids = List.of();
		private List<Component> conditions = List.of();
		private final Map<String, String> extras = new LinkedHashMap<>();

		public Builder durationTicks(Integer v) { this.durationTicks = v; return this; }
		public Builder experience(Float v) { this.experience = v; return this; }
		public Builder energy(Long v) { this.energy = v; return this; }
		public Builder temperature(Float v) { this.temperature = v; return this; }
		public Builder pressure(Float v) { this.pressure = v; return this; }
		public Builder inputFluids(List<RecipeFluid> v) { this.inputFluids = v == null ? List.of() : List.copyOf(v); return this; }
		public Builder outputFluids(List<RecipeFluid> v) { this.outputFluids = v == null ? List.of() : List.copyOf(v); return this; }
		public Builder conditions(List<Component> v) { this.conditions = v == null ? List.of() : List.copyOf(v); return this; }
		public Builder extra(String key, String value) {
			if (key != null && value != null) extras.put(key, value);
			return this;
		}

		public RecipeDetails build() {
			return new RecipeDetails(durationTicks, experience, energy, temperature, pressure,
					inputFluids, outputFluids, conditions, Map.copyOf(extras));
		}
	}
}
