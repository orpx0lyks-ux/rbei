package fr.max.rbei.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Donne accès aux méthodes protégées d'ajout / retrait de widgets d'un écran. */
@Mixin(Screen.class)
public interface ScreenInvoker {

	@Invoker("addRenderableWidget")
	<T extends GuiEventListener & Renderable & NarratableEntry> T rbei$addRenderableWidget(T widget);

	@Invoker("removeWidget")
	void rbei$removeWidget(GuiEventListener widget);
}
