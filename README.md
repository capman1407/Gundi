# Gundi

An Apple-style addon for [Meteor Client](https://meteorclient.com) on **Minecraft 1.21.11**. It adds clean glass HUD elements, six GUI themes, a themed chat window, nametags, a custom crosshair and cosmetics.

## Download

1. Install [Fabric](https://fabricmc.net/use/installer/) for Minecraft **1.21.11**.
2. Put [Meteor Client](https://meteorclient.com) (1.21.11 build) in your `mods` folder.
3. Download the latest `gundi-*.jar` from the [Releases](../../releases) page and put it in the same `mods` folder.
4. Start the game. Gundi's modules are in the **Gundi** category, and its HUD elements are in the **Gundi** group of Meteor's HUD editor.

## Features

### GUI themes
Six full Meteor GUI themes: **Apple, Neon, Terminal, Noir, Aurora and Arcade**. They include a theme gallery and Spotlight-style search. HUD elements and modules can follow the selected theme automatically.

### HUD elements
| Element | What it does |
| --- | --- |
| Gundi Chat | A chat window that matches your theme. It has tabs (All, Players, Whispers, System), player heads, stacked repeats, mention pings, keyword highlights and right-click to copy. |
| Dynamic Island | An iPhone-style pill that shows module toggles. |
| Glass Coords | Coordinates with Nether/Overworld conversion. |
| Glass Clock | Real-time clock with a Minecraft day/night arc. |
| Compass Bar | Compass strip with player and death markers. |
| Radar | Circular radar with players, chests and directions. |
| Speed Graph | Your speed with a live graph. |
| Armor Rings | Armor durability as Apple Watch-style rings. |
| Pickup Feed | Notification cards for items entering your inventory. |
| Area Titles | Cinematic title when you enter a new biome or dimension. |
| Spiral Scout | Progress panel for the Spiral Scout module. |

### Modules
| Module | What it does |
| --- | --- |
| Apple Nametags | Nametags with head, health, gear and enchants. |
| Crosshair | Custom crosshair with many shapes, theme colors, movement spread and hit feedback. |
| Hit Marker | Shooter-style hit marker on your crosshair. |
| Spiral Scout | Flies an endless square spiral with ElytraFly to load a big area and find bases. Saves progress. |
| Wings | Cosmetic angel wings (only visible to you). |
| Glow Trail | A soft glowing ribbon that follows behind you (only visible to you). |

## Building from source

Requires JDK 21 or newer.

```bash
./gradlew build
```

The jar is written to `build/libs/`. To test in a dev client, run `./gradlew runClient`.

## License

[GPL-3.0](LICENSE)
