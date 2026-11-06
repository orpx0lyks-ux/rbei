package fr.max.rbei;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import fr.max.rbei.config.RbeiConfig;
import fr.max.rbei.mixin.ContainerScreenAccessor;
import fr.max.rbei.mixin.ScreenInvoker;
import fr.max.rbei.recipe.RecipeDatabase;
import fr.max.rbei.ui.CraftableOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Entrée client de RBEI-Core (RBEI - RecipeBookEnoughItems). */
public class RbeiClient implements ClientModInitializer {
	public static final String MOD_ID = "rbei";
	public static final String DISPLAY_NAME = "RBEI - RecipeBookEnoughItems";
	public static KeyMapping openKey;

	private static final WidgetSprites ICON = new WidgetSprites(
			Identifier.fromNamespaceAndPath("rbei", "rbei_button"),
			Identifier.fromNamespaceAndPath("rbei", "rbei_button_highlighted"));

	@Override
	public void onInitializeClient() {
		RbeiConfig.get();

		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath("rbei", "main"));
		openKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.rbei.open", InputConstants.KEY_O, category));

		ClientRecipeSynchronizedEvent.EVENT.register((mc, recipes) -> RecipeDatabase.invalidateAndWarm(mc));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openKey.consumeClick()) {
				if (mc.player != null && mc.level != null && !(mc.gui.screen() instanceof RbeiScreen)) {
					mc.gui.setScreen(new RbeiScreen(mc.gui.screen()));
				}
			}
		});

		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			replaceRecipeBookButton(mc, screen);
			registerShortcuts(screen);
			if (screen instanceof AbstractContainerScreen<?> container) {
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, tickDelta) ->
						CraftableOverlay.render(mc, container, graphics));
			}
		});
	}

	/** Remplace le bouton du livre de recettes (inventaire, établi, fours) par un bouton RBEI. */
	private static void replaceRecipeBookButton(Minecraft mc, Screen screen) {
		if (!(screen instanceof InventoryScreen || screen instanceof CraftingScreen
				|| screen instanceof AbstractFurnaceScreen<?>)) {
			return;
		}
		List<ImageButton> found = new ArrayList<>();
		for (GuiEventListener child : screen.children()) {
			if (child instanceof ImageButton ib) found.add(ib);
		}
		ScreenInvoker inv = (ScreenInvoker) screen;
		for (ImageButton ib : found) {
			inv.rbei$removeWidget(ib);
			inv.rbei$addRenderableWidget(iconButton(mc, screen, ib.getX() + 1, ib.getY()));
		}
	}

	/** Bouton 18x18 qui affiche l'icône du mod (textures/gui/sprites/rbei_button*.png). */
	private static ImageButton iconButton(Minecraft mc, Screen screen, int x, int y) {
		ImageButton button = new ImageButton(x, y, 18, 18, ICON,
				btn -> mc.gui.setScreen(new RbeiScreen(screen)),
				Component.translatable("rbei.title"));
		button.setTooltip(Tooltip.create(Component.translatable("rbei.title")));
		return button;
	}

	// ------------------------------------------------------------------ raccourcis

	/**
	 * Dans tous les inventaires (survie, créatif, coffres, établi...) :
	 * survol d'un objet + R = recettes, survol + U = usages,
	 * Ctrl + clic gauche = recettes, Ctrl + clic droit = usages.
	 */
	private static void registerShortcuts(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> container)) return;

		ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
			if (container.getFocused() instanceof EditBox) return true; // on tape du texte
			if (openKey.matches(event) && !(screen instanceof CreativeModeInventoryScreen)) {
				Minecraft.getInstance().gui.setScreen(new RbeiScreen(container));
				return false;
			}
			int key = event.key();
			if (key != InputConstants.KEY_R && key != InputConstants.KEY_U) return true;
			ItemStack stack = hovered(container);
			if (stack.isEmpty()) return true;
			open(container, stack, key == InputConstants.KEY_U);
			return false; // touche consommée
		});

		ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
			if (!event.hasControlDown()) return true;
			int button = event.button();
			if (button != 0 && button != 1) return true;
			ItemStack stack = hovered(container);
			if (stack.isEmpty()) return true;
			open(container, stack, button == 1);
			return false; // clic consommé : pas d'action vanilla
		});
	}

	private static ItemStack hovered(AbstractContainerScreen<?> container) {
		Slot slot = ((ContainerScreenAccessor) container).rbei$getHoveredSlot();
		return slot == null ? ItemStack.EMPTY : slot.getItem();
	}

	private static void open(Screen parent, ItemStack stack, boolean uses) {
		Minecraft.getInstance().gui.setScreen(new RbeiScreen(parent, stack, uses));
	}
}
