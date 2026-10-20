# Jasper homepage — local design preview

Serve this directory with `python3 -m http.server 4173 --bind 127.0.0.1 --directory website` from the repository root. No deployment workflow is configured.

Direction: crisp light workspace, moss accent, genuine product screenshots, three-platform selector, workflow overview, SDK resources and release links. Static HTML/CSS/JavaScript with relative asset URLs for future GitHub Pages compatibility. Links currently lead to the repository and releases list, rather than claiming a particular release is available.

Screenshots captured from running installed Jasper apps on macOS, Windows 11 ARM64 and Ubuntu 26.04 ARM64, 2026-10-19. Jasper Meadow Light; separate demo configuration; Python emits identical sample terminal output without contacting live infrastructure. Platform captures have precise inset crops and transparent rounded corners, produced by `demo/window-cutout.swift`. The macOS image is a focused, one-shot window capture taken before live capture attaches, so its native traffic lights remain visible without a capture indicator. No interface elements were composited or generated. Installed builds may differ; recapture from the final release before publication. No personal account or connection data is included.

The matching demo script and config used for capture are in `/tmp/jasper-homepage-demo` on the host and Linux VM, and `C:\Users\dustin\vmtask\homepage-demo` in Windows. Original VM desktop captures are retained in the host temporary folder. For publication, review final copy, exact platform support, license and release availability with the owner.

The built-in plugins section uses two captures of the same running Jasper window: Editor plus Remote sessions, and terminal plus Remote sessions. Only the OS title/toolbar is cropped; the actual workspace, tabs and panels are untouched. Sample `.example.test` hosts are saved entries, not connected servers; the YAML file is local. The section presents Remote editing as a capability, not as what this screenshot demonstrates. Demo profile: `/tmp/jasper-plugins-demo`. Its synthetic terminal program and YAML sample are preserved in `demo/session.py` and `demo/deployment.yaml`.

The platformless Editor/Remote workspace is the hero; OS screenshots sit further down. Sessions, Tunnels and File Sharing are tightly cropped native captures. SFTP and Vault are 2× renders of the production plugin components through the existing real-host test harness using the bundled Meadow theme, populated with synthetic data. SFTP shows a sample directory and simulated transfer; Vault stores three fictional logins in a temporary demo vault and selects one with its password masked. The harness makes no network connections. Native title bars, rounded outer-window remnants and the Vault's temporary-path footer are excluded from the tour assets.

To regenerate the populated views from the repository root:

```sh
./gradlew :jasper-app:test --tests dev.jasper.app.plugins.HomepageCaptureTest --init-script website/demo/capture.gradle
swift website/demo/crop.swift jasper-app/build/homepage-demo/sftp.png website/assets/tour-sftp.png 4 4 1112 832
swift website/demo/crop.swift jasper-app/build/homepage-demo/vault.png website/assets/tour-vault.png 0 0 1800 520
```

The opt-in fixture lives in `website/demo`, outside the normal test source set. It asserts the sample file rows and clean plugin shutdown; screenshots still require visual review. No production app code changes are needed.

The refreshed workspace and all five feature previews use warm cream surfaces and moss #52643F actions, matching the bundled Meadow theme and `demo/jasper-meadow.json`. The three platform captures precede this moss revision. The palette is also bundled as the app’s default light theme in this local branch; explicit saved selections stay intact.

The turtle Jasper’s homepage concept is `assets/buddy-dimensional-v1.png`, generated with the built-in image tool from the existing turtle artwork and a user-supplied dimensional pet reference. Prompt: preserve the olive turtle, round glasses, cream segmented belly and tan shell; add sculpted pixel-art depth, upper-left highlights and a welcoming wave on transparency. This sibling concept leaves the shipped sprite sheet unchanged.

The homepage animates the original Jasper mascot image through SVG body/arm masks: subtle 4.8-second idle breathing and a short wave every 14 seconds. Motion pauses off-screen, in hidden tabs, or via the visible pause control, and is disabled for reduced-motion preferences. Page accent/hover colors match Meadow (#52643F / #3F5030).

Jasper now narrates the welcome, plugin tour, and SDK handoff. Matching presenting and laptop poses live in `assets/buddy-presenting-v1.png` and `assets/buddy-coding-v1.png`; built-in image generation used the approved Jasper mascot as identity/style reference, requesting an open-palm presenting pose and a seated laptop pose on transparent backgrounds. Tour narration follows the selected feature.

The workspace walkthrough uses genuine character-by-character app captures and a 28-second browser animation: type a YAML comment in Editor → type `kubectl get pods` and show synthetic results → expand the terminal → return to Editor. Lossless WebP frames in `assets/workspace-typing/` preserve the real syntax highlighting and terminal echo. The isolated Python demo handles the sample command without executing it against a cluster. A Tabler pointer (MIT; `assets/tabler-LICENSE.txt`, filled/pointer.svg from tabler/tabler-icons) indicates the tab and rail actions. Typing frames load only for visible, permitted playback, with at most two decodes in flight. Playback pauses off-screen, in hidden tabs, on manual selection, and for reduced motion. The demo profile uses 52 px rails, 40 px tabs, 44 px toolbar and 34 px status bar; app layout defaults are unchanged. Local profile backup: `/tmp/jasper-plugins-demo/config.before-moss.toml`.
