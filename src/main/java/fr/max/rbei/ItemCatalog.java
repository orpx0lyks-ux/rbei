package fr.max.rbei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Liste des objets, organisée comme les onglets de l'inventaire créatif (survie uniquement). */
final class ItemCatalog {
	private ItemCatalog() {}

	public record Tab(Component name, ItemStack icon, List<ItemStack> items) {}

	static List<Tab> build(Minecraft mc) {
		List<Tab> tabs = new ArrayList<>();
		List<ItemStack> all = new ArrayList<>();
		SurvivalFilter.invalidate();

		try {
			// hasPermissions=false → exclut les objets opérateurs
			CreativeModeTabs.tryRebuildTabContents(
					mc.player.connection.enabledFeatures(), false, mc.level.registryAccess());

			for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
				if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
				if (!tab.shouldDisplay() || !tab.hasAnyItems()) continue;
				ItemStack icon = tab.getIconItem();
				// Onglet œufs d'apparition / opérateurs : non obtenables en survie
				if (SurvivalFilter.isSpawnEgg(icon.getItem()) || icon.is(Items.BARRIER)) continue;

				List<ItemStack> list = new ArrayList<>();
				for (ItemStack s : tab.getDisplayItems()) {
					if (SurvivalFilter.isSurvivalItem(s)) list.add(s.copy());
				}
				if (!list.isEmpty()) tabs.add(new Tab(tab.getDisplayName(), icon, list));
			}
			for (ItemStack s : CreativeModeTabs.searchTab().getSearchTabDisplayItems()) {
				if (SurvivalFilter.isSurvivalItem(s)) all.add(s.copy());
			}
		} catch (RuntimeException ex) {
			// repli ci-dessous
		}

		if (all.isEmpty()) {
			for (Item item : BuiltInRegistries.ITEM) {
				if (SurvivalFilter.isSurvivalItem(item)) all.add(item.getDefaultInstance());
			}
		}
		tabs.add(0, new Tab(Component.translatable("rbei.tab.all"), new ItemStack(Items.COMPASS), all));
		return tabs;
	}
}
