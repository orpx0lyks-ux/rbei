package fr.max.rbei.ui;

import fr.max.rbei.config.RbeiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

public final class UiSounds {
	private UiSounds() {}

	public static void click() {
		if (!RbeiConfig.get().uiSounds()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) return;
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}
}
