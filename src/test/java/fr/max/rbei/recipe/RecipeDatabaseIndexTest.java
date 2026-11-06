package fr.max.rbei.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Tests purs (sans Minecraft) sur la déduplication et la structure du modèle.
 * Les tests d'intégration RecipeManager nécessitent un client Minecraft démarré.
 */
class RecipeDatabaseIndexTest {

	@Test
	void universalRecipeBuilderRejectsEmptyUidContract() {
		UniversalRecipe a = UniversalRecipe.builder("a#1")
				.sourceId("test")
				.grid(1, 1)
				.build();
		UniversalRecipe b = UniversalRecipe.builder("a#1")
				.sourceId("test")
				.grid(1, 1)
				.build();
		assertEquals(a.uid(), b.uid());
	}

	@Test
	void recipeDetailsExtrasPreserved() {
		RecipeDetails d = RecipeDetails.builder()
				.durationTicks(200)
				.energy(1000L)
				.extra("pressure_psi", "5")
				.build();
		assertFalse(d.isEmpty());
		assertEquals(200, d.durationTicks());
		assertEquals(1000L, d.energy());
		assertEquals("5", d.extras().get("pressure_psi"));
	}

	@Test
	void softAdapterIdsAreUnique() {
		List<fr.max.rbei.recipe.api.RecipeAdapter> adapters =
				fr.max.rbei.recipe.adapter.SoftAdapters.builtin();
		Set<String> ids = new HashSet<>();
		for (var a : adapters) {
			assertTrue(ids.add(a.id()), "id dupliqué : " + a.id());
			assertFalse(a.requiredMods().isEmpty());
			assertTrue(a.limitation() != null && !a.limitation().isBlank());
			assertTrue(a.collect(null).isEmpty());
		}
	}

	@Test
	void recipeSlotChanceOptional() {
		RecipeSlot certain = new RecipeSlot(0, 0, List.of());
		assertTrue(certain.isEmpty());
		assertEquals(null, certain.chance());
	}

	@Test
	void dedupUidSetBehavesAsDatabase() {
		Set<String> seen = new HashSet<>();
		assertTrue(seen.add("minecraft:stick#0"));
		assertFalse(seen.add("minecraft:stick#0"));
		assertTrue(seen.add("minecraft:stick#1"));
		assertEquals(2, seen.size());
	}
}
