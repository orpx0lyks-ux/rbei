# RBEI — RecipeBookEnoughItems

**A vanilla-friendly JEI-style recipe book for Fabric.**  
RBEI replaces Minecraft's recipe book with a clean item & recipe browser: look up crafts, uses, brewing, loot and ores — without leaving your world, and without installing JEI/REI/EMI.

Built for **Minecraft 26.2** · **Fabric** · **client-side only**.

---

## Why RBEI?

Minecraft's recipe book is limited. **RBEI** keeps a vanilla feel while giving you a JEI-style browser that indexes:

- Crafting & smelting recipes  
- Brewing steps  
- Loot / mob drops (with chances when available)  
- Ore generation ranges (Y min / max / best layers)  
- Many mod recipes detected automatically  

No JEI, REI, or EMI required — RBEI works on its own.

---

## Features

### Item browser
- Full item list with live count  
- Fast search: plain text, `@mod` (namespace), `#tag`  
- Category tabs (favorites, tools, blocks, nature, …)  
- **Recent** bar for items you just looked at  
- Yellow markers on favorites in the grid  

### Recipes & uses
- **Left click / R** → how to obtain / craft an item  
- **Right click / U** → where the item is used  
- Multi-page results with clean craft grids  
- Station icon (crafting table, furnace, brewing stand, …)  
- **+** transfer button → push ingredients into an open crafting table or furnace (Shift = fill max)  

### Favorites
- **☆ / ★** on the recipe header (not confused with transfer **+**)  
- Middle-click an item to toggle favorite  
- Dedicated favorites tab (Nether Star icon)  

### Quality of life
- Options panel (**O**): dark theme, UI sounds, craftable overlay, JSON export  
- Craftable overlay highlights items you can make from inventory  
- Compare two items (**=**)  
- Copy registry ID with **C**  
- Replaces the vanilla recipe-book button on inventory / crafting / furnaces  
- Open anytime with the keybind (default **O** outside the search box)  

---

## Screenshots

### Favorites & browser

![Favorites & browser](https://cdn.modrinth.com/data/AmOxYHkh/images/0167ecd86c3084aa3d8b7e8019fc58f3b872a33d.png)

*Nether Star favorites tab, starred items, recent bar, and keybind help*

### Item browser

![Item browser](https://cdn.modrinth.com/data/AmOxYHkh/images/aec5b889db6b1f539e3cbe1f3b68f8a13a22f34e.png)

*Category tabs with Nether Star favorites and built-in controls*

### Crafting recipes

![Crafting recipes](https://cdn.modrinth.com/data/AmOxYHkh/images/ae8482f96e0fb2e04a8992a8ecaca7831f66b908.png)

*Crafting table recipe view with star favorite and compare buttons*

### Brewing

![Brewing](https://cdn.modrinth.com/data/AmOxYHkh/images/c6fa68f785835a53ee8d92a669caeb677d1de586.png)

*Brewing stand recipes with step count and star favorite*

### Mob drops

![Mob drops](https://cdn.modrinth.com/data/AmOxYHkh/images/072980f4e4b90033c80b241b3acde35e71585fcb.png)

*Loot sources — e.g. carrots dropped by zombies*

### Ore generation

![Ore generation](https://cdn.modrinth.com/data/AmOxYHkh/images/9767f09fcb09929b0e9313d5a2841dd7d02c6058.png)

*Ore Y range and best layer info*

---

## Controls

| Input | Action |
|-------|--------|
| Open key (default **O**) | Open / close RBEI |
| Left click / **R** | Show recipes |
| Right click / **U** | Show uses |
| Middle click | Toggle favorite |
| **C** | Copy hovered item ID |
| **O** (inside RBEI) | Options |
| **T** | Toggle theme |
| **+** | Transfer ingredients to grid |
| **=** | Add to compare |
| Backspace | Go back |

---

## Dependencies

| Mod / runtime | Requirement |
|---------------|-------------|
| [Fabric API](https://modrinth.com/mod/fabric-api) | **Required** |
| Fabric Loader | ≥ 0.19.5 |
| Java | **25+** |
| Minecraft | **26.2** |

---

## Installation

1. Install Fabric Loader for Minecraft **26.2**  
2. Install **Fabric API**  
3. Drop `rbei-<version>.jar` into your `mods` folder  
4. Launch the game and press **O** (or your bound key)

Config file (optional): `config/rbei-client.json`  
(theme, sounds, overlay, favorites, recent)

---

## Compatibility notes

- **Client-only** — no server install needed  
- Works alongside most Fabric mods  
- Recipe coverage: vanilla + many mods via native recipe types and auto-detection  
- Some highly custom machine UIs may show incomplete layouts; brewing, loot, and ores have dedicated sources  
- Not a JEI/REI/EMI plugin — it does not replace those APIs; it is a standalone browser  

---

## FAQ

**Is this JEI / REI / EMI?**  
No. Same idea (browse items & recipes), different mod. You don’t need those installed.

**Can I use it on a server?**  
Players install it on the **client**. The server does not need RBEI.

**Does transfer work everywhere?**  
Transfer **+** works when you opened RBEI from a crafting table or furnace screen with a compatible grid.

**Where are favorites stored?**  
Locally in `config/rbei-client.json`.

---

## License & author

- **License:** MIT  
- **Author:** max  
- **Mod ID:** `rbei`

## Links

- [Modrinth](https://modrinth.com/mod/rbei)  
- [Source (GitHub)](https://github.com/orpx0lyks-ux/rbei)  
- [Issues / feedback](https://github.com/orpx0lyks-ux/rbei/issues)  
- [Wiki / docs](https://github.com/orpx0lyks-ux/rbei/tree/main/docs)  

---

*Thanks for downloading. Feedback and bug reports welcome on [GitHub Issues](https://github.com/orpx0lyks-ux/rbei/issues).*
