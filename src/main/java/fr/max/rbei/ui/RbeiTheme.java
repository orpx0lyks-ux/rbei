package fr.max.rbei.ui;

import fr.max.rbei.config.RbeiConfig;

/** Palettes light / dark pour l'UI RBEI. */
public final class RbeiTheme {
	public final int panel;
	public final int panelDeep;
	public final int text;
	public final int textMuted;
	public final int accent;
	public final int accentSoft;
	public final int hoverSlot;
	public final int btn;
	public final int btnHover;
	public final int edgeDark;
	public final int edgeLight;
	public final int edgeMid;
	public final int slotInset;
	public final int slotDepth;
	public final int searchBg;
	public final int headerBar;
	public final int error;
	public final int fav;
	public final int overlay;

	private RbeiTheme(
			int panel, int panelDeep, int text, int textMuted, int accent, int accentSoft,
			int hoverSlot, int btn, int btnHover, int edgeDark, int edgeLight, int edgeMid,
			int slotInset, int slotDepth, int searchBg, int headerBar, int error, int fav, int overlay) {
		this.panel = panel;
		this.panelDeep = panelDeep;
		this.text = text;
		this.textMuted = textMuted;
		this.accent = accent;
		this.accentSoft = accentSoft;
		this.hoverSlot = hoverSlot;
		this.btn = btn;
		this.btnHover = btnHover;
		this.edgeDark = edgeDark;
		this.edgeLight = edgeLight;
		this.edgeMid = edgeMid;
		this.slotInset = slotInset;
		this.slotDepth = slotDepth;
		this.searchBg = searchBg;
		this.headerBar = headerBar;
		this.error = error;
		this.fav = fav;
		this.overlay = overlay;
	}

	public static final RbeiTheme LIGHT = new RbeiTheme(
			0xFFC8C8C8, 0xFFB0B0B0, 0xFF2E2E2E, 0xFF6A6A6A,
			0xFF3D9B6A, 0xFF7BC49A, 0x663D9B6A,
			0xFFA8A8A8, 0xFF8FD4B0,
			0xFF2A2A2A, 0xFFF2F2F2, 0xFF5A5A5A,
			0xFF8B8B8B, 0xFF3A3A3A, 0xFF1A1A1A, 0xFFD8D8D8,
			0xFFB33030, 0xFFE8B84A, 0x553D9B6A);

	public static final RbeiTheme DARK = new RbeiTheme(
			0xFF2A2E32, 0xFF1E2226, 0xFFE8E8E8, 0xFF9A9A9A,
			0xFF4DBF82, 0xFF3D9B6A, 0x664DBF82,
			0xFF3A4046, 0xFF4DBF82,
			0xFF0A0C0E, 0xFF4A5056, 0xFF1A1E22,
			0xFF3A4046, 0xFF121518, 0xFF0E1012, 0xFF32383E,
			0xFFE05555, 0xFFE8B84A, 0x554DBF82);

	public static RbeiTheme current() {
		return RbeiConfig.get().darkTheme() ? DARK : LIGHT;
	}
}
