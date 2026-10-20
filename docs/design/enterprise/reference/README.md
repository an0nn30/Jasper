# Enterprise presentation design references

These are unchanged copies of the design document and mocks the user supplied for the Enterprise presentation: folders
01 to 06 on 2026-10-14 and 2026-10-15, folders 07 to 10 on the evening of 2026-10-15 (originally
`~/Downloads/enterprise_mocks/`). Keeping copies here avoids relying on paths outside the repository. The
[manifest](manifest.json) lists every file with its SHA-256 hash and, for images, its original pixel size. The
images have not been resized, recoloured or edited, and filenames are unchanged.

The [design specification](../../../superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md) records the
requirements. [DESIGN_LANGUAGE.md](DESIGN_LANGUAGE.md) states its own precedence: where its text and a mock disagree,
the mock wins on layout and the text wins on intent. Colours come from the active theme; the hex values in the
document and mocks are targets for the Enterprise theme, not requirements for other themes. Numbers, names and data
shown in the mocks (row counts, host names, saved setting values) are fixtures.

| Folder | Content |
|---|---|
| [01 Workstation](01%20Workstation) | Main window, disconnected and connected |
| [02 New File Server](02%20New%20File%20Server) | New File Server dialog for HTTP, FTP, SFTP and TFTP |
| [03 Add Host](03%20Add%20Host) | Add Host dialog, default and with the credential menu open |
| [04 New Tunnel](04%20New%20Tunnel) | New Tunnel dialog for local, remote and dynamic tunnels |
| [05 Settings](05%20Settings) | Eighteen Settings pages, plus Keymap while recording a shortcut |
| [06 Run Dialog](06%20Run%20Dialog) | The command palette as a Run window, just opened and filtered |
| [07 Credential Vault](07%20Credential%20Vault) | Unlock Vault over the main window, the Credential Vault window, its New menu, Add Login and Add SSH Key |
| [08 Dark Mode](08%20Dark%20Mode) | Main window, connected, in Enterprise Dark |
| [09 App Icon](09%20App%20Icon) | The application icon (9b, Narrow Square Prompt) for macOS, Windows and Linux, with masters and its README |
| [10 Boot Splash](10%20Boot%20Splash) | Ten frames of the start-up splash and an animated HTML mock; `extracted/` holds the mock's markup (`template.html`), art (`art.svg`) and icon (`icon.svg`), unpacked byte for byte from its bundle |
