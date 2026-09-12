# Screenshot UI — native acceptance

> **Geometry update (2026-09-12):** Use the current 4px terminal inset for native acceptance. The original 24px mock measurement is historical and superseded.

Pending user execution. Run `./gradlew :moray-app:run` from `/Users/dustin/projects/moray`. This opens real windows and the user's shell. Agents do not launch it unattended.

- [ ] At the reference proportions, compare the supplied mock with the actual window: current38px title/tab row,53px horizontal toolbar, matching terminal/status fill, no status separator, and the current4px terminal inset.
- [ ] Native traffic lights are centered and usable beside tabs; drag and double-click blank title space, resize and enter/leave fullscreen. Tab controls and plus do not accidentally move the window.
- [ ] Select, close, middle-click, rename, reorder and add tabs. With many or long tabs, overflow navigation works and the active tab is visible after resizing; native controls and trailing title remain unobstructed.
- [ ] Toolbar labels are inline and sentence case. New tab has its shortcut hint and filled background. Split opens the right/down popup. Settings/Reload remain disabled on the right. Try compact widths and every toolbar mode.
- [ ] Status shows current shell/running state and path on the left, current grid and Built-in defaults on the right. Long paths clip without losing the right fields; the fill blends with the terminal.
- [ ] Switch dark/light with multiple windows, hidden tabs and zoomed splits. Content, selection/find, font overrides and split ratios survive. The initial app font and reset are16px.
- [ ] Try a display with different scaling, opposite macOS appearance, inactive windows and long shell titles. Screen menus, Quit, find-field editing and ordinary terminal input still work.

Headless previews exclude native traffic lights, frame/shadow and platform font rasterization differences. They are actual Swing renders, not proof of native acceptance. Shell prompt/user/path/grid values are live metadata, so they need not equal the mock's sample strings. No benchmark or daily-use acceptance is implied.
