# Notifications Plan 3: SDK Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Implemented on branch `claude/notifications-3` (worktree `.worktrees/notifications-3`, cut from main
`c02673cd`, where plans 1 and 2 are merged), executed natively (one context, TDD per task) at the user's choice, with
one independent whole-branch review at the end. `./gradlew check --continue`: 8,333 tests, 0 failures, 0 errors, 6
skipped (39 new, after the final-review fix). Deviations from the plan text:
- Task 5: the contract test's second plugin takes its context as `broken`, because a lambda parameter named `context`
  would shadow the test's local of the same name and not compile.
- Task 12: Step 4's `grep -rnw "Notices"` also matches the English verb in three existing comments (`FakePluginHost`,
  `FakeSessions`, `LocalWatcher`), and the manifest grep matched stale `build/` copies until the next build; the type
  is gone from source and every manifest declares `>=0.14.0, <0.15`.
- Process: escapes typed into the executor's tool calls arrived as raw characters, so every Java file written was put
  through an escape pass before compiling; the source hygiene check is clean.

Final review (one independent reviewer on the whole branch): no critical findings, one important, seven minor.
- Fixed: a plugin whose manifest `name` showed nothing (only zero-width, no-break or bidi characters) stopped Jasper
  from launching, because its notification group could not be named. `DescriptorParser` now rejects such a name, so the
  plugin is an unreadable-plugin problem (`DescriptorParserTest.aNameThatShowsNothingIsRejected`,
  `PluginRuntimeTest.aPluginWhoseNameShowsNothingIsAProblemAndJasperStillStarts`).
- Fixed: the GUI check in `docs/STATUS.md` named "Remote activities", but Remote begins no activities; it now uses a
  long File Server transfer. Task 13's text above still has the old wording.
- Deferred (minor):
  - clearing the Errors window, or the 100-incident cap, expires a plugin's notifications that carried a cause, links
    included;
  - an expiring action that posts the same failure again within 60 s is coalesced into the entry it expires, so the
    new failure is not shown (plan 1's center; no bundled plugin posts actions yet);
  - a plugin whose id is a prefix of another's can declare groups under the other's id;
  - the fake rejects an activity title over 200 characters (the application cuts it) and expires activity outcomes
    when the plugin stops (the application leaves them live, with a body that does nothing);
  - `docs/notifications.md` says the Tunnels group carries tunnel failures, but it carries only the Tunnels panel's
    operation failures; `docs/plugin-authoring.md` omits some expiry triggers (an expiring action, Clear All, the
    Errors window);
  - the fake records a plugin's raw name for its groups, where the application's model cuts it to one line of 80.

**Goal:** Give plugins notifications through the SDK (`dev.jasper.sdk.notifications`, SDK 0.14.0). Replace `Notices` in
every bundled plugin with titled notifications that carry their causes. Announce plugin problems and activity outcomes
in the application's notification center.

**Architecture:**
- The SDK gains a JDK-only notifications API: groups, specs, actions and handles.
- In the app, `plugins.HostedNotifications` adapts it to the plan-1 `NotificationCenter`:
  - it registers each plugin's groups;
  - it posts from any thread;
  - it runs actions inside the plugin's containment;
  - it hands a cause to `IncidentNotices` for a plugin-attributed incident with Show more…;
  - it expires a plugin's notifications when the plugin stops.
- The testkit's `FakeNotifications` records the same, and one contract suite checks both implementations.
- The plugin host posts activity outcomes into `<plugin-id>.activities`, and the application announces startup
  problems in `jasper.plugins`.
- Each bundled plugin is then migrated, `Notices` is removed and the SDK becomes 0.14.0.

**Tech Stack:** Java 25 (JetBrains Runtime 25), Swing with FlatLaf, JUnit 5, AssertJ, Gradle wrapper.

**Spec:** [`docs/superpowers/specs/2026-10-02-jasper-notifications-design.md`](../specs/2026-10-02-jasper-notifications-design.md)
(plan 3 of the six in its section 14). Sections 2, 6 and 7 bind this plan; section 6 is the SDK. Read the status
banners of [plan 1](2026-10-02-notifications-1-core.md) and [plan 2](2026-10-02-notifications-2-incidents.md): their
deferred findings keyed to plan 3 are handled here (interpretations 9, 10 and 15 below).

## Global Constraints

**Build and workflow**

- Build and test only with `./gradlew` on JBR 25; tests are headless. Never launch the GUI or the benchmark.
- Work only in `.worktrees/notifications-3` on branch `claude/notifications-3`. Never edit the main checkout, never
  push, never use a bare `git stash`.

**Architecture**

- The SDK and the testkit stay JDK-only. `verifySdkArchitecture` keeps their package graphs acyclic:
  `dev.jasper.sdk.notifications` may depend on `dev.jasper.sdk.terminal` (`WindowHandle`), and
  `dev.jasper.sdk.plugin` depends on it, never the reverse.
- SDK types appear in the application only inside `dev.jasper.app.plugins`. Bundled plugins compile against the SDK
  only (`verifyPluginArchitecture`). Application types never reach the SDK, the testkit or a plugin.

**The notifications API**

- Text is plain, never HTML.
- Limits, from spec sections 2 and 6:
  - a title is visible and at most **200** characters;
  - content is at most **4,000** characters;
  - at most **8** actions, Show more… included, so at most 7 of the plugin's own when there is a cause;
  - an action label is visible and at most **40** characters;
  - a group name is one visible line of at most **80** characters.
  - "Visible" means it contains a character that is not white space, a space separator (no-break spaces included), a
    formatting mark (zero-width characters, bidi controls) or a control character.
- Group ids follow the panel id pattern and equal the plugin id or start with `<plugin-id>.`.
  `<plugin-id>.activities` is reserved for the host. Declaring an id again returns the same group.
- **Titles never carry unbounded user data** (paths, host names, exception messages). Those go in the content, and the
  title says what failed: "Could not save the host", with the reason as content.
- `post` is safe from any thread and returns at once. `post` after the plugin's context closed throws
  `IllegalStateException`.
- Actions and the body run on the UI thread inside the plugin's containment, so what they throw becomes an incident
  attributed to the plugin.
- A plugin's notifications expire when it stops.
- Clean break (the MVP rule): `dev.jasper.sdk.ui.Notices` and `PluginContext.notices()` are removed with no shim.
  `JasperSdk.VERSION` becomes **0.14.0**, and every bundled manifest declares `sdk = ">=0.14.0, <0.15"`.

**Unchanged rules**

- No animations anywhere.
- The log is unchanged: new log calls pass the throwable with a fixed message.
- Doclint runs on public types: no bare `<`, `>` or `&` in JavaDoc outside `{@code}`, and `@param` names must match.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in Java source. Write
  escapes such as `"\u00A0"` and `"\u202E"`. `"…"`, `"×"` and `"·"` may be written as characters.

**Tests and commits**

- Inject clocks and executors; don't sleep. EDT tests use `@ExtendWith(EdtTestExtension.class)`. Field initializers,
  `@BeforeEach` and `@AfterEach` do not run on the EDT.
- One commit per task. Every commit message ends with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Spec interpretations (recorded deviations)

Recorded here and, at completion, in `docs/STATUS.md`:

1. **SDK 0.14.0, not 0.13.0.** 0.13.0 shipped with the dock layout on 2026-10-02 (`22eff3c6`, `PanelSpec.withSplit`),
   after the spec was written. Manifests declare `>=0.14.0, <0.15`.
2. **`NotificationGroup.error(title, content, cause)`** is added beside the spec's `error(title, cause)`. It is for a
   failure the plugin can explain in its own words and still wants Show more… for, which most migrated call sites are.
   The shortcut methods cut over-long content to 4,000 characters; `NotificationSpec` itself rejects it.
3. **Group ids.**
   - `GroupSpec` ids follow the panel id pattern, at most 160 characters. The host also requires `<plugin-id>.` and
     reserves `.activities`.
   - The application's model accepts any plugin-id-shaped group id (`[a-z][a-z0-9_.-]{0,159}`), so a plugin id
     without a dot still names its default group.
4. **With a cause, a spec has at most 7 actions,** because the host adds Show more… as the eighth.
5. **A cause becomes a plugin incident named after the notification's title** (its operation). Each such post is
   announced on its own and never folded into an earlier notification, as plan 2 does for launch failures. A cause the
   incident system rejects (its own failure, or one raised while announcing) still posts the notification, without
   Show more….
6. **The plugin host posts activity outcomes** (`PluginHost` hears the activity topic): SUCCEEDED as INFO, FAILED as
   ERROR, titled after the activity with its detail as content, its action as the body's default action; CANCELLED
   posts nothing, and neither does an activity of a plugin that is no longer running (the host fails those when the
   plugin stops or fails to start, and the plugin's incident already says why).
   - This differs from spec section 1, which has `ActivityNotifier` do it. Putting it in the host lets the contract
     suite cover both hosts.
   - `ActivityNotifier` keeps feeding Buddy unchanged until plan 4.
7. **Plugin problems** (spec section 7, producer 5) are posted once after startup, one per problem, with Open Plugins:
   - a load failure: "Name could not be loaded", with Show more… only when an exception lies behind it;
   - a skipped plugin, except a copy superseded by another copy: "Name was not started";
   - a discovery problem: "A plugin could not be read".

   A plugin that failed while starting is not repeated: plan 2's incident notification already announces it (plan 2's
   hand-off). NEEDS_CONSENT and DISABLED are not problems.
8. **A plugin settings file that cannot be opened** is a plugin problem in `jasper.plugins`, posted through the
   runtime's `NotificationHost.problem`. It used to be the runtime's `notice`.
9. **Plan 1's deferred findings for plan 3** are fixed in the application's model, for every poster:
   - poster icons are fitted to 16 px;
   - bidi controls are stripped;
   - text made only of no-break spaces or zero-width characters counts as blank;
   - group names are one line of at most 80 characters.
10. **The Errors window keeps the error icon for plugin incidents.** Showing a plugin's own icon needs the plugin
    manager's icon decoding (`pluginmanager.PluginIcons`), so it is deferred again (plan 2's deferred finding).
11. **`NotificationRequest.message` and `JasperApplication.notice` are removed** with `Notices`; they only served
    plugin notices.
12. **Between Task 4 and Task 12, a not-yet-migrated `notices().error(message)` posts an ERROR titled with the plugin's
    name**, with the message as content. Task 12 removes it.
13. **`NotificationHandle.expired()` becomes true** after `expire()`, after an action that expires it, after the user
    removes or clears it in history, when its group drops it (OFF), and when the plugin stops. Two posts that the center
    coalesces (identical text within 60 s) share one entry, so expiring either expires both.
14. **A plugin's notifications carry the plugin's id as their source**, so plan 5 can tell them apart. Plugin incident
    and plugin problem notifications stay Jasper's own (plan 2).
15. **Plan 2's hand-off is closed:** SDK specs validate everything the application's model would reject, so a
    plugin's bad input fails in `dev.jasper.sdk` with the plugin's frames. It is attributed to the plugin and never
    taken for the notification system's own failure.
16. **A cause wrapped by a future is recorded as the failure behind it.** `IncidentNotices.pluginNotice` unwraps
    `CompletionException` and `ExecutionException`, so the Errors window names the real exception. The fake records
    what the plugin passed. Migrated plugins pass the failure they were given.
17. **Migrated titles never carry unbounded user data.** Paths, host names and failure messages go in the content, so
    the center's coalescing and the Errors window's naming stay stable. Every migrated failure with a `Throwable` at
    hand passes it as the cause (spec section 6, "most of them"), even where the user can fix it, such as a malformed
    `hosts.toml`. A problem with no exception behind it carries no cause; one that leaves the plugin working (vimrc
    lines skipped, a `servers.toml` entry ignored, no vault or window yet, a cancelled host import, Split with Same
    Host outside an SSH pane) is a warning.

## Review Focus

1. **A plugin that posts while it is being stopped:**
   - A post that lands after its context closed is expired at once.
   - A post made after it closed throws `IllegalStateException`, and nothing else.

   Pinned by Task 4's `aPostThatLandsAfterThePluginStoppedExpiresAtOnce` and the contract test
   `aHandleExpiresItsNotificationAndStoppingExpiresTheRest`.
2. **Expiring a handle before its post reached the UI thread** still expires the notification once it lands. Pinned by
   Task 4's `expiringBeforeThePostLandsExpiresItWhenItLands`.
3. **Invisible or oversized text from a plugin** (only no-break or zero-width spaces, a 201-character title, bidi
   controls, a ninth action, eight actions with a cause) is rejected by the SDK in the plugin's own call, never deep in
   the application. Pinned by Task 2's value tests and Task 1's model tests.
4. **A plugin's action or body that throws** is contained and attributed to the plugin. It neither escapes into the
   card nor loops. Pinned by the contract test `actionsAndTheBodyRunAndExpireWhenTheySay` ("Break").
5. **A plugin declaring a group it does not own** (another plugin's prefix, `test.ab.x` for `test.a`, the host's
   `.activities`) is refused, and the same id twice returns the same group. Pinned by the contract tests
   `groupsStayInThePluginsNamespace` and `notificationsReachTheHostInTheirGroups`.

## File structure

**Create**

- In the SDK, `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/`:
  - `Notifications`, `NotificationGroup`, `NotificationSpec`, `NotificationAction`, `NotificationHandle`;
  - `GroupSpec`, `DisplayMode`, `Severity`;
  - `Texts` (package-private text rules) and `package-info`.
- `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeNotifications.java`: the fake host's record, plus
  standalone recording groups for component tests.
- `jasper-app/src/main/java/dev/jasper/app/plugins/HostedNotifications.java`: the SDK adapter over the center.
- `jasper-app/src/main/java/dev/jasper/app/application/PluginProblemNotices.java`: startup plugin problems.
- Tests:
  - `jasper-sdk/src/test/java/dev/jasper/sdk/notifications/NotificationsValuesTest.java`
  - `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeNotificationsTest.java`
  - `jasper-app/src/test/java/dev/jasper/app/notificationui/NotificationStyleTest.java`
  - `jasper-app/src/test/java/dev/jasper/app/plugins/NotificationsFixture.java`
  - `jasper-app/src/test/java/dev/jasper/app/plugins/HostedNotificationsTest.java`
  - `jasper-app/src/test/java/dev/jasper/app/application/PluginProblemNoticesTest.java`

**Modify**

- **App model and views:** `notifications/NotificationText`, `NotificationRequest`, `NotificationAction`,
  `NotificationGroup` and `notificationui/NotificationStyle`.
- **SDK:** `plugin/PluginContext`, `activity/Activities`, `activity/ActivityHandle`, `ui/Platform`, `JasperSdk`; delete
  `ui/Notices`.
- **Testkit:** `FakePluginContext`, `FakePluginHost`, the contract suite (`ContractHarness`, `PluginContractTest`) and
  `FakeContractTest`.
- **App plugins package:** `PluginRuntime`, `PluginHost`, `HostedContext` and `package-info`.
- **Application:** `IncidentNotices` and `JasperApplication`.
- **Bundled plugins:** Snippets, Editor, File Server, Vault and Remote (each call site listed in its task), the seven
  `plugin.toml` manifests, and their tests.
- **Docs:** `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `jasper-sdk/README.md`, `docs/notifications.md`,
  `docs/app-maintenance.md`, `docs/STATUS.md`, and the spec's and this plan's status banners.

---

### Task 1: Model hardening

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/NotificationText.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/NotificationRequest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/NotificationAction.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/NotificationGroup.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/notificationui/NotificationStyle.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notifications/NotificationValuesTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notificationui/NotificationStyleTest.java`

**Interfaces:**
- Consumes: plan 1's model.
- Produces: package-private `NotificationText.visible(String)`; `NotificationGroup.MAX_NAME = 80` and its relaxed id
  pattern; package-private `NotificationStyle.fitted(Icon)`.

- [ ] **Step 1: Write the failing model tests**

In `NotificationValuesTest`, replace the whole `groupIdsAreNamespaced` test with:

```java
    @Test void groupIdsArePluginIdShapedAndNamesAreOneBoundedLine() {
        for (String good : List.of("jasper.general", "sample", "dev.example.tool.activities", "dev.example-tool_x"))
            assertThat(new NotificationGroup(good, "Name", "jasper", DisplayMode.CARD).id()).isEqualTo(good);
        for (String bad : List.of("", ".x", "Jasper.general", "x.y z", "a".repeat(161)))
            assertThatThrownBy(() -> new NotificationGroup(bad, "Name", "jasper", DisplayMode.CARD)).as(bad)
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new NotificationGroup("jasper.x", "  Two\nlines  ", "jasper", DisplayMode.CARD).name()).isEqualTo("Two lines");
        assertThat(new NotificationGroup("jasper.x", "n".repeat(100), "jasper", DisplayMode.CARD).name())
            .hasSize(NotificationGroup.MAX_NAME).endsWith("…");
        for (String blank : List.of(" ", "\u00A0", "\u200B\u2060"))
            assertThatThrownBy(() -> new NotificationGroup("jasper.x", blank, "jasper", DisplayMode.CARD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void invisibleTextIsBlankAndBidiControlsAreDropped() {
        for (String invisible : List.of("\u00A0\u2007", "\u200B\u2060", "\uFEFF", "\u202E"))
            assertThatThrownBy(() -> titled(invisible)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationAction.of("\u00A0", () -> {})).isInstanceOf(IllegalArgumentException.class);
        assertThat(titled("a\u202Eb\u2066c\u200Fd").title()).isEqualTo("abcd");
        assertThat(titled("t").withContent("x\u2067y\u2069\nz\u061C").content()).isEqualTo("xy\nz");
        assertThat(titled("a\u200Db").title()).as("a zero-width joiner joins emoji, so it stays").isEqualTo("a\u200Db");
    }
```

- [ ] **Step 2: Write the failing icon test**

Create `jasper-app/src/test/java/dev/jasper/app/notificationui/NotificationStyleTest.java`:

```java
package dev.jasper.app.notificationui;

import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationStyleTest {
    private static Icon square(int size, Color color) {
        return new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) { g.setColor(color); g.fillRect(x, y, size, size); }
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    private static BufferedImage painted(Icon icon, int size) {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { icon.paintIcon(null, g, 0, 0); } finally { g.dispose(); }
        return image;
    }

    @Test void aPostersIconIsFittedToTheSixteenPixelBox() {
        int box = UIScale.scale(16);
        Icon big = NotificationStyle.fitted(square(64, Color.RED));
        assertThat(big.getIconWidth()).isEqualTo(box);
        assertThat(big.getIconHeight()).isEqualTo(box);
        BufferedImage bigImage = painted(big, box * 2);
        assertThat(new Color(bigImage.getRGB(box / 2, box / 2), true)).isEqualTo(Color.RED);
        assertThat(bigImage.getRGB(box + box / 2, box + box / 2)).as("a large icon is scaled into the box").isZero();
        Icon small = NotificationStyle.fitted(square(box / 2, Color.BLUE));
        assertThat(small.getIconWidth()).isEqualTo(box);
        BufferedImage smallImage = painted(small, box);
        assertThat(new Color(smallImage.getRGB(box / 2, box / 2), true)).as("a small icon is centred").isEqualTo(Color.BLUE);
        assertThat(smallImage.getRGB(0, 0)).isZero();
        Icon exact = square(box, Color.GREEN);
        assertThat(NotificationStyle.fitted(exact)).isSameAs(exact);
    }
}
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.NotificationValuesTest' --tests 'dev.jasper.app.notificationui.NotificationStyleTest'`
Expected: compilation fails, because `NotificationGroup.MAX_NAME` and `NotificationStyle.fitted` do not exist.

- [ ] **Step 4: Implement**

In `NotificationText.java`:

1. In `line`, replace `if (Character.isISOControl(c)) continue;` with:

```java
            if (Character.isISOControl(c) || bidiControl(c)) continue;
```

2. In `block`, replace `if (c == '\n' || !Character.isISOControl(c)) out.append(c);` with:

```java
            if (c == '\n' || !(Character.isISOControl(c) || bidiControl(c))) out.append(c);
```

3. Before `cut`, add:

```java
    /**
     * Whether the text shows anything: some character that is not white space, a space separator (no-break spaces
     * included), a formatting mark (zero-width characters, bidi controls) or another control character.
     */
    static boolean visible(String text) {
        return text.codePoints().anyMatch(point -> !Character.isWhitespace(point) && !Character.isSpaceChar(point)
            && Character.getType(point) != Character.FORMAT && !Character.isISOControl(point));
    }

    /** A bidirectional formatting character: it could reorder how the text around it reads. */
    private static boolean bidiControl(char c) {
        return c == '\u061C' || c == '\u200E' || c == '\u200F' || (c >= '\u202A' && c <= '\u202E') || (c >= '\u2066' && c <= '\u2069');
    }
```

In `NotificationRequest.java`, replace `if (title.isEmpty()) throw new IllegalArgumentException("A notification needs a title");` with:

```java
        if (!NotificationText.visible(title)) throw new IllegalArgumentException("A notification needs a title");
```

In `NotificationAction.java`, replace `if (label.isEmpty()) throw new IllegalArgumentException("An action needs a label");` with:

```java
        if (!NotificationText.visible(label)) throw new IllegalArgumentException("An action needs a label");
```

Replace the body of `NotificationGroup.java` (keep the package line) with:

```java
package dev.jasper.app.notifications;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A kind of notification the user can tune: an id shaped like a plugin id (Jasper's are {@code jasper.} ones, a
 * plugin's are its id and ids under it), the name Settings and history show (one line of at most {@value #MAX_NAME}
 * characters), who declared it ({@code jasper} or a plugin id) and how its notifications appear until the user says
 * otherwise.
 */
public record NotificationGroup(String id, String name, String owner, DisplayMode defaultMode) {
    static final int MAX_NAME = 80;
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_.-]{0,159}");

    public NotificationGroup {
        if (id == null || !ID.matcher(id).matches()) throw new IllegalArgumentException("Not a notification group id: " + id);
        name = NotificationText.line(Objects.requireNonNull(name, "name"), MAX_NAME);
        if (!NotificationText.visible(name)) throw new IllegalArgumentException("A group needs a name");
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("A group needs an owner");
        Objects.requireNonNull(defaultMode, "defaultMode");
    }
}
```

In `NotificationStyle.java`, add these imports: `com.formdev.flatlaf.util.UIScale`, `java.awt.Component`,
`java.awt.Graphics`, `java.awt.Graphics2D` and `java.awt.RenderingHints`. Then:

1. In `icon`, replace `return notification.request().icon().orElseGet(() -> AppIcons.bundled(switch (notification.request().severity()) {` with:

```java
        return notification.request().icon().map(NotificationStyle::fitted).orElseGet(() -> AppIcons.bundled(switch (notification.request().severity()) {
```

2. After `icon`, add:

