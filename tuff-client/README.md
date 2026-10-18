# Glade

A calm, minimal Minecraft launcher with its own in-game mod. Its look is
**Grove Glass**: soft paper, forest green and frosted see-through panels over
an animated pixel forest.

```
tuff-client/
  launcher/   Tauri 2 app (Rust backend + Svelte 5 UI), ships as a .deb
  mod/        Glade Fabric mod for Minecraft 26.3 (bundled into the launcher)
  art/        logo, fonts and icon sources
  design/     the design concepts and mockups the look was picked from
```

Both use Plus Jakarta Sans for text and Fraunces italic for the wordmark (SIL
Open Font License), and draw the same animated grove, so the launcher and the
game look alike.

## Launcher

- **Accounts**: Microsoft sign-in (device code, several accounts, silent token
  refresh) and offline accounts for singleplayer and offline-mode servers. The
  account chip shows a 3D skin head; its menu switches accounts and adds new
  ones. Premium accounts show their real skin from Mojang; offline accounts get
  the default skin, read from your installed Minecraft so Glade never ships
  Mojang's art.
- **3D heads**: every head is a real 3D model, and the skin's outer layer stands
  off the head as individual blocks.
- **Profiles**: any release or snapshot, vanilla or Fabric. Each profile has its
  own folder, memory, JVM arguments, Java, resolution and icon. Libraries,
  assets and Java runtimes are shared and checked with SHA-1.
- **Glade presets**: ready-made Fabric 26.3 profiles with the Glade mod, each
  in its own folder so worlds, mods and settings never mix (create a preset
  twice and you get a second folder):
  - *Performance*: Sodium, Lithium, FerriteCore, ModernFix, ImmediatelyFast,
    Entity Culling, More Culling, Dynamic FPS and BadOptimizations. The game
    looks exactly the same.
  - *Performance+*: adds C2ME, ScalableLux, Krypton, Sodium Extra, Reese's
    Sodium Options, Enhanced Block Entities and Clumps, with fast graphics
    and an 8 chunk view.
  - *Performance++*: adds Noisium, VMP and Ksyxis, with the leanest settings
    and a 6 chunk view, for low-end PCs and laptops.
  - *Scenic*: the core optimisations plus Iris with Complementary Reimagined,
    Continuity, LambDynamicLights, Entity Model and Texture Features with
    Fresh Animations, Falling Leaves and Visuality.
  Mods without a build for the profile's version are skipped and listed.
  Custom profiles take any version, vanilla or Fabric. The list lives in
  `launcher/src-tauri/src/presets.rs`.
- **Mods**: Modrinth search for mods, resource packs, shaders and modpacks,
  filtered to the profile. Installs required dependencies (and Iris for
  shaders), checks for updates in one request, lets you toggle or remove
  content, and imports `.mrpack` files.
- **Servers**: live player counts, MOTD, favicon and ping (Server List Ping
  with SRV lookup). Join goes straight into the server.
- **Wardrobe**: 3D skin preview with animations; upload or reset skins, pick
  classic or slim arms, and switch capes.
- **Screenshots**: a gallery across all profiles.
- **Console**: live game log with level filters and search, plus a plain-language
  explanation when the game crashes.
- **Performance**: Java is downloaded automatically for each version, with
  tuned G1 flags. The home scene pauses when the window is unfocused or hidden
  (and stays still with Reduce motion), and the window can minimize or hide
  while you play.
- **Extras**: Discord Rich Presence and keyboard shortcuts (`Ctrl+1`–`8` to
  switch pages, `Ctrl+Enter` to play).

Data lives in `~/.local/share/glade` (set `GLADE_HOME` to move it). An older
`tuff-client` folder is moved there on first start.

### Build

Requires Node 22+, Rust (stable) and the WebKitGTK and GTK3 dev packages:

```sh
sudo apt install libwebkit2gtk-4.1-dev libgtk-3-dev librsvg2-dev libssl-dev
cd tuff-client/launcher
npm ci
npm run tauri dev                    # run in development
npx tauri build --bundles deb        # build src-tauri/target/release/bundle/deb/*.deb
```

`npm run dev` opens the UI in a plain browser with sample data, which is handy
for UI work.

