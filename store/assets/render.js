// Renders Freetia store assets from the exact vector path data in app/src/main/res/drawable (BRAND_KIT §2.1, §9).
// Usage: node render.js <outDir> <wordmarkPngOut>
const { Resvg } = require('@resvg/resvg-js');
const fs = require('fs');
const path = require('path');

const out = process.argv[2];
const wordmarkOut = process.argv[3];
fs.mkdirSync(out, { recursive: true });
const FONTS = ['Roboto_700Bold.ttf', 'Roboto_400Regular.ttf'].map((f) => path.join(__dirname, 'node_modules/@expo-google-fonts/roboto', f));

const BLUE = '#4C8DFF', VIOLET = '#8B5CF6', INK = '#0B0E14', MUTED = '#9AA3B2';
const F_BODY = 'M43,33 H67 A5,5 0 0 1 67,43 H48 V70 A5,5 0 0 1 38,70 V38 A5,5 0 0 1 43,33 Z';
const F_PLAY = 'M53,48.5 Q52,48 52,49.2 V65.8 Q52,67 53,66.5 L69,58.6 Q70.5,57.5 69,56.4 Z';
const SQUIRCLE = 'M28,0 H80 A28,28 0 0 1 108,28 V80 A28,28 0 0 1 80,108 H28 A28,28 0 0 1 0,80 V28 A28,28 0 0 1 28,0 Z';

const glyph = `<path fill="#FFFFFF" d="${F_BODY}"/><path fill="#FFFFFF" d="${F_PLAY}"/>`;
// ic_brand_mark: squircle tile, glyph scaled 1.35 about (54,54).
const tile = (id) => `<path fill="url(#${id})" d="${SQUIRCLE}"/>
  <g transform="translate(54 54) scale(1.35) translate(-54 -54)">${glyph}</g>`;
const diag = (id, x2, y2) => `<linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="${x2}" y2="${y2}">
  <stop offset="0" stop-color="${BLUE}"/><stop offset="1" stop-color="${VIOLET}"/></linearGradient>`;
// Wordmark: Roboto Bold, letter-spacing -0.5% of the font size.
const wordmark = (x, y, size, anchor = 'start') =>
  `<text x="${x}" y="${y}" font-family="Roboto" font-weight="700" font-size="${size}" letter-spacing="${-0.005 * size}" fill="#FFFFFF" text-anchor="${anchor}" dominant-baseline="central">Freetia TV</text>`;
const ambient = (w, h) => `<rect width="${w}" height="${h}" fill="${INK}"/>
  <radialGradient id="glowV" cx="${0.22 * w}" cy="${0.35 * h}" r="${0.6 * w}" gradientUnits="userSpaceOnUse">
    <stop offset="0" stop-color="${VIOLET}" stop-opacity="0.22"/><stop offset="1" stop-color="${VIOLET}" stop-opacity="0"/></radialGradient>
  <radialGradient id="glowB" cx="${0.85 * w}" cy="${0.9 * h}" r="${0.6 * w}" gradientUnits="userSpaceOnUse">
    <stop offset="0" stop-color="${BLUE}" stop-opacity="0.16"/><stop offset="1" stop-color="${BLUE}" stop-opacity="0"/></radialGradient>
  <rect width="${w}" height="${h}" fill="url(#glowV)"/><rect width="${w}" height="${h}" fill="url(#glowB)"/>`;

// Play's feature graphic and Amazon's Fire TV icon/background must be 24-bit PNG with no alpha channel,
// but resvg always writes RGBA, so opaque assets are re-encoded as colour type 2 (RGB).
const CRC = new Int32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c; });
const crc32 = (buf) => { let c = -1; for (const b of buf) c = CRC[(c ^ b) & 255] ^ (c >>> 8); return (c ^ -1) >>> 0; };
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function rgbPng(rgba, w, h) {
  const raw = Buffer.alloc((w * 3 + 1) * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const s = (y * w + x) * 4, d = y * (w * 3 + 1) + 1 + x * 3;
    raw[d] = rgba[s]; raw[d + 1] = rgba[s + 1]; raw[d + 2] = rgba[s + 2];
  }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 2;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr),
    chunk('IDAT', require('zlib').deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

function render(file, w, h, body, opts = {}) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">${body}</svg>`;
  const img = new Resvg(svg, {
    fitTo: { mode: 'width', value: w },
    background: opts.transparent ? undefined : INK,
    font: { fontFiles: FONTS, loadSystemFonts: false, defaultFontFamily: 'Roboto' },
  }).render();
  const png = opts.transparent ? img.asPng() : rgbPng(img.pixels, img.width, img.height);
  fs.writeFileSync(file, png);
  console.log(file, w + 'x' + h, png.length, 'bytes');
}

// Play / Amazon icon: adaptive-icon composition (full-bleed gradient + glyph on the 108 grid), no mask.
const icon = (size) => `<defs>${diag('g', 108, 108)}</defs><g transform="scale(${size / 108})">
  <rect width="108" height="108" fill="url(#g)"/>${glyph}</g>`;
render(path.join(out, 'icon-512.png'), 512, 512, icon(512));
render(path.join(out, 'icon-114.png'), 114, 114, icon(114));

// Feature graphic 1024x500: ambient background, tile and wordmark on the left.
{
  const t = 200, x = 96, y = (500 - t) / 2, gap = 0.4 * t;
  render(path.join(out, 'feature-graphic-1024x500.png'), 1024, 500,
    `<defs>${diag('g', 108, 108)}</defs>${ambient(1024, 500)}
     <g transform="translate(${x} ${y}) scale(${t / 108})">${tile('g')}</g>
     ${wordmark(x + t + gap, 232, 84)}
     <text x="${x + t + gap}" y="304" font-family="Roboto" font-weight="400" font-size="30" fill="${MUTED}" dominant-baseline="central">Your playlists on the big screen</text>`);
}

// TV banner 1280x720: gradient, glyph and wordmark (Play TV banner and Amazon Fire TV app icon).
{
  const s = 6.4; // glyph bounds x38-72, y33-75 on the 108 grid
  const gx = 150 - 38 * s, gy = 360 - 54 * s;
  render(path.join(out, 'tv-banner-1280x720.png'), 1280, 720,
    `<defs>${diag('g', 1280, 720)}</defs><rect width="1280" height="720" fill="url(#g)"/>
     <g transform="translate(${gx} ${gy}) scale(${s})">${glyph}</g>
     ${wordmark(150 + 34 * s + 0.4 * 34 * s, 360, 150)}`);
}

// Fire TV background 1920x1080: ambient only. Amazon draws its own UI and the app icon over it, and a faded tile would recolour the glyph (BRAND_KIT §2.2).
render(path.join(out, 'firetv-background-1920x1080.png'), 1920, 1080,
  ambient(1920, 1080));

// In-app banner wordmark (transparent) for res/drawable/tv_banner.xml: 176x48dp, 3x pixels, in drawable-nodpi (the layer-list sets its dp size).
if (wordmarkOut) {
  render(wordmarkOut, 528, 144, wordmark(0, 72, 108), { transparent: true });
}
