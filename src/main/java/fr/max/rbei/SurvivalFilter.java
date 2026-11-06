package fr.max.rbei;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Objets obtenables hors mode opérateur / créatif pur.
 * Exclut l'onglet OP, les œufs d'apparition et les blocs debug.
 */
public final class SurvivalFilter {
	private static final Set<String> CREATIVE_ONLY = Set.of(
			"minecraft:barrier",
			"minecraft:light",
			"minecraft:structure_void",
			"minecraft:structure_block",
			"minecraft:jigsaw",
			"minecraft:command_block",
			"minecraft:chain_command_block",
			"minecraft:repeating_command_block",
			"minecraft:command_block_minecart",
			"minecraft:debug_stick",
			"minecraft:knowledge_book",
			"minecraft:spawner",
			"minecraft:trial_spawner",
			"minecraft:bedrock"
	);

	private static Set<Item> cache;
	private static Object cacheKey;

	private SurvivalFilter() {}

	public static void invalidate() {
		cache = null;
		cacheKey = null;
	}

	public static boolean isSurvivalItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		return isSurvivalItem(stack.getItem());
	}

	public static boolean isSurvivalItem(Item item) {
		if (item == null || item == Items.AIR) return false;
		Set<Item> allowed = current();
		if (allowed != null && !allowed.isEmpty()) return allowed.contains(item);
		return !isCreativeOnlyId(BuiltInRegistries.ITEM.getKey(item));
	}

	/** Ensemble des items des onglets créatifs (sans permissions OP, sans œufs). */
	public static Set<Item> current() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.level == null || mc.player == null) return cache;
		Object key = System.identityHashCode(mc.level) + ":" + System.identityHashCode(mc.player.connection);
		if (cache != null && key.equals(cacheKey)) return cache;

		Set<Item> set = new HashSet<>();
		try {
			CreativeModeTabs.tryRebuildTabContents(
					mc.player.connection.enabledFeatures(), false, mc.level.registryAccess());
			for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
				if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
				if (!tab.shouldDisplay() || !tab.hasAnyItems()) continue;
				ItemStack icon = tab.getIconItem();
				if (isSpawnEgg(icon.getItem()) || icon.is(Items.BARRIER) && isLikelyOpTab(tab)) continue;
				for (ItemStack s : tab.getDisplayItems()) {
					if (!isCreativeOnlyId(BuiltInRegistries.ITEM.getKey(s.getItem()))
							&& !isSpawnEgg(s.getItem())) {
						set.add(s.getItem());
					}
				}
			}
		} catch (RuntimeException ex) {
			for (Item item : BuiltInRegistries.ITEM) {
				if (item != Items.AIR && !isCreativeOnlyId(BuiltInRegistries.ITEM.getKey(item))
						&& !isSpawnEgg(item)) {
					set.add(item);
				}
			}
		}
		cache = set;
		cacheKey = key;
		return set;
	}

	private static boolean isLikelyOpTab(CreativeModeTab tab) {
		// L'onglet opérateurs a souvent très peu d'items "normaux" ; on le détecte via l'icône barrier
		// déjà filtrée. Garde-fou : nom traduit contenant "Operator" / "Opérateur" peu fiable.
		return tab.getIconItem().is(Items.BARRIER);
	}

	public static boolean isSpawnEgg(Item item) {
		if (item == null) return false;
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		return id.getPath().endsWith("_spawn_egg");
	}

	public static boolean isCreativeOnlyId(Identifier id) {
		if (id == null) return true;
		if (CREATIVE_ONLY.contains(id.toString())) return true;
		return id.getPath().endsWith("_spawn_egg");
	}
}
