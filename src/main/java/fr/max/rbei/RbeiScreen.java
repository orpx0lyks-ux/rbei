package fr.max.rbei;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import fr.max.rbei.config.RbeiConfig;
import fr.max.rbei.recipe.ItemKey;
import fr.max.rbei.recipe.RecipeDatabase;
import fr.max.rbei.ui.IndexExporter;
import fr.max.rbei.ui.InfoFormat;
import fr.max.rbei.ui.ItemCompare;
import fr.max.rbei.ui.RbeiTheme;
import fr.max.rbei.ui.RecipeTransfer;
import fr.max.rbei.ui.SearchQuery;
import fr.max.rbei.ui.UiSounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * RBEI browser — favorites, recent, tags, theme, compare, limitations.
 * Item categories sit at the bottom to cut down scrolling.
 */
public class RbeiScreen extends Screen {
	private static final int COLS = 9, ROWS = 5, S = 18;
	private static final int MAIN_W = 198, MAIN_H = 200, REC_W = 180, GAP = 8;
	private static final int CAT_PER_PAGE = 7, CAT_W = 22, RECIPE_CAT_TABS = 5;
	private static final int FAV_TAB = 0;
	private static final int BOTTOM_CAT_H = 26;

	private record Hit(int x, int y, int w, int h, ItemStack stack) {
		boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
	}

	private record Btn(int x, int y, int w, int h, Runnable run) {
		boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
	}

	private record State(ItemStack focus, boolean uses, int cat, int page) {}

	private record Category(String key, ItemStack icon, Component name, List<RecipeIndex.Entry> entries) {}

	private enum SideMode { RECIPES, COMPARE, OPTIONS }

	private final Screen parent;

	private List<ItemCatalog.Tab> tabs;
	private int tabIndex = 1, tabPage, scroll; // 1 = All items (0 = Favorites)
	private List<ItemStack> filtered = List.of();
	private EditBox search;
	private String lastQuery = "";
	private final Map<String, String> searchNameCache = new HashMap<>();
	private int px, py;
	private boolean dragging;

	private ItemStack focus;
	private boolean uses;
	private List<RecipeIndex.Entry> results = List.of();
	private int page;
	private List<Category> categories = List.of();
	private int catIndex, catPage;
	private final Deque<State> history = new ArrayDeque<>();

	private final List<Hit> hits = new ArrayList<>();
	private final List<Btn> btns = new ArrayList<>();
	private Hit hover;
	private Component tabHover;
	private Component message;
	private boolean shiftHeld;

	private ItemStack startFocus;
	private boolean startUses;

	private SideMode sideMode = SideMode.RECIPES;
	private ItemStack compareA = ItemStack.EMPTY;
	private ItemStack compareB = ItemStack.EMPTY;
	private float openAnim;
	private float pageFlash;
	private long openTick;

	public RbeiScreen(Screen parent) {
		this(parent, null, false);
	}

	public RbeiScreen(Screen parent, ItemStack startFocus, boolean startUses) {
		super(Component.translatable("rbei.title"));
		this.parent = parent;
		this.startFocus = startFocus;
		this.startUses = startUses;
	}

	private RbeiTheme th() {
		return RbeiTheme.current();
	}

	@Override
	protected void init() {
		openTick = minecraft.level != null ? minecraft.level.getGameTime() : 0;
		openAnim = 0;
		if (tabs == null) {
			rebuildTabs();
			RecipeDatabase.get(minecraft);
		}
		int total = MAIN_W + GAP + REC_W;
		px = Math.max(2, (width - total) / 2);
		py = Math.max(36, (height - MAIN_H) / 2);

		search = new EditBox(font, px + 14, py + 30, 170, 10, Component.translatable("rbei.search"));
		search.setBordered(false);
		search.setTextColor(0xFFE8E8E8);
		search.setTextColorUneditable(0xFFAAAAAA);
		search.setMaxLength(64);
		search.setValue(lastQuery);
		search.setResponder(s -> {
			lastQuery = s;
			scroll = 0;
			refilter();
		});
		addWidget(search);
		refilter();
		if (startFocus != null) {
			open(startFocus, startUses);
			startFocus = null;
		} else {
			setInitialFocus(search);
		}
	}

