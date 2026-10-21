# Jasper homepage — local design preview

Serve this directory with `python3 -m http.server 4173 --bind 127.0.0.1 --directory website` from the repository root. The manual Pages workflow is `.github/workflows/website-pages.yml`.

Direction: crisp light workspace, moss accent, genuine product screenshots, three-platform selector, workflow overview, SDK resources and release links. Static HTML/CSS/JavaScript with relative asset URLs for future GitHub Pages compatibility. Links currently lead to the repository and releases list, rather than claiming a particular release is available.

Platform screenshots refreshed from running installed Jasper apps on macOS, Windows 11 ARM64 and Ubuntu 26.04 ARM64, 2026-10-20. Each demo window is set to 1600 × 960 logical pixels; native 2× captures sit on identical 3200 × 1920 transparent canvases. Windows’ invisible resize border is excluded. No screenshots are upscaled. Jasper Meadow Light; separate demo configuration; Python emits identical sample terminal output without contacting live infrastructure. Platform captures use lossless native PNG exports, precise inset crops and transparent rounded corners. The macOS image is a focused, one-shot window capture taken before live capture attaches, so its native traffic lights remain visible without a capture indicator. The homepage overlays larger vector traffic lights on the macOS preview for legibility; the source PNG retains its native controls. Installed builds may differ; recapture from the final release before publication. No personal account or connection data is included.

The matching demo script and config used for capture are in `/tmp/jasper-homepage-demo` on the host and Linux VM, and `C:\Users\dustin\vmtask\homepage-demo` in Windows. Original VM desktop captures are retained in the host temporary folder. For publication, review final copy, exact platform support, license and release availability with the owner.

The built-in plugins section uses two captures of the same running Jasper window: Editor plus Remote sessions, and terminal plus Remote sessions. Only the OS title/toolbar is cropped; the actual workspace, tabs and panels are untouched. Sample `.example.test` hosts are saved entries, not connected servers; the YAML file is local. The section presents Remote editing as a capability, not as what this screenshot demonstrates. Demo profile: `/tmp/jasper-plugins-demo`.

Jasper welcomes visitors in the opening meadow scene; the platformless Editor/Remote workspace follows, with OS screenshots further down. Credential Vault leads the feature tour. Sessions, SFTP, Tunnels and Vault are 3× renders of the production plugin components through the existing real-host test harness using the bundled Meadow theme, populated with synthetic data. Remote previews use 18 px demo fonts; Vault uses 16 px. Tunnels shows two saved local forwards, and SFTP shows four sample entries. File Sharing is a refreshed 1504 × 1610 native Retina capture with an 18 px demo UI font. SFTP shows a sample directory and simulated transfer; Vault stores three fictional logins in a temporary demo vault and selects one with its password masked. The harness makes no network connections. Native title bars, rounded outer-window remnants and the Vault's temporary-path footer are excluded from the tour assets.

The refreshed workspace and all five feature previews use warm cream surfaces and moss #52643F actions, matching the bundled Meadow theme. The platform captures retain native OS chrome.

The turtle Jasper’s homepage concept is `assets/buddy-dimensional-v1.png`, generated with the built-in image tool from the existing turtle artwork and a user-supplied dimensional pet reference. Prompt: preserve the olive turtle, round glasses, cream segmented belly and tan shell; add sculpted pixel-art depth, upper-left highlights and a welcoming wave on transparency. This sibling concept leaves the shipped sprite sheet unchanged.

The homepage animates the original Jasper mascot image through SVG body/arm masks: subtle 4.8-second idle breathing and a short wave every 14 seconds. Motion pauses off-screen, in hidden tabs, or via the visible pause control, and is disabled for reduced-motion preferences. Page accent/hover colors match Meadow (#52643F / #3F5030).

Jasper now narrates the welcome, plugin tour, and SDK handoff. Matching presenting and laptop poses live in `assets/buddy-presenting-v1.png` and `assets/buddy-coding-v1.png`; built-in image generation used the approved Jasper mascot as identity/style reference, requesting an open-palm presenting pose and a seated laptop pose on transparent backgrounds. Tour narration follows the selected feature.

The workspace walkthrough uses genuine character-by-character app captures and a 28-second browser animation: type a YAML comment in Editor → type `kubectl get pods` and show synthetic results → expand the terminal → return to Editor. All 35 lossless WebP frames in `assets/workspace-typing/` were recaptured from native PNG exports at 3200 × 1528 (about 3.1 MB total); they preserve real syntax highlighting and terminal echo. The three workspace PNG stills have the same dimensions. The isolated Python demo handles the sample command without executing it against a cluster. A Tabler pointer (MIT; `assets/tabler-LICENSE.txt`, filled/pointer.svg from tabler/tabler-icons) indicates the tab and rail actions. Typing frames load only for visible, permitted playback, with at most two decodes in flight. Playback pauses off-screen, in hidden tabs, on manual selection, and for reduced motion. The walkthrough demo profile uses an 18 px UI font, 18 px Editor font and 20 px terminal font, with 52 px rails, 40 px tabs, 44 px toolbar and 34 px status bar before native UI scaling; app layout defaults are unchanged. Local profile backup: `/tmp/jasper-plugins-demo/config.before-moss.toml`.

## Manual deployment

`Deploy website` runs only through GitHub Actions’ **Run workflow** button with **main** selected. It validates and packages that run’s exact commit, then deploys the artifact through the `github-pages` environment. A non-main run fails before checkout; pushes and app releases do not trigger publishing.

Before the first deployment, merge the website and workflow into `main`, set repository **Settings → Pages → Source** to **GitHub Actions**, and restrict the `github-pages` environment’s deployment branches to `main`. Then run **Deploy website** from `main` when ready to publish. Preparing these files locally does not deploy the site.

`build_site.py` publishes only its explicit `PUBLIC_FILES` allowlist: the homepage, its styles/scripts, selected images, animation frames and the cursor license. The README and build/test helpers stay outside the artifact. Add new public assets to that allowlist when the homepage needs them.

To validate and inspect the exact bundle locally (use a new output directory):

```sh
python3 -m unittest discover -s website -p test_build_site.py
python3 website/build_site.py /tmp/jasper-pages-preview
python3 -m http.server 4174 --bind 127.0.0.1 --directory /tmp/jasper-pages-preview
```