### Microsoft login setup

Minecraft only accepts sign-ins from Azure apps that Mojang has approved:

1. Register an app at <https://portal.azure.com> under **App registrations**.
   Choose *Personal Microsoft accounts only* and enable **Allow public client
   flows** under Authentication.
2. Request Minecraft API access for it using Mojang's form for third-party
   launchers (linked from the Minecraft Help Center).
3. Put the client ID in the launcher (Settings → Accounts), or build with
   `TUFF_MS_CLIENT_ID=<id>` so it's baked in. The CI workflow reads it from the
   `TUFF_MS_CLIENT_ID` repository secret.

Until then, offline accounts work for singleplayer.

### Discord Rich Presence

Create an application at <https://discord.com/developers>, upload an art asset
named `tuff`, and paste the application ID in Settings → Discord (or build with
`TUFF_DISCORD_APP_ID`).

## Glade mod

Fabric mod for Minecraft 26.3 (Java 25). The launcher installs it into Glade
profiles automatically.

- **Title screen**: the animated grove (a sun on a slow arc with turning rays,
  blocky clouds at three depths, birds, rolling hills, three layers of trees
  swaying in the wind and falling leaves) drawn at full screen resolution, with
  a frosted glass card holding the leaf, the wordmark and the main buttons.
- **Account menu**: the chip top left shows a 3D head of the current account.
  Its menu lists every account with its own 3D head (outer layer as real
  blocks), adds offline accounts and switches to them on the spot. Microsoft
  accounts need their sign-in, so switching to another one happens in the
  launcher; switching back to the account the game started with always works.
  The launcher shares only account names and kinds with the game, never tokens.
- **Mod menu** (Right Shift): a sidebar (Mods, Theme, Packs, Settings, HUD)
  beside a light glass panel, at a steady size at any GUI scale.
  - *Mods*: filter chips (All, New, Favorites, HUD, Utility, Visual), search,
    and a grid of cards with a big icon or a live HUD preview, a heart to
    favorite, and a switch. Grid or list view; right-click or the gear opens a
    settings drawer (switches, choices and sliders).
  - *Theme*: accent color (Forest by default, Copper, Amethyst, Mint, Sky,
    Rose, Gold, Red), background blur and animations.
  - *Packs*: enable, disable and reorder resource packs.
  - *Settings*: title screen and background toggles, keybinds, HUD reset.
- **HUD editor**: drag modules anywhere, with snapping to screen edges, the
  center and other modules. Scroll to resize; right-click to reset.
- **HUD modules**: FPS, CPS, Keystrokes, Armor Status, Coordinates and
  direction, Ping, Potion Effects, Clock, Server address, Speed, Memory.
- **Utility and visual**: Zoom (hold C, smooth and with optional cinematic
  camera), Fullbright, Toggle Sprint, Crosshair, Hide Scoreboard.

Settings are saved to `config/tuff/config.json`.

```sh
cd tuff-client/mod
./gradlew build      # build/libs/tuff-client-1.0.0.jar
```

### Art

- `art/logo.mjs` draws the leaf logo and writes the app icons, favicon, mod
  icon and title emblem.
- `art/fonts.py` cuts static Plus Jakarta Sans and Fraunces files from the
  Google Fonts variable fonts and writes the mod's font definitions.
- `art/sprites.py` builds the mod's pixel icons and rounded-corner sprites.
- `art/icons.mjs` renders the menu's Lucide line icons (ISC license).

## CI

`.github/workflows/tuff-client.yml`:

- builds the mod and uploads the jar;
- boots real Minecraft with the mod (Fabric client game test on Mesa's software
  Vulkan driver). The test checks that the Glade title screen replaces the
  vanilla one and its fonts load, opens the account menu, adds an offline
  account, switches to it and back, opens every mod menu page, the settings
  drawer and the HUD editor in a world, checks that zoom magnifies the rendered
  image and that fullbright toggles cleanly, and saves screenshots of each step
  (`./gradlew runClientGameTest` locally);
- bundles the mod into the launcher, runs the Rust tests and Svelte type
  checks, and uploads the `.deb`.

Glade is not an official Minecraft product and is not approved by or
associated with Mojang or Microsoft.