	private void rebuildTabs() {
		List<ItemCatalog.Tab> built = ItemCatalog.build(minecraft);
		ItemStack star = new ItemStack(Items.NETHER_STAR);
		List<ItemStack> favs = RbeiConfig.get().favoriteStacks();
		List<ItemCatalog.Tab> withFav = new ArrayList<>();
		withFav.add(new ItemCatalog.Tab(Component.translatable("rbei.tab.favorites"), star, favs));
		withFav.addAll(built);
		tabs = withFav;
		if (tabIndex >= tabs.size()) tabIndex = Math.min(1, tabs.size() - 1);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	private void refilter() {
		String q = lastQuery.trim().toLowerCase(Locale.ROOT);
		List<ItemStack> base = q.isEmpty() ? tabs.get(tabIndex).items() : tabs.get(Math.min(1, tabs.size() - 1)).items();
		// recherche globale = onglet "Tous" (index 1 après favoris)
		if (!q.isEmpty()) {
			for (ItemCatalog.Tab t : tabs) {
				if (t.name().getString().equals(Component.translatable("rbei.tab.all").getString())
						|| BuiltInRegistries.ITEM.getKey(t.icon().getItem()).getPath().equals("compass")) {
					base = t.items();
					break;
				}
			}
		}
		if (q.isEmpty()) {
			filtered = base;
		} else {
			List<ItemStack> out = new ArrayList<>();
			for (ItemStack s : base) {
				String name = searchNameCache.computeIfAbsent(ItemKey.of(s),
						k -> s.getHoverName().getString().toLowerCase(Locale.ROOT));
				if (SearchQuery.matches(s, q, name)) out.add(s);
			}
			filtered = out;
		}
		scroll = Mth.clamp(scroll, 0, maxScroll());
	}

	private int maxScroll() {
		return Math.max(0, (filtered.size() + COLS - 1) / COLS - ROWS);
	}

	private void open(ItemStack stack, boolean showUses) {
		if (focus != null && !(ItemStack.isSameItem(focus, stack) && uses == showUses)) {
			history.push(new State(focus, uses, catIndex, page));
		}
		setFocus(stack, showUses, 0, 0);
	}

	private void setFocus(ItemStack stack, boolean showUses, int newCat, int newPage) {
		message = null;
		sideMode = SideMode.RECIPES;
		focus = stack.copyWithCount(1);
		uses = showUses;
		RbeiConfig.get().pushRecent(stack);
		results = showUses
				? RecipeDatabase.get(minecraft).uiUsesOf(stack)
				: RecipeDatabase.get(minecraft).uiRecipesFor(stack);
		categories = groupByStation(results);
		catIndex = Mth.clamp(newCat, 0, Math.max(0, categories.size() - 1));
		catPage = catIndex / RECIPE_CAT_TABS;
		page = Mth.clamp(newPage, 0, pageCount() - 1);
		UiSounds.click();
	}

	private int perPage() {
		return !categories.isEmpty() && categories.get(catIndex).key().equals("minecraft:brewing_stand") ? 3 : 1;
	}

	private int pageCount() {
		int per = perPage();
		return Math.max(1, (current().size() + per - 1) / per);
	}

	private void back() {
		if (sideMode != SideMode.RECIPES) {
			sideMode = SideMode.RECIPES;
			return;
		}
		State st = history.pop();
		setFocus(st.focus(), st.uses(), st.cat(), st.page());
	}

	private List<RecipeIndex.Entry> current() {
		return categories.isEmpty() ? List.of() : categories.get(catIndex).entries();
	}

	private void changePage(int delta) {
		if (current().isEmpty()) return;
		message = null;
		page = Math.floorMod(page + delta, pageCount());
		pageFlash = 1f;
		UiSounds.click();
	}

	private static List<Category> groupByStation(List<RecipeIndex.Entry> list) {
		Map<String, List<RecipeIndex.Entry>> map = new LinkedHashMap<>();
		Map<String, ItemStack> icons = new HashMap<>();
		Map<String, Component> names = new HashMap<>();
		for (RecipeIndex.Entry e : list) {
			ItemStack st = e.icon() != null ? e.icon()
					: (e.stations().isEmpty() ? e.outputs().get(0) : e.stations().get(0));
			String key = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
			map.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
			icons.putIfAbsent(key, st);
			names.putIfAbsent(key, e.kind());
		}
		List<Category> out = new ArrayList<>();
		for (Map.Entry<String, List<RecipeIndex.Entry>> en : map.entrySet()) {
			out.add(new Category(en.getKey(), icons.get(en.getKey()), names.get(en.getKey()), en.getValue()));
		}
		out.sort(Comparator.comparingInt(c -> c.key().equals("minecraft:crafting_table") ? 0 : 1));
		return out;
	}

	private AbstractContainerScreen<?> containerParent() {
		return parent instanceof AbstractContainerScreen<?> cs ? cs : null;
	}

	private boolean canTransfer(RecipeIndex.Entry e) {
		return RecipeTransfer.canTransfer(containerParent(), e);
	}

	private void doTransfer(RecipeIndex.Entry e, boolean max) {
		Component err = RecipeTransfer.transfer(minecraft, containerParent(), e, max);
		message = err;
	}

	private void copyId(ItemStack stack) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		minecraft.keyboardHandler.setClipboard(id);
		message = Component.translatable("rbei.copy.id", id);
		UiSounds.click();
	}

