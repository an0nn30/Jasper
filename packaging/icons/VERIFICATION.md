# Protean icon verification — 2026-10-29

All 47 supplied files are retained byte for byte in `protean-source/`. Shipped ICNS/ICO and matching platform PNG/SVG representations match them exactly. Missing runtime/desktop sizes are resized by the importer.

The static macOS catalog compiles from the ten supplied iconset entries. `assetutil --info` confirms ten Jasper icon representations, 16 through 1024 pixels with standard and Retina scales. Runtime images decode at their declared dimensions; visual inspection of all three 256 px platform assets matches the supplied artwork.

Focused application-icon, Linux/macOS notification, packaged-resource and presentation tests pass: 38 tests, no failures or skips. `verifyApplicationArchitecture` and `verifyPackagingSupport` pass. The Linux notification test replaces seeded stale cache bytes with the exact shipped 128 px resource.

Headless `packageApp` was attempted with AOT disabled. Its strict runtime gate rejects the installed JBR b583.48 because the required JCEF b635.75 build is absent; `verifyPackage` remains pending. Native Windows/Linux package and desktop appearance remain manual acceptance checks.
