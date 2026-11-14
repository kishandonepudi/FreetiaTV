# Freetia brand kit

Version 1.0 (14 Nov 2026). This applies to every Freetia app on phone, tablet, Android TV and Fire TV.

## 1. Brand in one line
Free, calm, familiar. A Freetia app should feel like a good TV remote: obvious, quick and never in the way.

## 2. Logo

### 2.1 The mark
The glyph is a bold **"F" whose middle arm is a detached play triangle**, in white on a blue→violet gradient tile.
- The F stands for Freetia.
- The triangle stands for playback.
- The gap between them makes the mark ownable and keeps it legible at 16 px.

| Asset | File | Use |
|---|---|---|
| Glyph (white, 108-unit grid) | `app/src/main/res/drawable/ic_launcher_foreground.xml` | Adaptive-icon foreground, monochrome icon, banner |
| Gradient background | `app/src/main/res/drawable/ic_launcher_background.xml` | Adaptive-icon background |
| In-app tile (squircle + glyph) | `app/src/main/res/drawable/ic_brand_mark.xml` | Headers, splash, empty states, about |
| TV banner | `app/src/main/res/drawable/tv_banner.xml` | Android TV / Fire TV launcher row |

**Geometry** (108×108 grid, adaptive-icon safe zone is the central 66 units):
- F body: `M43,33 H67 A5,5 0 0 1 67,43 H48 V70 A5,5 0 0 1 38,70 V38 A5,5 0 0 1 43,33 Z`
- Play arm: a soft-cornered triangle from x=52 to x=70.5, centred on y=57.5. There's a 4-unit gap from the stem.
- Glyph bounds: x 38–72, y 33–75. Optical centre (55, 54).

### 2.2 Rules
- **Required placements:** in every app,
  - the launcher icon and TV banner
  - the top-left of the home/browse screen header, at 30 dp
  - the splash/loading screen
  - the About screen
