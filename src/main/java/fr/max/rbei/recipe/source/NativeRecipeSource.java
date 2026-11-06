package fr.max.rbei.recipe.source;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fr.max.rbei.recipe.DisplayConverter;
import fr.max.rbei.recipe.RecipeSource;
import fr.max.rbei.recipe.ReflectiveHarvester;
import fr.max.rbei.recipe.UniversalRecipe;
import net.fabricmc.fabric.api.recipe.v1.FabricRecipeManager;
import net.fabricmc.fabric.api.recipe.v1.sync.SynchronizedRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * Niveau 1 — toutes les recettes du RecipeManager (vanilla + mods via RecipeType),
 * sync Fabric, puis livre de recettes en dernier recours.
 */
public final class NativeRecipeSource implements RecipeSource {

	private List<String> pendingLimitations = List.of();

	@Override
	public String id() {
		return "rbei:native";
	}

	@Override
	public List<UniversalRecipe> collect(Minecraft mc) {
		List<UniversalRecipe> out = new ArrayList<>();
		List<String> limitations = new ArrayList<>();
		pendingLimitations = limitations;
		if (mc.level == null) return out;
		ContextMap ctx = SlotDisplayContext.fromLevel(mc.level);
		Set<String> seen = new HashSet<>();

		IntegratedServer server = mc.getSingleplayerServer();
		if (server != null) {
			ingestManager(server.getRecipeManager(), ctx, out, seen, limitations);
			return out;
		}

		Collection<RecipeHolder<?>> synced = synchronizedRecipes(mc);
		if (synced != null && !synced.isEmpty()) {
			for (RecipeHolder<?> holder : synced) {
				ingestHolder(holder, ctx, out, seen, limitations);
			}
			return out;
		}

		if (mc.player != null) {
			int i = 0;
			for (RecipeCollection col : mc.player.getRecipeBook().getCollections()) {
				for (RecipeDisplayEntry e : col.getRecipes()) {
					RecipeDisplay d = e.display();
					UniversalRecipe u = DisplayConverter.convert(d, ctx, null, i++);
					if (u != null) {
						if (seen.add(u.uid())) out.add(u);
					} else {
						UniversalRecipe auto = ReflectiveHarvester.fromUnknownDisplay(d, ctx, null, i, null);
						if (auto != null && seen.add(auto.uid())) out.add(auto);
						else addRecipeBookFallback(out, seen, d, ctx, i, limitations);
					}
				}
			}
		}
		return out;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void ingestManager(RecipeManager manager, ContextMap ctx, List<UniversalRecipe> out,
			Set<String> seen, List<String> limitations) {
		// Tous les RecipeType enregistrés (mods inclus)
		for (RecipeType<?> type : BuiltInRegistries.RECIPE_TYPE) {
			try {
				Collection<RecipeHolder<?>> holders = manager instanceof FabricRecipeManager fabric
						? (Collection) fabric.getAllOfType((RecipeType) type)
						: List.of();
				for (RecipeHolder<?> holder : holders) {
					ingestHolder(holder, ctx, out, seen, limitations);
				}
			} catch (Throwable t) {
				Identifier typeId = BuiltInRegistries.RECIPE_TYPE.getKey(type);
				limitations.add("RecipeType illisible : " + typeId);
			}
		}
		// Complément getRecipes()
		for (RecipeHolder<?> holder : manager.getRecipes()) {
			ingestHolder(holder, ctx, out, seen, limitations);
		}
	}

	private static void ingestHolder(RecipeHolder<?> holder, ContextMap ctx, List<UniversalRecipe> out,
			Set<String> seen, List<String> limitations) {
		String base = holder.id().identifier().toString();
		if (!seen.add(base)) return;
		List<UniversalRecipe> converted = DisplayConverter.fromHolder(holder, ctx, limitations);
		if (!converted.isEmpty()) {
			out.addAll(converted);
			return;
		}
		ReflectiveHarvester.fromRecipe(holder, ctx).ifPresent(out::add);
	}

	private static Collection<RecipeHolder<?>> synchronizedRecipes(Minecraft mc) {
		try {
			if (mc.getConnection() == null) return null;
			SynchronizedRecipes sync = mc.getConnection().recipes().getSynchronizedRecipes();
			if (sync == null) return null;
			return sync.recipes();
		} catch (Throwable t) {
			return null;
		}
	}

	private static void addRecipeBookFallback(List<UniversalRecipe> out, Set<String> seen, RecipeDisplay d,
			ContextMap ctx, int index, List<String> limitations) {
		List<ItemStack> outputs = DisplayConverter.resolve(d.result(), ctx);
		if (outputs.isEmpty()) {
			limitations.add("Display livre de recettes sans résultat (#" + index + ")");
			return;
		}
		String uid = "recipebook#" + index;
		if (!seen.add(uid)) return;
		out.add(UniversalRecipe.builder(uid)
				.sourceId("rbei:native-recipebook")
				.kind(Component.literal("?"))
				.stations(DisplayConverter.resolve(d.craftingStation(), ctx))
				.outputStacks(outputs)
				.build());
	}

	@Override
	public String limitation() {
		if (pendingLimitations == null || pendingLimitations.isEmpty()) return null;
		int n = pendingLimitations.size();
		if (n <= 3) return String.join(" | ", pendingLimitations);
		return n + " recettes/displays partiellement supportés (voir logs debug rbei/recipes)";
	}
}
