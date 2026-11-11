# Editor file associations: making Jasper the default app for source files

Status: draft for the user's review (2026-11-10). Builder check done; its questions are answered below.

## Intent

You want to double-click a `.java`, `.py` or `.md` file in Finder, File Explorer or a Linux file manager and have it
open in the Jasper Editor. Jasper must never take those file types for itself. Installing Jasper leaves every
association as it was, as the packages already do for web links ([browser.md](../../browser.md), "The packages make
no app the default"). The user claims types from the Editor's settings page: a list of file types with checkboxes and
a **Make Jasper the Default** button. IntelliJ does the same thing in **Settings › Editor › File Types › Associate File
Types with IntelliJ IDEA…**, and IntelliJ is the reference for open UI questions.

## Goals

- A **File associations** section on the Editor's settings page (Settings › Tools › Editor). It lists file types, each
  with its extensions, a checkbox and the app the system opens it with now.
- A built-in list of common source and text types, plus extensions the user adds.
- One action that makes Jasper the system's default app for the ticked types: silently on macOS and Linux, and on
  Windows through Windows Settings, because Windows allows nothing else.
- **Restore Previous**: when Jasper claims a type, it saves the app that had it, and can hand the type back.
- The same feature on macOS, Windows and Linux (x64 and aarch64), with each platform's own words: Finder, File
  Explorer, "file manager".

## Non-goals

- Claiming any type at install, upgrade or first launch, or asking about it at start-up.
- Folders, `.html` (whether local HTML goes to the Browser is a separate open question), URL schemes and anything the
  Browser plugin owns.
- Taking any type when the user hasn't pressed the button. Restore only hands back types the user picks.

## Anti-goals (a failure even if it works)

- **Defaults changed silently on Windows** by writing `UserChoice` or working around its hash or the UCPD driver. Only
  Windows Settings may change a Windows default.
- **A loop, or a file that ends up only revealed.** When Jasper's Default editor is *system's default app* and Jasper
  is that app for a type, opening such a file from Jasper must end in the Jasper Editor. "Where handed-in files open"
  below settles this: the desktop's edit action hands the file back once, and it then opens in the Jasper Editor.
- **Running files.** Claiming a type never makes Jasper run anything. Associations open files in the Editor only.
- **Leftovers that break other apps.** Uninstalling leaves no association that names a Jasper which is gone, except
  where a platform keeps the user's own choice (see "Uninstall").

## Where handed-in files open (your decision)

**Files handed to Jasper from outside always open in the Jasper Editor**, whatever the Editor's Default editor setting.
"From outside" covers:

- the macOS open-file and `file:` open-URI events (`JasperApplication.filesOpened`, `uriOpened`);
- path and `file:` URL launch arguments (`AppArguments`, which also covers Windows `"%1"` and Linux `--url %u`);
- a second launch handing its paths to the running Jasper.

`jasper <path>` typed in a shell is a launch argument too, so it opens in the Jasper Editor as well.

Default editor still decides everything Jasper opens itself: `Platform.openInEditor` and `openFile`, Cmd-click on a
path, config files and `EDITOR`/`VISUAL` in Jasper's shells.

This changes today's routing. Today those opens go `openRequestedFile` → `PluginRuntime.open` →
`FileEditors.openFromWorker` → the Editor's `DefaultEditor.edit`, which follows Default editor: VS Code launches, and
with *system* the file is only revealed. Finder's Open With › Jasper and dropping a file on the Dock icon change with
it, which is the point.

**SDK.** `FileEditor.edit(Path, OptionalInt, Optional<WindowOwner>)` can't tell where an open came from. 0.31.0 adds a
way for the request to say "handed in from outside Jasper", for example a new default method with an origin
parameter that the app calls for these opens. The builder picks the shape. A plugin that implements only the old
method keeps working unchanged. The Editor's `DefaultEditor` sends handed-in opens straight to `jasper.edit` and
everything else through the current `choice()` switch. The testkit fake and `ContractHarness` record the origin.

## What the user sees

The section comes last on the Editor page, after Default editor. It's built with SDK nodes like the rest of the page
(the presentation check forbids Swing in plugins), so the builder picks the closest existing nodes, probably the
table from SDK 0.25.0.

- **Rows.** One row per file type: checkbox, name ("Java"), extensions (".java"), and the current handler: "Jasper",
  the other app's name ("Xcode", "Visual Studio Code"), "No default app", or "Checking…" until the answer comes.
  Rows Jasper doesn't own are ticked by default. Rows Jasper owns are unticked and say "Jasper".
- **Make Jasper the Default** (macOS and Linux) claims the ticked rows and refreshes the list. On Windows the button is
  **Choose in Windows Settings…**: it registers the ticked types and opens Windows Settings (see Windows). The list
  refreshes when a Jasper window becomes active again, as the default-browser check does.
- **Restore Previous** is enabled when a ticked row belongs to Jasper and has a saved previous handler. It hands those
  rows back. On Windows it opens Settings, as the claim does, naming the app to choose again.
- **Add Extension…** adds a row for an extension the user types, such as `.toml`. The row's name is the extension.
  User rows have a remove control; built-in rows don't. If the extension can't be claimed on this platform, the row
  says why and its checkbox is disabled (see "Custom extensions").
- **Results.** Rows that fail show the reason inline ("macOS refused: …"). Nothing is posted as a notification when
  every row succeeds.
- **Partly claimed rows** (my call). When some of a row's platform types are claimed and others are refused, nothing
  is rolled back. The row says "Partly Jasper's" and names what was refused. Previous handlers are saved for the types
  that changed. The row stays ticked, so pressing the button again retries the rest.
- **No previous handler** (my call). On macOS and Windows a default can't be unset. When a type had no default
  before Jasper claimed it, Restore is disabled for that row with "No app had this type before Jasper; choose one in
  Finder's Get Info" (Windows: "…in Windows Settings › Default apps"). On Linux Restore removes Jasper's line (see
  Linux).
- **Unavailable.** When this Jasper can't own associations, the section shows one explanatory line and no controls:
  - a development run (`:jasper-app:run`), which has no app bundle or registered package;
  - the Windows portable ZIP and the user installation from source;
  - Linux without `xdg-mime` (xdg-utils), or outside the `.deb`/`.rpm` desktop entry.

  Each reason gets its own sentence; the builder writes them in the docs' voice.

## Built-in list

| Row | Extensions | macOS UTI | Linux MIME type |
|---|---|---|---|
| Java | .java | `com.sun.java-source` | `text/x-java` |
| Kotlin | .kt, .kts | imported `org.kotlinlang.source` | `text/x-kotlin` |
| C | .c, .h | `public.c-source`, `public.c-header` | `text/x-csrc`, `text/x-chdr` |
| C++ | .cpp, .cc, .cxx, .hpp, .hh | `public.c-plus-plus-source`, `public.c-plus-plus-header` | `text/x-c++src`, `text/x-c++hdr` |
| Python | .py | `public.python-script` | `text/x-python3`, `text/x-python` |
| Go | .go | imported `dev.jasper.go-source` | `text/x-go` |
| Rust | .rs | imported `dev.jasper.rust-source` | `text/rust` |
| JavaScript / TypeScript | .js, .mjs, .ts | `com.netscape.javascript-source`, imported for .ts | `text/javascript`, `application/x-typescript`… |
| Shell | .sh, .zsh, .bash | `public.shell-script` | `application/x-shellscript` |
| Markdown | .md, .markdown | `net.daringfireball.markdown` | `text/markdown` |
| JSON | .json | `public.json` | `application/json` |
| YAML | .yaml, .yml | `public.yaml` | `application/yaml`, `application/x-yaml` |
| TOML | .toml | imported `dev.jasper.toml` | `application/toml` |
| XML | .xml | `public.xml` | `application/xml`, `text/xml` |
| Plain text | .txt, .log | `public.plain-text`, `com.apple.log` | `text/plain` |

The identifiers in this table are my first draft. The builder checks each one against the system's type database on
every platform (`UTType` on macOS 11+, `/usr/share/mime/globs2` on Linux) and fixes the table and the shipped
declarations to match. A row can have several platform types. Claiming the row claims all of them, and the row counts
as Jasper's only when all of them are.

**Spill-over on macOS** (my call). Setting a default for a broad type such as `public.plain-text`, `public.xml` or
`public.json` may also change more specific types that have no handler of their own, which would give Jasper types
the user never ticked. The builder checks this: list the content types whose handler changes before and after a claim,
on a scratch user account or VM. If it happens, the Plain text row (and any other broad row affected) is left out of
the macOS list rather than shipped with a warning.

Shell scripts are opened for editing only (see the "Running files" anti-goal);
the builder checks that claiming `public.shell-script` changes no Terminal "run" binding on macOS.

**Who owns what.** The Editor plugin owns the rows (names, extensions, order, user rows) as product data. The app owns
the mapping from extension to platform type, because that is platform knowledge. The SDK speaks in extensions.

## Platform mechanics

All the code lives in a new `dev.jasper.app.platform.FileAssociations`, next to `DefaultBrowser`. It follows
`DefaultBrowser`'s shape: checks run on a worker with time-outs, the last answer is cached, a check that fails keeps
the last answer, and changes are reported on the EDT. Parsing stays in pure static methods tested like
`DefaultBrowserTest`.

### macOS

- **Declare.** `macDocumentTypes` (`gradle/packaging.gradle`) adds a `CFBundleDocumentTypes` entry for each built-in
  UTI, with role `Editor` and rank **`Alternate`**, so installing changes no default. `UTImportedTypeDeclarations`
  declares the types macOS lacks (Kotlin, Go, Rust, TypeScript, TOML, and whatever else the check above finds),
  conforming to `public.source-code` or `public.plain-text`. `verifyPackage`'s plist check (`packaging.gradle`, around
  line 704) is extended to match.
