package fr.max.rbei.recipe;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Fluide normalisé (id + quantité). Les mods utilisent des unités différentes ;
 * {@code unit} conserve le libellé d'origine (mB, buckets, droplets...).
 *
 * // [SPEC] Objectif : représenter les fluides sans dépendre d'une API de fluide précise.
 * // [WHY] Minecraft 26.2 n'a pas d'API fluide vanilla unifiée pour les recettes.
 * // [TRADE-OFF] Pas de rendu 3D du fluide — seulement id/quantité/libellé pour l'UI.
 */
public record RecipeFluid(Identifier id, long amount, String unit, Component label) {

	public RecipeFluid(Identifier id, long amount) {
		this(id, amount, "mB", Component.literal(id.toString()));
	}

	public Component describe() {
		return Component.literal(amount + " " + unit + " ").append(label);
	}
}
