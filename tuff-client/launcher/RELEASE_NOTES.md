Glade 1.8.5
- New in-game modules, like Lunar Client's: Motion Blur, Hitboxes, Cooldowns (pearl, chorus fruit and more, with time left), Shiny Pots, Glint Color, Hurt Cam, Minimal Bobbing, Low Health Warning, Snaplook, FOV Changer, Saturation, TNT Timer, Clear Glass, Nick Hider, Chat Macros and Tab Editor (ping as a number and player heads on every server).
- Toggle Sprint now does Toggle Sneak too, can switch with the sprint key, and shows "[Sprinting (Toggled)]" on screen. Item Counter counts splash potions.
- More options on existing modules: CPS left or right only, Clock seconds and date, Speed units, Reach decimals, Potion Effects icons, names, Roman numerals and blinking, Keystrokes size, spacing, arrows and CPS labels, Coordinates decimals and one axis per line, Crosshair any color, thickness, center dot, circle style, a color when aiming at a player or mob and third person, Scoreboard without the red numbers, Combo hide at zero, colored Ping and Memory as a percent.
- Fixed: F4 saved a clip instead of starting Freelook.
- Motion Blur needs Minecraft 1.21.6 or newer.

Glade 1.8.4
- Start-up fixed: the start screen now waits for the launcher check and then updates the Glade mod files itself ("Updating Glade…"), so nothing starts updating after you press Play. It only says "You're up to date" when both worked, and says so when the update server can't be reached.
- Mod files are never replaced while Minecraft is starting or running.
- The 3D character has proper 3D skin layers: every pixel of your skin's outer layer (hats, jackets, hair) stands out a little, like the 3D Skin Layers mod, in the skin's exact colours. Turning the Glade hoodie off no longer breaks the skin.
- The Glade hoodie fits every skin, including slim (Alex) skins, and no longer paints brown hair over yours.
- The maximize button is back.
- Kill clips and Giveaways are now the top row of Latest news.

Glade 1.8.3
- Microsoft sign-in fixed: it always opens Microsoft's own sign-in page in a window, with no code to type. A client ID saved by older versions used to switch it to the code method, which couldn't sign in; that setting is gone.
- The sign-in window is private, so you can add a different Microsoft account each time.
- "Add Microsoft account" in the account menu opens the sign-in window straight away.
- If an account added with an old client ID says its session expired, remove it and sign in again.

Glade 1.8.2
- A start screen: Glade checks for a launcher update before opening. If there is one, it installs first; offline or slow checks give up after a few seconds so the launcher still opens.
- Darker backdrops behind dialogs, to match the new look.

Glade 1.8.1
- Your character on Home now wears the Glade hoodie and headphones over your own skin. Turn it off in Settings ("Glade hoodie").
- Home matches the new design: green markers on news cards, a featured server card you can join in one click, the mod count under Play, and Friends with an "Add a friend" button (friends are coming soon).
- The sidebar now has Play, Mods, Servers, Profiles and Store up top, and Wardrobe, Screenshots, Console and Settings at the bottom.
- The account button shows your face, and a new mail button opens what's new.

Glade 1.8.0
- A new look for the launcher: a dark theme, a slim menu on the left, and new art throughout.
- Home: your character in 3D with proper lighting and 3D skin layers (hats, hoodies and hair stand out). Non-premium accounts show Steve.
- A new Play button, with the version and your profile right under it.
- Quick Play along the top: popular servers with their real server icons. Click one to join it.
- Latest news cards, and Friends, Discord and Store spots (coming soon).
- A new sign-in screen on first start, and pictures for crashes, empty profiles and being offline.
- New fonts: Geist, with Unbounded on the Play button.

Glade 1.7.1
- Microsoft sign-in works: Glade now signs in the same way as the official Minecraft launcher. Click Add account, then Sign in with Microsoft, and Microsoft's sign-in page opens in a window. Sign in there and it closes by itself; no code to type. If you had pasted a client ID in Settings, clear it.

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