- **Query.** Add `URLForApplicationToOpenContentType:` (macOS 12+) to `MacLaunchServices.m`, with a fallback to
  `LSCopyDefaultRoleHandlerForContentType` on macOS 11 (Jasper's minimum is 11.0). Return the bundle id and display
  name.
- **Claim.** Use `setDefaultApplicationAtURL:toOpenContentType:completionHandler:` on 12+ and
  `LSSetDefaultRoleHandlerForContentType(uti, kLSRolesAll, "dev.jasper.app")` on 11, then query again to confirm.
- **Restore.** Find the saved bundle id's URL with `URLForApplicationWithBundleIdentifier:` and set it back. When that
  app is no longer installed, the row says so and stays Jasper's.
- `MacLaunchServicesTest.m` covers the new functions with probe types that touch no real default, or reads only.

### Windows

- **Register at the click, in HKCU.** For each ticked extension:
  - ProgId `JasperFile` (`Software\Classes\JasperFile`, display name "Jasper document", icon `Jasper.exe,0`,
    `shell\open\command` = `"<install dir>\Jasper.exe" "%1"`). The browser's ProgId puts `--url` before `"%1"` so a
    link can't pass options. File Explorer passes absolute paths, which can't start with `--`, so the plain form is
    safe; `--url` would refuse plain paths anyway;
  - `Software\Classes\.<ext>\OpenWithProgids\JasperFile`;
  - `Software\Jasper\Capabilities\FileAssociations\.<ext>` = `JasperFile`.

  `RegisteredApplications\Jasper` and the `Capabilities` key already come from the installer (browser plan 4c). Then
  call `SHChangeNotify(SHCNE_ASSOCCHANGED)`. The writes use JNA, which `WindowsUserPath` already does.
- **Open Settings** at `ms-settings:defaultapps?registeredAppUser=Jasper`, where the user picks Jasper for each type.
  The builder checks that deep link on Windows 10 22H2 and on Windows 11 23H2 and later, and falls back to
  `ms-settings:defaultapps`. The section says, in one line, what to do there.
- **Query.** Read
  `HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\FileExts\.<ext>\UserChoiceLatest` (value, then `ProgId`
  subkey), else `…\UserChoice`, `ProgId` value: the first found decides, as `DefaultBrowser.askWindows` does for
  https. `JasperFile` means Jasper (compared case-insensitively). For another ProgId, the handler's name comes from
  `AssocQueryString(ASSOCSTR_FRIENDLYAPPNAME)`.
- **Restore** opens Settings the same way, and the row names the saved app to choose.
- **Available** only when `HKCU\Software\Jasper\Capabilities` exists, so the portable ZIP and the user installation
  are excluded, as they are for the browser.
- **Uninstall.** The WiX template (`packaging/windows/cygwin/CygwinInstallerTemplate.java` and its test) also removes
  `Software\Classes\JasperFile`. `Capabilities` is removed already. Leftover `OpenWithProgids\JasperFile` values point
  to a missing ProgId, which Windows ignores. The builder confirms that in `Verify-Installer.ps1`.

### Linux

- **Declare.** The `.deb`/`.rpm` desktop entry's `MimeType=` (`linuxDesktopMimeTypes` in
  `gradle/packaging-support.gradle`) adds the built-in MIME types, so desktops list Jasper under Open With. This changes
  no defaults: installing still leaves every `defaults.list` alone. `linuxDesktopEntryProblem` and its tests are
  updated. `Exec=… --url %u` stays as it is: `AppArguments.link` already takes a `file:` URL after `--url`, so one file
  arrives per launch.