```java
    /** A poster's icon drawn within the 16 px box: a larger one is scaled down, a smaller one centred. */
    static Icon fitted(Icon icon) {
        int box = UIScale.scale(16);
        if (icon.getIconWidth() == box && icon.getIconHeight() == box) return icon;
        return new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                int width = Math.max(1, icon.getIconWidth()), height = Math.max(1, icon.getIconHeight());
                double scale = Math.min(1.0, Math.min((double) box / width, (double) box / height));
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.translate(x + (box - width * scale) / 2.0, y + (box - height * scale) / 2.0);
                    g2.scale(scale, scale);
                    icon.paintIcon(c, g2, 0, 0);
                } finally {
                    g2.dispose();
                }
            }
            @Override public int getIconWidth() { return box; }
            @Override public int getIconHeight() { return box; }
        };
    }
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.*' --tests 'dev.jasper.app.notificationui.*'`
Expected: PASS, including every existing notification test.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/notifications/NotificationText.java jasper-app/src/main/java/dev/jasper/app/notifications/NotificationRequest.java jasper-app/src/main/java/dev/jasper/app/notifications/NotificationAction.java jasper-app/src/main/java/dev/jasper/app/notifications/NotificationGroup.java jasper-app/src/main/java/dev/jasper/app/notificationui/NotificationStyle.java jasper-app/src/test/java/dev/jasper/app/notifications/NotificationValuesTest.java jasper-app/src/test/java/dev/jasper/app/notificationui/NotificationStyleTest.java
git commit -m "fix(notifications): treat invisible text as blank, drop bidi controls and fit poster icons" -m "Titles, labels and group names made only of no-break or zero-width spaces are blank; bidi controls are stripped; group ids take any plugin-id shape and names one line of 80; a poster's icon is drawn within the 16 px box." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: The SDK notifications API

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/Severity.java`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/DisplayMode.java`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/Texts.java`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/GroupSpec.java`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/NotificationAction.java`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/NotificationSpec.java`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/NotificationHandle.java`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/NotificationGroup.java`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/Notifications.java`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/package-info.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/notifications/NotificationsValuesTest.java`

**Interfaces:**
- Consumes: `dev.jasper.sdk.terminal.WindowHandle` (`id()`).
- Produces (package `dev.jasper.sdk.notifications`, all public):
  - `enum Severity { INFO, WARNING, ERROR }` and `enum DisplayMode { CARD, STICKY, HISTORY, OFF }`.
  - `record GroupSpec(String id, String name, DisplayMode defaultMode)`.
  - `record NotificationAction(String label, Runnable run, boolean expires)` with `of(label, run)`.
  - `record NotificationSpec(Severity severity, String title, String content, Optional<Icon> icon,
    List<NotificationAction> actions, Optional<Runnable> activate, Optional<Throwable> cause,
    Optional<WindowHandle> window)`:
    - factory `of(severity, title)`;
    - `withContent`, `withIcon`, `withAction`, `withActivate`, `withCause`, `withWindow`;
    - package-private `MAX_TITLE = 200`, `MAX_CONTENT = 4_000`, `MAX_ACTIONS = 8`.
  - `interface NotificationHandle { void expire(); boolean expired(); }`.
  - `interface NotificationGroup`:
    - `String id()` and `NotificationHandle post(NotificationSpec)`;
    - defaults `info(title, content)`, `warning(title, content)`, `error(title, content)`, `error(title, Throwable)`
      and `error(title, content, Throwable)`.
  - `interface Notifications { NotificationGroup group(); NotificationGroup group(GroupSpec spec); }`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-sdk/src/test/java/dev/jasper/sdk/notifications/NotificationsValuesTest.java`:

```java
package dev.jasper.sdk.notifications;

