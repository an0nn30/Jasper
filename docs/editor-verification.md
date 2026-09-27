# Editor verification

Automated coverage runs in `./gradlew check` (`plugins/editor/src/test`, the SDK contract suite and
`JasperApplicationPluginsTest`). These GUI checks are the user's; an unchecked item is unverified.

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