	private void toggleFavorite(ItemStack stack) {
		RbeiConfig.get().toggleFavorite(stack);
		rebuildTabs();
		if (tabIndex == FAV_TAB) refilter();
		message = RbeiConfig.get().isFavorite(stack)
				? Component.translatable("rbei.fav.added")
				: Component.translatable("rbei.fav.removed");
		UiSounds.click();
	}

	private void addCompare(ItemStack stack) {
		if (compareA.isEmpty() || ItemStack.isSameItem(compareA, stack)) {
			compareA = stack.copyWithCount(1);
			compareB = ItemStack.EMPTY;
		} else {
			compareB = stack.copyWithCount(1);
		}
		sideMode = SideMode.COMPARE;
		UiSounds.click();
	}

	// ------------------------------------------------------------------ dessin

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
		hits.clear();
		btns.clear();
		hover = null;
		tabHover = null;
		RbeiTheme t = th();

		openAnim = Math.min(1f, openAnim + dt * 0.25f);
		if (pageFlash > 0) pageFlash = Math.max(0, pageFlash - dt * 0.15f);

		int vignette = (int) (0xC0 * openAnim) << 24 | 0x0C0E10;
		Gfx.fill(g, 0, 0, width, height, vignette);

		panel(g, px, py, MAIN_W, MAIN_H);
		Gfx.fill(g, px + 4, py + 4, px + MAIN_W - 4, py + 22, t.headerBar);
		Gfx.fill(g, px + 4, py + 22, px + MAIN_W - 4, py + 23, t.edgeMid);
		Gfx.text(g, font, Component.translatable("rbei.title"), px + 10, py + 8, t.text);
		String count = Component.translatable("rbei.count", filtered.size()).getString();
		Gfx.text(g, font, count, px + MAIN_W - 48 - font.width(count), py + 8, t.textMuted);

		button(g, px + MAIN_W - 22, py + 6, 14, 12, "O", mx, my, () -> {
			sideMode = SideMode.OPTIONS;
			UiSounds.click();
		});

		drawSearch(g, mx, my, dt);
		drawTopCategories(g, mx, my);
		drawGrid(g, mx, my);
		drawScrollbar(g);
		drawBottomRecent(g, mx, my);
		drawRecipePanel(g, mx, my);