- **Clear space:** at least 25% of the tile's width on all sides. Minimum size: 24 dp in-app, 16 px for favicons.
- **Never:**
  - recolour the glyph (it's white only, or brand ink on light backgrounds)
  - outline or rotate it, or add a shadow inside the tile
  - stretch it
  - put text inside the tile
  - re-draw it by hand: copy the path data
- **Sub-brands:** future Freetia apps keep the same tile and gradient and put their own name in the wordmark ("Freetia Radio", for example). Don't invent new glyphs per app.
- **Wordmark:** "Freetia" in the system sans (Roboto on Android), Bold, letter-spacing −0.5%. It sits to the right of the tile with a gap of 40% of the tile width.

### 2.3 Trademark clearance (do this before any store submission)
The mark is built only from basic shapes (an F and a triangle) on purpose, so it can't copy anyone's artwork. It has **not** been legally cleared. Before launch:
1. Search the name "Freetia" and the figurative mark in:
   - WIPO Global Brand Database: <https://branddb.wipo.int>
   - USPTO: <https://tmsearch.uspto.gov>
   - IP India: <https://ipindia.gov.in>
   - EUIPO TMview: <https://www.tmdn.org/tmview>
2. Classes to check:
   - 9 (software)
   - 38 (streaming)
   - 41 (entertainment)
3. Record the search date and result in `standards/TRADEMARK_LOG.md`. Have a trademark attorney confirm before filing.
4. Avoid confusion with existing play-button logos in the same classes. Never put the triangle alone in a circle or rounded square: that is the most crowded form.

## 3. Colour
| Token | Hex | Use |
|---|---|---|
| `brand_blue` | `#4C8DFF` | Primary accent, focus gradient start, links, live progress |
| `brand_violet` | `#8B5CF6` | Gradient end, ambient glow |
| `brand_ink` | `#0B0E14` | App background (dark theme) |
| `surface` | `#161B26` | Cards, panels |
| `surface_variant` | `#1F2633` | Raised panels, inputs |
| `text_primary` | `#FFFFFF` | Titles, focused text |
| `text_muted` | `#9AA3B2` | Secondary text, counts, timestamps |
| `live_red` | `#FF3B47` | LIVE badge only |
| `error` | `#FF5A5F` | Errors and warnings |

- **Focus gradient:** `brand_blue → brand_violet`, left to right. It's the only gradient used for interactive states.
- **Contrast:** body text must reach at least 4.5:1 on its background, and large text (18 sp and up) at least 3:1. `text_muted` on `brand_ink` is 7.4:1. Don't put muted text on the focus gradient.
- **High-contrast mode** (for seniors): the background becomes pure black, muted text becomes white, and the focus border goes from 2 to 4 dp.
- Code tokens live in `res/values/colors.xml` and `ui/theme/Theme.kt`. Change both together.

## 4. Typography
Use the system sans only; don't bundle fonts. Sizes are for a 960×540 dp TV canvas. Phones use the same values.

| Role | Size / weight | Example |
|---|---|---|
| Screen title | 22 sp Bold | Playlist name |
| Panel title | 18–20 sp Bold | Channel name in details or banner |
| Row title | 16 sp SemiBold | Channel name in list |
| Body | 14–16 sp Regular | Descriptions |
| Meta / caption | 12–13 sp | Group, time, counts |
| Overline | 11 sp Bold, +1 sp tracking, caps | "NEXT HOUR" |
| Badge | 10 sp Bold, caps | LIVE |

- Nothing below 10 sp, and only badges may use 10–11 sp.
- Line height is 1.3 em: an em value, not fixed sp, so text never clips inside fixed-height rows.
- Accessibility: text must survive the system font scale at 1.3×. Use `maxLines` plus ellipsis rather than a fixed height for text-only containers.

## 5. Layout
- **Grid:** 4 dp base. The spacing scale is 4, 6, 8, 12, 16, 20, 24, 32, 48.
- **TV safe area:** 48 dp left and right, 24–27 dp top and bottom. Nothing interactive goes outside it.
- **Browse screen on TV:** three panes,
  - category rail, 176 dp
  - list, flexible width
  - details panel, 300 dp

  Phones (narrower than 720 dp) use a chip row above the list instead.
- **Depth:** no screen is more than two levels below Home.
- **Hint bar:** every screen ends with key-cap hints, for example `[OK] Watch  [↑↓] Browse  [Back ×2] Exit`.

## 6. Shape, elevation, motion
- **Corner radii:**
  - 6 dp for logo wells
  - 8–10 dp for rows
  - 12–16 dp for panels
  - 50% for pills and chips
- **Elevation:** use tone, not shadows. Surfaces are white at 5–8% alpha over ink, and panels get a 1 dp white border at 8% alpha.
- **Focus state** (required on every focusable element):
  - focus gradient fill or a white pill
  - 2 dp white border
  - scale 1.02–1.03
  - 150 ms tween
- **Motion:**
  - 150 ms for focus
  - 300 ms for fades (preview video, banners)
  - no motion longer than 400 ms
  - no bouncing or parallax
- **Ambient background:** two radial glows (violet at the top-left at 22% alpha, blue at the bottom-right at 16% alpha) over ink. Use it on browse and home screens only, never behind video.

## 7. Components
| Component | Spec |
|---|---|
| Channel row | 52 dp tall. Number (14 sp, right-aligned, 36 dp wide), logo well (56×36 dp), title and group |
| Category item | 40 dp pill. Name and count. Focused: white fill with ink text. Selected but not focused: 18% blue tint |
| Details panel | 16:9 live preview, name, channel number, up to 3 chips, then up to 3 "NEXT HOUR" programmes, then the OK hint |
| Live preview | Muted, at most 480p, starts after 900 ms of focus, fades in over 300 ms, LIVE badge top-left. Turns off automatically on low-RAM devices |
| Info banner | Bottom gradient overlay. Logo (64×40 dp), number in `brand_blue`, name, meta line, hint bar. Auto-hides after 5 s |
| Chip | 12 sp, pill, white at 12% alpha |
| Key cap | 12 sp Bold, 4 dp radius, white at 20% alpha |

## 8. Voice and copy
- Plain, short, friendly. Write "This channel isn't available right now", not "Error 0x80".
- Say what to do next: "Press ↑ or ↓ for another channel."
- Never name channel providers or playlist sources in store copy or UI chrome.
- Sentence case everywhere except overlines and badges.

## 9. Store assets (export from the vector sources)
| Asset | Size |
|---|---|
| Play icon | 512×512 PNG (tile and glyph, full bleed, no rounded mask) |
| Play feature graphic | 1024×500 (ambient background, tile and wordmark on the left) |
| TV banner | 1280×720 (gradient, glyph and wordmark) |
| Amazon icons | 512×512 and 114×114 |
| Screenshots | 1920×1080 on TV; phone at the device's native size |

## 10. Checklist used by `brand-ux-reviewer`
- [ ] B1 Logo shown in every required placement, using the vector assets
- [ ] B2 Colours come from tokens only; no new hex values without updating §3
- [ ] B3 Text sizes from §4; nothing below 12 sp except badges; em line heights
- [ ] B4 TV safe area respected; works at 960×540 dp and on a phone narrower than 720 dp
- [ ] B5 Every focusable element has the §6 focus state and is reachable by D-pad
- [ ] B6 Every screen has a hint bar and no screen is more than 2 levels deep
- [ ] B7 Copy follows §8; no provider names
- [ ] B8 Contrast meets §3; high-contrast mode still readable
