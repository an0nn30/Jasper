# Settings and Browser scroll input

> Implementer and review work follows Superpowers test-driven development. The checks below run headless.

**Goal.** Restore Settings page scrolling at each intended increment and preserve macOS Browser trackpad motion through the Chromium input boundary.

**Architecture.** The Settings overlay thumb implements Swing's scroll-pane scrollbar contract. Browser translates macOS AWT wheel rotation to integer Chromium pixels and keeps subpixel motion per axis. Both fixes keep their existing entry points.

**Authorities.** Settings page layout in the [Enterprise presentation design](../specs/2026-10-15-jasper-enterprise-presentation-design.md) and Browser input in [Browser design sections 7 and 10](../specs/2026-10-26-jasper-browser-plugin-design.md). Runtime contract from [JBR AWTView](https://github.com/JetBrains/JetBrainsRuntime/blob/8a91bff64015/src/java.desktop/macosx/native/libawt_lwawt/awt/AWTView.m#L545-L562), [JCEF wheel bridge](https://github.com/JetBrains/jcef/blob/9df0879ddc3d775be97eb5bcc5cc834985a7e7e6/native/CefBrowser_N.cpp#L1072-L1118) and [IntelliJ's OSR adapter](https://github.com/JetBrains/intellij-community/blob/master/platform/ui.jcef/jcef/JBCefOsrWheelRotation.java).

**Status.** Implemented on `codex/fix-scroll-input` from `e2eb05921`. Settings `fb7eaa5f3`, Browser `01af5c49d`. The focused runs used JBR JCEF 25.0.4.1 b635.75 and passed 31 tests with zero failures, errors or skips. Full check and branch review are pending. Hardware acceptance is user-run. The Browser red run was repeated with the mouse hook routed and its original cast in place to isolate the conversion.

## Settings overlay scrollbar

- [x] **Test.** In `jasper-ui/src/test/java/dev/jasper/ui/swing/SettingsPageScrollTest.java`, create `SettingsShell.pageScroller(KitContext)` under FlatLaf. Dispatch unit-scroll events to a vertically and horizontally overflowing `Scrollable` with unit increments 24 and 12, and block increments equal to the viewport dimensions. Check `(wheelRotation, preciseWheelRotation, scrollAmount) = (0, 0.25, 1)` moves 6 px vertically, `(1,1,1)` moves 24 px vertically, and `Shift+(1,1,1)` moves 12 px horizontally. Check negative turns clamp at the start. For a plain panel, check the 16-pixel fallback and a subsequent explicit one-pixel scrollbar unit. Settings can receive a plain view from `PluginStubPage.component()`.
- [x] **Red.** Run `./gradlew :jasper-ui:test --tests '*SettingsPageScrollTest'`. The wheel moved from 6 to 7 on a page asking for 24, and the plain panel moved 1 pixel.
- [x] **Fix.** In `OverlayScrollPane.java`, extend `JScrollPane.ScrollBar` in `Thumb` and continue to use its inherited viewport-based increments. Override `getUnitIncrement(int)` and `setUnitIncrement(int)` so a plain component defaults to 16 and an explicitly configured increment takes precedence.

```java
private final class Thumb extends ScrollBar {
    private boolean unitIncrementSet;

    Thumb() { super(JScrollBar.VERTICAL); }

    @Override public int getUnitIncrement(int direction) {
        if (unitIncrementSet || getViewport().getView() instanceof Scrollable) {
            return super.getUnitIncrement(direction);
        }
        return 16;
    }

    @Override public void setUnitIncrement(int increment) {
        unitIncrementSet = true;
        super.setUnitIncrement(increment);
    }
    // Existing painting and hit-testing.
}
```

- [x] **Green.** `./gradlew :jasper-ui:test --tests '*SettingsPageScrollTest' --tests '*SettingsControlsTest'` passed 17 tests. Committed as `fb7eaa5f3`.

## macOS Browser wheel delivery

- [x] **Test.** Extend `PageViewMouseTest.java` through `PageView.useMouse`. Use synthetic macOS unit events with amounts of 1 and JBR-style fractional rotations. Check that `+0.35` and `+0.08` send `-3` then `-1` units. Send two `+0.06` inputs with a horizontal `+0.5` between them. Check the horizontal `-5` with Shift and the vertical `-1`. Send `-0.06` and `-0.05` after residual positive movement and check one `+1` unit. Check an ordinary `+1` sends `-10` with amount 1. Assert coordinates, timestamp and modifiers at the actual forwarded event and prove Ctrl+wheel magnifies only on Windows/Linux while macOS Ctrl+wheel reaches the page. Check non-Mac unit events retain their incoming amount and existing sign.
- [x] **Red.** `./gradlew :jasper-plugin-browser:test --tests '*PageViewMouseTest'` exposed that the mouse hook received no wheel events. Routing the hook with the old cast produced `[0, 0, -1]` instead of `[-3, -1, -10]`, and three zero deltas in the diagonal case. The non-Mac encoding and Ctrl zoom assertions passed with the hook in place.
- [x] **Fix.** In `PageView.java`, use `Hooks.mouse()` for the translated wheel. On macOS unit scroll, multiply negated precise rotation by 10, retain separate fractional pixel state by Shift, discard an axis's remainder when its direction reverses, and suppress zero integral translation. Use scroll amount 1. If an event reports zero precise rotation but nonzero integral rotation, use the integral input as the distance. Start both remainders at zero on attachment. Preserve the prior non-Mac encoding and the existing consumed/zoom checks. In `PageBrowser.attach()`, send `MouseWheelEvent` through `sendMouseWheelEvent` and other `MouseEvent` through `sendMouseEvent`.

```java
double pixels = rotation * MAC_WHEEL_PIXELS_PER_UNIT;
double remainder = event.isShiftDown() ? horizontalWheelRemainder : verticalWheelRemainder;
if (Math.signum(pixels) != Math.signum(remainder)) remainder = 0;
double total = pixels + remainder;
int wholePixels = (int) total;
if (event.isShiftDown()) horizontalWheelRemainder = total - wholePixels;
else verticalWheelRemainder = total - wholePixels;
```

- [x] **Green.** `./gradlew :jasper-plugin-browser:test --tests '*PageViewMouseTest'` passed 14 tests. Committed as `01af5c49d`.
- [ ] **Verify.** Run `./gradlew check` on the pinned JBR and record the suite result here and in `docs/STATUS.md`.
