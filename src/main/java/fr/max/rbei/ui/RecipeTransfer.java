package fr.max.rbei.ui;

import java.util.ArrayList;
import java.util.List;

import fr.max.rbei.RecipeIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Transfert vers grille d'établi / inventaire / four (API ContainerInput 26.2).
 */
public final class RecipeTransfer {
	public enum Mode { NONE, CRAFTING, FURNACE }

	private RecipeTransfer() {}

	public static Mode modeOf(AbstractContainerScreen<?> parent) {
		if (parent == null) return Mode.NONE;
		AbstractContainerMenu menu = parent.getMenu();
		if (menu instanceof CraftingMenu || menu instanceof InventoryMenu) return Mode.CRAFTING;
		if (menu instanceof AbstractFurnaceMenu || parent instanceof AbstractFurnaceScreen<?>) {
			return Mode.FURNACE;
		}
		return Mode.NONE;
	}

	public static int craftGridSize(AbstractContainerScreen<?> parent) {
		if (parent == null) return 0;
		AbstractContainerMenu menu = parent.getMenu();
		if (menu instanceof CraftingMenu) return 3;
		if (menu instanceof InventoryMenu) return 2;
		return 0;
	}

	public static boolean canTransfer(AbstractContainerScreen<?> parent, RecipeIndex.Entry e) {
		Mode mode = modeOf(parent);
		if (mode == Mode.NONE) return false;
		if (mode == Mode.CRAFTING) {
			int g = craftGridSize(parent);
			if (g == 0 || !e.crafting()) return false;
			return e.shaped() ? e.cols() <= g && e.rows() <= g : e.cells().size() <= g * g;
		}
		return isFurnaceRecipe(e) && !e.cells().isEmpty();
	}

	public static boolean isFurnaceRecipe(RecipeIndex.Entry e) {
		for (ItemStack st : e.stations()) {
			if (st.is(Items.FURNACE) || st.is(Items.BLAST_FURNACE) || st.is(Items.SMOKER)) return true;
		}
		ItemStack icon = e.icon() != null ? e.icon()
				: (e.stations().isEmpty() ? ItemStack.EMPTY : e.stations().get(0));
		if (icon.isEmpty()) return false;
		String id = BuiltInRegistries.ITEM.getKey(icon.getItem()).toString();
		return id.contains("furnace") || id.contains("smoker") || id.contains("blast");
	}

	public static Component transfer(Minecraft mc, AbstractContainerScreen<?> parent,
			RecipeIndex.Entry e, boolean max) {
		Mode mode = modeOf(parent);
		if (mode == Mode.CRAFTING) return transferCrafting(mc, parent, e, max);
		if (mode == Mode.FURNACE) return transferFurnace(mc, parent, e);
		return Component.translatable("rbei.missing");
	}

	private static Component transferCrafting(Minecraft mc, AbstractContainerScreen<?> parent,
			RecipeIndex.Entry e, boolean max) {
		AbstractContainerMenu menu = parent.getMenu();
		int g = craftGridSize(parent);
		int gridEnd = g * g;
		var player = mc.player;
		var gameMode = mc.gameMode;

		List<Integer> targets = new ArrayList<>();
		List<List<ItemStack>> needs = new ArrayList<>();
		int seq = 0;
		for (RecipeIndex.Cell c : e.cells()) {
			int idx = e.shaped() ? c.row() * g + c.col() : seq;
			seq++;
			if (c.options().isEmpty()) continue;
			if (idx < 0 || idx >= gridEnd) continue;
			targets.add(1 + idx);
			needs.add(c.options());
		}
		if (targets.isEmpty()) return Component.translatable("rbei.missing");

		List<ItemStack> kinds = new ArrayList<>();
		List<Integer> totals = new ArrayList<>();
		for (Slot src : menu.slots) {
			if (src.container != player.getInventory() || src.getContainerSlot() >= 36) continue;
			ItemStack st = src.getItem();
			if (st.isEmpty()) continue;
			int k = indexOf(kinds, st);
			if (k < 0) {
				kinds.add(st.copyWithCount(1));
				totals.add(st.getCount());
			} else {
				totals.set(k, totals.get(k) + st.getCount());
			}
		}

		int[] pick = assign(needs, kinds, totals, true);
		int n = pick == null ? 0 : crafts(pick, kinds, totals);
		if (n < 1) {
			pick = assign(needs, kinds, totals, false);
			n = pick == null ? 0 : crafts(pick, kinds, totals);
		}
		if (n < 1) return Component.translatable("rbei.missing");
		if (!max) n = 1;

		for (int i = 1; i <= gridEnd; i++) {
			if (!menu.getSlot(i).getItem().isEmpty()) {
				gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, player);
			}
		}
		for (int i = 1; i <= gridEnd; i++) {
			if (!menu.getSlot(i).getItem().isEmpty()) {
				return Component.translatable("rbei.missing");
			}
		}

