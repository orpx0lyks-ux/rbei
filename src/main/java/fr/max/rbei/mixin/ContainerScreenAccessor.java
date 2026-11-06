package fr.max.rbei.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Permet de connaître l'emplacement survolé dans n'importe quel inventaire. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {

	@Accessor("hoveredSlot")
	Slot rbei$getHoveredSlot();

	@Accessor("leftPos")
	int rbei$getLeftPos();

	@Accessor("topPos")
	int rbei$getTopPos();

	@Accessor("imageWidth")
	int rbei$getImageWidth();
}