		if (hover != null) {
			Gfx.tooltip(g, font, hover.stack(), mx, my);
		} else if (tabHover != null) {
			Gfx.tooltip(g, font, tabHover, mx, my);
		}
	}

	private void drawSearch(GuiGraphicsExtractor g, int mx, int my, float dt) {
		RbeiTheme t = th();
		int sx = px + 8, sy = py + 26, sw = MAIN_W - 28, sh = 16;
		search.setX(sx + 6);
		search.setY(sy + 4);
		search.setWidth(sw - 12);
		inset(g, sx, sy, sw, sh);
		Gfx.fill(g, sx + 1, sy + 1, sx + sw - 1, sy + sh - 1, t.searchBg);
		if (search.isFocused()) {
			Gfx.fill(g, sx, sy, sx + sw, sy + 1, t.accent);
			Gfx.fill(g, sx, sy + sh - 1, sx + sw, sy + sh, t.accentSoft);
		}
		if (lastQuery.isEmpty() && !search.isFocused()) {
			Gfx.text(g, font, Component.translatable("rbei.search"), sx + 6, sy + 4, 0xFF7A7A7A);
		}
		search.extractRenderState(g, mx, my, dt);
	}

	/** Categories under the search bar — arrows sit in reserved space on the right. */
	private void drawTopCategories(GuiGraphicsExtractor g, int mx, int my) {
		RbeiTheme t = th();
		int by = py + 44;
		int pages = Math.max(1, (tabs.size() + CAT_PER_PAGE - 1) / CAT_PER_PAGE);
		int arrowW = pages > 1 ? 30 : 0;
		int maxCats = Math.min(CAT_PER_PAGE, Math.max(1, (MAIN_W - 14 - arrowW) / CAT_W));
		for (int i = 0; i < maxCats; i++) {
			int idx = tabPage * CAT_PER_PAGE + i;
			if (idx >= tabs.size()) break;
			ItemCatalog.Tab tab = tabs.get(idx);
			int tx = px + 6 + i * CAT_W;
			boolean selected = idx == tabIndex;
			boolean over = mx >= tx && mx < tx + 20 && my >= by && my < by + 22;
			int fill = selected ? t.accentSoft : (over ? t.btnHover : t.btn);
			Gfx.fill(g, tx, by, tx + 20, by + 22, t.edgeDark);
			Gfx.fill(g, tx + 1, by + 1, tx + 19, by + 21, fill);
			if (selected) Gfx.fill(g, tx + 1, by + 1, tx + 19, by + 3, t.accent);
			Gfx.item(g, tab.icon(), tx + 2, by + 3);
			int finalIdx = idx;
			btns.add(new Btn(tx, by, 20, 22, () -> {
				tabIndex = finalIdx;
				scroll = 0;
				refilter();
				UiSounds.click();
			}));
			if (over) tabHover = tab.name();
		}
		if (pages > 1) {
			int ax = px + MAIN_W - 8 - 28;
			button(g, ax, by + 4, 12, 14, "<", mx, my, () -> tabPage = Math.floorMod(tabPage - 1, pages));
			button(g, ax + 14, by + 4, 12, 14, ">", mx, my, () -> tabPage = Math.floorMod(tabPage + 1, pages));
		}
	}

	/** Recent items at the bottom of the item panel. */
	private void drawBottomRecent(GuiGraphicsExtractor g, int mx, int my) {
		List<ItemStack> recent = RbeiConfig.get().recentStacks(8);
		RbeiTheme t = th();
		int by = py + MAIN_H - BOTTOM_CAT_H - 4;
		Gfx.fill(g, px + 4, by - 2, px + MAIN_W - 4, by - 1, t.edgeMid);
		Gfx.text(g, font, Component.translatable("rbei.recent"), px + 8, by + 6, t.textMuted);
		if (recent.isEmpty()) return;
		int x = px + 48;
		for (ItemStack s : recent) {
			slot(g, x, by + 2, s, mx, my);
			x += S;
		}
	}

	private void drawGrid(GuiGraphicsExtractor g, int mx, int my) {
		int gx = px + 8, gy = py + 70;
		inset(g, gx - 2, gy - 2, COLS * S + 4, ROWS * S + 4);
		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < COLS; c++) {
				int i = (scroll + r) * COLS + c;
				int sx = gx + c * S, sy = gy + r * S;
				ItemStack stack = i < filtered.size() ? filtered.get(i) : ItemStack.EMPTY;
				slot(g, sx, sy, stack, mx, my);
				if (!stack.isEmpty() && RbeiConfig.get().isFavorite(stack)) {
					Gfx.fill(g, sx + 12, sy + 1, sx + 16, sy + 5, th().fav);
				}
			}
		}
	}

	private int sbX() { return px + 8 + COLS * S + 6; }
	private int sbY() { return py + 70; }

	private void drawScrollbar(GuiGraphicsExtractor g) {
		RbeiTheme t = th();
		int x = sbX(), y = sbY(), h = ROWS * S;
		Gfx.fill(g, x, y, x + 12, y + h, t.edgeDark);
		Gfx.fill(g, x + 1, y + 1, x + 11, y + h - 1, 0xFF4A4A4A);
		int ms = maxScroll();
		int thumb = Math.max(12, h / Math.max(1, ms + 1));
		int ty = y + 1 + (ms == 0 ? 0 : (int) ((h - 2 - thumb) * (scroll / (double) ms)));
		Gfx.fill(g, x + 2, ty, x + 10, ty + thumb, t.edgeLight);
		Gfx.fill(g, x + 3, ty + 1, x + 9, ty + thumb - 1, ms == 0 ? 0xFF8A8A8A : t.accentSoft);
	}

	private void setScrollFromMouse(double my) {
		int h = ROWS * S;
		double rel = (my - sbY()) / h;
		scroll = Mth.clamp((int) (rel * (maxScroll() + 1)), 0, maxScroll());
	}

	private void drawRecipePanel(GuiGraphicsExtractor g, int mx, int my) {
		RbeiTheme t = th();
		int rx = px + MAIN_W + GAP, ry = py;
		panel(g, rx, ry, REC_W, MAIN_H);
		if (pageFlash > 0) {
			int a = (int) (pageFlash * 0x40) << 24 | (t.accent & 0xFFFFFF);
			Gfx.fill(g, rx + 2, ry + 2, rx + REC_W - 2, ry + MAIN_H - 2, a);
		}

		if (sideMode == SideMode.OPTIONS) {
			drawOptions(g, rx, ry, mx, my);
			return;
		}
		if (sideMode == SideMode.COMPARE) {
			drawCompare(g, rx, ry, mx, my);
			return;
		}

		if (focus == null || focus.isEmpty()) {
			Gfx.fill(g, rx + 4, ry + 4, rx + REC_W - 4, ry + 22, t.headerBar);
			Gfx.text(g, font, Component.translatable("rbei.panel.recipes"), rx + 10, ry + 8, t.text);
			int hx = rx + 14, hy = ry + 40;
			hintRow(g, hx, hy, "R", Component.translatable("rbei.hint.left"));
			hintRow(g, hx, hy + 20, "U", Component.translatable("rbei.hint.right"));
			hintRow(g, hx, hy + 40, "MMB", Component.translatable("rbei.hint.middle"));
			hintRow(g, hx, hy + 60, "⌫", Component.translatable("rbei.hint.back"));
			hintRow(g, hx, hy + 80, "C", Component.translatable("rbei.hint.copy"));
			hintRow(g, hx, hy + 100, "O", Component.translatable("rbei.hint.options"));
			Gfx.text(g, font, Component.translatable("rbei.hint.click"), rx + 14, hy + 126, t.textMuted);
			return;
		}

		Gfx.fill(g, rx + 4, ry + 4, rx + REC_W - 4, ry + 28, t.headerBar);
		Gfx.fill(g, rx + 4, ry + 28, rx + REC_W - 4, ry + 29, t.edgeMid);
		slot(g, rx + 8, ry + 7, focus, mx, my);
		Component mode = Component.translatable(uses ? "rbei.mode.uses" : "rbei.mode.recipes");
		Gfx.text(g, font, mode, rx + 30, ry + 8, t.accent);
		Gfx.text(g, font, font.plainSubstrByWidth(focus.getHoverName().getString(), 100),
				rx + 30, ry + 17, t.text);

		button(g, rx + REC_W - 38, ry + 8, 14, 14,
				RbeiConfig.get().isFavorite(focus) ? "★" : "☆", mx, my, () -> toggleFavorite(focus));
		button(g, rx + REC_W - 20, ry + 8, 14, 14, "=", mx, my, () -> addCompare(focus));

		if (categories.isEmpty()) {
			Gfx.text(g, font, Component.translatable("rbei.none"), rx + 10, ry + 42, t.textMuted);
			return;
		}

		drawCatTabs(g, rx, ry, mx, my);
		List<RecipeIndex.Entry> list = current();
		if (list.isEmpty()) return;

		if (perPage() > 1) {
			drawBrewPage(g, rx, ry, mx, my, list);
			drawPager(g, rx, ry, mx, my);
			return;
		}

		RecipeIndex.Entry e = list.get(Mth.clamp(page, 0, list.size() - 1));
		Gfx.text(g, font, font.plainSubstrByWidth(e.kind().getString(), 118), rx + 10, ry + 34, t.textMuted);
		if (!e.stations().isEmpty()) {
			slot(g, rx + REC_W - 10 - S, ry + 30, pick(e.stations()), mx, my);
		} else if (e.icon() != null) {
			slotBg(g, rx + REC_W - 10 - S, ry + 30, S, S);
			Gfx.item(g, e.icon(), rx + REC_W - 10 - S + 1, ry + 31);
		}

		int zoneX = rx + 10, zoneY = ry + 52, zoneW = REC_W - 20, zoneH = 58;
		Gfx.fill(g, zoneX, zoneY, zoneX + zoneW, zoneY + zoneH, t.edgeDark);
		Gfx.fill(g, zoneX + 1, zoneY + 1, zoneX + zoneW - 1, zoneY + zoneH - 1, t.panelDeep);

		boolean hasInputs = !e.cells().isEmpty();
		int gx = zoneX + 8;
		int gy = zoneY + 4 + (3 * S - e.rows() * S) / 2;
		for (RecipeIndex.Cell c : e.cells()) {
			slot(g, gx + c.col() * S, gy + c.row() * S, pick(c.options()), mx, my);
		}
		int outX = hasInputs ? zoneX + 92 : zoneX + 12;
		int midY = zoneY + zoneH / 2 - 4;
		if (hasInputs) Gfx.text(g, font, "→", zoneX + 70, midY, t.accent);
		slot(g, outX, zoneY + zoneH / 2 - 9, pick(e.outputs()), mx, my);

		if (canTransfer(e)) {
			int bx = zoneX + zoneW - 20, byy = zoneY + zoneH / 2 - 7;
			button(g, bx, byy, 14, 14, "+", mx, my, () -> doTransfer(e, shiftHeld));
			if (mx >= bx && mx < bx + 14 && my >= byy && my < byy + 14) {
				tabHover = Component.translatable("rbei.transfer");
			}
		}

		int infoY = zoneY + zoneH + 8;
		infoY = drawMeta(g, rx, ry, e, infoY);

		int infoLimit = ry + MAIN_H - BOTTOM_CAT_H - 40;
		for (Component line : InfoFormat.lines(e)) {
			if (infoY > infoLimit) break;
			drawInfoLine(g, rx + 10, infoY, REC_W - 24, line, t);
			infoY += 12;
		}
		if (message != null) {
			Gfx.text(g, font, font.plainSubstrByWidth(message.getString(), REC_W - 24),
					rx + 10, ry + MAIN_H - BOTTOM_CAT_H - 36, t.error);
		}
		drawPager(g, rx, ry, mx, my);
	}

	private int drawMeta(GuiGraphicsExtractor g, int rx, int ry, RecipeIndex.Entry e, int infoY) {
		RbeiTheme t = th();
		RecipeIndex.Details d = e.details();
		if (d == null || d == RecipeIndex.Details.EMPTY) return infoY;
		if (d.energy() != null) {
			int bw = REC_W - 20;
			Gfx.fill(g, rx + 10, infoY, rx + 10 + bw, infoY + 6, t.edgeDark);
			int fill = (int) Math.min(bw, Math.max(4, d.energy() / 100));
			Gfx.fill(g, rx + 10, infoY, rx + 10 + fill, infoY + 6, t.accent);
			infoY += 8;
		}
		return infoY;
	}

	/** Label in accent, value in muted — e.g. "Source: Sealing Ghast". */
	private void drawInfoLine(GuiGraphicsExtractor g, int x, int y, int maxW, Component line, RbeiTheme t) {
		String full = line.getString();
		int colon = full.indexOf(':');
		if (colon > 0 && colon < 14) {
			String label = full.substring(0, colon + 1);
			String value = full.substring(colon + 1).trim();
			Gfx.text(g, font, label, x, y, t.accent);
			int lx = x + font.width(label) + 4;
			Gfx.text(g, font, font.plainSubstrByWidth(value, Math.max(20, maxW - font.width(label) - 4)),
					lx, y, t.text);
		} else {
			Gfx.text(g, font, font.plainSubstrByWidth(full, maxW), x, y, t.textMuted);
		}
	}

	private void drawOptions(GuiGraphicsExtractor g, int rx, int ry, int mx, int my) {
		RbeiTheme t = th();
		RbeiConfig cfg = RbeiConfig.get();
		Gfx.fill(g, rx + 4, ry + 4, rx + REC_W - 4, ry + 22, t.headerBar);
		Gfx.text(g, font, Component.translatable("rbei.options.title"), rx + 10, ry + 8, t.text);
		button(g, rx + REC_W - 22, ry + 6, 14, 12, "X", mx, my, () -> sideMode = SideMode.RECIPES);

		int y = ry + 34;
		y = optionToggle(g, rx, y, mx, my,
				Component.translatable("rbei.options.dark"),
				cfg.darkTheme(), () -> {
					cfg.toggleTheme();
					UiSounds.click();
				});
		y = optionToggle(g, rx, y, mx, my,
				Component.translatable("rbei.options.sounds"),
				cfg.uiSounds(), () -> {
					cfg.setUiSounds(!cfg.uiSounds());
					UiSounds.click();
				});
		y = optionToggle(g, rx, y, mx, my,
				Component.translatable("rbei.options.overlay"),
				cfg.craftableOverlay(), () -> {
					cfg.setCraftableOverlay(!cfg.craftableOverlay());
					UiSounds.click();
				});

		y += 10;
		Gfx.text(g, font, Component.translatable("rbei.options.hint"), rx + 12, y, t.textMuted);
		y += 18;
		button(g, rx + 12, y, 100, 16, Component.translatable("rbei.export").getString(),
				mx, my, () -> {
					message = IndexExporter.export(minecraft);
					UiSounds.click();
				});
		if (message != null) {
			Gfx.text(g, font, font.plainSubstrByWidth(message.getString(), REC_W - 24),
					rx + 12, y + 22, t.accent);
		}
	}

	private int optionToggle(GuiGraphicsExtractor g, int rx, int y, int mx, int my,
			Component label, boolean on, Runnable toggle) {
		RbeiTheme t = th();
		int bx = rx + 12, by = y, bw = REC_W - 24, bh = 18;
		boolean over = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
		Gfx.fill(g, bx, by, bx + bw, by + bh, t.edgeDark);
		Gfx.fill(g, bx + 1, by + 1, bx + bw - 1, by + bh - 1, over ? t.btnHover : t.btn);
		Gfx.text(g, font, label, bx + 6, by + 5, t.text);
		String state = on ? "ON" : "OFF";
		int sw = font.width(state);
		Gfx.text(g, font, state, bx + bw - 8 - sw, by + 5, on ? t.accent : t.textMuted);
		btns.add(new Btn(bx, by, bw, bh, toggle));
		return y + 22;
	}

	private void drawCompare(GuiGraphicsExtractor g, int rx, int ry, int mx, int my) {
		RbeiTheme t = th();
		Gfx.fill(g, rx + 4, ry + 4, rx + REC_W - 4, ry + 22, t.headerBar);
		Gfx.text(g, font, Component.translatable("rbei.compare.title"), rx + 10, ry + 8, t.text);
		button(g, rx + REC_W - 22, ry + 6, 14, 12, "X", mx, my, () -> sideMode = SideMode.RECIPES);
		if (!compareA.isEmpty()) slot(g, rx + 10, ry + 30, compareA, mx, my);
		if (!compareB.isEmpty()) slot(g, rx + 40, ry + 30, compareB, mx, my);
		int y = ry + 54;
		for (Component line : ItemCompare.lines(compareA, compareB)) {
			if (y > ry + MAIN_H - 16) break;
			Gfx.text(g, font, font.plainSubstrByWidth(line.getString(), REC_W - 20), rx + 10, y, t.textMuted);
			y += 10;
		}
	}

	private void drawCatTabs(GuiGraphicsExtractor g, int rx, int ry, int mx, int my) {
		RbeiTheme t = th();
		int by = ry + MAIN_H - BOTTOM_CAT_H - 4;
		Gfx.fill(g, rx + 4, by - 2, rx + REC_W - 4, by - 1, t.edgeMid);
		int pages = Math.max(1, (categories.size() + RECIPE_CAT_TABS - 1) / RECIPE_CAT_TABS);
		int arrowW = pages > 1 ? 30 : 0;
		int maxCats = Math.min(RECIPE_CAT_TABS, Math.max(1, (REC_W - 14 - arrowW) / 28));
		for (int i = 0; i < maxCats; i++) {
			int idx = catPage * RECIPE_CAT_TABS + i;
			if (idx >= categories.size()) break;
			Category cat = categories.get(idx);
			int tx = rx + 6 + i * 28;
			boolean selected = idx == catIndex;
			boolean over = mx >= tx && mx < tx + 26 && my >= by && my < by + 22;
			int fill = selected ? t.accentSoft : (over ? t.btnHover : t.btn);
			Gfx.fill(g, tx, by, tx + 26, by + 22, t.edgeDark);
			Gfx.fill(g, tx + 1, by + 1, tx + 25, by + 21, fill);
			if (selected) Gfx.fill(g, tx + 1, by + 1, tx + 25, by + 3, t.accent);
			Gfx.item(g, cat.icon(), tx + 5, by + 3);
			int finalIdx = idx;
			btns.add(new Btn(tx, by, 26, 22, () -> {
				catIndex = finalIdx;
				page = 0;
				UiSounds.click();
			}));
			if (over) tabHover = cat.name();
		}
		if (pages > 1) {
			int ax = rx + REC_W - 8 - 28;
			button(g, ax, by + 4, 12, 14, "<", mx, my, () -> catPage = Math.floorMod(catPage - 1, pages));
			button(g, ax + 14, by + 4, 12, 14, ">", mx, my, () -> catPage = Math.floorMod(catPage + 1, pages));
		}
	}

	private void drawBrewPage(GuiGraphicsExtractor g, int rx, int ry, int mx, int my,
			List<RecipeIndex.Entry> list) {
		RbeiTheme t = th();
		int per = perPage();
		Gfx.text(g, font, Component.translatable("rbei.brew.steps", list.size()),
				rx + 10, ry + 34, t.textMuted);
		for (int i = 0; i < per; i++) {
			int idx = page * per + i;
			if (idx >= list.size()) break;
			RecipeIndex.Entry e = list.get(idx);
			int y = ry + 48 + i * 36;
			if (i > 0) Gfx.fill(g, rx + 10, y - 3, rx + REC_W - 10, y - 2, t.edgeMid);
			ItemStack ingredient = e.cells().isEmpty() ? ItemStack.EMPTY : pick(e.cells().get(0).options());
			ItemStack bottle = e.cells().size() > 1 ? pick(e.cells().get(1).options()) : ItemStack.EMPTY;
			int bx = rx + 14;
			slot(g, bx + 27, y, ingredient, mx, my);
			slot(g, bx, y + 16, bottle, mx, my);
			slot(g, bx + 27, y + 16, bottle, mx, my);
			slot(g, bx + 54, y + 16, bottle, mx, my);
			Gfx.text(g, font, "→", bx + 78, y + 12, t.accent);
			slot(g, bx + 92, y + 8, pick(e.outputs()), mx, my);
		}
	}

	private void drawPager(GuiGraphicsExtractor g, int rx, int ry, int mx, int my) {
		RbeiTheme t = th();
		int by = ry + MAIN_H - BOTTOM_CAT_H - 22;
		Gfx.fill(g, rx + 4, by - 3, rx + REC_W - 4, by - 2, t.edgeMid);
		button(g, rx + 8, by, 16, 14, "<", mx, my, () -> changePage(-1));
		button(g, rx + REC_W - 8 - 16, by, 16, 14, ">", mx, my, () -> changePage(1));
		String label = (page + 1) + " / " + pageCount();
		Gfx.text(g, font, label, rx + REC_W / 2 - font.width(label) / 2, by + 3, t.text);
	}

	private void hintRow(GuiGraphicsExtractor g, int x, int y, String key, Component text) {
		RbeiTheme t = th();
		int kw = Math.max(18, font.width(key) + 6);
		Gfx.fill(g, x, y, x + kw, y + 14, t.edgeDark);
		Gfx.fill(g, x + 1, y + 1, x + kw - 1, y + 13, t.btn);
		Gfx.text(g, font, key, x + kw / 2 - font.width(key) / 2, y + 3, t.text);
		Gfx.text(g, font, text, x + kw + 6, y + 3, t.textMuted);
	}

	private static ItemStack pick(List<ItemStack> options) {
		if (options.isEmpty()) return ItemStack.EMPTY;
		return options.get((int) ((System.currentTimeMillis() / 1000) % options.size()));
	}

	private void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		RbeiTheme t = th();
		Gfx.fill(g, x, y, x + w, y + h, t.edgeDark);
		Gfx.fill(g, x + 1, y + 1, x + w - 1, y + h - 1, t.edgeLight);
		Gfx.fill(g, x + 2, y + 2, x + w - 1, y + h - 1, t.edgeMid);
		Gfx.fill(g, x + 2, y + 2, x + w - 2, y + h - 2, t.panel);
		Gfx.fill(g, x + 3, y + 3, x + w - 3, y + 4, 0x33FFFFFF);
	}

	private void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		RbeiTheme t = th();
		Gfx.fill(g, x, y, x + w, y + h, t.slotDepth);
		Gfx.fill(g, x + 1, y + 1, x + w, y + h, t.edgeLight);
		Gfx.fill(g, x + 1, y + 1, x + w - 1, y + h - 1, t.slotInset);
	}

	private void slotBg(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		inset(g, x, y, w, h);
	}

	private void slot(GuiGraphicsExtractor g, int x, int y, ItemStack stack, int mx, int my) {
		RbeiTheme t = th();
		slotBg(g, x, y, S, S);
		if (stack == null || stack.isEmpty()) return;
		Gfx.item(g, stack, x + 1, y + 1);
		Hit hit = new Hit(x + 1, y + 1, 16, 16, stack);
		hits.add(hit);
		if (hit.in(mx, my)) {
			hover = hit;
			Gfx.fill(g, x + 1, y + 1, x + 17, y + 17, t.hoverSlot);
		}
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label,
			int mx, int my, Runnable run) {
		RbeiTheme t = th();
		boolean over = mx >= x && mx < x + w && my >= y && my < y + h;
		Gfx.fill(g, x, y, x + w, y + h, t.edgeDark);
		Gfx.fill(g, x + 1, y + 1, x + w - 1, y + h - 1, t.edgeLight);
		Gfx.fill(g, x + 1, y + 1, x + w - 1, y + h - 2, over ? t.btnHover : t.btn);
		Gfx.fill(g, x + 1, y + h - 2, x + w - 1, y + h - 1, t.edgeMid);
		if (over) Gfx.fill(g, x + 1, y + 1, x + w - 1, y + 2, t.accent);
		Gfx.text(g, font, label, x + w / 2 - font.width(label) / 2, y + (h - 8) / 2, t.text);
		btns.add(new Btn(x, y, w, h, run));
	}

	// ------------------------------------------------------------------ entrées

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		shiftHeld = event.hasShiftDown();
		if (search.isFocused()) {
			search.setFocused(false);
			setFocused(null);
		}
		double x = event.x(), y = event.y();
		int b = event.button();

		for (Btn bt : btns) {
			if (bt.in(x, y)) {
				bt.run().run();
				return true;
			}
		}
		if (b == 0 && x >= sbX() && x < sbX() + 12 && y >= sbY() && y < sbY() + ROWS * S) {
			dragging = true;
			setScrollFromMouse(y);
			return true;
		}
		for (Hit h : hits) {
			if (!h.in(x, y)) continue;
			if (b == 2) { // milieu
				if (event.hasControlDown()) copyId(h.stack());
				else toggleFavorite(h.stack());
				return true;
			}
			if (b == 0 && shiftHeld) {
				addCompare(h.stack());
				return true;
			}
			if (b == 0) open(h.stack(), false);
			else if (b == 1) open(h.stack(), true);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			setScrollFromMouse(event.y());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		int rx = px + MAIN_W + GAP;
		if (mx >= rx && mx < rx + REC_W) {
			changePage(scrollY > 0 ? -1 : 1);
		} else {
			scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (super.keyPressed(event)) return true;
		int key = event.key();
		if (key == InputConstants.KEY_BACKSPACE && (!history.isEmpty() || sideMode != SideMode.RECIPES)) {
			back();
			return true;
		}
		if (key == InputConstants.KEY_O && !search.isFocused()) {
			sideMode = sideMode == SideMode.OPTIONS ? SideMode.RECIPES : SideMode.OPTIONS;
			UiSounds.click();
			return true;
		}
		if (key == InputConstants.KEY_T && !search.isFocused()) {
			RbeiConfig.get().toggleTheme();
			UiSounds.click();
			return true;
		}
		if (key == InputConstants.KEY_C && hover != null && !search.isFocused()) {
			copyId(hover.stack());
			return true;
		}
		if (hover != null) {
			if (key == InputConstants.KEY_R) {
				open(hover.stack(), false);
				return true;
			}
			if (key == InputConstants.KEY_U) {
				open(hover.stack(), true);
				return true;
			}
		}
		return false;
	}
}