		for (int i = 0; i < targets.size(); i++) {
			ItemStack kind = kinds.get(pick[i]);
			int remaining = n;
			for (Slot src : menu.slots) {
				if (remaining <= 0) break;
				if (src.container != player.getInventory() || src.getContainerSlot() >= 36) continue;
				ItemStack have = src.getItem();
				if (have.isEmpty() || !ItemStack.isSameItem(have, kind)) continue;
				int count = have.getCount();
				int take = Math.min(remaining, count);
				gameMode.handleContainerInput(menu.containerId, src.index, 0, ContainerInput.PICKUP, player);
				if (take == count) {
					gameMode.handleContainerInput(menu.containerId, targets.get(i), 0, ContainerInput.PICKUP, player);
				} else {
					for (int t = 0; t < take; t++) {
						gameMode.handleContainerInput(menu.containerId, targets.get(i), 1, ContainerInput.PICKUP, player);
					}
					gameMode.handleContainerInput(menu.containerId, src.index, 0, ContainerInput.PICKUP, player);
				}
				remaining -= take;
			}
			if (remaining > 0) return Component.translatable("rbei.missing");
		}
		UiSounds.click();
		return null;
	}

	private static Component transferFurnace(Minecraft mc, AbstractContainerScreen<?> parent,
			RecipeIndex.Entry e) {
		AbstractContainerMenu menu = parent.getMenu();
		if (menu.slots.size() < 3) return Component.translatable("rbei.missing");
		var player = mc.player;
		var gameMode = mc.gameMode;

		List<ItemStack> options = e.cells().isEmpty() ? List.of() : e.cells().get(0).options();
		if (options.isEmpty()) return Component.translatable("rbei.missing");

		if (!menu.getSlot(0).getItem().isEmpty()) {
			gameMode.handleContainerInput(menu.containerId, 0, 0, ContainerInput.QUICK_MOVE, player);
		}

		boolean placed = false;
		for (Slot src : menu.slots) {
			if (src.container != player.getInventory() || src.getContainerSlot() >= 36) continue;
			ItemStack st = src.getItem();
			if (st.isEmpty() || !matchesAny(st, options)) continue;
			gameMode.handleContainerInput(menu.containerId, src.index, 0, ContainerInput.PICKUP, player);
			gameMode.handleContainerInput(menu.containerId, 0, 0, ContainerInput.PICKUP, player);
			if (!player.containerMenu.getCarried().isEmpty()) {
				gameMode.handleContainerInput(menu.containerId, src.index, 0, ContainerInput.PICKUP, player);
			}
			placed = true;
			break;
		}
		if (!placed) return Component.translatable("rbei.missing");
		UiSounds.click();
		return null;
	}

	private static boolean matchesAny(ItemStack stack, List<ItemStack> options) {
		for (ItemStack o : options) {
			if (ItemStack.isSameItem(stack, o)) return true;
		}
		return false;
	}

	private static int indexOf(List<ItemStack> kinds, ItemStack st) {
		for (int i = 0; i < kinds.size(); i++) {
			if (ItemStack.isSameItem(kinds.get(i), st)) return i;
		}
		return -1;
	}

	private static int[] assign(List<List<ItemStack>> needs, List<ItemStack> kinds, List<Integer> totals,
			boolean concentrated) {
		int[] left = totals.stream().mapToInt(Integer::intValue).toArray();
		int[] pick = new int[needs.size()];
		for (int i = 0; i < needs.size(); i++) {
			int best = -1;
			for (int k = 0; k < kinds.size(); k++) {
				if (left[k] <= 0 || !matchesAny(kinds.get(k), needs.get(i))) continue;
				if (best < 0 || left[k] > left[best]) best = k;
			}
			if (best < 0) return null;
			pick[i] = best;
			if (!concentrated) left[best]--;
		}
		return pick;
	}

	private static int crafts(int[] pick, List<ItemStack> kinds, List<Integer> totals) {
		int[] cells = new int[kinds.size()];
		for (int k : pick) cells[k]++;
		int n = 64;
		for (int k = 0; k < kinds.size(); k++) {
			if (cells[k] == 0) continue;
			n = Math.min(n, Math.min(kinds.get(k).getMaxStackSize(), totals.get(k) / cells[k]));
		}
		return n;
	}

	public static boolean hasIngredients(AbstractContainerMenu menu, RecipeIndex.Entry e) {
		if (e.cells().isEmpty()) return false;
		List<ItemStack> pool = new ArrayList<>();
		for (Slot slot : menu.slots) {
			if (!slot.getItem().isEmpty()) pool.add(slot.getItem());
		}
		int[] used = new int[pool.size()];
		for (RecipeIndex.Cell c : e.cells()) {
			if (c.options().isEmpty()) continue;
			boolean found = false;
			for (int i = 0; i < pool.size(); i++) {
				if (used[i] >= pool.get(i).getCount()) continue;
				if (!matchesAny(pool.get(i), c.options())) continue;
				used[i]++;
				found = true;
				break;
			}
			if (!found) return false;
		}
		return true;
	}
}
