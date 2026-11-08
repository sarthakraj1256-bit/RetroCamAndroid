# RetroCam feature matrix

Legend: `[x]` means implemented in source; `[~]` means wired in source but not build/runtime verified; `[ ]` means not implemented.

## Camera and photos
- [x] Native Android project foundation
- [x] CameraX preview, front/back switching, tap focus/AE, zoom, flash, torch capability checks and rule-of-thirds grid
- [x] Photo capture to RetroCam MediaStore folder
- [x] Still-photo retro looks using off-screen GLES with CPU fallback
- [x] Still-photo grain, scanlines, vignette, chromatic split and strength control
- [x] Apply still-photo look after capture or to imported photos
- [x] Photo Studio: EXIF correction, rotate 90°, ±10° straighten, aspect presets with crop repositioning, look, brightness, contrast and saturation
- [x] Save edited photo as a separate copy; original import stays intact
- [~] CameraX Media3Effect live preview and video capture bridge (colour grade + grain/scanline/vignette/VHS-DV texture overlay; build/device verification required)
- [ ] True live/video RGB chromatic split and identical still/live look parity
- [x] 3s/10s photo timer and device-supported exposure compensation
- [ ] Manual ISO/shutter/focus/white balance and aspect-ratio guides
- [ ] Advanced photo curves, selective HSL, freeform crop/straighten, healing/retouch, masks and persistent edit history

## Video and editing
- [x] CameraX video recording with device-supported quality fallback
- [x] Quick keyframe-bounded video trim
- [x] Multi-clip timeline UI: import, reorder, remove and per-clip trims
- [x] Per-clip playback speed presets (0.5×–3×)
- [~] Media3 Transformer combined multi-clip MP4 export
- [~] One looping background-music sequence mixed with embedded clip audio
- [~] Master clip volume plus per-clip audio gain and fade-in/out
- [~] Optional static title overlay on all montage clips
- [ ] Multiple independent music/SFX layers and waveform editing
- [ ] Transitions/crossfades, animated titles, stickers, keyframes and overlays
- [ ] Split-at-playhead, speed ramps and advanced export controls

## Library and release
- [x] In-app RetroCam-owned media library with open/share and auto-refresh on return
- [x] Open images in Photo Studio from the library
- [ ] Albums, favorites and persistent edit history
- [~] Pure Kotlin timeline trim/speed unit tests and GitHub Actions Android build workflow (not executed yet)
- [ ] Clean Android Gradle build
- [ ] Physical-device QA on OnePlus Nord 6 and exact Motorola models
- [ ] Signed APK/AAB and release checks
