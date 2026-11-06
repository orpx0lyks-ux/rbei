package fr.max.rbei.recipe.source;

import java.util.ArrayList;
import java.util.List;

import fr.max.rbei.recipe.RecipeDetails;
import fr.max.rbei.recipe.RecipeSlot;
import fr.max.rbei.recipe.RecipeSource;
import fr.max.rbei.recipe.UniversalRecipe;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.ComposterBlock;

/**
 * Composteur — transformation vanilla hors RecipeManager (COMPOSTABLES → bone meal).
 */
public final class ComposterSource implements RecipeSource {

	@Override
	public String id() {
		return "rbei:composter";
	}

	@Override
	public List<UniversalRecipe> collect(Minecraft mc) {
		List<UniversalRecipe> out = new ArrayList<>();
		ItemStack station = new ItemStack(Items.COMPOSTER);
		ItemStack boneMeal = new ItemStack(Items.BONE_MEAL);
		Object2FloatMap<ItemLike> map = ComposterBlock.COMPOSTABLES;
		if (map == null || map.isEmpty()) return out;

		for (Object2FloatMap.Entry<ItemLike> e : map.object2FloatEntrySet()) {
			ItemLike like = e.getKey();
			if (like == null) continue;
			Item item = like.asItem();
			if (item == null || item == Items.AIR) continue;
			float chance = e.getFloatValue();
			Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
			out.add(UniversalRecipe.builder("composter:" + itemId)
					.id(Identifier.fromNamespaceAndPath("minecraft", "composter/" + itemId.getPath()))
					.sourceId("rbei:composter")
					.kind(station.getHoverName())
					.stations(List.of(station))
					.grid(1, 1)
					.input(new RecipeSlot(0, 0, List.of(item.getDefaultInstance())))
					.output(new RecipeSlot(0, 0, List.of(boneMeal), 1, chance))
					.info(List.of(Component.translatable("rbei.meta.compost_chance",
							(int) (chance * 100))))
					.details(RecipeDetails.builder()
							.extra("compost_chance", Float.toString(chance))
							.build())
					.build());
		}
		return out;
	}
}
