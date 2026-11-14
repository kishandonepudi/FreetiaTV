# Demo playlist for store screenshots

`demo.m3u` is the playlist used to capture the store screenshots in `store/assets/screenshots/`.
Store screenshots must not show real broadcasters' names or logos, so:

- All 24 channel names are invented and neutral ("Demo News 1", "Nature HD", "Kids Fun", ...).
- No `tvg-logo` is set, so the app shows its initials fallback.
- Categories come from `group-title`: News, Movies, Kids, Sports, Music, Documentary.

## Stream licences
Every channel points at one of two freely available open test streams:

| Stream | URL | Licence |
|---|---|---|
| Big Buck Bunny (odd channel numbers) | https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8 | CC BY 3.0, (c) Blender Foundation, www.bigbuckbunny.org |
| Apple HLS "bipbop" example (even channel numbers) | https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8 | Apple's public HLS test stream, published for developer testing |

Big Buck Bunny is credited as required by CC BY. Before using a frame of the Apple test stream in a store
listing, check Apple's terms; the published screenshots show only Big Buck Bunny.

## Recapturing
1. Serve this folder: `python -m http.server 8765` (run in `store/demo`).
2. On the Android TV emulator the host is `http://10.0.2.2:8765/demo.m3u`.
3. Install the `store` debug APK, `adb shell pm clear com.freetia.tv`, add the link, name it "My TV".
4. Capture with `adb exec-out screencap -p > NN-name.png` (1920x1080).
