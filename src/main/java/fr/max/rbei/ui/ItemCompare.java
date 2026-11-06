package fr.max.rbei.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.equipment.Equippable;

/** Comparaison simple de 1–2 objets (stats / id). */
public final class ItemCompare {
	private ItemCompare() {}

	public static List<Component> lines(ItemStack a, ItemStack b) {
		List<Component> out = new ArrayList<>();
		if (a != null && !a.isEmpty()) out.addAll(describe(a, "A"));
		if (b != null && !b.isEmpty()) {
			if (!out.isEmpty()) out.add(Component.literal("———"));
			out.addAll(describe(b, "B"));
		}
		return out;
	}

	private static List<Component> describe(ItemStack stack, String label) {
		List<Component> out = new ArrayList<>();
		out.add(Component.literal("[" + label + "] ").append(stack.getHoverName()));
		out.add(Component.translatable("rbei.compare.id",
				BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
		out.add(Component.translatable("rbei.compare.stack", stack.getMaxStackSize()));
		if (stack.isDamageableItem()) {
			out.add(Component.translatable("rbei.compare.durability", stack.getMaxDamage()));
		}
		ItemAttributeModifiers mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
		if (mods != null) {
			mods.modifiers().forEach(entry -> out.add(Component.literal("  · ")
					.append(Component.translatable(entry.attribute().value().getDescriptionId()))
					.append(Component.literal(": " + format(entry.modifier().amount())))));
		}
		Tool tool = stack.get(DataComponents.TOOL);
		if (tool != null) {
			out.add(Component.translatable("rbei.compare.tool_rules", tool.rules().size()));
		}
		Equippable eq = stack.get(DataComponents.EQUIPPABLE);
		if (eq != null) {
			out.add(Component.translatable("rbei.compare.slot", eq.slot().getSerializedName()));
		}
		return out;
	}

	private static String format(double v) {
		if (Math.abs(v - Math.rint(v)) < 1e-6) return String.valueOf((long) Math.rint(v));
		return String.format(java.util.Locale.ROOT, "%.2f", v);
	}
}
