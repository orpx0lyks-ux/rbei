package fr.max.rbei.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Modèle normalisé de recette — indépendant de Minecraft RecipeDisplay et des APIs mods.
 *
 * // [SPEC] Objectif : une recette peut avoir N entrées, N sorties, fluides, énergie, chances.
 * // [WHY] JEI/REI/EMI normalisent aussi ; c'est le seul moyen d'indexer uniformément.
 * // [TRADE-OFF] Perte éventuelle de layout exact → conservé dans details.extras / grille cols×rows.
 */
public final class UniversalRecipe {
	private final String uid;
	private final Identifier id;
	private final String sourceId;
	private final Component kind;
	private final ItemStack icon;
	private final List<ItemStack> stations;
	private final int cols;
	private final int rows;
	private final List<RecipeSlot> inputs;
	private final List<RecipeSlot> outputs;
	private final boolean crafting;
	private final boolean shaped;
	private final List<Component> info;
	private final RecipeDetails details;
	private final Object ore; // RecipeIndex.OreInfo conservé via bridge ; typé Object pour éviter cycle

	private UniversalRecipe(Builder b) {
		this.uid = Objects.requireNonNull(b.uid, "uid");
		this.id = b.id;
		this.sourceId = b.sourceId == null ? "unknown" : b.sourceId;
		this.kind = b.kind == null ? Component.literal("?") : b.kind;
		this.icon = b.icon;
		this.stations = List.copyOf(b.stations);
		this.cols = Math.max(1, b.cols);
		this.rows = Math.max(1, b.rows);
		this.inputs = List.copyOf(b.inputs);
		this.outputs = List.copyOf(b.outputs);
		this.crafting = b.crafting;
		this.shaped = b.shaped;
		this.info = List.copyOf(b.info);
		this.details = b.details == null ? RecipeDetails.EMPTY : b.details;
		this.ore = b.ore;
	}

	public String uid() { return uid; }
	public Identifier id() { return id; }
	public String sourceId() { return sourceId; }
	public Component kind() { return kind; }
	public ItemStack icon() { return icon; }
	public List<ItemStack> stations() { return stations; }
	public int cols() { return cols; }
	public int rows() { return rows; }
	public List<RecipeSlot> inputs() { return inputs; }
	public List<RecipeSlot> outputs() { return outputs; }
	public boolean crafting() { return crafting; }
	public boolean shaped() { return shaped; }
	public List<Component> info() { return info; }
	public RecipeDetails details() { return details; }
	public Object ore() { return ore; }

	/** Namespace du mod d'origine (recette id, sinon station, sinon source). */
	public String modId() {
		if (id != null) return id.getNamespace();
		if (!stations.isEmpty()) {
			return BuiltInRegistries.ITEM.getKey(stations.get(0).getItem()).getNamespace();
		}
		return sourceId.contains(":") ? sourceId.substring(0, sourceId.indexOf(':')) : sourceId;
	}

	private List<ItemStack> flatOutputsCache;

	public List<ItemStack> flatOutputs() {
		if (flatOutputsCache != null) return flatOutputsCache;
		List<ItemStack> out = new ArrayList<>(outputs.size());
		for (RecipeSlot s : outputs) {
			if (!s.isEmpty()) out.add(s.options().get(0).copyWithCount(Math.max(1, s.count())));
		}
		flatOutputsCache = List.copyOf(out);
		return flatOutputsCache;
	}

	public static Builder builder(String uid) {
		return new Builder(uid);
	}

	public static final class Builder {
		private final String uid;
		private Identifier id;
		private String sourceId;
		private Component kind;
		private ItemStack icon;
		private List<ItemStack> stations = List.of();
		private int cols = 1;
		private int rows = 1;
		private final List<RecipeSlot> inputs = new ArrayList<>();
		private final List<RecipeSlot> outputs = new ArrayList<>();
		private boolean crafting;
		private boolean shaped;
		private List<Component> info = new ArrayList<>();
		private RecipeDetails details = RecipeDetails.EMPTY;
		private Object ore;

		private Builder(String uid) { this.uid = uid; }

		public Builder id(Identifier id) { this.id = id; return this; }
		public Builder sourceId(String sourceId) { this.sourceId = sourceId; return this; }
		public Builder kind(Component kind) { this.kind = kind; return this; }
		public Builder icon(ItemStack icon) { this.icon = icon; return this; }
		public Builder stations(List<ItemStack> stations) {
			this.stations = stations == null ? List.of() : stations;
			return this;
		}
		public Builder grid(int cols, int rows) { this.cols = cols; this.rows = rows; return this; }
		public Builder input(RecipeSlot slot) { if (slot != null && !slot.isEmpty()) inputs.add(slot); return this; }
		public Builder inputs(List<RecipeSlot> slots) {
			if (slots != null) for (RecipeSlot s : slots) input(s);
			return this;
		}
		public Builder output(RecipeSlot slot) { if (slot != null && !slot.isEmpty()) outputs.add(slot); return this; }
		public Builder outputs(List<RecipeSlot> slots) {
			if (slots != null) for (RecipeSlot s : slots) output(s);
			return this;
		}
		public Builder outputStacks(List<ItemStack> stacks) {
			if (stacks == null) return this;
			int i = 0;
			for (ItemStack s : stacks) {
				if (s != null && !s.isEmpty()) {
					outputs.add(new RecipeSlot(i++, 0, List.of(s.copy()), s.getCount(), null));
				}
			}
			return this;
		}
		public Builder crafting(boolean crafting) { this.crafting = crafting; return this; }
		public Builder shaped(boolean shaped) { this.shaped = shaped; return this; }
		public Builder info(List<Component> info) {
			this.info = info == null ? new ArrayList<>() : new ArrayList<>(info);
			return this;
		}
		public Builder addInfo(Component line) { if (line != null) this.info.add(line); return this; }
		public Builder details(RecipeDetails details) { this.details = details; return this; }
		public Builder ore(Object ore) { this.ore = ore; return this; }

		public UniversalRecipe build() {
			return new UniversalRecipe(this);
		}
	}
}