- **Query.** `xdg-mime query default <mime>`, with the same 2-second time-out and process cleanup as
  `DefaultBrowser.run`. `jasper-workstation-Jasper.desktop` means Jasper. For another entry, the handler's name comes
  from its `Name=`, found through `XDG_DATA_DIRS`.
- **Claim.** `xdg-mime default jasper-workstation-Jasper.desktop <mime…>`, then query again.
- **Restore.** `xdg-mime default <saved entry> <mime>`. When there was no previous default, remove Jasper's line for
  that type from `$XDG_CONFIG_HOME/mimeapps.list` (`[Default Applications]`), because `xdg-mime` can't unset. The file
  is edited in place, keeping its other lines.
- **Risk to verify.** GIO and KDE may ignore a default for a MIME type the desktop entry doesn't list. That matters
  only for custom extensions (below). The builder tests GNOME and KDE in the `jasper-ci-linux` container or a VM. If it
  is ignored, user rows add the type under `[Added Associations]` too. If it is still ignored, Linux custom rows become
  "can't be claimed on this desktop".

### Custom extensions

Your decision: Jasper declares the type when the OS lacks one.

- **macOS.** `UTType typeWithFilenameExtension:` (UTType framework, macOS 11+) resolves the extension. A declared type
  that conforms to `public.text` can be claimed, because the plist claims `public.text` at `Alternate`. A dynamic
  (`dyn.…`) type can't be declared at runtime, so the row is disabled with "macOS has no file type for .xyz". The
  shipped imports above cover the common ones. The builder checks whether Launch Services keeps a default set for a
  `dyn.` type. If it reliably does, such rows can be claimed too.
