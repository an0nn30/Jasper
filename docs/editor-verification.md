# Editor verification

Automated coverage runs in `./gradlew check` (`plugins/editor/src/test`, `plugins/remote/src/test` for remote files,
the SDK contract suite and `JasperApplicationPluginsTest`). These GUI checks are the user's; an unchecked item is
unverified.

## Plan A: local files

- [ ] 1. File → Open in Editor… (Cmd/Ctrl+O) in a terminal window opens the chosen files in tabs in one Editor window.
- [ ] 2. Dropping a file from Finder onto the Editor window opens it; dropping a folder does nothing.
- [ ] 3. TOML, JSON, Python, Bash and YAML samples are highlighted; `Cargo.lock` and a `#!/usr/bin/env python3` script without an extension are detected.
- [ ] 4. Editing shows ●; Cmd/Ctrl+S saves and clears it; `ls -l` shows the same permissions, and a symbolic link to the file is still a link.
- [ ] 5. Cmd/Ctrl+W closes the current tab, asking about unsaved changes; with no tabs it closes the window.
- [ ] 6. Cmd+Q with an unsaved tab asks Save All / Discard / Cancel; Cancel keeps Jasper running, Save All saves then quits.
- [ ] 7. Changing an open file from the terminal (`echo x >> file`) reloads a clean tab within a few seconds and shows the banner on an edited one.
- [ ] 8. Switching the theme between light and dark recolours the editor; the tab row, status line and find bar follow the theme.
- [ ] 9. Find (Cmd/Ctrl+F), Replace (Alt+Cmd/Ctrl+F), Go to Line (Cmd/Ctrl+L) and Toggle Comment (Cmd/Ctrl+/) work; Shift+Cmd/Ctrl+S opens the save dialog rather than anything else.
- [ ] 10. With two unsaved files, change one on disk from the terminal, then press Cmd+Q and choose Save All: the conflict prompt for that file appears over the "Saving 2 files…" wait and can be answered; Cancel on the wait leaves Jasper running.
- [ ] 11. Saving a read-only file (chmod 444) fails with "permission denied" and leaves the file unchanged.

## Plan B: remote files

- [ ] 12. In the SFTP browser, double-clicking a file (and Enter on a selected file) opens it in a new Editor tab with the host dimmed after its name; the tab's tooltip reads `user@host:/path`.
- [ ] 13. Editing and saving a remote file writes it back: `cat` on the host shows the new content, and `ls -l` shows the same owner, group and permissions.
- [ ] 14. Changing the same remote file from the terminal (`echo x >> file`) before saving makes the save ask Overwrite / Reload / Cancel.
- [ ] 15. Double-clicking a link to a file opens the file, and saving keeps the link (`ls -l` still shows `->`); double-clicking a link to a folder navigates into it.
- [ ] 16. Selecting several files and choosing Open in Editor opens each in a tab; with more than 20 selected, 20 open and the browser says "Opened 20 of N files."
- [ ] 17. Saving a read-only remote file (`chmod 444`) fails with "permission denied" and leaves it unchanged.
- [ ] 18. After the SSH connection ends (close its tabs and wait for it to drop), saving an edited remote tab reconnects, asking for sign-in in a terminal window if needed; with every terminal window closed, the save fails with "Reconnecting to <host> needs an open terminal window for the sign-in prompt."
- [ ] 19. With the Editor disabled in the Plugins manager (after the restart), double-clicking a file in the SFTP browser does nothing and Open in Editor is not in the menu.
- [ ] 20. On a host at least 50 ms away (ping), saving an edited remote file and Save All with about five remote tabs complete in reasonable time; note the times (each save currently costs several dozen sequential SFTP round trips).

## Vim mode (plan V1a)

- [ ] 21. The Vim status item and Toggle Vim Mode switch every open tab between Vim mode and plain editing, keeping text and undo; after a restart the choice holds (`vim = true` is in the settings file).
- [ ] 22. The caret is a block in Normal mode, a bar in Insert mode (`i`) and an underline after `d`; the strip under the text shows `-- INSERT --` and pending keys such as `2d`, and `"qp` on an empty register shows `E353: Nothing in register q` and flashes.
- [ ] 23. Typing fast in Insert mode keeps every character in order; `u` then undoes the whole insert, and `.` repeats it elsewhere.
- [ ] 24. macOS: Option characters (⌥e e for é) insert in Insert mode and work after `f`; ⌘S, ⌘F, ⌘Z and ⌘W still reach the Editor.
- [ ] 25. Linux or Windows: Ctrl+S saves in Normal mode, Ctrl+R redoes, and Ctrl+W in Insert mode deletes a word rather than closing the tab.
- [ ] 26. With a Japanese or Chinese input method on, `dd` and `x` work in Normal mode, and composed text inserts in Insert mode and repeats with `.`.
- [ ] 27. `"+yy` then pasting in another application gives the line; text copied elsewhere comes back with `"+p`.
- [ ] 28. Clicking past the end of a line in Normal mode puts the caret on the line's last character.
- [ ] 29. A remote file opened from the SFTP browser edits and saves in Vim mode as it does without it.
