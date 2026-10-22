Glade 1.7.0
- New FPS Boost module (on by default): turns off clouds, entity shadows and biome blending, and stops drawing mobs and items past 48 blocks. Each part can be switched off; turning the module off puts the game's defaults back.
- The Right Shift HUD editor fades in and out, and its card, module bars and hints now use Glade's menu fonts and style at a smaller size.
- Glade is now tested on Minecraft 1.21.11 in game, and on Windows (also next to Sodium).

Glade 1.6.4
- Sharper in-game menus: Glade's title screen, menu and HUD editor now put every letter, corner and icon on whole screen pixels, so nothing looks broken up, whatever your window size.
- The title screen card is smaller, and the version text at the bottom is smaller too.
- The card in the middle of the HUD editor is solid, so the crosshair no longer shows through it.

Glade 1.6.3
- Right Shift in game now opens the HUD editor: your HUD stays on screen and you can drag modules to move them, drag a module's corner to resize it, and use its bar to open its settings or remove it. Right-click a module for its settings. The card in the middle opens Mods & Settings, plus Theme, Packs and Settings. Right Shift again closes it.
- Nametags no longer overlap a server's own nametags: when a server puts its own name above a player, Glade hides the normal one (Nametags, "Hide nametags under a server's own nametags"). Your own nametag in third person also respects servers that hide nametags.

Glade 1.6.2
- The leaf badge now shows next to Glade players in the tab list too (Cosmetics, "Badge in the tab list").
- Choose which side of the name the badge goes on: Left or Right (Cosmetics, "Badge side").

Glade 1.6.1
- The badge next to Glade players' names is now the Glade leaf logo (a lighter green so it stands out on nametags).
- Silver tier 2 ranks are brighter on nametags.
- New Tiers module: MCTiers and PvPTiers ranks next to nametags. Pick a gamemode for each list (or their best one), put each on the left or right of the name, and see a pixel icon for the gamemode: a skull for Neth Pot, sword, axe, potion, end crystal, shield, golden apple and mace. Tiers are colored gold, silver and bronze; retired tiers are pale blue.
- Glade's menu and title screen stay pixel-sharp on smaller screens too (like 1366x768 laptops), not just 1080p and up.
- Clips: when you kill a player, Glade saves the last 15 seconds (you choose 5 to 60, plus a moment after) as a video in Videos/Glade, recorded like a screen recorder with game sound. Also on death or a key. Choose frame rate, resolution and quality in the Clips settings. Recording runs in its own low-priority process so your FPS stays put. Needs ffmpeg (sudo apt install ffmpeg).
- New HUD modules: Reach (how far your hits land) and Combo (hits in a row).
- HUD Profiles: keep three HUD layouts and switch between them.
- Tab Ping: ping as a number in the player list (1.21).
- Entity Distance: stop drawing far away mobs and items for more FPS.
- Chat+ can hide messages containing words you choose.
- Discord shows where you're playing (server or singleplayer).
- Crisper, smaller Glade menu: text, icons and corners now land exactly on your screen's pixels, and the menu takes less of the screen.
- Set a module's key right in its settings (Zoom, Freelook, Waypoints): click the key, press any key or a side mouse button, Esc clears it.
- Freelook is on F4 by default.
- New Scoreboard module (replaces Hide Scoreboard): hide the scoreboard or just remove its background.
- New Menu Background module: no dark tint behind your inventory and chests, and optionally no blur behind menus.
- HUD text modules: choose the font (Default or Small) and the format, like "FPS 60", "FPS: 60", "60 FPS" or "[FPS 60]".
- Nametags no longer change the scoreboard's background.
- The launcher checks for updates every 5 minutes and whenever you come back to it.
