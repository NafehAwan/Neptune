# Tuff Client design concepts

Five design systems for Tuff Client, plus **Grove Glass** (Grove with frosted,
see-through panels). Each concept has its own colours, fonts, logos and
animated background, shown on the title screen, the mod menu and the launcher.

| # | Name | Fonts | Background |
|---|---|---|---|
| 1 | Obsidian | Geist Sans + Geist Mono | Dot grid rippling from a drifting spotlight |
| 2 | Glacier | Manrope | Aurora over low-poly mountains, falling snow |
| 3 | Ember | Sora | Sparks rising over a glowing, blocky horizon |
| 4 | Prism | Outfit + Unbounded | Morphing violet/pink mesh over a faint grid |
| 5 | Grove | Plus Jakarta Sans + Fraunces | Parallax pixel forest with a slow day cycle |
| 6 | Grove Glass | as Grove | as Grove, seen through frosted panels |

Pictures are in `out/`: `00-overview.png`, then per design `NN-name-board`,
`-title`, `-menu`, `-launcher` and `-motion` (three moments of the
background), plus `logos.png` (two marks per design).

Grove Glass also has an animated demo, `out/groveglass-demo.mp4`: the scene
moving (sun arc, rays, clouds at three depths, swaying trees, birds, falling
leaves), then the account menu opening with a spinning 3D skin head, adding an
offline account (it gets the default head because it has no skin) and switching
to it. Stills: `06-groveglass-account*.png`.

## Rebuild

```sh
cd tuff-client/design
npm install
node make-icons.mjs                                   # Lucide icons -> icons.js
PLAYWRIGHT_PATH=$(npm root -g)/playwright node render.mjs
PLAYWRIGHT_PATH=$(npm root -g)/playwright node video.mjs   # needs ffmpeg
```

Open `page.html?t=groveglass&s=title&time=3` in a browser to see a screen
live (`s` = board, title, menu, launcher, logos, overview, motion).
