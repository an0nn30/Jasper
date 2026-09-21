# Moving from Moray to Jasper

The app is now **Jasper**, with the Silver Desk Buddy turtle icon. Java packages
are `dev.jasper.app`, `dev.jasper.terminal` and `dev.jasper.buddy`; Gradle modules
are `jasper-app`, `jasper-terminal` and `jasper-buddy`. Use `./gradlew :jasper-app:run` for development and
`./gradlew check :jasper-app:packageDist` to verify and package it.

Native outputs are `Jasper.app`, `Jasper.exe`, and Jasper-named distributions.
The bundle identifier is `dev.jasper.app`. The version override is
`-PjasperVersion=1.0.1`; application-specific Java properties use `jasper.*`.
The Git remote and repository directory retain the name `moray`.

## Existing settings

This rebrand does not move or overwrite any installed app or user data.
Jasper uses a new settings directory:

| Platform | Previous directory | Jasper directory |
| --- | --- | --- |
| macOS | `~/.config/moray` | `~/.config/jasper` |
| Linux | `$XDG_CONFIG_HOME/moray` | `$XDG_CONFIG_HOME/jasper` |
| Windows | `%APPDATA%\moray` | `%APPDATA%\jasper` |

On Linux, an unset or relative `XDG_CONFIG_HOME` falls back to `~/.config`.

To carry settings over, close the app and copy `config.toml` and
`command-history.toml` from the old directory into the new one. If Jasper already
has settings, merge them instead of overwriting them. Logs do not need to be copied.
Replace any old `[colors]` block (`appearance`, `theme`, custom palette files) with
`ui.theme.variant = "dark"` or `"light"`; the old keys are reported as unknown settings
and ignored. Update any custom shortcuts, scripts,
or integrations referencing the old executable, package, property, or module name.
Historical benchmark manifests preserve the original artifact names and hashes.