- **Windows.** Any extension can be claimed. Registration is per extension.
- **Linux.** An extension with no glob in any `mime/globs2` under `XDG_DATA_DIRS` gets a type of Jasper's own,
  `text/x-jasper-<ext>`, a subclass of `text/plain`. It's written to `~/.local/share/mime/packages/jasper-workstation.xml`,
  followed by `update-mime-database ~/.local/share/mime`. Removing the last user row that uses one removes it from that
  file.
- Extensions are validated: a leading dot, 1 to 16 characters from `[A-Za-z0-9_+-]`, compared ignoring case, and none
  that the built-in list or Browser-owned types (`.html`, `.htm`) already have. They're saved in the Editor's settings
  file as `file_association_extensions = [".toml", ".proto"]`, documented in `settings.toml` and
  `docs/configuration.md`. Ticks are not saved: they're only the selection for the next button press.

## Saving previous handlers

The app owns this, because the app does the claiming. When a claim changes a type's handler from X to Jasper, the
app saves X (macOS bundle id, Windows ProgId plus friendly name, Linux desktop entry id, or "none") per platform type.
It goes in one small TOML file owned by the app under Jasper's home, beside its other app state; the builder picks the
place. Claiming a type Jasper already owns saves nothing. A successful restore deletes the entry. The file is written
atomically, and a file that can't be read is treated as empty, with a log line.

