package fr.max.rbei.ui;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import fr.max.rbei.recipe.RecipeDatabase;
import fr.max.rbei.recipe.RecipeSlot;
import fr.max.rbei.recipe.UniversalRecipe;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Export de l'index vers config/rbei-export.json. */
public final class IndexExporter {
	private static final Logger LOG = LoggerFactory.getLogger("rbei/export");

	private IndexExporter() {}

	public static Component export(Minecraft mc) {
		RecipeDatabase db = RecipeDatabase.get(mc);
		JsonObject root = new JsonObject();
		root.addProperty("mod", "RBEI-Core");
		root.addProperty("version", "1.1.1");
		root.addProperty("recipes", db.all().size());
		JsonArray arr = new JsonArray();
		for (UniversalRecipe u : db.all()) {
			JsonObject o = new JsonObject();
			o.addProperty("uid", u.uid());
			o.addProperty("mod", u.modId());
			o.addProperty("source", u.sourceId());
			o.addProperty("kind", u.kind().getString());
			if (u.id() != null) o.addProperty("id", u.id().toString());
			JsonArray outs = new JsonArray();
			for (ItemStack s : u.flatOutputs()) {
				outs.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
			}
			o.add("outputs", outs);
			JsonArray ins = new JsonArray();
			for (RecipeSlot slot : u.inputs()) {
				for (ItemStack s : slot.options()) {
					ins.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
				}
			}
			o.add("inputs", ins);
			arr.add(o);
		}
		root.add("entries", arr);

		Path path = FabricLoader.getInstance().getConfigDir().resolve("rbei-export.json");
		try {
			Files.createDirectories(path.getParent());
			try (Writer w = Files.newBufferedWriter(path)) {
				new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
			}
			LOG.info("RBEI export: {} recipes → {}", db.all().size(), path);
			return Component.translatable("rbei.export.ok", path.getFileName().toString());
		} catch (IOException ex) {
			LOG.warn("Export échoué", ex);
			return Component.translatable("rbei.export.fail");
		}
	}
}
