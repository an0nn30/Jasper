# Plugin icons

Prefer semantic names (SDK 0.7.4; SDK 0.7.5 adds the SFTP file and transfer names; SDK 0.7.6 adds SPLIT, ZOOM, TERMINAL and SERVER):

```java
import dev.jasper.sdk.ui.IconName;

var lock = context.appearance().icon(IconName.LOCK);
var unlock = context.appearance().icon(IconName.UNLOCK);
button.setIcon(lock);
```

Jasper owns the image; plugins supply no paths. Icons are IntelliJ classic-UI artwork whose
palette colours follow the live light/dark theme (KEY, LOCK, UNLOCK, CONNECT, DISCONNECT and
DELETE keep tinted monochrome outlines). Vault uses these calls for its status/menu icon and
manager lock/unlock controls. Remote uses `IconName.NETWORK` for its Sessions toolbar, actions,
host panel and status icon, and `IconName.SERVER` for its session tab icons. Its lock/unlock
resources are identical copies of its original SVG artwork.

Use the same icon for actions, toolbar dropdowns, menus, rail/panels, status items, palette
rows and ordinary Swing components. Returned icons are 16 logical pixels. Application resources
load through the host's class loader; a plugin does not need any image assets to use semantic
icons.

`IconName` contains 34 meanings. Jasper maps each to its own SVG. Existing `Appearance`
implementations inherit a default method that throws UnsupportedOperationException until they
implement this catalog; Jasper and the testkit implement every name. Null names throw
NullPointerException.

Headless render of actual manager buttons and status-bar controls, using named icons:

![Vault lock and unlock states](images/vault-semantic-icons.png)

## Custom artwork remains supported

For a plugin-specific image, the original `icon("path/in/your/jar.svg")` remains available.
This is optional custom artwork; ordinary controls should prefer `icon(IconName)`.
Custom Swing icons and legacy single-path calls retain their behavior and dimensions.

## Test selection without a GUI

```java
try (var host = new FakePluginHost()) {
    var context = host.start(info, Set.of(), Set.of(), plugin);
    var icon = (FakeNamedIcon) context.appearance().icon(IconName.LOCK);
    assertThat(icon).isEqualTo(new FakeNamedIcon(IconName.LOCK));
}
```

`FakeNamedIcon` records the semantic name, reports 16px dimensions and paints nothing.