## SDK surface (0.31.0)

The new types go in `dev.jasper.sdk.ui`, beside `Platform`'s default-browser calls. Names and exact shapes are for the
builder to settle in the plan, within these limits:

- `Platform.fileAssociations()` returns `FileAssociations` (an interface implemented by the app and by the testkit
  fake), or calls directly on `Platform`, as the builder judges best.
- **Availability** gives available or unavailable with a reason the page can show.
- **Status** for a set of extensions is asynchronous and returns, per extension, its handler (Jasper, another app by
  name, none, unknown) and whether a previous handler is saved. A change event, like `onDefaultBrowserChanged`, lets
  the page refresh.
- **Claim** and **restore** take extensions, are asynchronous, and return per-extension results (done, needs the user
  in system settings, refused with reason). On Windows the call opens Settings itself.
- **Custom extension check** answers whether an extension can be claimed here, and if not, why.
- The testkit fake gets setters to script each of these, and `ContractHarness` gets the matching contract tests.
  `docs/plugin-authoring.md`'s testkit table, `docs/plugin-apis.md` and `sdk-reference.html` (via `./gradlew
  sdkReference`) are updated.

SDK types appear in the app only inside `dev.jasper.app.plugins`, so the adapter lives there and calls
`dev.jasper.app.platform.FileAssociations`.

## Docs

- `docs/editor.md`: a "Making Jasper the default for file types" section, per platform, in the style of browser.md's
  "Making Jasper your default browser".
- `docs/configuration.md`: `file_association_extensions`.
- `docs/packaging.md`: the new plist entries, desktop entry MIME types and Windows ProgId, and that installing still
  makes Jasper the default for nothing.
- `docs/STATUS.md` entry. A new SDK minor version bumps whatever the version-bump recipe in `docs/app-maintenance.md`
  lists.

## Tests

- Pure parsers and decision logic in `FileAssociations` (registry lookups through an injected reader, as
  `DefaultBrowserTest` does; `xdg-mime` output; `mimeapps.list` editing; shared-mime XML writing; extension
  validation; previous-handler file read and write, including a corrupt file).
- `MacLaunchServicesTest.m` for the native additions.
- Editor page tests with the testkit fake: rows, statuses, default ticks, unavailable states, add and remove of user
  rows, result reasons, the Windows button label.
- Handed-in routing: for each Default editor choice (Jasper, an installed editor, custom, system), a handed-in open goes
  to the Jasper Editor, and a Jasper-side open still follows the choice. `JasperApplication` tests show each route
  (open-file event, open-URI `file:`, launch argument, second-launch hand-off) passing the outside origin.
- The loop anti-goal: Default editor is *system* and the fake desktop hands the file back. A Jasper-side open of a
  `.java` ends in the Jasper Editor, once.
- Packaging checks: plist document types and imports, the desktop entry's `MimeType=`, and the WiX template's removal of
  `JasperFile`.
- No test changes a real default on the machine running `check`. Tests that touch the real OS only read.

## Left to the builder

- Final SDK names and shapes, within the limits above.
- Which SDK nodes the section uses, and the exact wording of every label and message.
- Where the previous-handler file lives.
- Verifying and correcting the type identifiers in the built-in table.
- Whether it takes one plan or several. My suggestion: (1) app platform layer plus packaging, (2) SDK, testkit and
  Editor page, (3) docs.

## GUI checks for the user (after the build)

Claim, check and restore a few types on macOS (Finder Get Info › Open with), Windows 11 (Settings › Default apps and
File Explorer) and a GNOME desktop (Files › Open With). On Windows, also confirm the deep link and that uninstalling
leaves Explorer working. These are batched for the end of the build, as usual.