import java.util.ArrayList;
import java.util.List;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NotificationsValuesTest {
    /** A group that keeps what was posted. */
    private static final class Kept implements NotificationGroup {
        final List<NotificationSpec> posted = new ArrayList<>();
        @Override public String id() { return "dev.example.tool"; }
        @Override public NotificationHandle post(NotificationSpec spec) {
            posted.add(spec);
            return new NotificationHandle() {
                @Override public void expire() { }
                @Override public boolean expired() { return false; }
            };
        }
    }

    @Test void aSpecNeedsATitleThatShowsSomething() {
        assertThat(NotificationSpec.of(Severity.INFO, "Saved").title()).isEqualTo("Saved");
        for (String blank : List.of("", "  ", "\n\t", "\u00A0\u2007", "\u200B\u2060", "\u202E\u2066", "\uFEFF"))
            assertThatIllegalArgumentException().as("%s", blank.codePoints().boxed().toList())
                .isThrownBy(() -> NotificationSpec.of(Severity.INFO, blank));
        assertThatNullPointerException().isThrownBy(() -> NotificationSpec.of(Severity.INFO, null));
        assertThat(NotificationSpec.of(Severity.INFO, "t".repeat(200)).title()).hasSize(200);
        assertThatIllegalArgumentException().isThrownBy(() -> NotificationSpec.of(Severity.INFO, "t".repeat(201)));
    }

    @Test void contentActionsAndTheOptionalPartsAreBounded() {
        var spec = NotificationSpec.of(Severity.WARNING, "Sync").withContent("c".repeat(4_000));
        assertThat(spec.content()).hasSize(4_000);
        assertThatIllegalArgumentException().isThrownBy(() -> spec.withContent("c".repeat(4_001)));
        var eight = NotificationSpec.of(Severity.INFO, "t");
        for (int i = 0; i < 8; i++) eight = eight.withAction(NotificationAction.of("Action " + i, () -> { }));
        var full = eight;
        assertThatIllegalArgumentException().isThrownBy(() -> full.withAction(NotificationAction.of("Ninth", () -> { })));
        assertThatIllegalArgumentException().as("Show more… takes the eighth place when there is a cause")
            .isThrownBy(() -> full.withCause(new IllegalStateException("x")));
        assertThatIllegalArgumentException().isThrownBy(() -> NotificationAction.of("\u00A0", () -> { }));
        assertThatIllegalArgumentException().isThrownBy(() -> NotificationAction.of("l".repeat(41), () -> { }));
        assertThat(new NotificationAction("Keep", () -> { }, false).expires()).isFalse();
        assertThat(NotificationAction.of("Go", () -> { }).expires()).isTrue();
        Runnable body = () -> { };
        var cause = new IllegalStateException("x");
        var icon = new ImageIcon();
        var rich = NotificationSpec.of(Severity.ERROR, "t").withActivate(body).withCause(cause).withIcon(icon);
        assertThat(rich.activate()).containsSame(body);
        assertThat(rich.cause()).containsSame(cause);
        assertThat(rich.icon()).containsSame(icon);
        assertThat(rich.window()).isEmpty();
    }

    @Test void groupSpecsAreNamespacedAndNamedOnOneLine() {
        assertThat(new GroupSpec("dev.example.tool.sync", "Tool sync", DisplayMode.HISTORY).id()).isEqualTo("dev.example.tool.sync");
        for (String bad : List.of("tool", "Dev.example.x", "dev..x", "dev.x.", ".dev.x", "dev.x y", "d." + "x".repeat(159)))
            assertThatIllegalArgumentException().as(bad).isThrownBy(() -> new GroupSpec(bad, "Name", DisplayMode.CARD));
        for (String name : List.of(" ", "\u200B", "Two\nlines", "n".repeat(81)))
            assertThatIllegalArgumentException().as(name).isThrownBy(() -> new GroupSpec("dev.example.x", name, DisplayMode.CARD));
        assertThatNullPointerException().isThrownBy(() -> new GroupSpec("dev.example.x", "X", null));
    }

    @Test void theShortcutsPostTheirSeverityAndAnErrorsCauseIsSummarized() {
        var group = new Kept();
        group.info("Saved", "Three hosts");
        group.warning("Slow", "");
        group.error("Broke", "detail");
        group.error("Could not copy the address", new IllegalStateException("clipboard busy\nsecond line"));
        group.error("Could not save", "hosts.toml is read-only", new java.io.UncheckedIOException(new java.io.IOException("read-only")));
        var wrapped = new java.util.concurrent.CompletionException(new java.io.IOException("connection reset"));
        group.error("Could not connect", wrapped);
        assertThat(group.posted).extracting(NotificationSpec::severity, NotificationSpec::title, NotificationSpec::content)
            .containsExactly(tuple(Severity.INFO, "Saved", "Three hosts"), tuple(Severity.WARNING, "Slow", ""),
                tuple(Severity.ERROR, "Broke", "detail"),
                tuple(Severity.ERROR, "Could not copy the address", "IllegalStateException: clipboard busy"),
                tuple(Severity.ERROR, "Could not save", "hosts.toml is read-only"),
                tuple(Severity.ERROR, "Could not connect", "IOException: connection reset"));
        assertThat(group.posted.get(2).cause()).isEmpty();
        assertThat(group.posted.get(3).cause()).isPresent();
        assertThat(group.posted.get(4).cause()).isPresent();
        assertThat(group.posted.get(5).cause()).as("the cause is kept as given").containsSame(wrapped);
    }

    @Test void aCauseThatCannotDescribeItselfIsSummarizedByItsNameAndLongTextIsCut() {
        var group = new Kept();
        group.error("Broke", new RuntimeException() { @Override public String getMessage() { throw new IllegalStateException("no"); } });
        group.error("Broke", new IllegalStateException("m".repeat(500)));
        group.error("Broke", "c".repeat(5_000), new IllegalStateException("x"));
        group.info("Long", "i".repeat(5_000));
        assertThat(group.posted.get(0).content()).isEqualTo(group.posted.get(0).cause().orElseThrow().getClass().getName());
        assertThat(group.posted.get(1).content()).hasSize(300).startsWith("IllegalStateException: mmm").endsWith("…");
        assertThat(group.posted.get(2).content()).as("a shortcut cuts what it is given").hasSize(4_000).endsWith("…");
        assertThat(group.posted.get(3).content()).hasSize(4_000);
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-sdk:test --tests 'dev.jasper.sdk.notifications.NotificationsValuesTest'`
Expected: compilation fails, because the package does not exist.

- [ ] **Step 3: Write the values**

Create `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/Severity.java`:

```java
package dev.jasper.sdk.notifications;

/** How serious a notification is; it picks the default icon. */
public enum Severity {
    /** Something happened. */
    INFO,
    /** Something may need attention. */
    WARNING,
    /** Something failed. */
    ERROR
}
```

Create `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/DisplayMode.java`:

```java
package dev.jasper.sdk.notifications;

/** How a group's notifications appear until the user chooses otherwise in Settings. */
public enum DisplayMode {
    /** A card that hides by itself, and history. */
    CARD,
    /** A card that stays until it is closed, and history. */
    STICKY,
    /** History only. */
    HISTORY,
    /** Not shown. */
    OFF
}
```

Create `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/Texts.java`:

```java
package dev.jasper.sdk.notifications;

import java.util.Objects;

/** The text the host can show: something visible, within a length. Pure. */
final class Texts {
    private Texts() {}

    /**
     * Whether the text shows anything: some character that is not white space, a space separator (no-break spaces
     * included), a formatting mark (zero-width characters, bidi controls) or another control character.
     */
    static boolean visible(String text) {
        return text.codePoints().anyMatch(point -> !Character.isWhitespace(point) && !Character.isSpaceChar(point)
            && Character.getType(point) != Character.FORMAT && !Character.isISOControl(point));
    }

    /** The text, when it shows something and is at most {@code max} characters long. */
    static String requireVisible(String text, int max, String what) {
        Objects.requireNonNull(text, what);
        if (!visible(text)) throw new IllegalArgumentException("A notification " + what + " must show something");
        if (text.length() > max) throw new IllegalArgumentException("A notification " + what + " has at most " + max + " characters");
        return text;
    }

    /** {@code text}, or its first characters and an ellipsis in {@code max}; a surrogate pair is kept whole or dropped. */
    static String cut(String text, int max) {
        if (text.length() <= max) return text;
        int end = max - 1;
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end) + "…";
    }
}
```

Create `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/GroupSpec.java`:

```java
package dev.jasper.sdk.notifications;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A notification group a plugin declares besides its default one, so the user can tune it separately.
 *
 * @param id namespaced id that starts with the plugin's id and a dot, such as {@code dev.example.tool.sync}; the
 *     plugin's own id names its default group, and {@code <plugin-id>.activities} is the host's
 * @param name what Settings and history call the group, such as "Tool sync": one visible line of at most 80 characters
 * @param defaultMode how its notifications appear until the user chooses otherwise
 */
public record GroupSpec(String id, String name, DisplayMode defaultMode) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");
    static final int MAX_ID = 160;
    static final int MAX_NAME = 80;

    /** Validates the id and the name. */
    public GroupSpec {
        if (id == null || id.length() > MAX_ID || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced notification group id: " + id);
        Texts.requireVisible(name, MAX_NAME, "group name");
        if (name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) throw new IllegalArgumentException("A group's name is one line");
        Objects.requireNonNull(defaultMode, "defaultMode");
    }
}
```

Create `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/NotificationAction.java`:

```java
package dev.jasper.sdk.notifications;

import java.util.Objects;

/**
 * A link on a notification's card and history row. It runs on the UI thread inside the plugin's containment, so what
 * it throws becomes an incident attributed to the plugin.
 *
 * @param label the link's text: visible, at most 40 characters
 * @param run what it does
 * @param expires whether the notification expires after it runs, so its links stop working
 */
public record NotificationAction(String label, Runnable run, boolean expires) {
    static final int MAX_LABEL = 40;

    /** Validates the label. */
    public NotificationAction {
        Texts.requireVisible(label, MAX_LABEL, "action label");
        Objects.requireNonNull(run, "run");
    }

    /**
     * An action that expires its notification after running.
     *
     * @param label the link's text
     * @param run what it does
     * @return the action
     */
    public static NotificationAction of(String label, Runnable run) { return new NotificationAction(label, run, true); }
}
```

Create `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/NotificationSpec.java`:

```java
package dev.jasper.sdk.notifications;

import dev.jasper.sdk.terminal.WindowHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.Icon;

/**
 * What a plugin posts. Text is plain and never markup.
 *
 * @param severity it picks the default icon
 * @param title one visible line of at most 200 characters; say what happened, and keep paths, host names and messages
 *     for the content
 * @param content more detail, possibly several lines, at most 4,000 characters; may be empty
 * @param icon a 16 px icon instead of the severity's
 * @param actions links, at most 8, or 7 with a cause, since the host then adds Show more… after them
 * @param activate what a click on the body does
 * @param cause the failure behind an error: the host records it as an incident attributed to the plugin and offers
 *     Show more…, which opens its stack trace
 * @param window the window whose card it is; absent means the window the user used last
 */
public record NotificationSpec(Severity severity, String title, String content, Optional<Icon> icon,
                               List<NotificationAction> actions, Optional<Runnable> activate, Optional<Throwable> cause,
                               Optional<WindowHandle> window) {
    static final int MAX_TITLE = 200;
    static final int MAX_CONTENT = 4_000;
    static final int MAX_ACTIONS = 8;

    /** Validates the text and the number of actions, and copies the actions. */
    public NotificationSpec {
        Objects.requireNonNull(severity, "severity");
        Texts.requireVisible(title, MAX_TITLE, "title");
        Objects.requireNonNull(content, "content");
        if (content.length() > MAX_CONTENT)
            throw new IllegalArgumentException("A notification's content has at most " + MAX_CONTENT + " characters");
        Objects.requireNonNull(icon, "icon");
        actions = List.copyOf(actions);
        Objects.requireNonNull(activate, "activate");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(window, "window");
        int room = cause.isPresent() ? MAX_ACTIONS - 1 : MAX_ACTIONS;
        if (actions.size() > room)
            throw new IllegalArgumentException("A notification has at most " + room + " actions" + (cause.isPresent() ? " with a cause" : ""));
    }

    /**
     * A notification with only a severity and a title.
     *
     * @param severity how serious it is
     * @param title what happened
     * @return the spec
     */
    public static NotificationSpec of(Severity severity, String title) {
        return new NotificationSpec(severity, title, "", Optional.empty(), List.of(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * A copy with this content.
     *
     * @param value more detail
     * @return the copy
     */
    public NotificationSpec withContent(String value) {
        return new NotificationSpec(severity, title, value, icon, actions, activate, cause, window);
    }

    /**
     * A copy with this icon.
     *
     * @param value a 16 px icon
     * @return the copy
     */
    public NotificationSpec withIcon(Icon value) {
        return new NotificationSpec(severity, title, content, Optional.of(value), actions, activate, cause, window);
    }

    /**
     * A copy with one more action.
     *
     * @param action the link
     * @return the copy
     */
    public NotificationSpec withAction(NotificationAction action) {
        var more = new ArrayList<>(actions);
        more.add(Objects.requireNonNull(action, "action"));
        return new NotificationSpec(severity, title, content, icon, more, activate, cause, window);
    }

    /**
     * A copy with a default action, run when the user clicks the body.
     *
     * @param value what a click does
     * @return the copy
     */
    public NotificationSpec withActivate(Runnable value) {
        return new NotificationSpec(severity, title, content, icon, actions, Optional.of(value), cause, window);
    }

    /**
     * A copy with the failure behind it.
     *
     * @param value the failure
     * @return the copy
     */
    public NotificationSpec withCause(Throwable value) {
        return new NotificationSpec(severity, title, content, icon, actions, activate, Optional.of(value), window);
    }

    /**
     * A copy whose card shows in this window.
     *
     * @param value the window
     * @return the copy
     */
    public NotificationSpec withWindow(WindowHandle value) {
        return new NotificationSpec(severity, title, content, icon, actions, activate, cause, Optional.of(value));
    }
}
```

`Optional.of(value)` throws `NullPointerException` for a null value, as the other SDK `with` methods do.

- [ ] **Step 4: Write the services**

Create `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/NotificationHandle.java`:

```java
package dev.jasper.sdk.notifications;

/** A posted notification, for the plugin to withdraw once it no longer applies. Safe from any thread. */
public interface NotificationHandle {
    /** Expires the notification: its card closes and its links stop working; it stays in history. Idempotent. */
    void expire();

    /**
     * Whether it has expired: through {@link #expire()}, an action that expires it, the user removing or clearing it,
     * its group dropping it, or the plugin stopping.
     *
     * @return true once expired
     */
    boolean expired();
}
```

Create `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/NotificationGroup.java`:

```java
package dev.jasper.sdk.notifications;

import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * One of the plugin's notification groups. Posting is safe from any thread and returns at once; the host shows the
 * notification as the group's display mode says. The shortcuts cut content longer than 4,000 characters. Implemented
 * by the application and by the testkit.
 */
public interface NotificationGroup {
    /**
     * The group's id.
     *
     * @return the id
     */
    String id();

    /**
     * Posts a notification.
     *
     * @param spec what to show
     * @return the handle that expires it
     * @throws IllegalStateException when the plugin's context is closed
     */
    NotificationHandle post(NotificationSpec spec);

    /**
     * Posts information.
     *
     * @param title what happened
     * @param content more detail; may be empty
     * @return the handle
     */
    default NotificationHandle info(String title, String content) { return shortcut(Severity.INFO, title, content); }

    /**
     * Posts a warning.
     *
     * @param title what may need attention
     * @param content more detail; may be empty
     * @return the handle
     */
    default NotificationHandle warning(String title, String content) { return shortcut(Severity.WARNING, title, content); }

    /**
     * Posts an error.
     *
     * @param title what failed
     * @param content why, in the user's terms; may be empty
     * @return the handle
     */
    default NotificationHandle error(String title, String content) { return shortcut(Severity.ERROR, title, content); }

    /**
     * Posts an error caused by {@code cause}: its content is the cause's simple class name and the first line of its
     * message, and the host offers Show more…, which opens the stack trace. A {@link CompletionException} or
     * {@link ExecutionException} is read through to the failure behind it, so a future's failure can be passed as is.
     *
     * @param title what failed
     * @param cause the failure
     * @return the handle
     */
    default NotificationHandle error(String title, Throwable cause) {
        return post(NotificationSpec.of(Severity.ERROR, title).withContent(summary(Objects.requireNonNull(cause, "cause"))).withCause(cause));
    }

    /**
     * Posts an error in the plugin's own words that also carries its cause, so the host offers Show more….
     *
     * @param title what failed
     * @param content why, in the user's terms
     * @param cause the failure
     * @return the handle
     */
    default NotificationHandle error(String title, String content, Throwable cause) {
        Objects.requireNonNull(content, "content");
        return post(NotificationSpec.of(Severity.ERROR, title).withContent(Texts.cut(content, NotificationSpec.MAX_CONTENT))
            .withCause(Objects.requireNonNull(cause, "cause")));
    }

    private NotificationHandle shortcut(Severity severity, String title, String content) {
        Objects.requireNonNull(content, "content");
        return post(NotificationSpec.of(severity, title).withContent(Texts.cut(content, NotificationSpec.MAX_CONTENT)));
    }

    /** The cause's simple class name and its message's first line, at most 300 characters. Never throws. */
    private static String summary(Throwable cause) {
        Throwable shown = cause;
        try {
            for (int depth = 0; depth < 16 && (shown instanceof CompletionException || shown instanceof ExecutionException)
                    && shown.getCause() != null; depth++) shown = shown.getCause();
        } catch (RuntimeException unreadable) { /* keep what was read */ }
        String name = shown.getClass().getSimpleName();
        if (name.isEmpty()) name = shown.getClass().getName();
        String message;
        try { message = shown.getMessage(); }
        catch (RuntimeException unreadable) { message = null; }
        String line = message == null ? "" : message.lines().findFirst().orElse("").strip();
        return Texts.cut(line.isEmpty() ? name : name + ": " + line, 300);
    }
}
```

Create `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/Notifications.java`:

```java
package dev.jasper.sdk.notifications;

/**
 * The plugin's notifications. Each belongs to a group the user can tune in Settings: the plugin's default group, or one
 * it declares. Activity outcomes need nothing here: the host posts {@code ActivityHandle.succeed} and {@code fail} in
 * its own {@code <plugin-id>.activities} group, so muting those never mutes the plugin's errors. Plugins cannot
 * observe other notifications. No capability is needed.
 */
public interface Notifications {
    /**
     * The plugin's default group: its id is the plugin's id, it is named after the plugin and it shows cards.
     *
     * @return the default group
     */
    NotificationGroup group();

    /**
     * Declares another group; call it during start so Settings can list the group. Declaring an id again returns the
     * same group, and the plugin's own id returns the default group.
     *
     * @param spec the group
     * @return the group
     * @throws IllegalArgumentException when the id is outside the plugin's namespace or is {@code <plugin-id>.activities}
     * @throws IllegalStateException when the plugin's context is closed
     */
    NotificationGroup group(GroupSpec spec);
}
```

Create `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/package-info.java`:

```java
/**
 * Notifications a plugin posts: groups the user tunes, plain-text specs with links, an optional cause that the host
 * records as the plugin's incident, and handles that withdraw them. Depends on {@code dev.jasper.sdk.terminal}.
 */
package dev.jasper.sdk.notifications;
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :jasper-sdk:test --tests 'dev.jasper.sdk.notifications.*' :jasper-sdk:javadoc verifySdkArchitecture`
Expected: PASS (5 tests); javadoc and the SDK architecture check succeed.

- [ ] **Step 6: Commit**

```bash
git add jasper-sdk/src/main/java/dev/jasper/sdk/notifications jasper-sdk/src/test/java/dev/jasper/sdk/notifications
git commit -m "feat(sdk): add the notifications API" -m "Groups, plain-text specs with links, an optional cause and window, and handles, with the limits the host can show; nothing implements it yet." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: A plugin's notification with a cause

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/IncidentNotices.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/IncidentNoticesTest.java`

**Interfaces:**
- Consumes: plan 2's `IncidentNotices` (private `capture`, `record`, `showMore`, `occurrences`).
- Produces: `void IncidentNotices.pluginNotice(String pluginId, NotificationRequest request, Throwable cause,
  Consumer<Optional<UUID>> posted)`, safe from any thread. `posted` hears the posted notification's id on the EDT, or
  empty when its group dropped it.

- [ ] **Step 1: Write the failing tests**

Add these tests and helper to `IncidentNoticesTest`. Add imports for `dev.jasper.app.notifications.NotificationRequest`
and `java.util.Optional` if they are missing; the file already has `NotificationGroup`, `DisplayMode`, `Notification`,
`NotificationAction`, `Severity`, `UUID` and `ArrayList`.

```java
    private static NotificationRequest pluginRequest(String group, String content) {
        return NotificationRequest.of(group, Severity.ERROR, "Could not copy the address").withContent(content)
            .withSource("dev.jasper.remote").withAction(new NotificationAction("Retry", () -> { }, true));
    }

    private void declareRemote() {
        center.register(new NotificationGroup("dev.jasper.remote", "Remote", "dev.jasper.remote", DisplayMode.CARD));
    }

    @Test void aPluginsNotificationWithACauseIsItsIncidentAndOffersShowMoreLast() {
        var notices = notices();
        declareRemote();
        var posted = new ArrayList<Optional<UUID>>();
        notices.pluginNotice("dev.jasper.remote", pluginRequest("dev.jasper.remote", "Clipboard busy"),
            new IllegalStateException("clipboard busy"), posted::add);
        Incident incident = incidents.list().getFirst();
        assertThat(incident.source()).isEqualTo("dev.jasper.remote");
        assertThat(incident.operation()).isEqualTo("Could not copy the address");
        Notification notice = center.history().getFirst();
        assertThat(notice.request().group()).isEqualTo("dev.jasper.remote");
        assertThat(notice.request().source()).isEqualTo("dev.jasper.remote");
        assertThat(notice.request().content()).isEqualTo("Clipboard busy");
        assertThat(labels(notice)).containsExactly("Retry", "Show more…");
        assertThat(posted).containsExactly(Optional.of(notice.id()));
        center.runAction(notice.id(), 1);
        assertThat(opened).containsExactly(incident.id());
    }

    @Test void eachOfAPluginsNotificationsWithTheSameCauseIsPostedOnItsOwn() {
        var notices = notices();
        declareRemote();
        List<IllegalStateException> twice = sameSite(2);
        notices.pluginNotice("dev.jasper.remote", pluginRequest("dev.jasper.remote", "first"), twice.get(0), id -> { });
        notices.pluginNotice("dev.jasper.remote", pluginRequest("dev.jasper.remote", "second"), twice.get(1), id -> { });
        assertThat(center.history()).extracting(entry -> entry.request().content()).containsExactly("second", "first");
        assertThat(incidents.list()).singleElement().satisfies(incident -> assertThat(incident.count()).isEqualTo(2));
    }

    @Test void aPluginsNotificationWhoseCauseCannotBeRecordedIsStillPosted() {
        var notices = notices();
        declareRemote();
        var posted = new ArrayList<Optional<UUID>>();
        notices.pluginNotice("dev.jasper.remote", pluginRequest("dev.jasper.remote", "own"),
            thrownIn("dev.jasper.app.notificationui.CardLayer"), posted::add);
        assertThat(incidents.list()).isEmpty();
        assertThat(center.history()).singleElement().satisfies(entry -> assertThat(labels(entry)).containsExactly("Retry"));
        assertThat(posted).containsExactly(Optional.of(center.history().getFirst().id()));
    }

    @Test void aCauseWrappedByAFutureIsRecordedAsTheFailureBehindIt() {
        var notices = notices();
        declareRemote();
        notices.pluginNotice("dev.jasper.remote", pluginRequest("dev.jasper.remote", "wrapped"),
            new java.util.concurrent.CompletionException(new java.io.IOException("connection reset")), id -> { });
        assertThat(incidents.list()).singleElement().satisfies(incident -> {
            assertThat(incident.exceptionClass()).isEqualTo("java.io.IOException");
            assertThat(incident.message()).isEqualTo("connection reset");
        });
    }

    @Test void aPluginsNotificationInAMutedGroupIsRecordedButReportsNothingPosted() {
        var notices = notices();
        center.register(new NotificationGroup("dev.jasper.remote.quiet", "Quiet", "dev.jasper.remote", DisplayMode.OFF));
        var posted = new ArrayList<Optional<UUID>>();
        notices.pluginNotice("dev.jasper.remote", pluginRequest("dev.jasper.remote.quiet", "quiet"),
            new IllegalStateException("x"), posted::add);
        assertThat(incidents.list()).hasSize(1);
        assertThat(center.history()).isEmpty();
        assertThat(posted).containsExactly(Optional.empty());
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.IncidentNoticesTest'`
Expected: compilation fails, because `pluginNotice` does not exist.

- [ ] **Step 3: Implement**

In `IncidentNotices.java`:

1. In the class JavaDoc, replace

```java
 * the notification system itself is left to the log, and capture never throws or re-enters. The three failure entry
 * points are safe from any thread; everything else is EDT only.
```

   with:

```java
 * the notification system itself is left to the log, and capture never throws or re-enters. A plugin's own
 * notification that carries a cause becomes the plugin's incident too, and is posted with Show more…. The four entry
 * points that take a failure are safe from any thread; everything else is EDT only.
```

2. Below the `LOG` field, add:

```java
    /** For an announcement whose posted id nobody needs. */
    private static final Consumer<Optional<UUID>> UNHEARD = id -> { };
```

3. In `jasperFailure` and `pluginFailure`, replace `true, this::announcement);` with `true, this::announcement, UNHEARD);`.

4. In `launchFailure`, replace

```java
        if (capture(NotificationRequest.JASPER, LAUNCH, failure, false,
                incident -> launchRequest(window, text).withAction(showMore(incident)))) return;
```

with

```java
        if (capture(NotificationRequest.JASPER, LAUNCH, failure, false,
                incident -> launchRequest(window, text).withAction(showMore(incident)), UNHEARD)) return;
```

5. After `launchFailure` and `launchRequest`, add:

```java
    /**
     * A plugin's own notification that carries a cause (spec section 6). The cause is captured here, on the posting
     * thread, and recorded as an incident attributed to the plugin and named after the notification's title. The
     * notification is posted as the plugin asked, with Show more… last. Every post is announced on its own, never
     * folded into an earlier one. A cause that cannot be recorded (the notification system's own, or raised while an
     * incident is announced) still gets the notification, without Show more…. A future's wrapper is recorded as the
     * failure behind it. {@code posted} hears the notification's id on the EDT, or empty when its group dropped it.
     * Any thread.
     */
    void pluginNotice(String pluginId, NotificationRequest request, Throwable cause, Consumer<Optional<UUID>> posted) {
        if (closed) return;
        if (capture(pluginId, request.title(), unwrapped(cause), false, incident -> request.withAction(showMore(incident)), posted))
            return;
        try { ui.execute(() -> posted.accept(center.post(request))); }
        catch (RuntimeException unannounced) {
            LOG.log(System.Logger.Level.WARNING, "A plugin's notification could not be posted", unannounced);
        }
    }

    /** The failure behind a CompletionException or ExecutionException, as far as it can be read. Never throws. */
    private static Throwable unwrapped(Throwable cause) {
        Throwable inner = cause;
        try {
            for (int depth = 0; depth < 16 && (inner instanceof java.util.concurrent.CompletionException
                    || inner instanceof java.util.concurrent.ExecutionException) && inner.getCause() != null; depth++)
                inner = inner.getCause();
        } catch (RuntimeException unreadable) { /* keep what was read */ }
        return inner;
    }
```

6. Give `capture` the `posted` parameter: replace its signature

```java
    private boolean capture(String source, String operation, Throwable failure, boolean repeatable,
                            Function<Incident, NotificationRequest> announce) {
```

with

```java
    private boolean capture(String source, String operation, Throwable failure, boolean repeatable,
                            Function<Incident, NotificationRequest> announce, Consumer<Optional<UUID>> posted) {
```

   add `@param posted hears a non-repeatable announcement's id on the EDT, or empty when its group dropped it` to its
   JavaDoc, and replace

```java
        if (!repeatable) return queue(() -> record(source, operation, thread, capture, 1, false, announce));
```

   with

```java
        if (!repeatable) return queue(() -> record(source, operation, thread, capture, 1, false, announce, posted));
```

7. In `recordWaiting`, replace `taken.count().get(), true, announce);` with `taken.count().get(), true, announce, UNHEARD);`.

8. Replace the signature of `record`

```java
    private void record(String source, String operation, String thread, Capture capture, int times, boolean repeatable,
                        Function<Incident, NotificationRequest> announce) {
```

   with

```java
    private void record(String source, String operation, String thread, Capture capture, int times, boolean repeatable,
                        Function<Incident, NotificationRequest> announce, Consumer<Optional<UUID>> posted) {
```

   and its branch

```java
            if (repeatable) announceRepeatable(incident, times, announce);
            else center.post(announce.apply(incident)).ifPresent(id ->
                occurrences.computeIfAbsent(incident.id(), key -> new LinkedHashSet<>()).add(id));
```

   with

```java
            if (repeatable) announceRepeatable(incident, times, announce);
            else {
                Optional<UUID> landed = center.post(announce.apply(incident));
                landed.ifPresent(id -> occurrences.computeIfAbsent(incident.id(), key -> new LinkedHashSet<>()).add(id));
                posted.accept(landed);
            }
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*'`
Expected: PASS, including every existing `IncidentNoticesTest` test.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/application/IncidentNotices.java jasper-app/src/test/java/dev/jasper/app/application/IncidentNoticesTest.java
git commit -m "feat(notifications): record a plugin notification's cause as the plugin's incident" -m "The cause is captured on the posting thread and recorded on the EDT; the plugin's notification is posted on its own with Show more… last, and its id is reported back." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: `PluginContext.notifications()` in both hosts, with the contract suite

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginContext.java`
- Create: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeNotifications.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginContext.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java`
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java`
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java`
- Modify: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`
- Test: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeNotificationsTest.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedNotifications.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/package-info.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/NotificationRequest.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/plugins/NotificationsFixture.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/HostedNotificationsTest.java`
- Modify (tests): `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`, `HostedPaletteTest.java`,
  `HostedFileSourcesTest.java`, `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationNotificationsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/notifications/NotificationValuesTest.java`

**Interfaces:**
- Consumes:
  - from Task 2: the SDK notifications API;
  - from Task 3: `IncidentNotices.pluginNotice(String, NotificationRequest, Throwable, Consumer<Optional<UUID>>)`;
  - plan 1's `NotificationCenter` (`register`, `post`, `expire`, `onChange`, `history`, `groups`, `runAction`,
    `activate`) and plan 2's `Containment.run(String, String, Runnable)`.
- Produces:
  - **SDK:** `PluginContext.notifications()` (`Notifications`).
  - **Testkit:**
    - `FakeNotifications`, whose standalone form is `new FakeNotifications().group(id)`;
    - its methods `posts()`, `lines()`, `groups()`, `runAction(title, label)`, `click(title)`;
    - `FakeNotifications.Post` with `pluginId()`, `group()`, `spec()`, `expired()`, `line()`;
    - `FakePluginHost.notifications()`.
  - **Contract:**
    - `ContractHarness.notifications()`, `notificationGroups()`, `runNotificationAction(title, label)` and
      `clickNotification(title)`;
    - `ContractHarness.notices()` is removed.
  - **App `plugins` package:**
    - public records `PluginRuntime.CausedNotice(String pluginId, NotificationRequest request, Throwable cause,
      Consumer<Optional<UUID>> posted)` and `PluginRuntime.NotificationHost(NotificationCenter center,
      Consumer<CausedNotice> caused, BiConsumer<String, String> problem)`, plus `NotificationHost.detached()`;
    - the 8- and 9-argument `PluginRuntime` constructors take a `NotificationHost` where they took
      `Consumer<String> notice`;
    - `PluginHost.Environment.notifications()` replaces `notice()`;
    - package-private `HostedNotifications` with `static final String ACTIVITIES = ".activities"` and `void close()`;
    - `HostedContext.notifications` (field).
  - **Removed:** `JasperApplication.notice(String)` and `NotificationRequest.message(...)`.
  - **App tests:** `NotificationsFixture` (`center`, `host`, `problems`, `lines()`, `groups()`, `runAction`, `click`).

- [ ] **Step 1: Write the failing testkit and contract tests**

Create `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeNotificationsTest.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.notifications.DisplayMode;
import dev.jasper.sdk.notifications.GroupSpec;
import dev.jasper.sdk.notifications.NotificationAction;
import dev.jasper.sdk.notifications.NotificationSpec;
import dev.jasper.sdk.notifications.Severity;
import dev.jasper.sdk.plugin.PluginContext;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FakeNotificationsTest {
    @Test void aStandaloneGroupRecordsPostsForAComponentUnderTest() {
        var notifications = new FakeNotifications();
        var group = notifications.group("dev.example.tool");
        var ran = new ArrayList<String>();
        group.info("Saved", "Three hosts");
        var handle = group.post(NotificationSpec.of(Severity.ERROR, "Could not copy").withContent("busy")
            .withCause(new IllegalStateException("busy")).withAction(NotificationAction.of("Retry", () -> ran.add("retry"))));
        assertThat(notifications.lines()).containsExactly("dev.example.tool|INFO|Saved|Three hosts",
            "dev.example.tool|ERROR|Could not copy|busy|IllegalStateException");
        assertThat(notifications.groups()).containsExactly("dev.example.tool|dev.example.tool|CARD");
        assertThat(notifications.runAction("Could not copy", "Retry")).isTrue();
        assertThat(ran).containsExactly("retry");
        assertThat(handle.expired()).as("Retry expires it").isTrue();
        assertThat(notifications.runAction("Could not copy", "Retry")).isFalse();
        assertThat(notifications.runAction("Saved", "Nothing")).isFalse();
    }

    @Test void aStandaloneGroupLetsAThrowingActionReachTheTest() {
        var notifications = new FakeNotifications();
        notifications.group("dev.example.tool").post(NotificationSpec.of(Severity.INFO, "t")
            .withAction(NotificationAction.of("Break", () -> { throw new IllegalStateException("broken"); })));
        assertThatIllegalStateException().isThrownBy(() -> notifications.runAction("t", "Break"));
    }

    @Test void theHostDeclaresAPluginsGroupsAndContainsItsFailingActions() {
        try (var host = new FakePluginHost()) {
            var context = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("dev.example.tool", "Tool", "1.0.0", Set.of()), Set.of(), Set.of(), context::set);
            var sync = context.get().notifications().group(new GroupSpec("dev.example.tool.sync", "Sync", DisplayMode.HISTORY));
            sync.post(NotificationSpec.of(Severity.WARNING, "Slow")
                .withAction(new NotificationAction("Break", () -> { throw new IllegalStateException("broken"); }, false)));
            assertThat(host.notifications().groups()).containsExactly("dev.example.tool|Tool|CARD",
                "dev.example.tool.activities|Tool activities|CARD", "dev.example.tool.sync|Sync|HISTORY");
            assertThat(host.notifications().runAction("Slow", "Break")).isTrue();
            assertThat(host.failures()).singleElement().asString()
                .startsWith("dev.example.tool notification action Break: java.lang.IllegalStateException");
            host.stopAll();
            assertThat(host.notifications().posts()).allSatisfy(post -> assertThat(post.expired()).isTrue());
        }
    }
}
```

In `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java`:

1. Add the imports `dev.jasper.sdk.notifications.DisplayMode`, `dev.jasper.sdk.notifications.GroupSpec`,
   `dev.jasper.sdk.notifications.NotificationAction`, `dev.jasper.sdk.notifications.NotificationHandle`,
   `dev.jasper.sdk.notifications.NotificationSpec` and `dev.jasper.sdk.notifications.Severity`.

2. Replace the test `noticesAndTheEditorReachTheHostAndAPaneKnowsItsShell` with:

```java
        @Test void theEditorReachesTheHostAndAPaneKnowsItsShell() {
            UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "t");
            UUID pane = h.addTerminalPane(tab, "zsh", Path.of("/src"));
            var a = new AtomicReference<PluginContext>();
            h.start(info("test.a", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), a::set);
            h.ui(() -> {
                a.get().platform().openInEditor(Path.of("/tmp/x.toml"));
                assertThat(a.get().terminals().pane(pane).orElseThrow().info().shell()).isEqualTo("zsh");
            });
            h.flush();
            for (int i = 0; i < 100 && h.openedInEditor().isEmpty(); i++) { try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
            assertThat(h.openedInEditor()).containsExactly(Path.of("/tmp/x.toml"));
        }
```

3. After that test, add:

```java
    @Test void notificationsReachTheHostInTheirGroups() {
        PluginContext a = started("test.a", Set.of());
        UUID window = h.addTerminalWindow();
        h.ui(() -> {
            var plugin = a.notifications();
            var tunnels = plugin.group(new GroupSpec("test.a.tunnels", "Tunnels", DisplayMode.HISTORY));
            assertThat(plugin.group().id()).isEqualTo("test.a");
            assertThat(plugin.group(new GroupSpec("test.a.tunnels", "Again", DisplayMode.OFF))).isSameAs(tunnels);
            assertThat(plugin.group(new GroupSpec("test.a", "Mine", DisplayMode.OFF))).isSameAs(plugin.group());
            plugin.group().info("Saved", "Three hosts");
            plugin.group().warning("Slow", "");
            tunnels.post(NotificationSpec.of(Severity.ERROR, "Tunnel down").withContent("db:5432")
                .withAction(NotificationAction.of("Restart", () -> { })).withWindow(a.terminals().window(window).orElseThrow()));
        });
        h.flush();
        assertThat(h.notificationGroups()).containsExactly("test.a|test.a|CARD", "test.a.activities|test.a activities|CARD",
            "test.a.tunnels|Tunnels|HISTORY");
        assertThat(h.notifications()).containsExactly(
            "test.a|INFO|Saved|Three hosts|-|-|-|live",
            "test.a|WARNING|Slow||-|-|-|live",
            "test.a.tunnels|ERROR|Tunnel down|db:5432|Restart|-|" + window + "|live");
    }

    @Test void groupsStayInThePluginsNamespace() {
        PluginContext a = started("test.a", Set.of());
        h.ui(() -> {
            assertThatIllegalArgumentException().isThrownBy(() -> a.notifications().group(new GroupSpec("test.b.x", "X", DisplayMode.CARD)));
            assertThatIllegalArgumentException().isThrownBy(() -> a.notifications().group(new GroupSpec("test.ab.x", "X", DisplayMode.CARD)));
            assertThatIllegalArgumentException().as("the host's")
                .isThrownBy(() -> a.notifications().group(new GroupSpec("test.a.activities", "Mine", DisplayMode.CARD)));
        });
    }

    @Test void actionsAndTheBodyRunAndExpireWhenTheySay() {
        PluginContext a = started("test.a", Set.of());
        var ran = new java.util.concurrent.CopyOnWriteArrayList<String>();
        h.ui(() -> a.notifications().group().post(NotificationSpec.of(Severity.INFO, "Update ready")
            .withAction(NotificationAction.of("Install", () -> ran.add("install")))
            .withAction(new NotificationAction("Later", () -> ran.add("later"), false))
            .withAction(new NotificationAction("Break", () -> { throw new IllegalStateException("broken action"); }, false))
            .withActivate(() -> ran.add("body"))));
        h.flush();
        assertThat(h.runNotificationAction("Update ready", "Later")).isTrue();
        assertThat(h.runNotificationAction("Update ready", "Break")).as("contained, not thrown").isTrue();
        assertThat(h.clickNotification("Update ready")).isTrue();
        assertThat(h.notifications()).singleElement().asString().endsWith("|live");
        assertThat(h.runNotificationAction("Update ready", "Install")).isTrue();
        assertThat(h.notifications()).singleElement().asString().endsWith("|expired");
        assertThat(h.runNotificationAction("Update ready", "Later")).as("an expired notification's links stop working").isFalse();
        assertThat(ran).containsExactly("later", "body", "install");
    }

    @Test void aHandleExpiresItsNotificationAndStoppingExpiresTheRest() {
        PluginContext a = started("test.a", Set.of());
        var handle = new AtomicReference<NotificationHandle>();
        h.ui(() -> {
            handle.set(a.notifications().group().info("First", ""));
            a.notifications().group().info("Second", "");
        });
        h.flush();
        handle.get().expire();
        h.flush();
        assertThat(handle.get().expired()).isTrue();
        assertThat(h.notifications()).containsExactly("test.a|INFO|First||-|-|-|expired", "test.a|INFO|Second||-|-|-|live");
        h.stopAll();
        h.flush();
        assertThat(h.notifications()).allSatisfy(line -> assertThat(line).endsWith("|expired"));
        assertThatIllegalStateException().as("a closed context refuses posts").isThrownBy(() -> a.notifications().group().info("Late", ""));
    }

    @Test void aPostFromAnyThreadArrivesAndItsCauseIsMarked() throws Exception {
        PluginContext a = started("test.a", Set.of());
        Thread worker = Thread.ofPlatform().start(() ->
            a.notifications().group().error("Could not copy the address", new IllegalStateException("clipboard busy")));
        worker.join();
        h.flush();
        assertThat(h.notifications())
            .containsExactly("test.a|ERROR|Could not copy the address|IllegalStateException: clipboard busy|-|cause|-|live");
    }
```

In `ContractHarness.java`, replace

```java
        /** Every error notice as {@code <plugin name>: <message>}. */
        List<String> notices();
```

with

```java
        /**
         * Every notification plugins posted, oldest first, as {@code group|SEVERITY|title|content|labels|cause|window|state}:
         * the plugin's action labels joined with {@code ;}, or {@code -}; {@code cause} when it carried one (the application
         * then offers Show more…), or {@code -}; the target window's id, or {@code -}; and {@code live} or {@code expired}.
         */
        List<String> notifications();
        /** Every notification group declared for plugins, in declaration order, as {@code id|name|MODE}. */
        List<String> notificationGroups();
        /** UI thread: clicks the labelled link of the newest notification with this title; false when there is none or it expired. */
        boolean runNotificationAction(String title, String label);
        /** UI thread: clicks the body of the newest notification with this title; false when there is none or it expired. */
        boolean clickNotification(String title);
```

In `FakeContractTest.java`:

1. Replace `@Override public List<String> notices() { return host.notices(); }` with:

```java
            @Override public List<String> notifications() { return host.notifications().posts().stream().map(FakeContractTest::contractLine).toList(); }
            @Override public List<String> notificationGroups() { return host.notifications().groups(); }
            @Override public boolean runNotificationAction(String title, String label) { return host.notifications().runAction(title, label); }
            @Override public boolean clickNotification(String title) { return host.notifications().click(title); }
```

2. Add this method to the class:

```java
    /** The contract's line for a fake post. */
    static String contractLine(FakeNotifications.Post post) {
        var spec = post.spec();
        List<String> labels = spec.actions().stream().map(dev.jasper.sdk.notifications.NotificationAction::label).toList();
        return String.join("|", post.group(), spec.severity().name(), spec.title(), spec.content(),
            labels.isEmpty() ? "-" : String.join(";", labels), spec.cause().isPresent() ? "cause" : "-",
            spec.window().map(window -> window.id().toString()).orElse("-"), post.expired() ? "expired" : "live");
    }
```

- [ ] **Step 2: Write the failing application tests**

Create `jasper-app/src/test/java/dev/jasper/app/plugins/NotificationsFixture.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;

/**
 * A notification center for plugin hosts in tests, read back as lines. It holds what the application's center would:
 * a cause becomes a Show more… link (the application records the incident), and problems are kept as
 * {@code title: details}. Accessors hop to the UI thread.
 */
final class NotificationsFixture {
    static final String SHOW_MORE = "Show more…";
    final NotificationCenter center = new NotificationCenter(SwingUtilities::invokeLater, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED);
    final List<String> problems;
    final PluginRuntime.NotificationHost host;

    NotificationsFixture() { this(new CopyOnWriteArrayList<>()); }

    NotificationsFixture(List<String> problems) {
        this.problems = problems;
        host = new PluginRuntime.NotificationHost(center,
            caused -> SwingUtilities.invokeLater(() -> caused.posted().accept(
                center.post(caused.request().withAction(new NotificationAction(SHOW_MORE, () -> { }, false))))),
            (title, details) -> problems.add(title + ": " + details));
    }

    /** Every notification, oldest first, as the contract's {@code group|SEVERITY|title|content|labels|cause|window|state}. */
    List<String> lines() {
        return onEdtValue(() -> {
            List<Notification> oldestFirst = new ArrayList<>(center.history());
            Collections.reverse(oldestFirst);
            return oldestFirst.stream().map(NotificationsFixture::line).toList();
        });
    }

    /** Declared groups, in declaration order, as {@code id|name|MODE}. */
    List<String> groups() {
        return onEdtValue(() -> center.groups().stream().map(group -> group.id() + "|" + group.name() + "|" + group.defaultMode()).toList());
    }

    boolean runAction(String title, String label) {
        return onEdtValue(() -> newest(title).map(entry -> {
            List<NotificationAction> actions = entry.request().actions();
            for (int i = 0; i < actions.size(); i++) {
                if (!actions.get(i).label().equals(label)) continue;
                center.runAction(entry.id(), i);
                return true;
            }
            return false;
        }).orElse(false));
    }

    boolean click(String title) {
        return onEdtValue(() -> newest(title).map(entry -> { center.activate(entry.id()); return true; }).orElse(false));
    }

    /** UI thread: the newest entry with this title, unless it has expired. */
    private Optional<Notification> newest(String title) {
        return center.history().stream().filter(entry -> entry.request().title().equals(title)).findFirst().filter(entry -> !entry.expired());
    }

    private static String line(Notification entry) {
        var request = entry.request();
        List<String> labels = new ArrayList<>(request.actions().stream().map(NotificationAction::label).toList());
        boolean cause = !labels.isEmpty() && labels.getLast().equals(SHOW_MORE);
        if (cause) labels.removeLast();
        return String.join("|", request.group(), request.severity().name(), request.title(), request.content(),
            labels.isEmpty() ? "-" : String.join(";", labels), cause ? "cause" : "-",
            request.target().map(UUID::toString).orElse("-"), entry.expired() ? "expired" : "live");
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/plugins/HostedNotificationsTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.sdk.notifications.DisplayMode;
import dev.jasper.sdk.notifications.GroupSpec;
import dev.jasper.sdk.notifications.NotificationAction;
import dev.jasper.sdk.notifications.NotificationSpec;
import dev.jasper.sdk.notifications.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class HostedNotificationsTest {
    private final NotificationsFixture fixture = new NotificationsFixture();
    private final List<Runnable> queued = new ArrayList<>();

    /** EDT: a plugin's notifications whose work for the UI thread waits in {@code queued}, so a test can order it. */
    private HostedNotifications hosted() {
        return new HostedNotifications("dev.example.tool", "Tool", fixture.host, new Containment(() -> true), queued::add, () -> false);
    }

    private void runQueued() { while (!queued.isEmpty()) queued.removeFirst().run(); }

    private UUID idOf(String title) {
        return fixture.center.history().stream().filter(entry -> entry.request().title().equals(title)).findFirst().orElseThrow().id();
    }

    @Test void theDefaultAndActivitiesGroupsAreDeclaredWithThePluginAsOwner() {
        hosted();
        assertThat(fixture.center.groups()).extracting(group -> group.id() + "|" + group.name() + "|" + group.owner() + "|" + group.defaultMode())
            .containsExactly("dev.example.tool|Tool|dev.example.tool|CARD",
                "dev.example.tool.activities|Tool activities|dev.example.tool|CARD");
    }

    @Test void expiringBeforeThePostLandsExpiresItWhenItLands() {
        var hosted = hosted();
        var handle = hosted.group().info("Saved", "");
        handle.expire();
        assertThat(handle.expired()).isTrue();
        assertThat(fixture.center.history()).as("the post waits for the UI thread").isEmpty();
        runQueued();
        assertThat(fixture.center.history()).singleElement().satisfies(entry -> {
            assertThat(entry.expired()).isTrue();
            assertThat(entry.request().source()).isEqualTo("dev.example.tool");
        });
    }

    @Test void aPostThatLandsAfterThePluginStoppedExpiresAtOnce() {
        var hosted = hosted();
        var handle = hosted.group().info("Late", "");
        hosted.close();
        runQueued();
        assertThat(handle.expired()).isTrue();
        assertThat(fixture.center.history()).as("a stopped plugin's post is dropped").isEmpty();
        assertThatIllegalStateException().isThrownBy(() -> hosted.group().info("After", ""));
        assertThatIllegalStateException().isThrownBy(() -> hosted.group(new GroupSpec("dev.example.tool.x", "X", DisplayMode.CARD)));
    }

    @Test void stoppingExpiresWhatItPostedAndTheCenterTellsHandlesTheirFate() {
        var hosted = hosted();
        var updated = hosted.group().post(NotificationSpec.of(Severity.INFO, "Update ready").withAction(NotificationAction.of("Install", () -> { })));
        var removed = hosted.group().info("Removed", "");
        var stays = hosted.group().info("Stays", "");
        runQueued();
        fixture.center.runAction(idOf("Update ready"), 0);
        fixture.center.remove(idOf("Removed"));
        assertThat(updated.expired()).as("Install expires it").isTrue();
        assertThat(removed.expired()).as("removed from history").isTrue();
        assertThat(stays.expired()).isFalse();
        hosted.close();
        assertThat(stays.expired()).isTrue();
        assertThat(fixture.center.find(idOf("Stays")).orElseThrow().expired()).isTrue();
    }

    @Test void aPostToAMutedGroupReportsItselfExpired() {
        var hosted = hosted();
        var quiet = hosted.group(new GroupSpec("dev.example.tool.quiet", "Quiet", DisplayMode.OFF));
        runQueued();
        var handle = quiet.info("Unseen", "");
        runQueued();
        assertThat(handle.expired()).isTrue();
        assertThat(fixture.center.history()).isEmpty();
    }
}
```

In `JasperApplicationNotificationsTest.java`, add the imports `dev.jasper.app.notifications.NotificationRequest` and
`dev.jasper.app.notifications.Severity`, and replace the whole test `aPluginNoticeBecomesAnUnreadErrorAndShutdownStopsTheBadge` with:

```java
    @Test void anErrorBecomesAnUnreadErrorAndShutdownStopsTheBadge() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            assertThat(badge(application[0])).isEmpty();
            application[0].notificationCenter().submit(NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.ERROR, "Could not connect"));
        });
        edt(() -> assertThat(badge(application[0]))
            .contains(new PanelBadge(1, PanelBadge.Tone.ERROR, "1 unread notification, 1 error")));
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        edt(() -> application[0].notificationCenter().submit(NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.ERROR, "After shutdown")));
        edt(() -> assertThat(badge(application[0])).as("the feature stopped listening at shutdown")
            .contains(new PanelBadge(1, PanelBadge.Tone.ERROR, "1 unread notification, 1 error")));
    }
```

In `NotificationValuesTest.java`, delete the test `aMessageOfUnknownShapeBecomesATitleAndContent`.

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.plugins.*'`
Expected: compilation fails: `notifications()`, `FakeNotifications`, `HostedNotifications` and
`PluginRuntime.NotificationHost` do not exist.

- [ ] **Step 4: Add `PluginContext.notifications()` and the fake**

In `PluginContext.java`, add the import `dev.jasper.sdk.notifications.Notifications` and, after `fileSources()`, add:

```java
    /**
     * The plugin's notifications: its default group and the groups it declares. No capability is needed.
     *
     * @return the notifications service
     * @since 0.14.0
     */
    Notifications notifications();
```

Create `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeNotifications.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.notifications.DisplayMode;
import dev.jasper.sdk.notifications.GroupSpec;
import dev.jasper.sdk.notifications.NotificationAction;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.notifications.NotificationHandle;
import dev.jasper.sdk.notifications.NotificationSpec;
import dev.jasper.sdk.notifications.Notifications;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * What fake plugins posted and the groups declared for them, as the application's notification center holds them.
 * The host's is {@link FakePluginHost#notifications()}; a component under test that takes a {@link NotificationGroup}
 * gets a recording one from a new instance's {@link #group(String)}. Safe from any thread.
 *
 * @since 0.14.0
 */
public final class FakeNotifications {
    /** The host's group for a plugin's activity outcomes: {@code <plugin-id>.activities}. */
    static final String ACTIVITIES = ".activities";

    /** One post, and whether it has expired since. */
    public static final class Post {
        private final String pluginId;
        private final String group;
        private final NotificationSpec spec;
        private volatile boolean expired;

        Post(String pluginId, String group, NotificationSpec spec) { this.pluginId = pluginId; this.group = group; this.spec = spec; }

        /**
         * The plugin that posted it; a standalone group's own id.
         *
         * @return the plugin id
         */
        public String pluginId() { return pluginId; }

        /**
         * The group it was posted in.
         *
         * @return the group's id
         */
        public String group() { return group; }

        /**
         * What was posted.
         *
         * @return the spec
         */
        public NotificationSpec spec() { return spec; }

        /**
         * Whether it expired: through its handle, an expiring action, or its plugin stopping.
         *
         * @return true once expired
         */
        public boolean expired() { return expired; }

        /**
         * The post for assertions: {@code group|SEVERITY|title|content}, then {@code |} and the cause's simple class name
         * when it carried a cause.
         *
         * @return the line
         */
        public String line() {
            String line = group + "|" + spec.severity() + "|" + spec.title() + "|" + spec.content();
            return spec.cause().map(cause -> line + "|" + cause.getClass().getSimpleName()).orElse(line);
        }
    }

    private final FakePluginHost host;
    private final List<Post> posts = new CopyOnWriteArrayList<>();
    private final Map<String, String> groups = new LinkedHashMap<>();

    /** A recorder with no host, for components that take a {@link NotificationGroup}. */
    public FakeNotifications() { this(null); }

    FakeNotifications(FakePluginHost host) { this.host = host; }

    /**
     * A group whose posts are recorded here, as if a plugin with that id posted them. Its actions run on the caller's
     * thread when {@link #runAction} clicks them, and what they throw reaches the caller.
     *
     * @param id the group's id
     * @return the group
     */
    public NotificationGroup group(String id) {
        declare(Objects.requireNonNull(id, "id"), id, DisplayMode.CARD);
        return new Group(id, id, () -> true);
    }

    /**
     * Every post, oldest first.
     *
     * @return the posts
     */
    public List<Post> posts() { return List.copyOf(posts); }

    /**
     * Every post's {@link Post#line()}, oldest first.
     *
     * @return the lines
     */
    public List<String> lines() { return posts.stream().map(Post::line).toList(); }

    /**
     * Declared groups, in declaration order, as {@code id|name|MODE}.
     *
     * @return the groups
     */
    public List<String> groups() { synchronized (groups) { return List.copyOf(groups.values()); } }

    /**
     * Clicks the labelled link of the newest post with this title, as the user would. A plugin's action that throws is
     * recorded among {@link FakePluginHost#failures()} instead of escaping.
     *
     * @param title the post's title
     * @param label the link's label
     * @return false when the newest such post has expired, or there is no such post or link
     */
    public boolean runAction(String title, String label) {
        Optional<Post> post = newestLive(title);
        if (post.isEmpty()) return false;
        Optional<NotificationAction> action = post.get().spec.actions().stream().filter(candidate -> candidate.label().equals(label)).findFirst();
        if (action.isEmpty()) return false;
        contained(post.get(), "notification action " + label, action.get().run());
        if (action.get().expires()) post.get().expired = true;
        return true;
    }

    /**
     * Clicks the body of the newest post with this title, running its default action if it has one.
     *
     * @param title the post's title
     * @return false when the newest such post has expired, or there is none
     */
    public boolean click(String title) {
        Optional<Post> post = newestLive(title);
        if (post.isEmpty()) return false;
        post.get().spec.activate().ifPresent(body -> contained(post.get(), "notification body", body));
        return true;
    }

    /** A plugin's notifications; {@code open} turns false once its context closes. */
    Notifications of(String pluginId, BooleanSupplier open) { return new PluginView(pluginId, open); }

    /** A plugin starts: its default group and the host's activities group exist. */
    void declareDefaults(String pluginId, String name) {
        declare(pluginId, name, DisplayMode.CARD);
        declare(pluginId + ACTIVITIES, name + " activities", DisplayMode.CARD);
    }

    /** A plugin stopped: everything it posted expires. */
    void stopped(String pluginId) {
        for (Post post : posts) if (post.pluginId.equals(pluginId)) post.expired = true;
    }

    private void declare(String id, String name, DisplayMode mode) {
        synchronized (groups) { groups.putIfAbsent(id, id + "|" + name + "|" + mode); }
    }

    private Optional<Post> newestLive(String title) {
        List<Post> all = posts();
        for (int i = all.size() - 1; i >= 0; i--)
            if (all.get(i).spec.title().equals(title)) return all.get(i).expired ? Optional.empty() : Optional.of(all.get(i));
        return Optional.empty();
    }

    /** Runs a plugin's callback as the host would; a standalone recorder lets what it throws reach the caller. */
    private void contained(Post post, String what, Runnable callback) {
        if (host == null) { callback.run(); return; }
        try { callback.run(); }
        catch (RuntimeException | LinkageError failure) { host.recordFailure(post.pluginId + " " + what + ": " + failure); }
    }

    private final class PluginView implements Notifications {
        private final String pluginId;
        private final BooleanSupplier open;
        private final Group defaultGroup;
        private final Map<String, Group> declared = new ConcurrentHashMap<>();

        PluginView(String pluginId, BooleanSupplier open) {
            this.pluginId = pluginId; this.open = open;
            defaultGroup = new Group(pluginId, pluginId, open);
        }

        @Override public NotificationGroup group() { return defaultGroup; }

        @Override public NotificationGroup group(GroupSpec spec) {
            Objects.requireNonNull(spec, "spec");
            if (!open.getAsBoolean()) throw new IllegalStateException("Plugin context is closed: " + pluginId);
            if (spec.id().equals(pluginId)) return defaultGroup;
            if (!spec.id().startsWith(pluginId + "."))
                throw new IllegalArgumentException("A notification group of " + pluginId + " starts with its id and a dot: " + spec.id());
            if (spec.id().equals(pluginId + ACTIVITIES))
                throw new IllegalArgumentException("The host declares " + spec.id() + " for activity outcomes");
            return declared.computeIfAbsent(spec.id(), id -> {
                declare(id, spec.name(), spec.defaultMode());
                return new Group(pluginId, id, open);
            });
        }
    }

    private final class Group implements NotificationGroup {
        private final String pluginId;
        private final String id;
        private final BooleanSupplier open;

        Group(String pluginId, String id, BooleanSupplier open) { this.pluginId = pluginId; this.id = id; this.open = open; }

        @Override public String id() { return id; }

        @Override public NotificationHandle post(NotificationSpec spec) {
            Objects.requireNonNull(spec, "spec");
            if (!open.getAsBoolean()) throw new IllegalStateException("Plugin context is closed: " + pluginId);
            var post = new Post(pluginId, id, spec);
            posts.add(post);
            return new NotificationHandle() {
                @Override public void expire() { post.expired = true; }
                @Override public boolean expired() { return post.expired; }
            };
        }
    }
}
```

In `FakePluginContext.java`:

1. After `final FakePluginSettings settings;` add `final dev.jasper.sdk.notifications.Notifications notifications;`.
2. At the end of the constructor add `this.notifications = host.notifications.of(info.id(), () -> state != State.CLOSED);`.
3. After `fileSources()` add:

```java
    @Override public dev.jasper.sdk.notifications.Notifications notifications() { return notifications; }
```

In `FakePluginHost.java`:

1. After `final List<java.net.URI> browsed = new CopyOnWriteArrayList<>();` add:

```java
    final FakeNotifications notifications = new FakeNotifications(this);
```

2. In `start`, directly after the loop that skips a plugin whose requirement is not active (before the loop over
   `committed`), add:

```java
        notifications.declareDefaults(info.id(), info.name());
```

3. In `teardown`, after `context.fileSources.closeAll();`, add:

```java
        notifications.stopped(context.plugin().id());
```

4. Before `failures()`, add:

```java
    /**
     * What plugins posted and the groups declared for them.
     *
     * @return the host's notifications
     * @since 0.14.0
     */
    public FakeNotifications notifications() { return notifications; }
```

- [ ] **Step 5: Add the application's host side**

In `PluginRuntime.java`:

1. Add these imports, skipping any already present: `dev.jasper.app.notifications.Delivery`,
   `dev.jasper.app.notifications.NotificationCenter`, `dev.jasper.app.notifications.NotificationRequest`,
   `java.time.Clock`, `java.util.Optional`, `java.util.UUID` and `java.util.function.BiConsumer`.

2. After the `Failure` record, add:

```java

    /**
     * A plugin's notification that carries a cause, for the application to record the cause as the plugin's incident.
     *
     * @param pluginId the plugin
     * @param request the notification as the plugin asked for it
     * @param cause the failure behind it
     * @param posted hears the posted notification's id on the UI thread, or empty when its group dropped it
     */
    public record CausedNotice(String pluginId, NotificationRequest request, Throwable cause, Consumer<Optional<UUID>> posted) {
        public CausedNotice {
            Objects.requireNonNull(pluginId, "pluginId");
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(cause, "cause");
            Objects.requireNonNull(posted, "posted");
        }
    }

    /**
     * Where plugins' notifications go (spec section 6).
     *
     * @param center the application's notification center; the host reaches it on the UI thread
     * @param caused records a post's cause as the plugin's incident and posts it with Show more…; any thread
     * @param problem posts a problem with plugins, as a title and details, among the application's plugin problems;
     *     any thread
     */
    public record NotificationHost(NotificationCenter center, Consumer<CausedNotice> caused, BiConsumer<String, String> problem) {
        public NotificationHost {
            Objects.requireNonNull(center, "center");
            Objects.requireNonNull(caused, "caused");
            Objects.requireNonNull(problem, "problem");
        }

        /**
         * A host whose notifications nobody looks at, for a runtime without the application (previews and tests): a
         * cause is not recorded, and a problem is only logged.
         *
         * @return the host
         */
        public static NotificationHost detached() {
            var center = new NotificationCenter(SwingUtilities::invokeLater, Clock.systemDefaultZone(), () -> Delivery.Presence.NO_WINDOW);
            return new NotificationHost(center,
                caused -> SwingUtilities.invokeLater(() -> caused.posted().accept(center.post(caused.request()))),
                (title, details) -> LOG.log(System.Logger.Level.WARNING, "A plugin problem: " + title));
        }
    }
```

3. Replace the field `private final Consumer<String> notice;` with `private final NotificationHost notifications;`.

4. In the six-argument constructor, replace

```java
        this(options, notifier, reports, contributions, windows, terminals,
            message -> LOG.log(System.Logger.Level.WARNING, "Plugin notice: " + message), new dev.jasper.app.platform.ConfigEditor()::open);
```

   with

```java
        this(options, notifier, reports, contributions, windows, terminals, NotificationHost.detached(),
            new dev.jasper.app.platform.ConfigEditor()::open);
```

5. In the eight- and nine-argument constructors, replace the parameter `Consumer<String> notice` with
   `NotificationHost notifications` and its JavaDoc line `@param notice shows a plugin's error notice to the user, on
   the UI thread` with `@param notifications where plugins' notifications and plugin problems go`. In the
   eight-argument one, replace `this(options, notifier, reports, contributions, windows, terminals, notice, editor,
   ShellProfiles.NONE);` with `this(options, notifier, reports, contributions, windows, terminals, notifications,
   editor, ShellProfiles.NONE);`. In the nine-argument one, replace `this.notice = Objects.requireNonNull(notice);` with
   `this.notifications = Objects.requireNonNull(notifications);`. Change the eight-argument constructor's first JavaDoc
   sentence to "As the six-argument constructor, plus where plugin notifications and editor requests go."

6. In `start`, where the `PluginHost.Environment` is built, replace `windows, terminals, notice, editor, settingsFiles,`
   with `windows, terminals, notifications, editor, settingsFiles,`.

7. In `openSettingsFile`, replace

```java
                SwingUtilities.invokeLater(() -> notice.accept(failure.getMessage() == null ? failure.toString() : failure.getMessage()));
```

   with

```java
                String name = Optional.ofNullable(launched.get(id)).map(candidate -> candidate.descriptor().name()).orElse(id);
                String reason = failure.getMessage() == null ? failure.toString() : failure.getMessage();
                notifications.problem().accept("Could not open a plugin's settings file", name + ": " + reason);
```

   and change its JavaDoc to `/** Seeds and opens a plugin's settings file off the UI thread; a failure is a plugin problem. */`.

In `PluginHost.java`, in the `Environment` record header and in both secondary constructors, replace the parameter
`Consumer<String> notice` with `PluginRuntime.NotificationHost notifications`; in both `this(...)` calls replace
`terminals, notice,` with `terminals, notifications,`.

Create `jasper-app/src/main/java/dev/jasper/app/plugins/HostedNotifications.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.sdk.notifications.DisplayMode;
import dev.jasper.sdk.notifications.GroupSpec;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.notifications.NotificationHandle;
import dev.jasper.sdk.notifications.NotificationSpec;
import dev.jasper.sdk.notifications.Notifications;
import dev.jasper.sdk.notifications.Severity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * One plugin's notifications (spec section 6).
 * <ul>
 *   <li>Its default group, the host's activities group and the groups it declares, registered with the application's
 *     center.</li>
 *   <li>Posts from any thread.</li>
 *   <li>Actions and default actions that run inside the plugin's containment.</li>
 *   <li>A cause recorded as the plugin's incident.</li>
 *   <li>Expiry of everything it posted when it stops.</li>
 * </ul>
 * The center is reached on the UI thread; the handles' bookkeeping lives there too.
 */
final class HostedNotifications implements Notifications {
    /** The host's group for a plugin's activity outcomes: {@code <plugin-id>.activities}. */
    static final String ACTIVITIES = ".activities";

    private final String pluginId;
    private final PluginRuntime.NotificationHost host;
    private final Containment containment;
    private final Consumer<Runnable> ui;
    private final BooleanSupplier onUi;
    private final Group defaultGroup;
    private final Map<String, Group> declared = new ConcurrentHashMap<>();
    /** The handles of every live notification posted, by the center's id. UI thread. */
    private final Map<UUID, List<Handle>> posted = new HashMap<>();
    private final Subscription listening;
    private volatile boolean closed;

    /** UI thread: declares the plugin's default group and the host's activities group for it. */
    HostedNotifications(String pluginId, String pluginName, PluginRuntime.NotificationHost host, Containment containment,
                        Consumer<Runnable> ui, BooleanSupplier onUi) {
        this.pluginId = pluginId;
        this.host = host;
        this.containment = containment;
        this.ui = ui;
        this.onUi = onUi;
        defaultGroup = new Group(pluginId);
        host.center().register(new dev.jasper.app.notifications.NotificationGroup(pluginId, pluginName, pluginId,
            dev.jasper.app.notifications.DisplayMode.CARD));
        host.center().register(new dev.jasper.app.notifications.NotificationGroup(pluginId + ACTIVITIES, pluginName + " activities",
            pluginId, dev.jasper.app.notifications.DisplayMode.CARD));
        listening = host.center().onChange(this::changed);
    }

    @Override public NotificationGroup group() { return defaultGroup; }

    @Override public NotificationGroup group(GroupSpec spec) {
        Objects.requireNonNull(spec, "spec");
        requireOpen();
        if (spec.id().equals(pluginId)) return defaultGroup;
        if (!spec.id().startsWith(pluginId + "."))
            throw new IllegalArgumentException("A notification group of " + pluginId + " starts with its id and a dot: " + spec.id());
        if (spec.id().equals(pluginId + ACTIVITIES))
            throw new IllegalArgumentException("The host declares " + spec.id() + " for activity outcomes");
        boolean[] made = new boolean[1];
        Group group = declared.computeIfAbsent(spec.id(), id -> { made[0] = true; return new Group(id); });
        if (made[0]) onUiThread(() -> host.center().register(new dev.jasper.app.notifications.NotificationGroup(spec.id(),
            spec.name(), pluginId, mode(spec.defaultMode()))));
        return group;
    }

    /** UI thread: the plugin stopped. Everything it posted expires, and later posts are refused. */
    void close() {
        if (closed) return;
        closed = true;
        listening.close();
        for (UUID id : List.copyOf(posted.keySet())) host.center().expire(id);
        posted.values().forEach(handles -> handles.forEach(Handle::markExpired));
        posted.clear();
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Plugin context is closed: " + pluginId);
    }

    private void onUiThread(Runnable task) {
        if (onUi.getAsBoolean()) task.run();
        else ui.accept(task);
    }

    /** The application's request for a plugin's spec; actions and the body run inside the plugin's containment. */
    private NotificationRequest request(String group, NotificationSpec spec) {
        var request = NotificationRequest.of(group, severity(spec.severity()), spec.title()).withContent(spec.content()).withSource(pluginId);
        if (spec.icon().isPresent()) request = request.withIcon(spec.icon().get());
        for (var action : spec.actions())
            request = request.withAction(new dev.jasper.app.notifications.NotificationAction(action.label(),
                () -> containment.run(pluginId, "notification action " + action.label(), action.run()), action.expires()));
        if (spec.activate().isPresent()) {
            Runnable body = spec.activate().get();
            request = request.withActivate(() -> containment.run(pluginId, "notification body", body));
        }
        if (spec.window().isPresent()) request = request.withTarget(spec.window().get().id());
        return request;
    }

    /** UI thread: the center says what became of the plugin's notifications. */
    private void changed(NotificationCenter.Change change) {
        switch (change) {
            case NotificationCenter.Change.Upserted upserted when upserted.notification().expired() -> expired(upserted.notification().id());
            case NotificationCenter.Change.Removed removed -> expired(removed.id());
            case NotificationCenter.Change.Cleared cleared -> {
                posted.values().forEach(handles -> handles.forEach(Handle::markExpired));
                posted.clear();
            }
            default -> { }
        }
    }

    private void expired(UUID id) {
        List<Handle> handles = posted.remove(id);
        if (handles != null) handles.forEach(Handle::markExpired);
    }

    private static dev.jasper.app.notifications.Severity severity(Severity value) {
        return switch (value) {
            case INFO -> dev.jasper.app.notifications.Severity.INFO;
            case WARNING -> dev.jasper.app.notifications.Severity.WARNING;
            case ERROR -> dev.jasper.app.notifications.Severity.ERROR;
        };
    }

    private static dev.jasper.app.notifications.DisplayMode mode(DisplayMode value) {
        return switch (value) {
            case CARD -> dev.jasper.app.notifications.DisplayMode.CARD;
            case STICKY -> dev.jasper.app.notifications.DisplayMode.STICKY;
            case HISTORY -> dev.jasper.app.notifications.DisplayMode.HISTORY;
            case OFF -> dev.jasper.app.notifications.DisplayMode.OFF;
        };
    }

    private final class Group implements NotificationGroup {
        private final String id;

        Group(String id) { this.id = id; }

        @Override public String id() { return id; }

        @Override public NotificationHandle post(NotificationSpec spec) {
            Objects.requireNonNull(spec, "spec");
            requireOpen();
            NotificationRequest request = request(id, spec);
            var handle = new Handle();
            if (spec.cause().isPresent())
                host.caused().accept(new PluginRuntime.CausedNotice(pluginId, request, spec.cause().get(), handle::posted));
            else onUiThread(() -> handle.posted(closed ? Optional.empty() : host.center().post(request)));
            return handle;
        }
    }

    /** One post's handle: it learns the center's id on the UI thread, and may be expired before that. */
    private final class Handle implements NotificationHandle {
        private volatile UUID id;
        private volatile boolean expired;

        @Override public void expire() {
            if (expired) return;
            expired = true;
            UUID known = id;
            if (known != null) onUiThread(() -> host.center().expire(known));
        }

        @Override public boolean expired() { return expired; }

        /** UI thread: where the post landed; empty when its group dropped it or the plugin had stopped. */
        void posted(Optional<UUID> landed) {
            if (landed.isEmpty()) { expired = true; return; }
            UUID known = landed.get();
            id = known;
            if (closed || expired) {
                expired = true;
                host.center().expire(known);
                return;
            }
            posted.computeIfAbsent(known, key -> new ArrayList<>()).add(this);
        }

        void markExpired() { expired = true; }
    }
}
```

In `HostedContext.java`:

1. Add the imports `dev.jasper.sdk.notifications.NotificationSpec`, `dev.jasper.sdk.notifications.Notifications` and
   `dev.jasper.sdk.notifications.Severity`.
2. After `final HostedFileSources fileSources;` add `final HostedNotifications notifications;`.
3. At the end of the constructor add:

```java
        this.notifications = new HostedNotifications(id, hosted.info().name(), host.environment.notifications(), host.containment,
            host.environment.ui(), host.environment.onUi());
```

4. In `teardown`, after `terminals.closeAll();`, add `notifications.close();`.
5. Replace the whole `notices()` method with:

```java
        @Override public Notifications notifications() { return notifications; }

        @Override public Notices notices() {
            return message -> {
                requireOpen();
                Objects.requireNonNull(message, "message");
                // Notices is replaced by notifications(): its error becomes one titled with the plugin's name.
                notifications.group().post(NotificationSpec.of(Severity.ERROR, hosted.info().name())
                    .withContent(message.length() <= 4_000 ? message : message.substring(0, 3_999) + "…"));
            };
        }
```

6. In `platform()`, replace `notices().error("Could not open " + file.getFileName() + " in an editor.");` with:

```java
                            notifications.group().error("Could not open a file in an editor", String.valueOf(file.getFileName()), failure);
```

   and `notices().error("Could not open " + address + " in a browser.");` with:

```java
                            notifications.group().error("Could not open a web address", address.toString(), failure);
```

In `jasper-app/src/main/java/dev/jasper/app/plugins/package-info.java`, add `dev.jasper.sdk.notifications` to the
"Allowed outgoing Jasper dependencies" list, after `dev.jasper.sdk.files`.

In `JasperApplication.java`:

1. In `startPlugins`, in the `new PluginRuntime(...)` call, replace `this::notice, new dev.jasper.app.platform.ConfigEditor()::open,` with:

```java
            new PluginRuntime.NotificationHost(notificationCenter,
                caused -> incidentNotices.pluginNotice(caused.pluginId(), caused.request(), caused.cause(), caused.posted()),
                (title, details) -> notificationCenter.submit(NotificationRequest.of(IncidentNotices.PLUGINS.id(), Severity.ERROR, title)
                    .withContent(details))),
            new dev.jasper.app.platform.ConfigEditor()::open,
```

2. Delete the method `notice(String message)` and its JavaDoc ("A plugin's error notice, until plugins post
   notifications themselves...").

In `NotificationRequest.java`, delete the constant `SHORT_TITLE` with its JavaDoc, and the whole method `message(...)`
with its JavaDoc.

- [ ] **Step 6: Point the application's plugin tests at the center**

In `AppContractTest.java`:

1. In the four public `host(...)` overloads that take `List<String> notices`, rename that parameter to
   `List<String> problems` and each `notices,` argument they pass on to `problems,`.
2. In the overload that ends with `List<java.net.URI> browsed)`, replace its body with:

```java
        return host(data, drainGrace, contributions, dark, auxiliary, terminals, new NotificationsFixture(problems), edited, diagnostics, profiles, browsed, null);
```

3. In the private `host(...)` builder, replace the parameter `List<String> notices` with
   `NotificationsFixture notifications` and the argument `notices::add` with `notifications.host`.
4. In `newHarness`, replace `List<String> notices = new CopyOnWriteArrayList<>();` with
   `var notifications = new NotificationsFixture();`. In its `host(...)` call, replace the argument `notices` with
   `notifications`.
5. In the harness, replace `@Override public List<String> notices() { return List.copyOf(notices); }` with:

```java
                        @Override public List<String> notifications() { return notifications.lines(); }
                        @Override public List<String> notificationGroups() { return notifications.groups(); }
                        @Override public boolean runNotificationAction(String title, String label) { return notifications.runAction(title, label); }
                        @Override public boolean clickNotification(String title) { return notifications.click(title); }
```

In `HostedPaletteTest.java`:

1. Replace the field `final List<String> notices = new CopyOnWriteArrayList<>();` with
   `final NotificationsFixture notifications = new NotificationsFixture();`.
2. In `host()`, replace the argument `notices::add` with `notifications.host`.
3. In the test that posts "Broken", replace `other.get().notices().error("Broken");` with
   `other.get().notifications().group().error("Broken", "");`, and replace
   `assertThat(notices).containsExactly("B: Broken");` with
   `assertThat(notifications.lines()).containsExactly("test.b|ERROR|Broken||-|-|-|live");`.

In `HostedFileSourcesTest.java`, replace the argument `notice -> { }` with `PluginRuntime.NotificationHost.detached()`.

- [ ] **Step 7: Run the tests to see them pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.notifications.*'`
Expected: PASS. Both `FakeContractTest` and `AppContractTest` run the five new contract tests, and the bundled plugins'
tests that still call `notices()` are untouched (Tasks 7–11 migrate them).

Then run: `./gradlew verifySdkArchitecture verifyApplicationArchitecture verifyPluginArchitecture :jasper-sdk:javadoc :jasper-sdk-testkit:javadoc :jasper-app:javadoc`
Expected: all succeed.

- [ ] **Step 8: Commit**

```bash
git add jasper-sdk jasper-sdk-testkit jasper-app/src/main/java/dev/jasper/app/plugins jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java jasper-app/src/main/java/dev/jasper/app/notifications/NotificationRequest.java jasper-app/src/test/java/dev/jasper/app/plugins jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationNotificationsTest.java jasper-app/src/test/java/dev/jasper/app/notifications/NotificationValuesTest.java
git commit -m "feat(sdk): give plugins notifications in the application and the testkit" -m "PluginContext.notifications() posts into the application's center from any thread, runs links inside the plugin's containment, records a cause as the plugin's incident and expires a plugin's notifications when it stops; the fake records the same and one contract suite checks both." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: Activity outcomes

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeNotifications.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/activity/Activities.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/activity/ActivityHandle.java`
- Test: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java`

**Interfaces:**
- Consumes: Task 4's `HostedNotifications.ACTIVITIES`, `PluginHost.Environment.notifications()`, `FakeNotifications`
  and `FakePluginHost.notifications`.
- Produces: package-private `Optional<Runnable> PluginHost.activation(String source, Optional<String> actionId)`, which
  moves here from `PluginRuntime`; package-private `FakeNotifications.outcome(ActivityEvent, Optional<Runnable>)`.

- [ ] **Step 1: Write the failing contract test**

Add to `PluginContractTest`, after `aPostFromAnyThreadArrivesAndItsCauseIsMarked`:

```java
    @Test void activityOutcomesArePostedInTheHostsActivitiesGroup() {
        var shown = new AtomicInteger();
        var context = new AtomicReference<PluginContext>();
        h.start(info("test.a"), Set.of(), Set.of(), started -> {
            context.set(started);
            started.actions().register(ActionSpec.of("test.a.show", "Show"), invoked -> shown.incrementAndGet());
        });
        PluginContext a = context.get();
        UUID window = h.addTerminalWindow();
        h.activateTerminalWindow(window);
        h.ui(() -> {
            a.activities().begin(ActivitySpec.of("Sync hosts")).succeed("3 hosts");
            a.activities().begin(ActivitySpec.of("Upload")).fail("Connection lost");
            a.activities().begin(ActivitySpec.of("Index")).cancelled();
            a.activities().begin(new ActivitySpec("Deploy", "", Optional.of("test.a.show"), Optional.empty())).succeed("");
        });
        h.flush();
        assertThat(h.clickNotification("Deploy")).as("the body runs the activity's action").isTrue();
        h.flush();
        assertThat(shown).hasValue(1);
        h.start(info("test.b"), Set.of(), Set.of(), context -> {
            context.activities().begin(ActivitySpec.of("Left open"));
            throw new IllegalStateException("broken");
        });
        h.flush();
        assertThat(h.activityLog()).as("the host still fails the activity it left open")
            .anySatisfy(event -> assertThat(event.title()).isEqualTo("Left open"));
        assertThat(h.notifications()).as("but a plugin that is not running posts no outcome").containsExactly(
            "test.a.activities|INFO|Sync hosts|3 hosts|-|-|-|live",
            "test.a.activities|ERROR|Upload|Connection lost|-|-|-|live",
            "test.a.activities|INFO|Deploy||-|-|-|live");
        h.ui(() -> a.activities().begin(ActivitySpec.of("Still running")));
        h.stopAll();
        h.flush();
        assertThat(h.notifications()).as("nor does one that stopped").hasSize(3);
    }
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakeContractTest' :jasper-app:test --tests 'dev.jasper.app.plugins.AppContractTest'`
Expected: FAIL in both. `activityOutcomesArePostedInTheHostsActivitiesGroup` finds no notifications.

- [ ] **Step 3: Post outcomes in the application's host**

In `PluginHost.java`:

1. Add the imports `dev.jasper.app.notifications.NotificationRequest`, `dev.jasper.app.notifications.Severity`,
   `dev.jasper.app.terminals.PaneEntry`, `dev.jasper.sdk.activity.Activities`, `dev.jasper.sdk.activity.ActivityEvent`
   and `java.util.Optional`.
2. After `private final dev.jasper.app.lifecycle.Subscription terminalBridge;` add
   `private final dev.jasper.sdk.Subscription outcomes;`.
3. In the constructor, after `this.terminalBridge = TerminalBridge.connect(environment.terminals(), bus);` add:

```java
        this.outcomes = bus.subscribe(EventBus.APP, Activities.TOPIC, this::outcome);
```

4. In `stop()`, after `terminalBridge.close();` add `outcomes.close();`.
5. Add these methods:

```java
    /**
     * An activity ended (spec section 7, producer 4). One that succeeded or failed is posted in its plugin's
     * {@code <plugin-id>.activities} group, titled after the activity, with its detail as content and its action as the
     * body's; a cancelled one posts nothing. Nor does an activity of a plugin that is no longer running: the host failed
     * it because the plugin stopped or failed to start, and that plugin's incident already says why. UI thread.
     */
    private void outcome(ActivityEvent event) {
        if (event.state() != ActivityEvent.State.SUCCEEDED && event.state() != ActivityEvent.State.FAILED) return;
        String source = event.sourcePluginId();
        if (!active(source)) return;
        try {
            var request = NotificationRequest.of(source + HostedNotifications.ACTIVITIES,
                    event.state() == ActivityEvent.State.SUCCEEDED ? Severity.INFO : Severity.ERROR, event.title())
                .withContent(event.detail()).withSource(source);
            Optional<Runnable> activate = activation(source, event.activateActionId());
            environment.notifications().center().post(activate.map(request::withActivate).orElse(request));
        } catch (IllegalArgumentException unshowable) {
            Containment.logger(source).log(System.Logger.Level.WARNING, "An activity's outcome could not be shown", unshowable);
        }
    }

    /**
     * An activity's own activation: the source plugin's action, invoked in the window the user used last. Empty when
     * the id names no action that plugin registered. UI thread.
     */
    Optional<Runnable> activation(String source, Optional<String> actionId) {
        return actionId.filter(id -> ownsAction(source, id)).<Runnable>map(id -> () -> {
            if (!ownsAction(source, id)) return;
            environment.terminals().activeWindow().ifPresent(window -> environment.contributions().action(id).ifPresent(entry ->
                entry.invoke(new Contributions.Invocation(window.id(), environment.terminals().activePane().map(PaneEntry::id)))));
        });
    }
```

In `PluginRuntime.java`, replace the body of the private `activation` method

```java
        PluginHost current = host;
        if (current == null) return Optional.empty();
        return actionId.filter(id -> current.ownsAction(source, id)).<Runnable>map(id -> () -> {
            if (!current.ownsAction(source, id)) return;
            terminals.activeWindow().ifPresent(window -> contributions.action(id).ifPresent(entry ->
                entry.invoke(new Contributions.Invocation(window.id(), terminals.activePane().map(PaneEntry::id)))));
        });
```

with

```java
        PluginHost current = host;
        return current == null ? Optional.empty() : current.activation(source, actionId);
```

and remove the `dev.jasper.app.terminals.PaneEntry` import if nothing else in the file uses it.

- [ ] **Step 4: Post outcomes in the fake host**

In `FakeNotifications.java`, add the imports `dev.jasper.sdk.activity.ActivityEvent` and
`dev.jasper.sdk.notifications.Severity`, and after `stopped` add:

```java
    /** As the host does: an activity that succeeded or failed is posted in its plugin's activities group. */
    void outcome(ActivityEvent event, Optional<Runnable> activate) {
        if (event.state() != ActivityEvent.State.SUCCEEDED && event.state() != ActivityEvent.State.FAILED) return;
        String detail = event.detail();
        var spec = NotificationSpec.of(event.state() == ActivityEvent.State.SUCCEEDED ? Severity.INFO : Severity.ERROR, event.title())
            .withContent(detail.length() <= 4_000 ? detail : detail.substring(0, 3_999) + "…");
        posts.add(new Post(event.sourcePluginId(), event.sourcePluginId() + ACTIVITIES, activate.map(spec::withActivate).orElse(spec)));
    }
```

In `FakePluginHost.java`, in `deliver`, replace

```java
        if (payload instanceof ActivityEvent event && topicId.equals(Activities.TOPIC.id())) activityLog.add(event);
```

with

```java
        if (payload instanceof ActivityEvent event && topicId.equals(Activities.TOPIC.id())) {
            activityLog.add(event);
            outcome(event);
        }
```

and add after `deliver`:

```java
    /**
     * As the host does: an activity that succeeded or failed is posted in its plugin's activities group, unless the
     * plugin is no longer running.
     */
    private void outcome(ActivityEvent event) {
        FakePluginContext owner = contexts.get(event.sourcePluginId());
        if (owner == null || !active(event.sourcePluginId())) return;
        // Like the host: only the source plugin's own action, run in the window used last.
        Optional<Runnable> activate = event.activateActionId().filter(owner.ui.actions::containsKey)
            .<Runnable>map(id -> () -> {
                var window = workspace.lastActive;
                if (window != null) invoke(id, window.id, workspace.activePane().map(pane -> pane.id).orElse(null));
            });
        try { notifications.outcome(event, activate); }
        catch (IllegalArgumentException unshowable) { failures.add(event.sourcePluginId() + " activity outcome: " + unshowable); }
    }
```

(`FakePluginHost` already imports `Optional`.)

- [ ] **Step 5: Document it in the SDK**

In `Activities.java`, add this paragraph to the interface JavaDoc, before its closing `*/`:

```java
 *
 * <p>When an activity succeeds or fails, the host posts its outcome as a notification in the plugin's
 * {@code <plugin-id>.activities} group: INFO or ERROR, titled after the activity, with the final detail as content and
 * the activity's action as a click on its body. A cancelled activity posts nothing, and neither does one the host failed
 * because the plugin stopped or failed to start. The user can mute that group without muting the plugin's own
 * notifications.
```

In `ActivityHandle.java`, add the sentence `The host posts the outcome as a notification in the plugin's activities
group; see {@link Activities}.` at the end of the main description of both `succeed` and `fail`.

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.plugins.*' :jasper-sdk:javadoc`
Expected: PASS, including `PluginRuntimeTest`'s activity tests (they still reach Buddy through `PluginRuntime.forward`).

- [ ] **Step 7: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java jasper-sdk-testkit jasper-sdk/src/main/java/dev/jasper/sdk/activity
git commit -m "feat(notifications): post plugin activity outcomes in the plugin's activities group" -m "The plugin host posts a succeeded activity as INFO and a failed one as ERROR in <plugin-id>.activities, with the activity's action on the body; the fake host does the same, and the contract suite checks both." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: Plugin problems at startup

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/application/PluginProblemNotices.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginRuntimeTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/PluginProblemNoticesTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationIncidentsTest.java`

**Interfaces:**
- Consumes: Task 3's `IncidentNotices.pluginNotice`; `IncidentNotices.PLUGINS`; `PluginStatus` (`id()`, `name()`,
  `state()`, `reason()`, `superseded()`); `PluginLoader.LoadFailure`.
- Produces:
  - `public record PluginRuntime.Problem(Kind kind, Optional<String> pluginId, Optional<String> name, String detail,
    Optional<Throwable> cause)` with `enum Kind { NOT_LOADED, NOT_STARTED, UNREADABLE }`;
  - `public List<PluginRuntime.Problem> PluginRuntime.problems()`;
  - `final class PluginProblemNotices` (application) with `OPEN_PLUGINS = "Open Plugins"`,
    `PluginProblemNotices(NotificationCenter, IncidentNotices, Runnable openPlugins)`,
    `void announce(List<PluginRuntime.Problem>)` and `static String title(PluginRuntime.Problem)`.

- [ ] **Step 1: Write the failing tests**

Add to `PluginRuntimeTest` (add `import static org.assertj.core.api.Assertions.tuple;`):

```java
    @Test void whatKeptPluginsFromRunningIsListedAsProblems() throws Exception {
        Path bundled = root.resolve("bundled");
        probe(bundled, "dev.example.probe");
        PluginJars.build(bundled.resolve("dev.example.future"), "future.jar",
            "id = \"dev.example.future\"\nname = \"Future\"\nversion = \"1.0.0\"\nentry = \"fix.future.Main\"\nsdk = \">=9.0.0\"\n",
            Map.of("fix.future.Main", PluginJars.emptyPlugin("fix.future", "Main")), List.of());
        PluginJars.build(bundled.resolve("dev.example.reserved"), "reserved.jar",
            PluginJars.descriptor("dev.example.reserved", "1.0.0", "dev.jasper.app.evil.Main"),
            Map.of("dev.jasper.app.evil.Main", PluginJars.emptyPlugin("dev.jasper.app.evil", "Main")), List.of());
        PluginJars.build(bundled.resolve("dev.example.nameless"), "nameless.jar", null,
            Map.of("fix.nameless.Main", PluginJars.emptyPlugin("fix.nameless", "Main")), List.of());
        PluginRuntime runtime = runtime(bundled, root.resolve("absent"), null, false, new ArrayList<>());
        onEdt(() -> runtime.start(Map.of(), true));
        assertThat(runtime.problems()).extracting(PluginRuntime.Problem::kind, problem -> problem.name().orElse("-"))
            .containsExactlyInAnyOrder(
                tuple(PluginRuntime.Problem.Kind.UNREADABLE, "-"),
                tuple(PluginRuntime.Problem.Kind.NOT_STARTED, "Future"),
                tuple(PluginRuntime.Problem.Kind.NOT_LOADED, "dev.example.reserved"));
        assertThat(runtime.problems()).filteredOn(problem -> problem.kind() == PluginRuntime.Problem.Kind.NOT_STARTED)
            .singleElement().satisfies(problem -> assertThat(problem.detail()).contains("needs SDK"));
        assertThat(runtime.problems()).filteredOn(problem -> problem.kind() == PluginRuntime.Problem.Kind.NOT_LOADED)
            .singleElement().satisfies(problem -> {
                assertThat(problem.pluginId()).contains("dev.example.reserved");
                assertThat(problem.detail()).contains("reserved package dev.jasper.app.evil");
                assertThat(problem.cause()).as("no exception lies behind it").isEmpty();
            });
        assertThat(runtime.statusLines()).anySatisfy(line -> assertThat(line).contains("dev.example.probe", "ACTIVE"));
        onEdt(runtime::stop);
    }
```

Create `jasper-app/src/test/java/dev/jasper/app/application/PluginProblemNoticesTest.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class PluginProblemNoticesTest {
    private final NotificationCenter center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED);
    private final Incidents incidents = new Incidents(Clock.systemUTC());
    private final List<UUID> opened = new ArrayList<>();
    private int pluginPages;

    private PluginProblemNotices notices() {
        center.register(NotificationsFeature.GENERAL);
        var incidentNotices = new IncidentNotices(center, incidents, Runnable::run, (delay, task) -> () -> { },
            id -> id, (id, done) -> { }, opened::add);
        return new PluginProblemNotices(center, incidentNotices, () -> pluginPages++);
    }

    private static List<String> labels(Notification notification) {
        return notification.request().actions().stream().map(NotificationAction::label).toList();
    }

    private static String line(Notification entry) {
        var request = entry.request();
        return request.group() + "|" + request.severity() + "|" + request.title() + "|" + request.content() + "|" + String.join(";", labels(entry));
    }

    @Test void eachProblemIsAPluginProblemWithOpenPlugins() {
        notices().announce(List.of(
            new PluginRuntime.Problem(PluginRuntime.Problem.Kind.NOT_STARTED, Optional.of("dev.example.future"), Optional.of("Future"),
                "needs SDK >=9.0.0; this is SDK 0.14.0", Optional.empty()),
            new PluginRuntime.Problem(PluginRuntime.Problem.Kind.UNREADABLE, Optional.empty(), Optional.empty(),
                "/plugins/stray: no plugin.toml in any jar", Optional.empty())));
        assertThat(center.history()).extracting(PluginProblemNoticesTest::line).containsExactly(
            "jasper.plugins|ERROR|A plugin could not be read|/plugins/stray: no plugin.toml in any jar|Open Plugins",
            "jasper.plugins|ERROR|Future was not started|needs SDK >=9.0.0; this is SDK 0.14.0|Open Plugins");
        center.runAction(center.history().getFirst().id(), 0);
        assertThat(pluginPages).isEqualTo(1);
        assertThat(center.history().getFirst().expired()).as("Open Plugins keeps working").isFalse();
        assertThat(incidents.list()).isEmpty();
    }

    @Test void aLoadFailureWithAnExceptionAlsoOffersShowMore() {
        var cause = new IllegalStateException("Cannot read reserved.jar", new java.io.IOException("bad zip"));
        notices().announce(List.of(new PluginRuntime.Problem(PluginRuntime.Problem.Kind.NOT_LOADED, Optional.of("dev.example.broken"),
            Optional.of("Broken"), "Cannot read reserved.jar: bad zip", Optional.of(cause))));
        Notification notice = center.history().getFirst();
        assertThat(notice.request().title()).isEqualTo("Broken could not be loaded");
        assertThat(labels(notice)).containsExactly("Open Plugins", "Show more…");
        assertThat(incidents.list()).singleElement().satisfies(incident -> assertThat(incident.source()).isEqualTo("dev.example.broken"));
    }
}
```

Add to `JasperApplicationIncidentsTest`:

```java
    @DisabledOnOs(OS.WINDOWS)
    @Test void aPluginThatWasNotStartedIsAPluginProblemWithOpenPlugins() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("dev-plugin");
        PluginJars.build(dev, "future.jar",
            "id = \"dev.example.future\"\nname = \"Future\"\nversion = \"1.0.0\"\nentry = \"fix.future.Main\"\nsdk = \">=9.0.0\"\n",
            Map.of("fix.future.Main", PluginJars.emptyPlugin("fix.future", "Main")), List.of());
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = application(terminated);
            application[0].startPlugins(null, dev, false, dirs);
            var notice = application[0].notificationCenter().history().stream()
                .filter(entry -> entry.request().group().equals("jasper.plugins")).findFirst().orElseThrow();
            assertThat(notice.request().title()).isEqualTo("Future was not started");
            assertThat(notice.request().actions()).extracting(NotificationAction::label).containsExactly("Open Plugins");
        });
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.PluginRuntimeTest' --tests 'dev.jasper.app.application.PluginProblemNoticesTest' --tests 'dev.jasper.app.application.JasperApplicationIncidentsTest'`
Expected: compilation fails, because `PluginRuntime.Problem` and `PluginProblemNotices` do not exist.

- [ ] **Step 3: List the problems in the runtime**

In `PluginRuntime.java`:

1. After the `NotificationHost` record, add:

```java

    /**
     * Something that kept a plugin from running at this launch, for the application to announce (spec section 7,
     * producer 5). A plugin that failed while starting is not one: its incident already says so.
     *
     * @param kind what went wrong
     * @param pluginId the plugin's id; empty for a directory that holds no readable plugin
     * @param name the plugin's name; empty with the id
     * @param detail why, in the user's terms
     * @param cause the exception behind it, when there is one
     */
    public record Problem(Kind kind, Optional<String> pluginId, Optional<String> name, String detail, Optional<Throwable> cause) {
        /** What went wrong. */
        public enum Kind {
            /** The plugin's jars could not be loaded. */
            NOT_LOADED,
            /** The plugin was skipped: it needs another SDK, or a dependency is missing, failed or forms a cycle. */
            NOT_STARTED,
            /** A directory in a plugins folder holds no plugin Jasper can read. */
            UNREADABLE
        }

        public Problem {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(pluginId, "pluginId");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(detail, "detail");
            Objects.requireNonNull(cause, "cause");
        }
    }
```

2. After `private final List<PluginStatus> statuses = new ArrayList<>();` add
   `private final List<Problem> startupProblems = new ArrayList<>();`.

3. After `pluginNames()` add:

```java
    /**
     * What kept plugins from running at this launch, in the order it was found; empty before {@link #start}. UI thread.
     *
     * @return the problems
     */
    public List<Problem> problems() { return List.copyOf(startupProblems); }
```

4. In `start`, directly before `var resolution = PluginResolver.resolve(...)`, add:

```java
        for (String problem : problems)
            startupProblems.add(new Problem(Problem.Kind.UNREADABLE, Optional.empty(), Optional.empty(), problem, Optional.empty()));
```

   directly after `statuses.addAll(resolution.rejected());` add:

```java
        for (PluginStatus rejected : resolution.rejected())
            if (rejected.state() == PluginStatus.State.SKIPPED && !rejected.superseded())
                startupProblems.add(new Problem(Problem.Kind.NOT_STARTED, Optional.of(rejected.id()), Optional.of(rejected.name()),
                    rejected.reason(), Optional.empty()));
```

   in the `catch (PluginLoader.LoadFailure failure)` block, after `statuses.add(PluginStatus.of(candidate, PluginStatus.State.FAILED, failure.getMessage()));`, add:

```java
                startupProblems.add(new Problem(Problem.Kind.NOT_LOADED, Optional.of(candidate.id()), Optional.of(candidate.descriptor().name()),
                    failure.getMessage(), failure.getCause() == null ? Optional.empty() : Optional.of(failure)));
```

   and after `statuses.add(PluginStatus.of(candidate, outcome.state(), outcome.reason()));` add:

```java
            if (outcome.state() == PluginStatus.State.SKIPPED)
                startupProblems.add(new Problem(Problem.Kind.NOT_STARTED, Optional.of(candidate.id()), Optional.of(candidate.descriptor().name()),
                    outcome.reason(), Optional.empty()));
```

- [ ] **Step 4: Announce them in the application**

Create `jasper-app/src/main/java/dev/jasper/app/application/PluginProblemNotices.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.plugins.PluginRuntime;
import java.util.List;

/**
 * What kept plugins from running at this launch, as plugin problems (spec section 7, producer 5): one ERROR
 * notification each, with Open Plugins, and Show more… when an exception lies behind it. A plugin that failed while
 * starting is not repeated here, because its incident's notification already says so. EDT only.
 */
final class PluginProblemNotices {
    static final String OPEN_PLUGINS = "Open Plugins";

    private final NotificationCenter center;
    private final IncidentNotices incidents;
    private final Runnable openPlugins;

    PluginProblemNotices(NotificationCenter center, IncidentNotices incidents, Runnable openPlugins) {
        this.center = center;
        this.incidents = incidents;
        this.openPlugins = openPlugins;
    }

    /** Posts one notification per problem. */
    void announce(List<PluginRuntime.Problem> problems) {
        for (PluginRuntime.Problem problem : problems) {
            var request = NotificationRequest.of(IncidentNotices.PLUGINS.id(), Severity.ERROR, title(problem))
                .withContent(problem.detail())
                .withAction(new NotificationAction(OPEN_PLUGINS, openPlugins, false));
            if (problem.cause().isPresent())
                incidents.pluginNotice(problem.pluginId().orElse(NotificationRequest.JASPER), request, problem.cause().get(), posted -> { });
            else center.post(request);
        }
    }

    /** "Remote could not be loaded", "Remote was not started" or "A plugin could not be read". */
    static String title(PluginRuntime.Problem problem) {
        String name = problem.name().orElse("A plugin");
        return switch (problem.kind()) {
            case NOT_LOADED -> name + " could not be loaded";
            case NOT_STARTED -> name + " was not started";
            case UNREADABLE -> "A plugin could not be read";
        };
    }
}
```

In `JasperApplication.startPlugins`, directly after the statement `plugins.start(configuration == null ? Map.of() : configuration.snapshot().plugins(), themes.current().chrome().dark());`, add:

```java
        new PluginProblemNotices(notificationCenter, incidentNotices,
            () -> openSettings(null, dev.jasper.app.pluginmanager.PluginsPage.ID)).announce(plugins.problems());
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.application.*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java jasper-app/src/main/java/dev/jasper/app/application/PluginProblemNotices.java jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java jasper-app/src/test/java/dev/jasper/app/plugins/PluginRuntimeTest.java jasper-app/src/test/java/dev/jasper/app/application/PluginProblemNoticesTest.java jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationIncidentsTest.java
git commit -m "feat(notifications): announce what kept plugins from running" -m "After plugins start, each load failure, skipped plugin and unreadable plugin directory is posted in Plugin problems with Open Plugins, and Show more… when an exception lies behind it." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: Snippets posts notifications

**Files:**
- Modify: `plugins/snippets/src/main/java/dev/jasper/snippets/SnippetStore.java`
- Modify: `plugins/snippets/src/main/java/dev/jasper/snippets/SnippetsScope.java`
- Modify: `plugins/snippets/src/main/java/dev/jasper/snippets/SnippetsPlugin.java`
- Test: `plugins/snippets/src/test/java/dev/jasper/snippets/SnippetStoreTest.java`

**Interfaces:**
- Consumes: Task 2's `dev.jasper.sdk.notifications.NotificationGroup` (`error(title, content, cause)`), Task 4's
  `PluginContext.notifications()` and `FakeNotifications` (`new FakeNotifications()`, `group(id)`, `lines()`; a line is
  `group|SEVERITY|title|content`, then `|` and the cause's simple class name when there is one).
- Produces: `SnippetStore.openInEditor(NotificationGroup)` and `SnippetsScope(SnippetStore, NotificationGroup, String)`.

Every migrated failure follows the spec: a title that names what failed and carries no user data, the detail
(paths, names, the failure's message) as content, and the `Throwable` as the cause wherever one is at hand.

- [ ] **Step 1: Write the failing test**

In `SnippetStoreTest.java`, add `import dev.jasper.sdk.testing.FakeNotifications;` and replace the test
`openInEditorCreatesAMissingFileAndReportsEditorFailures` with:

```java
    @Test void openInEditorCreatesAMissingFileAndPostsEditorFailures() throws Exception {
        Path file = dir.resolve("snippets.toml");
        var opened = new ArrayList<Path>();
        SwingUtilities.invokeAndWait(() -> {
            try (var store = inline(file, opened)) {
                var notifications = new FakeNotifications();
                store.openInEditor(notifications.group("dev.jasper.snippets"));
                assertThat(opened).containsExactly(file);
                assertThat(notifications.lines()).isEmpty();
                try { assertThat(Files.readString(file)).isEqualTo(SnippetFile.HEADER); }
                catch (IOException e) { throw new UncheckedIOException(e); }
            }
            try (var failing = new SnippetStore(file, path -> { throw new IllegalStateException("no editor"); }, inlineWorker(), Runnable::run)) {
                var notifications = new FakeNotifications();
                failing.openInEditor(notifications.group("dev.jasper.snippets"));
                assertThat(notifications.lines()).containsExactly(
                    "dev.jasper.snippets|ERROR|Could not open the snippets file|" + file + ": no editor|IllegalStateException");
            }
        });
    }
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-plugin-snippets:test`
Expected: compilation fails, because `openInEditor` takes a `Consumer<String>`.

- [ ] **Step 3: Post through the plugin's group**

In `SnippetStore.java`, add `import dev.jasper.sdk.notifications.NotificationGroup;` after
`import dev.jasper.sdk.Subscription;`, and replace

```java
        /** Creates the file with its header when missing, then hands it to the editor (which reports its own failures). */
        public void openInEditor(Consumer<String> onError) {
            submit(() -> {
                try {
                    if (!Files.exists(file)) StateFile.writeAtomically(file, ".snippets-", SnippetFile.HEADER);
                    editor.accept(file);
                } catch (IOException | RuntimeException failure) {
                    deliver.execute(() -> { if (!closed) onError.accept("Could not open " + file + ": " + failure.getMessage()); });
                }
            });
        }
```

with

```java
        /**
         * Creates the file with its header when missing, then hands it to the editor (which reports its own failures).
         * A failure here is posted in {@code notifications}, with its cause.
         */
        public void openInEditor(NotificationGroup notifications) {
            submit(() -> {
                try {
                    if (!Files.exists(file)) StateFile.writeAtomically(file, ".snippets-", SnippetFile.HEADER);
                    editor.accept(file);
                } catch (IOException | RuntimeException failure) {
                    String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
                    deliver.execute(() -> {
                        if (!closed) notifications.error("Could not open the snippets file", file + ": " + reason, failure);
                    });
                }
            });
        }
```

In `SnippetsScope.java`:

1. Replace `import java.util.function.Consumer;` with nothing, and add `import dev.jasper.sdk.notifications.NotificationGroup;`
   after `import dev.jasper.sdk.Subscription;`.
2. Replace `    private final Consumer<String> onError;` with `    private final NotificationGroup notifications;`.
3. Replace

```java
    SnippetsScope(SnippetStore store, Consumer<String> onError, String openActionId) {
        this.store = store; this.onError = onError; this.openActionId = openActionId;
    }
```

   with

```java
    SnippetsScope(SnippetStore store, NotificationGroup notifications, String openActionId) {
        this.store = store; this.notifications = notifications; this.openActionId = openActionId;
    }
```

4. Replace `store.openInEditor(onError);` with `store.openInEditor(notifications);`.

In `SnippetsPlugin.java`, replace `new SnippetsScope(store, context.notices()::error, OPEN)` with
`new SnippetsScope(store, context.notifications().group(), OPEN)`.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-plugin-snippets:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/snippets
git commit -m "feat(snippets): post notifications instead of notices" -m "A snippets file that cannot be opened is posted in the plugin's group with its cause." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: Editor posts notifications

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/recent/RecentFilesScope.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java`

**Interfaces:**
- Consumes: Task 2's `NotificationGroup` (`warning(title, content)`, `error(title, content, cause)`), Task 4's
  `PluginContext.notifications()` and `FakePluginHost.notifications()` (`lines()`).
- Produces: `RecentFilesScope(RecentFiles, Reopeners, Supplier<Instant>, BiConsumer<EditorDocument, WindowOwner>,
  NotificationGroup, Icon)`.

- [ ] **Step 1: Write the failing tests**

In `EditorWindowTest.java`, in `theVimrcIsReadWhenVimFirstTurnsOnAndReloadReadsItAgain`, replace

```java
        assertThat(host.notices()).as("Vim is off: nothing is read").isEmpty();
        window.toggleVim();
        assertThat(host.notices()).containsExactly("dev.jasper.editor: vimrc: 2 lines were skipped; see :messages");
```

with

```java
        assertThat(host.notifications().lines()).as("Vim is off: nothing is read").isEmpty();
        window.toggleVim();
        assertThat(host.notifications().lines())
            .containsExactly("dev.jasper.editor|WARNING|Some vimrc lines were skipped|2 lines were skipped; see :messages.");
```

and `assertThat(host.notices()).as("read once, and again only on Reload vimrc").hasSize(1);` with
`assertThat(host.notifications().lines()).as("read once, and again only on Reload vimrc").hasSize(1);`.

In `aVimrcThatCannotBeReadIsReported`, replace

```java
        assertThat(host.notices()).singleElement().asString()
            .startsWith("dev.jasper.editor: vimrc: could not read " + window.vimrcFile()).endsWith("; Vim runs with its defaults.");
```

with

```java
        assertThat(host.notifications().lines()).singleElement().asString()
            .startsWith("dev.jasper.editor|WARNING|Could not read the vimrc|" + window.vimrcFile() + ": ")
            .endsWith(". Vim runs with its defaults.");
```

In `EditorPluginTest.java`, add after `otherPluginsMakeTheirKeysReopenableAndOtherKeysAreDimmed`:

```java
    @Test void aRecentFileThatCannotBeReopenedIsPostedWithItsCause() throws Exception {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            editor.open(new StubDocument("stub:/srv/app.conf", "app.conf", "dustin@prod:/srv/app.conf", "prod"));
            drain(host);
            editor.reopener("stub:", key -> Optional.of(owner -> { throw new IllegalStateException("the host is gone"); }));
            List<String> rows = host.searchScope(RECENT, "", terminal, null);
            host.executeInScope(RECENT, rowId(rows), "open", terminal, null);
            assertThat(host.notifications().lines()).containsExactly(
                "dev.jasper.editor|ERROR|Could not reopen a recent file|dustin@prod:/srv/app.conf: the host is gone|IllegalStateException");
        }
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.EditorWindowTest' --tests 'dev.jasper.editor.EditorPluginTest'`
Expected: FAIL. The Editor still reports through `notices()`, which the fake records apart from notifications, so
`host.notifications().lines()` is empty.

- [ ] **Step 3: Post through the plugin's group**

In `RecentFilesScope.java`:

1. Replace `import java.util.function.Consumer;` with nothing, and add
   `import dev.jasper.sdk.notifications.NotificationGroup;` after `import dev.jasper.sdk.WindowOwner;`.
2. Replace `    private final Consumer<String> error;` with `    private final NotificationGroup notifications;`.
3. Replace `     * @param error shows an error notice` with
   `     * @param notifications where a file that cannot be reopened is posted`.
4. Replace `                            Consumer<String> error, Icon icon) {` with
   `                            NotificationGroup notifications, Icon icon) {`, and
   `this.error = Objects.requireNonNull(error);` with `this.notifications = Objects.requireNonNull(notifications);`.
5. Replace

```java
        if (factory.isEmpty()) { error.accept("Could not reopen " + entry.location() + ": the host or plugin is unavailable."); return; }
        try {
            open.accept(factory.get().apply(context.window()), context.window());
        } catch (RuntimeException failure) {
            error.accept("Could not reopen " + entry.location() + ": "
                + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
        }
```

   with

```java
        if (factory.isEmpty()) {
            notifications.error("Could not reopen a recent file", entry.location() + ": the host or plugin that opened it is unavailable.");
            return;
        }
        try {
            open.accept(factory.get().apply(context.window()), context.window());
        } catch (RuntimeException failure) {
            notifications.error("Could not reopen a recent file", entry.location() + ": "
                + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()), failure);
        }
```

In `EditorPlugin.java`, replace `            context.notices()::error, context.appearance().icon(IconName.FILE));` with
`            context.notifications().group(), context.appearance().icon(IconName.FILE));`.

In `EditorWindow.java`:

1. Replace

```java
        catch (RuntimeException unavailable) {
            context.notices().error("vimrc: the Editor's data folder is not available; Vim runs with its defaults.");
            return;
        }
        Vimrc.Result read = Vimrc.read(editor.get(), file);
        if (read.error() != null) context.notices().error("vimrc: could not read " + file + " (" + read.error() + "); Vim runs with its defaults.");
        else if (read.skipped() > 0)
            context.notices().error("vimrc: " + read.skipped() + (read.skipped() == 1 ? " line was" : " lines were") + " skipped; see :messages");
```

   with

```java
        catch (RuntimeException unavailable) {
            context.notifications().group().error("Could not read the vimrc",
                "The Editor's data folder is not available; Vim runs with its defaults.", unavailable);
            return;
        }
        Vimrc.Result read = Vimrc.read(editor.get(), file);
        if (read.error() != null)
            context.notifications().group().warning("Could not read the vimrc", file + ": " + read.error() + ". Vim runs with its defaults.");
        else if (read.skipped() > 0)
            context.notifications().group().warning("Some vimrc lines were skipped",
                read.skipped() + (read.skipped() == 1 ? " line was" : " lines were") + " skipped; see :messages.");
```

2. Replace

```java
                        context.notices().error("Could not open " + sourced.path() + ": "
                            + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
```

   with

```java
                        context.notifications().group().error("Could not open a file", sourced.path() + ": "
                            + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()), failure);
```

3. Replace

```java
            context.notices().error("Could not save to " + location.path() + ": "
                + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
```

   with

```java
            context.notifications().group().error("Could not save a file", location.path() + ": "
                + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()), failure);
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-plugin-editor:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/editor
git commit -m "feat(editor): post notifications instead of notices" -m "vimrc problems are warnings; files that cannot be opened, saved or reopened are errors with their cause." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 9: File Server posts notifications

**Files:**
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/FileServerPluginTest.java`

**Interfaces:**
- Consumes: Task 2's `NotificationGroup`, Task 4's `PluginContext.notifications()` and `FakePluginHost.notifications()`.
- Produces: nothing new; the private `report` takes a title.

- [ ] **Step 1: Write the failing test**

In `FileServerPluginTest.java`, in `autostartWaitsForTheVaultAndBadEntriesAreReported`, replace

```java
            assertThat(host.notices()).containsExactly("dev.jasper.fileserver: servers.toml: server 2: 'protocol' is required");
```

with

```java
            assertThat(host.notifications().lines())
                .containsExactly("dev.jasper.fileserver|WARNING|servers.toml has a problem|server 2: 'protocol' is required");
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.FileServerPluginTest'`
Expected: FAIL. The warning still goes through `notices()`, which the fake records apart from notifications.

- [ ] **Step 3: Post through the plugin's group**

In `FileServerPlugin.java`:

1. Replace

```java
        registry.server(id).ifPresent(view -> report(store.put(view.definition().copyAs(uniqueName(view.definition().name() + " copy")))));
```

   with

```java
        registry.server(id).ifPresent(view ->
            report("Could not duplicate the server", store.put(view.definition().copyAs(uniqueName(view.definition().name() + " copy")))));
```

2. Replace `            () -> registry.stop(id).thenRun(() -> report(store.remove(id))), () -> { }));` with
   `            () -> registry.stop(id).thenRun(() -> report("Could not delete the server", store.remove(id))), () -> { }));`.
3. Replace

```java
                catch (RuntimeException failure) { environment.ui().execute(() -> { if (!stopped) context.notices().error("Could not show " + folder + ": " + message(failure)); }); }
```

   with

```java
                catch (RuntimeException failure) {
                    environment.ui().execute(() -> {
                        if (!stopped) context.notifications().group().error("Could not show a server's folder", folder + ": " + message(failure), failure);
                    });
                }
```

4. Replace `            context.notices().error("servers.toml: " + warning);` with
   `            context.notifications().group().warning("servers.toml has a problem", warning);`.
5. Replace

```java
        if (error != null && !error.equals(reportedError)) context.notices().error(error + ". File Server will not save changes until it is fixed.");
```

   with

```java
        if (error != null && !error.equals(reportedError))
            context.notifications().group().error("servers.toml has errors", error + ". File Server will not save changes until it is fixed.");
```

6. Replace

```java
                String failure = null;
                try { tls.regenerate(); }
                catch (EngineFailure unwritable) { failure = unwritable.getMessage(); }
                String error = failure;
                environment.ui().execute(() -> {
                    if (stopped) return;
                    if (error != null) context.notices().error(error);
                    refreshPanels();
                });
```

   with

```java
                EngineFailure failure = null;
                try { tls.regenerate(); }
                catch (EngineFailure unwritable) { failure = unwritable; }
                EngineFailure error = failure;
                environment.ui().execute(() -> {
                    if (stopped) return;
                    if (error != null) context.notifications().group().error("Could not regenerate the TLS certificate", message(error), error);
                    refreshPanels();
                });
```

7. Replace

```java
    private void report(CompletableFuture<Void> mutation) {
        mutation.whenComplete((ignored, failure) -> environment.ui().execute(() -> {
            if (!stopped && failure != null) context.notices().error(message(failure));
        }));
    }
```

   with

```java
    private void report(String title, CompletableFuture<Void> mutation) {
        mutation.whenComplete((ignored, failure) -> environment.ui().execute(() -> {
            if (!stopped && failure != null) context.notifications().group().error(title, message(failure), failure);
        }));
    }
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-plugin-fileserver:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/fileserver
git commit -m "feat(fileserver): post notifications instead of notices" -m "servers.toml problems are warnings and its errors are errors; failed server changes, folder reveals and certificate regeneration are errors with their cause." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 10: Credential Vault posts notifications

**Files:**
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/VaultPlugin.java`
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/service/VaultService.java`
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/service/KeyImportPrompt.java`
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/ui/VaultScope.java`
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/ui/VaultManagerWindow.java`
- Test: `plugins/vault/src/test/java/dev/jasper/vault/service/VaultServiceTest.java`
- Test: `plugins/vault/src/test/java/dev/jasper/vault/service/KeyImportInterleavingTest.java`
- Test: `plugins/vault/src/test/java/dev/jasper/vault/ui/VaultScopeTest.java`
- Test: `plugins/vault/src/test/java/dev/jasper/vault/ui/VaultManagerWindowTest.java`

**Interfaces:**
- Consumes: Task 2's `NotificationGroup` (`warning`, `error(title, cause)`, `error(title, content, cause)`), Task 4's
  `PluginContext.notifications()` and `FakeNotifications`.
- Produces: `VaultService`'s two constructors take a `NotificationGroup notifications` where they took
  `Consumer<String> notice`; `KeyImportPrompt`'s constructor likewise; `VaultScope` has one constructor,
  `VaultScope(LockManager, VaultService, SecretClipboard, BiConsumer<WindowHandle, Optional<UUID>>, NotificationGroup)`.

- [ ] **Step 1: Write the failing tests**

In `VaultServiceTest.java`:

1. Add `import dev.jasper.sdk.testing.FakeNotifications;`.
2. Replace `    final List<String> notices = new ArrayList<>();` with
   `    final FakeNotifications notifications = new FakeNotifications();`.
3. In both `new VaultService(...)` calls, replace the argument `notices::add` with `notifications.group("dev.jasper.vault")`.
4. Replace

```java
        assertThat(notices).hasSize(3).allSatisfy(notice -> assertThat(notice).contains("Create a vault"));
```

   with

```java
        String noVault = "dev.jasper.vault|WARNING|No vault yet|Create a vault in Credential Vault first (Open Vault...)";
        assertThat(notifications.lines()).containsExactly(noVault, noVault, noVault);
```

5. Replace

```java
        assertThat(notices).singleElement().satisfies(notice -> assertThat(notice).contains("window"));
```

   with

```java
        assertThat(notifications.lines()).containsExactly("dev.jasper.vault|WARNING|Credential Vault has no window to ask in|");
```

In `KeyImportInterleavingTest.java`, add `import dev.jasper.sdk.testing.FakeNotifications;`, replace
`    final List<String> notices = new ArrayList<>();` with
`    final FakeNotifications notifications = new FakeNotifications();`, and replace the argument `notices::add,` with
`notifications.group("dev.jasper.vault"),`.

In `VaultManagerWindowTest.java`, add `import dev.jasper.sdk.testing.FakeNotifications;` and replace the last argument of
each of the five `new VaultService(...)` calls (`text -> { });` four times and `text -> {});` once) with
`new FakeNotifications().group("dev.jasper.vault"));`. The `SecretClipboard(text -> { }, …)` arguments stay.

In `VaultScopeTest.java`:

1. Add `import dev.jasper.sdk.testing.FakeNotifications;`.
2. After `    final List<String> opened = new ArrayList<>();` add:

```java
    final FakeNotifications notifications = new FakeNotifications();
    boolean clipboardBroken;
```

3. In `scope(Path dir)`, replace

```java
        var service = new VaultService(lock, () -> Optional.of(window), prompt -> { }, prompt -> { }, prompt -> { }, notice -> { });
        scope = new VaultScope(lock, service, new SecretClipboard(text -> clipboard = text, () -> Optional.of(clipboard), armed::add),
            (owner, id) -> opened.add(id.map(UUID::toString).orElse("-")));
```

   with

```java
        var service = new VaultService(lock, () -> Optional.of(window), prompt -> { }, prompt -> { }, prompt -> { },
            notifications.group("dev.jasper.vault"));
        scope = new VaultScope(lock, service, new SecretClipboard(text -> {
                if (clipboardBroken) throw new IllegalStateException("clipboard busy");
                clipboard = text;
            }, () -> Optional.of(clipboard), armed::add),
            (owner, id) -> opened.add(id.map(UUID::toString).orElse("-")), notifications.group("dev.jasper.vault"));
```

4. Add after `copyingPutsTheSecretOnTheClipboardWithAClearArmedAndOpenSelectsTheRow`:

```java
    @Test void aCopyThatFailsIsPostedWithItsCause(@TempDir Path dir) {
        scope(dir);
        populate();
        clipboardBroken = true;
        scope.execute(row(scope.search("", query).rows(), "cred." + PROD), VaultScope.COPY_USERNAME, query);
        assertThat(notifications.lines()).containsExactly(
            "dev.jasper.vault|ERROR|Could not copy from the vault|IllegalStateException: clipboard busy|IllegalStateException");
        assertThat(clipboard).isEmpty();
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-vault:test`
Expected: compilation fails, because `VaultService` and `VaultScope` take a `Consumer<String>`.

- [ ] **Step 3: Post through the plugin's group**

In `VaultService.java`:

1. Add `import dev.jasper.sdk.notifications.NotificationGroup;` after `import dev.jasper.sdk.PluginInfo;`.
2. Replace

```java
    static final String NO_VAULT_NOTICE = "Create a vault in Credential Vault first (Open Vault...)";
```

   with

```java
    static final String NO_VAULT_TITLE = "No vault yet";
    static final String NO_VAULT_NOTICE = "Create a vault in Credential Vault first (Open Vault...)";
```

3. Replace `    private final Consumer<String> notice;` with `    private final NotificationGroup notifications;`.
4. Replace

```java
                        Consumer<GrantPrompt> showGrant, Consumer<PickPrompt> showPick, Consumer<String> notice) {
        this.lock = lock; this.fallbackOwner = fallbackOwner; this.showUnlock = showUnlock; this.showGrant = showGrant; this.showPick = showPick; this.notice = notice;
```

   with

```java
                        Consumer<GrantPrompt> showGrant, Consumer<PickPrompt> showPick, NotificationGroup notifications) {
        this.lock = lock; this.fallbackOwner = fallbackOwner; this.showUnlock = showUnlock; this.showGrant = showGrant; this.showPick = showPick;
        this.notifications = notifications;
```

5. Replace `                        Consumer<GrantPrompt> showGrant, Consumer<PickPrompt> showPick, Consumer<String> notice,` with
   `                        Consumer<GrantPrompt> showGrant, Consumer<PickPrompt> showPick, NotificationGroup notifications,`,
   and `        this(lock, fallbackOwner, showUnlock, showGrant, showPick, notice);` with
   `        this(lock, fallbackOwner, showUnlock, showGrant, showPick, notifications);`.
6. Replace `            case NO_VAULT: notice.accept(NO_VAULT_NOTICE); return CompletableFuture.completedFuture(false);` with
   `            case NO_VAULT: notifications.warning(NO_VAULT_TITLE, NO_VAULT_NOTICE); return CompletableFuture.completedFuture(false);`.
7. Replace `if (failure != null) { notice.accept("Could not save the credential grant"); prompt.settle(Optional::empty); }` with
   `if (failure != null) { notifications.error("Could not save the credential grant", failure); prompt.settle(Optional::empty); }`.
8. Replace `lock, background, ui, notice, changed);` with `lock, background, ui, notifications, changed);`.
9. Replace `                notice.accept(NO_WINDOW_NOTICE);` with `                notifications.warning(NO_WINDOW_NOTICE, "");`.

In `KeyImportPrompt.java`:

1. Add `import dev.jasper.sdk.notifications.NotificationGroup;` after `import dev.jasper.sdk.Subscription;`.
2. Replace `    private final Consumer<String> notice;` with `    private final NotificationGroup notifications;`.
3. Replace `LockManager lock, Executor background, Executor ui, Consumer<String> notice, Runnable changed) {` with
   `LockManager lock, Executor background, Executor ui, NotificationGroup notifications, Runnable changed) {`, and
   `        this.notice = notice; this.changed = changed;` with `        this.notifications = notifications; this.changed = changed;`.
4. Replace

```java
                if (dismissed || !owner.isOpen()) notice.accept("Keys were saved in Vault; host import was cancelled. Re-import to finish.");
```

   with

```java
                if (dismissed || !owner.isOpen())
                    notifications.warning("Host import was cancelled", "Keys were saved in Vault; re-import the hosts to finish.");
```

In `VaultScope.java`:

1. Replace `import java.util.function.Consumer;` with nothing, and add
   `import dev.jasper.sdk.notifications.NotificationGroup;` after `import dev.jasper.sdk.Subscription;`.
2. Replace `    private final Consumer<String> notice;` with `    private final NotificationGroup notifications;`.
3. Replace both constructors

```java
    public VaultScope(LockManager lock, VaultService service, SecretClipboard clipboard,
                      BiConsumer<WindowHandle, Optional<UUID>> openManager) {
        this(lock, service, clipboard, openManager, message -> { });
    }

    public VaultScope(LockManager lock, VaultService service, SecretClipboard clipboard,
                      BiConsumer<WindowHandle, Optional<UUID>> openManager, Consumer<String> notice) {
        this.lock = lock; this.service = service; this.clipboard = clipboard; this.openManager = openManager; this.notice = notice;
    }
```

   with

```java
    public VaultScope(LockManager lock, VaultService service, SecretClipboard clipboard,
                      BiConsumer<WindowHandle, Optional<UUID>> openManager, NotificationGroup notifications) {
        this.lock = lock; this.service = service; this.clipboard = clipboard; this.openManager = openManager;
        this.notifications = notifications;
    }
```

4. Replace `            } catch (RuntimeException failure) { notice.accept("Could not copy from the vault: " + failure.getMessage()); }`
   with `            } catch (RuntimeException failure) { notifications.error("Could not copy from the vault", failure); }`.

In `VaultManagerWindow.java`:

1. Replace `                grant -> report(manager.revoke(grant)), generate, this::changePassword, lock::lock,` with
   `                grant -> report("Could not revoke the grant", manager.revoke(grant)), generate, this::changePassword, lock::lock,`.
2. Replace

```java
        catch (RuntimeException failure) { dialog.close(); context.notices().error(EditorForm.message(failure)); return; }
```

   with

```java
        catch (RuntimeException failure) {
            dialog.close();
            context.notifications().group().error("Could not open the form", title + ": " + EditorForm.message(failure), failure);
            return;
        }
```

3. Replace

```java
            if (failure != null) context.notices().error("Master password was not changed: " + EditorForm.message(failure));
```

   with

```java
            if (failure != null) context.notifications().group().error("Master password was not changed", EditorForm.message(failure), failure);
```

4. Replace `        report(copied);` with `        report("Could not copy the public key", copied);`.
5. Replace

```java
    private void report(CompletableFuture<Void> operation) {
        operation.whenComplete((ignored, failure) -> {
            if (failure != null) context.notices().error(EditorForm.message(failure));
```

   with

```java
    private void report(String title, CompletableFuture<Void> operation) {
        operation.whenComplete((ignored, failure) -> {
            if (failure != null) context.notifications().group().error(title, EditorForm.message(failure), failure);
```

In `VaultPlugin.java`:

1. Replace `            this::showUnlock, this::showGrant, this::showPick, context.notices()::error,` with
   `            this::showUnlock, this::showGrant, this::showPick, context.notifications().group(),`.
2. Replace `        scope = new VaultScope(lock, service, clipboard, this::open, context.notices()::error);` with
   `        scope = new VaultScope(lock, service, clipboard, this::open, context.notifications().group());`.
3. Replace `            if (!stopped) context.notices().error("Could not open Credential Vault: " + message(failure));` with
   `            if (!stopped) context.notifications().group().error("Could not open Credential Vault", message(failure), failure);`.
4. Replace `                    context.notices().error("Could not create the vault: " + message(failure));` with
   `                    context.notifications().group().error("Could not create the vault", message(failure), failure);`.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-plugin-vault:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/vault
git commit -m "feat(vault): post notifications instead of notices" -m "A missing vault or window and a cancelled host import are warnings; failures to copy, save a grant, open or create the vault and the manager's operations are errors with their cause." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 11: Remote posts notifications, with a Tunnels group

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ConfigImportController.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/edit/RemoteEditing.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransferUi.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsUi.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/edit/RemoteEditingTest.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/RemoteEditingPluginTest.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/TunnelsPluginTest.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/transfers/TransferUiTest.java`

**Interfaces:**
- Consumes: Task 2's `GroupSpec`, `DisplayMode`, `NotificationGroup`; Task 4's `PluginContext.notifications()`,
  `FakePluginHost.notifications()` (`lines()`, `posts()`, `groups()`) and `FakeNotifications`.
- Produces: `RemoteEditing`'s two constructors take a `NotificationGroup notifications` where they took
  `Consumer<String> notice`; `TunnelsUi.NOTIFICATIONS = "dev.jasper.remote.tunnels"`, the group Remote declares for
  tunnel failures (spec section 6).

- [ ] **Step 1: Write the failing tests**

In `RemoteEditingTest.java`:

1. Add `import dev.jasper.sdk.testing.FakeNotifications;`.
2. Replace `    final List<String> notices = new ArrayList<>();` with
   `    final FakeNotifications notifications = new FakeNotifications();`.
3. In the `editing` and `reopening` fields, replace the argument `notices::add` with
   `notifications.group("dev.jasper.remote")`.
4. In `anEditorFailureBecomesANotice`, replace

```java
        assertThat(notices).containsExactly("Could not open /etc/hosts in the Editor: the Editor window is closing");
```

   with

```java
        assertThat(notifications.lines()).containsExactly(
            "dev.jasper.remote|ERROR|Could not open a file in the Editor|/etc/hosts: the Editor window is closing|IllegalStateException");
```

In `RemoteEditingPluginTest.java`, in `anEditorThatThrowsBecomesANoticeAndThePanelKeepsWorking`, replace

```java
            assertThat(host.notices()).containsExactly("dev.jasper.remote: Could not open /notes.toml in the Editor: the Editor window is closing");
```

with

```java
            assertThat(host.notifications().lines()).containsExactly(
                "dev.jasper.remote|ERROR|Could not open a file in the Editor|/notes.toml: the Editor window is closing|IllegalStateException");
```

In `RemotePluginTest.java`, replace `            assertThat(host.notices()).hasSize(3);` with:

```java
            assertThat(host.notifications().posts()).extracting(post -> post.spec().title())
                .containsExactlyInAnyOrder("Could not duplicate the host", "Could not update favorites", "Could not save the host");
            assertThat(host.notifications().posts()).allSatisfy(post -> assertThat(post.spec().cause()).isPresent());
```

In `TunnelsPluginTest.java`, in `registersTheTunnelsSurface`, after `assertThat(host.failures()).isEmpty();` add:

```java
            assertThat(host.notifications().groups()).contains("dev.jasper.remote.tunnels|Tunnels|CARD");
```

In `TransferUiTest.java`, replace each `host.notices()` (three places) with `host.notifications().lines()`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-remote:test`
Expected: compilation fails, because `RemoteEditing` takes a `Consumer<String>`.

- [ ] **Step 3: Post through Remote's groups**

In `RemoteEditing.java`:

1. Add `import dev.jasper.sdk.notifications.NotificationGroup;` after `import dev.jasper.sdk.WindowOwner;`, and remove
   `import java.util.function.Consumer;`.
2. Replace `    private final Consumer<String> notice;` with `    private final NotificationGroup notifications;`.
3. In both constructors' JavaDoc, replace `     * @param notice shows an error notice` with
   `     * @param notifications where a file the Editor cannot open is posted`.
4. Replace

```java
    public RemoteEditing(Editor editor, EditChannels channels, Supplier<Optional<WindowHandle>> activeWindow, Consumer<String> notice) {
        this(editor, channels, activeWindow, notice, id -> Optional.empty());
    }
```

   with

```java
    public RemoteEditing(Editor editor, EditChannels channels, Supplier<Optional<WindowHandle>> activeWindow, NotificationGroup notifications) {
        this(editor, channels, activeWindow, notifications, id -> Optional.empty());
    }
```

5. Replace `    public RemoteEditing(Editor editor, EditChannels channels, Supplier<Optional<WindowHandle>> activeWindow, Consumer<String> notice,`
   with `    public RemoteEditing(Editor editor, EditChannels channels, Supplier<Optional<WindowHandle>> activeWindow, NotificationGroup notifications,`,
   and `this.notice = Objects.requireNonNull(notice);` with `this.notifications = Objects.requireNonNull(notifications);`.
6. Replace

```java
            notice.accept("Could not open " + path + " in the Editor: "
                + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
```

   with

```java
            notifications.error("Could not open a file in the Editor", path + ": "
                + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()), failure);
```

In `ConfigImportController.java`:

1. Replace

```java
                else { context.notices().error("Hosts could not be read. Fix hosts.toml before importing."); close(); }
```

   with

```java
                else {
                    context.notifications().group().error("Could not import hosts", "Hosts could not be read. Fix hosts.toml before importing.", failure);
                    close();
                }
```

2. Replace

```java
                ui.execute(() -> { if (live(version)) { context.notices().error("Could not read SSH config"); close(); } });
```

   with

```java
                ui.execute(() -> {
                    if (!live(version)) return;
                    context.notifications().group().error("Could not read the SSH config", sshDir.resolve("config") + ": "
                        + (unreadable.getMessage() == null ? unreadable.getClass().getSimpleName() : unreadable.getMessage()), unreadable);
                    close();
                });
```

3. Replace

```java
                                context.notices().error("Keys were saved in Vault; hosts were not updated. Refresh the import and retry.");
```

   with

```java
                                context.notifications().group().error("Hosts were not updated",
                                    "Keys were saved in Vault, but hosts.toml was not. Refresh the import and retry.", saveFailure);
```

In `RemotePlugin.java`:

1. In the `new dev.jasper.remote.edit.RemoteEditing(...)` call, replace `context.notices()::error,` with
   `context.notifications().group(),`.
2. Replace `        }); } catch (RuntimeException failure) { context.notices().error(message(failure)); return; }` with:

```java
        }); } catch (RuntimeException failure) {
            context.notifications().group().error("Could not start the connection", host.name() + ": " + message(failure), failure);
            return;
        }
```

3. Replace `            catch (RuntimeException failure) { context.notices().error(message(failure)); close(); return; }` with:

```java
            catch (RuntimeException failure) {
                context.notifications().group().error("Could not show the connection dialog", host.name() + ": " + message(failure), failure);
                close();
                return;
            }
```

4. Replace `        if (host.isEmpty()) { context.notices().error("Split with Same Host needs a connected SSH pane"); return; }` with
   `        if (host.isEmpty()) { context.notifications().group().warning("Split with Same Host needs a connected SSH pane", ""); return; }`.
5. Replace

```java
                else { if (!closed[0]) editor[0].showError(message(failure)); context.notices().error(message(failure)); }
```

   with

```java
                else {
                    if (!closed[0]) editor[0].showError(message(failure));
                    context.notifications().group().error("Could not save the host", message(failure), failure);
                }
```

6. Replace

```java
            () -> store.remove(host.id()).whenComplete((ignored, failure) -> ui.execute(() -> { if (failure != null) context.notices().error(message(failure)); dialog.close(); })), dialog::close);
```

   with

```java
            () -> store.remove(host.id()).whenComplete((ignored, failure) -> ui.execute(() -> {
                if (failure != null) context.notifications().group().error("Could not delete the host", message(failure), failure);
                dialog.close();
            })), dialog::close);
```

7. Replace `h -> reportMutation(store.put(duplicate(h)))` with
   `h -> reportMutation("Could not duplicate the host", store.put(duplicate(h)))`, and
   `(h, favorite) -> reportMutation(store.put(h.withFavorite(favorite)))` with
   `(h, favorite) -> reportMutation("Could not update favorites", store.put(h.withFavorite(favorite)))`.
8. Replace

```java
    private void reportMutation(CompletableFuture<Void> mutation) {
        mutation.whenComplete((ignored, failure) -> ui.execute(() -> {
            if (!stopped && failure != null) context.notices().error(message(failure));
        }));
    }
```

   with

```java
    private void reportMutation(String title, CompletableFuture<Void> mutation) {
        mutation.whenComplete((ignored, failure) -> ui.execute(() -> {
            if (!stopped && failure != null) context.notifications().group().error(title, message(failure), failure);
        }));
    }
```

In `SftpUi.java` (keep its compact style):

1. Replace the line
   `            catch(Exception failure) { ui.execute(()-> { if(!closed) context.notices().error(message(failure)); }); } });` with
   `            catch(Exception failure) { ui.execute(()-> { if(!closed) context.notifications().group().error("Could not prepare the transfer",message(failure),failure); }); } });`.
2. Replace the line
   `            if(error!=null) { ui.execute(()-> { if(!closed) context.notices().error(message(error)); });return; }` with
   `            if(error!=null) { ui.execute(()-> { if(!closed) context.notifications().group().error("Could not connect for the transfer",message(error),error); });return; }`.
3. Replace the line
   `                catch(Exception failure) { ui.execute(()-> { if(!closed) context.notices().error(message(failure)); });return; }` with
   `                catch(Exception failure) { ui.execute(()-> { if(!closed) context.notifications().group().error("Could not check for existing items",message(failure),failure); });return; }`.
4. Replace `if(!closed && error!=null) context.notices().error(message(error));` with
   `if(!closed && error!=null) context.notifications().group().error("Could not queue the transfer",message(error),error);`.

In `TransferUi.java`:

1. Replace

```java
            case RESUME -> report(coordinator.resume(id, window));
            case RETRY_FAILED -> report(coordinator.retryFailed(id, window));
            case RETRY_CLEANUP -> report(coordinator.cleanup(id, window));
```

   with

```java
            case RESUME -> report("Could not resume the transfer", coordinator.resume(id, window));
            case RETRY_FAILED -> report("Could not retry the failed items", coordinator.retryFailed(id, window));
            case RETRY_CLEANUP -> report("Could not clean up the transfer", coordinator.cleanup(id, window));
```

2. Replace `    private void report(CompletableFuture<?> operation) {` with
   `    private void report(String title, CompletableFuture<?> operation) {`, and in its body
   `            if (error != null) context.notices().error(message(error));` with
   `            if (error != null) context.notifications().group().error(title, message(error), error);`.
3. Replace `            if (error != null && !GONE.equals(message(error))) context.notices().error(message(error));` with:

```java
            if (error != null && !GONE.equals(message(error)))
                context.notifications().group().error("Could not clear the transfer", message(error), error);
```

4. Replace

```java
            if (error != null) { context.notices().error(message(error)); return; }
            if (entry.isEmpty()) { report(coordinator.resume(id, window)); return; }
```

   with

```java
            if (error != null) { context.notifications().group().error("Could not resolve the transfer", message(error), error); return; }
            if (entry.isEmpty()) { report("Could not resume the transfer", coordinator.resume(id, window)); return; }
```

5. Replace `report(coordinator.restart(target.id(), window));` with
   `report("Could not restart the transfer", coordinator.restart(target.id(), window));`.
6. Replace

```java
            if (error != null) { context.notices().error(message(error)); return; }
            report(coordinator.resume(entry.jobId(), window));
```

   with

```java
            if (error != null) { context.notifications().group().error("Could not resolve the conflict", message(error), error); return; }
            report("Could not resume the transfer", coordinator.resume(entry.jobId(), window));
```

In `TunnelsUi.java`:

1. Add, after `import dev.jasper.sdk.Subscription;`:

```java
import dev.jasper.sdk.notifications.DisplayMode;
import dev.jasper.sdk.notifications.GroupSpec;
import dev.jasper.sdk.notifications.NotificationGroup;
```

2. Replace `        STOP_ALL = "dev.jasper.remote.tunnels.stop-all", STATUS = "dev.jasper.remote.tunnels.status";` with:

```java
        STOP_ALL = "dev.jasper.remote.tunnels.stop-all", STATUS = "dev.jasper.remote.tunnels.status";
    /** The notification group for tunnel failures, which the user can mute apart from Remote's own. */
    public static final String NOTIFICATIONS = "dev.jasper.remote.tunnels";
```

3. Replace `    private final Icon icon;` with:

```java
    private final Icon icon;
    private final NotificationGroup notifications;
```

4. In the eight-argument constructor, replace `        this.clipboard = clipboard;` with:

```java
        this.clipboard = clipboard;
        notifications = context.notifications().group(new GroupSpec(NOTIFICATIONS, "Tunnels", DisplayMode.CARD));
```

5. Replace `                if (failure != null && !closed) context.notices().error(message(failure));` with
   `                if (failure != null && !closed) notifications.error("Could not delete the tunnel", message(failure), failure);`.
6. Replace `        report(store.put(Tunnel.create(` with `        report("Could not duplicate the tunnel", store.put(Tunnel.create(`.
7. Replace
   `            catch (RuntimeException unavailable) { context.notices().error("Could not copy the address: " + message(unavailable)); }`
   with
   `            catch (RuntimeException unavailable) { notifications.error("Could not copy the address", message(unavailable), unavailable); }`.
8. Replace

```java
    private void report(CompletableFuture<Void> mutation) {
        mutation.whenComplete((ignored, failure) -> ui.execute(() -> { if (failure != null && !closed) context.notices().error(message(failure)); }));
    }
```

   with

```java
    private void report(String title, CompletableFuture<Void> mutation) {
        mutation.whenComplete((ignored, failure) -> ui.execute(() -> { if (failure != null && !closed) notifications.error(title, message(failure), failure); }));
    }
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-plugin-remote:test`
Expected: PASS.

Then confirm that no plugin still calls `notices()`:

Run: `grep -rn "notices()" plugins --include='*.java'`
Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add plugins/remote
git commit -m "feat(remote): post notifications instead of notices, with a Tunnels group" -m "Each failure gets a title and its cause; tunnel failures go to dev.jasper.remote.tunnels so they can be muted apart from Remote's own." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 12: Remove `Notices` and move to SDK 0.14.0

**Files:**
- Delete: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Notices.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginContext.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginContext.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java`
- Modify: the seven `plugins/*/src/main/resources/plugin.toml` (history, sample, snippets, editor, fileserver, vault,
  remote)
- Test: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePaletteTest.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/CapabilitiesTest.java`,
  `jasper-sdk/src/test/java/dev/jasper/sdk/ui/AppearanceContractTest.java`,
  `jasper-sdk/src/test/java/dev/jasper/sdk/files/FilesValuesTest.java`,
  `jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/VersionRangeTest.java`

**Interfaces:**
- Consumes: Tasks 7 to 11 (no plugin calls `notices()` any more) and Task 4's transitional `HostedContext.notices()`.
- Produces: `JasperSdk.VERSION = "0.14.0"`; `PluginContext`, `FakePluginContext`, `FakePluginHost` and
  `HostedContext` have no `notices()`; `dev.jasper.sdk.ui.Notices` no longer exists.

- [ ] **Step 1: Write the failing tests**

In `CapabilitiesTest.java`, `AppearanceContractTest.java`, `FilesValuesTest.java` and `PaletteValuesTest.java`, replace
`assertThat(JasperSdk.VERSION).isEqualTo("0.13.0");` with `assertThat(JasperSdk.VERSION).isEqualTo("0.14.0");`.

In `VersionRangeTest.java`, replace the test `pluginsBuiltForOlderMinorsAreIncompatibleWithZeroThirteen` with:

```java
    @Test void pluginsBuiltForOlderMinorsAreIncompatibleWithZeroFourteen() {
        var host = Version.parse(dev.jasper.sdk.JasperSdk.VERSION);
        assertThat(VersionRange.parse(">=0.10.1, <0.11").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.11.0, <0.12").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.12.0, <0.13").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.13.0, <0.14").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.14.0, <0.15").contains(host)).isTrue();
    }
```

In `FakePaletteTest.java`, replace

```java
            a.get().notices().error("It broke");
            a.get().platform().openInEditor(Path.of("/tmp/x.toml"));
            assertThat(host.notices()).containsExactly("test.a: It broke");
```

with

```java
            a.get().notifications().group().error("It broke", "");
            a.get().platform().openInEditor(Path.of("/tmp/x.toml"));
            assertThat(host.notifications().lines()).containsExactly("test.a|ERROR|It broke|");
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakePaletteTest' :jasper-app:test --tests 'dev.jasper.app.plugins.VersionRangeTest'`
Expected: FAIL. The four version tests and `VersionRangeTest` find "0.13.0"; `FakePaletteTest` passes already.

- [ ] **Step 3: Remove `Notices` and raise the version**

1. Delete `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Notices.java`.
2. In `PluginContext.java`, delete `import dev.jasper.sdk.ui.Notices;` and

```java
        /** @return error notices shown to the user */
        Notices notices();

```

3. In `Platform.java`, replace

```java
     * action, then revealing the file. Runs in the background; a failure is reported through
     * {@link Notices}, never thrown.
```

   with

```java
     * action, then revealing the file. Runs in the background; a failure is posted in the plugin's notification group,
     * never thrown.
```

   and

```java
     * Opens a web address in the user's browser. Runs in the background; a failure is reported
     * through {@link Notices}, never thrown.
```

   with

```java
     * Opens a web address in the user's browser. Runs in the background; a failure is posted in the plugin's
     * notification group, never thrown.
```

4. In `JasperSdk.java`, replace `public static final String VERSION = "0.13.0";` with
   `public static final String VERSION = "0.14.0";`.
5. In `FakePluginContext.java`, delete the line
   `        @Override public dev.jasper.sdk.ui.Notices notices() { return message -> { requireOpen(); host.notices.add(info.id() + ": " + Objects.requireNonNull(message, "message")); }; }`.
6. In `FakePluginHost.java`, delete `        final List<String> notices = new ArrayList<>();` and

```java
        /** Every error notice as {@code pluginId: message}. */
        public List<String> notices() { return List.copyOf(notices); }

```

7. In `HostedContext.java`, delete the imports `dev.jasper.sdk.ui.Notices`, `dev.jasper.sdk.notifications.NotificationSpec`
   and `dev.jasper.sdk.notifications.Severity`, and delete Task 4's transitional method:

```java
        @Override public Notices notices() {
            return message -> {
                requireOpen();
                Objects.requireNonNull(message, "message");
                // Notices is replaced by notifications(): its error becomes one titled with the plugin's name.
                notifications.group().post(NotificationSpec.of(Severity.ERROR, hosted.info().name())
                    .withContent(message.length() <= 4_000 ? message : message.substring(0, 3_999) + "…"));
            };
        }

```

8. In each of the seven `plugin.toml` files, replace `sdk = ">=0.13.0, <0.14"` with `sdk = ">=0.14.0, <0.15"`.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.plugins.*' :jasper-plugin-history:test :jasper-plugin-sample:test :jasper-plugin-snippets:test :jasper-plugin-editor:test :jasper-plugin-fileserver:test :jasper-plugin-vault:test :jasper-plugin-remote:test`
Expected: PASS.

Then confirm nothing names `Notices`:

Run: `grep -rnw "Notices" --include='*.java' jasper-sdk jasper-sdk-testkit jasper-app/src plugins`
Expected: no output (`-w` leaves `IncidentNotices` and `PluginProblemNotices` alone).

Run: `grep -rn "sdk = \">=0.13" plugins`
Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-sdk jasper-sdk-testkit jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java jasper-app/src/test/java/dev/jasper/app/plugins/VersionRangeTest.java plugins
git commit -m "feat(sdk)!: remove Notices; SDK 0.14.0" -m "PluginContext.notices() and dev.jasper.sdk.ui.Notices are gone; plugins post through notifications(). Every bundled plugin declares >=0.14.0, <0.15." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 13: Documentation and full verification

**Files:**
- Modify: `docs/plugin-authoring.md`
- Modify: `docs/sdk-architecture.md`
- Modify: `jasper-sdk/README.md`
- Modify: `docs/notifications.md`
- Modify: `docs/app-maintenance.md`
- Modify: `docs/STATUS.md`
- Modify: `docs/superpowers/specs/2026-10-02-jasper-notifications-design.md`
- Modify: `docs/superpowers/plans/2026-10-03-notifications-3-sdk.md` (this plan's status banner)

**Interfaces:**
- Consumes: everything above.
- Produces: documentation only. `AppDocumentationTest` checks every local link and `#anchor` outside fenced code.

- [ ] **Step 1: Plugin authoring**

In `docs/plugin-authoring.md`:

1. In the descriptor example, replace `sdk = ">=0.13.0, <0.14"` with `sdk = ">=0.14.0, <0.15"`.
2. Replace the whole section from `## Notices and the editor` up to (not including) `## Rules that matter` with:

````markdown
## Notifications and the editor

`context.notifications()` (SDK 0.14.0) posts into Jasper's notifications, from any thread. Every plugin has a default
group, named after the plugin and shown as cards. Declare another group during `start` when some of your notifications
should be muted apart from the rest:

```java
NotificationGroup sync = context.notifications().group(new GroupSpec("dev.example.tool.sync", "Tool sync", DisplayMode.CARD));
context.notifications().group().info("Synced", "3 hosts");
sync.error("Could not sync", "prod: connection refused", failure);
```

- A title says what happened in a few words and carries no paths, host names or messages; those go in the content.
  A title is one line of at most 200 characters and content at most 4,000; the shortcuts cut longer content.
- Pass the cause whenever you have a `Throwable`. Jasper records it as your plugin's incident and adds **Show more…**,
  which opens the Errors window at its stack trace. `error(title, cause)` uses the cause's class and message as the
  content, reading through a `CompletionException` or `ExecutionException` to the failure behind it.
- `post(NotificationSpec)` takes the whole spec: up to 8 actions (7 with a cause), each run on the UI thread and
  expiring the notification unless made with `new NotificationAction(label, run, false)`; `withActivate` for a click
  on the body; `withIcon`; `withWindow` to show the card in a particular window.
- The returned `NotificationHandle` expires the notification. It also expires when the user removes it, when its
  group drops it, and when your plugin stops.
- Group ids follow the panel id pattern and start with your plugin id and a dot; `<plugin-id>.activities` is Jasper's.
- When one of your activities succeeds or fails, Jasper posts its outcome in `<plugin-id>.activities`; post nothing
  for it yourself.

`context.platform().openInEditor(file)` opens a file with the user's editor (configured editor, then the desktop, then
revealing the file), in the background; a failure is posted in your plugin's group. `context.platform().browse(uri)`
opens an http or https address in the user's browser the same way (SDK 0.10.2); any other address throws
`IllegalArgumentException`.

````

3. In the Testing section, replace

```
drive a contributed scope as the palette would; `paletteOpens()`, `notices()`, `openedInEditor()`
and `browsed()` record what the plugin asked for. `connectionLog()` lists delivered connection events, as
`activityLog()` does activities.
```

   with

```
drive a contributed scope as the palette would; `paletteOpens()`, `openedInEditor()`
and `browsed()` record what the plugin asked for. `connectionLog()` lists delivered connection events, as
`activityLog()` does activities. `host.notifications()` records what the plugin posted: `lines()` as
`group|SEVERITY|title|content`, with `|` and the cause's simple class name when it had one, `posts()` with their specs
and whether they expired, `groups()` as `id|name|MODE`, and `runAction(title, label)` and `click(title)` act as the
user would. A component that takes a `NotificationGroup` can be given `new FakeNotifications().group(id)`.
```

- [ ] **Step 2: SDK architecture and README**

In `docs/sdk-architecture.md`, replace

```
too, and `HostedPalette` carries it as `PaletteScope.inAll()`. `HostedContext.notices()`
posts an error notification in the Jasper group (`jasper.general`) until plan 3 replaces `Notices`; `platform().openInEditor` runs the application's
`ConfigEditor` on the plugin's executor and reports failure as a notice, and `platform().browse`
does the same with `platform.Browser` after refusing anything but an http or https address.
```

with

```
too, and `HostedPalette` carries it as `PaletteScope.inAll()`. `platform().openInEditor` runs the application's
`ConfigEditor` on the plugin's executor and posts a failure in the plugin's notification group, and
`platform().browse` does the same with `platform.Browser` after refusing anything but an http or https address.
```

and insert before `## Plugins manager, install and restart`:

```markdown
## Notifications (SDK 0.14.0)

`PluginContext.notifications()` is `plugins.HostedNotifications`, one per context. When the context is made it
registers the plugin's default group (`<plugin-id>`, CARD) and `<plugin-id>.activities` in the application's
`NotificationCenter`; `group(GroupSpec)` registers more inside the plugin's namespace. `post` requires an open context,
turns the SDK spec into a `NotificationRequest` with the plugin id as its source, wraps every action and the body in
`Containment.run`, and posts from any thread. A spec with a cause goes to `PluginRuntime.NotificationHost.caused`,
which the application wires to `IncidentNotices.pluginNotice`: the trace is captured on the posting thread and
recorded as the plugin's incident, named after the title, and the notification gains Show more…. A handle learns its
entry's id on the EDT and expires with it; teardown expires everything the plugin posted. `PluginHost` hears
`Activities.TOPIC` and posts a running plugin's succeeded or failed activity in its activities group.
`PluginRuntime.problems()` lists what kept plugins from running at launch, and `application.PluginProblemNotices`
posts each in Plugin problems with Open Plugins. The testkit's `FakeNotifications` records the same posts, and
`PluginContractTest` holds both hosts to one contract.

```

In `jasper-sdk/README.md`:

1. In the package table's `dev.jasper.sdk.ui` row, replace

```
`SyntaxRole`, `SyntaxStyle`, `Notices`, `Platform` |
```

   with

```
`SyntaxRole`, `SyntaxStyle`, `Platform` |
```

   and add after that row:

```
| `dev.jasper.sdk.notifications` | `Notifications`, `NotificationGroup`, `GroupSpec`, `NotificationSpec`, `NotificationAction`, `NotificationHandle`, `Severity`, `DisplayMode` |
```

2. At the end of the SDK 0.11.0 paragraph, replace

```
guards too. Plugins declare `sdk = ">=0.13.0, <0.14"`.
```

   with

```
guards too. Plugins declare `sdk = ">=0.14.0, <0.15"`.
```
3. Append at the end of the file:

```markdown

## Notifications (SDK 0.14.0)

`PluginContext.notifications()` replaces `Notices`, which is gone. `group()` is the plugin's default group, named after
the plugin; `group(GroupSpec)` declares another inside the plugin's namespace. Posting is safe from any thread: `info`,
`warning` and `error` take a title and content; `error(title, cause)` and `error(title, content, cause)` carry a cause
that Jasper records as the plugin's incident, with Show more…; `post(NotificationSpec)` adds actions, a default
action, an icon or a window. Jasper posts a succeeded or failed activity in `<plugin-id>.activities` by itself.
`FakePluginHost.notifications()` records posts as `group|SEVERITY|title|content[|Cause]`, lists declared groups and
runs an action (`runAction`) or the body (`click`); `new FakeNotifications().group(id)` records for a component under
test. A plugin built for 0.13 that called `notices()` moves to `notifications()` and declares
`sdk = ">=0.14.0, <0.15"`.
```

- [ ] **Step 3: The user's guide and maintenance recipes**

In `docs/notifications.md`, replace the Groups table

```
| Group | What it covers | Shows as |
|---|---|---|
| Jasper | errors and results of Jasper's own commands: a shell that could not start, a theme that could not be applied, Migrate Config | card |
| Configuration | problems in `config.toml`, themes and plugin settings files | sticky card |
| Plugin problems | a plugin that failed while Jasper asked it to do something | card |
| Internal errors | unexpected errors in Jasper itself | card |
```

with

```
| Group | What it covers | Shows as |
|---|---|---|
| Jasper | errors and results of Jasper's own commands: a shell that could not start, a theme that could not be applied, Migrate Config | card |
| Configuration | problems in `config.toml`, themes and plugin settings files | sticky card |
| Plugin problems | a plugin that failed while Jasper asked it to do something, or that could not be loaded, started or read at launch | card |
| Internal errors | unexpected errors in Jasper itself | card |
| *Plugin name*, one per plugin | the plugin's own messages, such as a host that could not be saved | card |
| *Plugin name* activities | a plugin's background work that finished or failed, such as a sync or an upload | card |
| Tunnels | Remote's tunnel failures | card |
```

and insert before `## The Errors window`:

```markdown
## Plugin notifications

Plugins post notifications in their own groups, so you can tell them apart from Jasper's. When an error a plugin
reports has an exception behind it, its notification offers **Show more…**, which opens the Errors window at it. When
a plugin's background work finishes or fails, Jasper posts the outcome in the plugin's activities group; clicking it
runs the plugin's action for that work, if it has one.

At launch, each plugin that could not be loaded, was not started (it needs another version of Jasper, or a plugin it
requires is missing) or could not be read is reported in Plugin problems with **Open Plugins**, which opens
Settings › Plugins. A plugin that failed while starting is reported once, by its incident's notification.

```

In `docs/app-maintenance.md`, insert before `## Platform integration`:

```markdown
## A plugin's notifications

Plugins post through `PluginContext.notifications()`, which `plugins.HostedNotifications` implements over the
application's `NotificationCenter` ([design](superpowers/specs/2026-10-02-jasper-notifications-design.md)). The
runtime gets a `PluginRuntime.NotificationHost`: the center; `caused`, for a plugin's notification with a cause, which
`JasperApplication` hands to `IncidentNotices.pluginNotice`; and `problem`, for a plugin problem of the runtime's own,
such as a settings file that cannot be opened. `PluginHost` posts activity outcomes, and
`application.PluginProblemNotices` posts `PluginRuntime.problems()` after `PluginRuntime.start`. Change the SDK side
in `PluginContractTest` first, so the testkit's `FakeNotifications` and the application keep one contract.

```

- [ ] **Step 4: Run the full check**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL: every test, the four architecture gates and doclint. If `ExFilesTest.bufferSelectsATabByNumberOrName`
(Editor) fails, it is the known Mac-only failure when `java.io.tmpdir` contains "zz", which predates this branch; record
it. `ListenerTest.acceptsOnLocalhostUntilClosed` (Remote) is a known flake under full-suite load; rerun
`./gradlew :jasper-plugin-remote:test --tests '*ListenerTest*'` alone if it fails.

Count the results:

```bash
python3 - <<'PY'
import pathlib, re
tests = failures = errors = skipped = 0
for report in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    head = report.read_text(encoding="utf-8")[:2000]
    match = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', head)
    if match:
        t, s, f, e = map(int, match.groups())
        tests += t; skipped += s; failures += f; errors += e
print(f"{tests} tests, {failures} failures, {errors} errors, {skipped} skipped")
PY
```

Then run the source hygiene check from `AGENTS.md` (expected: no output) and `git diff --check c02673cd` (expected: no
output).

- [ ] **Step 5: Record the outcome**

In `docs/STATUS.md`:

1. In the plan 2 entry, replace

```
- **Notifications plan 2: incidents and the Errors window, reviewed on branch `claude/notifications-2`; awaiting the
  user's GUI checks and merge decision** (2026-10-02 to 2026-10-03; plan
```

   with

```
- **Notifications plan 2: incidents and the Errors window, merged to local `main` (`c02673cd`); awaiting the user's
  GUI checks** (2026-10-02 to 2026-10-03; plan
```
2. Add a new first entry under `## Current state — 2026-10-03`, using the counts from Step 4:

```markdown
- **Notifications plan 3: SDK notifications, reviewed on branch `claude/notifications-3`; awaiting the user's GUI
  checks and merge decision** (2026-10-03; plan `docs/superpowers/plans/2026-10-03-notifications-3-sdk.md`,
  [guide](notifications.md#plugin-notifications)): SDK 0.14.0 adds `dev.jasper.sdk.notifications`
  (`PluginContext.notifications()`, groups, specs with actions, a default action, an icon, a window and a cause) and
  removes `Notices`; every bundled plugin declares `>=0.14.0, <0.15`. `plugins.HostedNotifications` posts into the
  center with the plugin id as source and contained links; a cause becomes the plugin's incident with Show more…
  (`IncidentNotices.pluginNotice`); a plugin's notifications expire when it stops. `PluginHost` posts succeeded and
  failed activities in `<plugin-id>.activities`. After launch, `PluginProblemNotices` posts each load failure, skipped
  plugin and unreadable plugin directory in Plugin problems with Open Plugins. The model now strips bidi controls,
  treats invisible-only text as blank, fits poster icons to 16 px and bounds group names. Snippets, Editor, File
  Server, Vault and Remote post titled notifications with their causes; Remote declares `dev.jasper.remote.tunnels`.
  Spec interpretations: see the plan's list (1 to 17). `./gradlew check --continue`: the counts from Task 13's run.
  No GUI launched. GUI checks for the user: a plugin error card with Show more… opening the Errors window; a Remote
  upload that fails, posted in "Remote activities"; a plugin directory without `plugin.toml` in the user plugins
  folder, reported at launch with Open Plugins.
```

   Replace "the counts from Task 13's run" with the four numbers the count printed, as
   `**N tests, N failures, N errors, N skipped**`, and name any failure with its cause.

In `docs/superpowers/specs/2026-10-02-jasper-notifications-design.md`, replace

```
(incidents)](../plans/2026-10-02-notifications-2-incidents.md) is implemented on branch `claude/notifications-2`.
```

with

```
(incidents)](../plans/2026-10-02-notifications-2-incidents.md) is merged to main (`c02673cd`). [Plan 3
(SDK)](../plans/2026-10-03-notifications-3-sdk.md) is implemented on branch `claude/notifications-3`.
```

In this plan, replace the `**Status:** Not started. …` paragraph with one that says the plan is implemented on branch
`claude/notifications-3`, lists every deviation from the plan text made during execution (each with its task and
reason) and the final review's fixes, and gives the Step 4 counts.

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*'`
Expected: PASS: every link and anchor resolves, including `notifications.md#plugin-notifications`.

- [ ] **Step 6: Commit**

```bash
git add docs jasper-sdk/README.md
git commit -m "docs(notifications): plugin notifications, SDK 0.14.0 and plan 3's status" -m "Plugin authoring, SDK architecture, the SDK README, the user's guide and the maintenance recipes describe notifications for plugins; STATUS, the spec and the plan record plan 3." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
