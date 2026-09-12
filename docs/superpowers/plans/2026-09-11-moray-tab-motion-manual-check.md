# Compact tab motion — native acceptance

Pending user execution. Run `./gradlew :moray-app:run` in `.worktrees/mock-ui`. Agents do not launch the app unattended.

- [ ] Default title/tab row is 38 logical pixels. Native traffic lights, blank-title dragging, double-click and fullscreen remain usable.
- [ ] View → Tab height… changes the current window to the accepted numeric height; cancel preserves it; reset returns 38. Try 28, 44, 54 and 72. Both native title region and tabs resize together. Theme changes retain the setting. This control is session-only.
- [ ] Open several tabs quickly: each new tab appears with a quick eased settle, without delaying its selection or shell launch. Tab labels/close buttons remain usable during motion.
- [ ] Use tabs with distinctly different title lengths, then select with mouse and keyboard: underline moves to the selected tab with matching quick easing. Rapid repeated changes start from its current position; no flicker or old-tab underline remains. Rename or receive shell-title updates during new-tab entry; motion should continue when the visible tab geometry permits it.
- [ ] With many tabs, select distant tabs, close an animating tab, resize and reorder. Selected tab stays accessible; navigation and plus never become stuck. No animation continues after closing a window.
- [ ] Cmd+1–9 on macOS / Ctrl+1–9 elsewhere selects the numbered existing tab. Missing numbers do nothing. Cmd/Ctrl+{ and } move left/right and wrap. Check terminal input and find-field editing remain correct.
- [ ] Switch appearance and tab height while selecting tabs. Motion retains colors and finishes at the current row geometry. Check opposite OS appearance and display scaling.

Headless tests establish deterministic intermediate and final states, lifecycle cleanup, key dispatch and geometry; smoothness, native controls and keyboard-layout behavior require the real application.
