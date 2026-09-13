# Official app icon implementation plan

**Goal:** Promote the approved brighter Eclipse icon into native macOS/Windows packages and application windows; remove the unused explorations.

**Design authority:** User-approved `eclipse-brighter/moray-eclipse.svg`, with explicit authorization for platform-specific shapes, padding, and cleanup. Palette, eel, rim lighting and prompt remain unchanged.

**Architecture:** Keep one editable SVG master and generate platform-specific SVG, PNG and native containers. jpackage consumes committed ICNS/ICO files; Swing loads the matching PNG representations. Native generation is a maintenance operation, not a dependency of ordinary builds.

**Execution:** Bounded follow-up on `codex/official-app-icon` in the shared checkout. Execute inline, test first at the resource/container boundary, review final diffs, no commits/push/GUI launch. This is separate from completed Plans 3/4; the user's direct integration request authorizes execution without another design gate.

- [x] Add headless tests for native icon frames, decodability and platform padding; observe failure on missing production assets.
- [x] Promote master to `packaging/icons/moray.svg`; implement `packaging/icons/generate.py` using Inkscape, Python stdlib, and macOS iconutil. Generate a macOS squircle on an 824/1024 footprint and Windows tight-corner tile on a 960/1024 footprint. ICNS supplies standard/@2x representations through 1024px; ICO includes 16,20,24,30,32,36,40,48,60,64,72,80,96,128,256px.
- [x] Add `ApplicationIcon` PNG loading and guarded Taskbar integration at production startup; set JFrame icons before pack/show. Keep CLI help/headless checks free of desktop calls. Test both actual resource sets.
- [x] Pass `--icon` to jpackage with declared input tracking. Extend `verifyPackage` to check macOS CFBundleIconFile and bytes; verify Windows embedded icon resources on Windows without launching the app.
- [x] Run focused tests, `./gradlew check :moray-app:packageDist`, native container inspection and rendered platform previews. Record Windows desktop verification as pending on this macOS host.
- [x] Move unused design explorations to Trash after copying/verifying the master. Update packaging documentation and STATUS with evidence and artifact paths. Leave unrelated files and prior benchmark artifacts intact.

**Completion:** All steps complete. Independent requesting-code-review agent reported no findings. One test expectation was corrected to allow faint antialias coverage (21/255 alpha) at the 16px Windows corner; other sizes have fully transparent corners. ICNS frame tags follow iconutil output (`ic04`, `ic05`, `ic10`, etc.), not assumed `icp4`/`ic0a` tags. Final evidence and native limits are in `packaging/icons/README.md`. No commits or pushes were requested.

**Integration authorization:** After completion and review, the user explicitly requested merging the combined icon/theme changes to `main` and pushing to `origin`. This supersedes the earlier no-commit/no-push execution scope.
