package dev.jasper.vault.ui;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.testing.FakeNotifications;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.UiAnchor;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.Windows;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.SshKey;
import dev.jasper.vault.model.Grant;
import dev.jasper.vault.service.VaultService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.vault.testsupport.KitViews.*;
import static org.assertj.core.api.Assertions.*;

/** The manager, a modeless dialog over the main window (the user's 2026-10-19 decision), with its sheets and inline unlock. */
class VaultManagerDialogTest {
    @TempDir Path directory;
    static final PluginInfo INFO = new PluginInfo("dev.jasper.vault", "Vault", "0.1.0", Set.of());
    static final String VAULT = "dev.jasper.vault", TITLE = "Credential Vault";
    static final VaultManagerDialog.Status STATUS = new VaultManagerDialog.Status("test", "5 min", "/synthetic/vault.jv");

    /**
     * The dialogs a context's plugin asks for: each spec, and each {@code show} and {@code toFront} as {@code call|title};
     * a title in {@code refused} is refused, as a host refuses a dialog it cannot make.
     */
    record Dialogs(List<DialogSpec> specs, List<String> calls, Set<String> refused) {
        Dialogs() { this(new ArrayList<>(), new ArrayList<>(), new java.util.HashSet<>()); }

        /** {@code real}, whose dialogs are recorded here. */
        PluginContext record(PluginContext real) {
            Windows windows = real.windows();
            Windows recorded = proxy(Windows.class, (method, args) -> {
                if (method.getName().equals("dialog") && refused.contains(((DialogSpec) args[0]).title())) {
                    specs.add((DialogSpec) args[0]);
                    throw new IllegalStateException("The host refused the dialog");
                }
                Object result = method.invoke(windows, args);
                if (!method.getName().equals("dialog")) return result;
                var spec = (DialogSpec) args[0]; specs.add(spec);
                var dialog = (PluginDialog) result;
                return proxy(PluginDialog.class, (call, values) -> {
                    if (call.getName().equals("show") || call.getName().equals("toFront")) calls.add(call.getName() + "|" + spec.title());
                    return call.invoke(dialog, values);
                });
            });
            return proxy(PluginContext.class, (method, args) -> method.getName().equals("windows") ? recorded : method.invoke(real, args));
        }

        /** The last dialog asked for with {@code title}. */
        DialogSpec spec(String title) { return specs.stream().filter(spec -> spec.title().equals(title)).reduce((first, last) -> last).orElseThrow(); }
    }

    interface Handler { Object handle(Method method, Object[] args) throws ReflectiveOperationException; }

    /** A {@code type} answering through {@code handler}, with an identity of its own. */
    static <T> T proxy(Class<T> type, Handler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "equals" -> self == args[0];
                case "hashCode" -> System.identityHashCode(self);
                default -> type.getSimpleName();
            };
            try { return handler.handle(method, args); } catch (InvocationTargetException failure) { throw failure.getCause(); }
        }));
    }

    static VaultService service(VaultUiFixture f, PluginContext context, UUID main, java.util.function.Consumer<dev.jasper.vault.service.UnlockPrompt> unlock) {
        return new VaultService(f.lock, () -> context.terminals().window(main), unlock, p -> { }, p -> { }, new FakeNotifications().group(VAULT));
    }

    @Test void theManagerIsOneModelessDialogOverTheWindowThatAskedAndNeverATab() throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
                var dialogs = new Dialogs();
                var context = dialogs.record(host.start(INFO, Set.of(), Set.of(), c -> { }));
                UUID main = host.addTerminalWindow(), child = host.addTerminalWindow();
                var manager = new VaultManagerDialog(context, f.lock, f.manager, service(f, context, main, p -> { }), () -> STATUS, () -> { });
                UUID id = UUID.randomUUID();
                f.manager.saveNote(new Note(id, "Recovery", "secret".toCharArray(), Instant.EPOCH)); f.drain();
                manager.show(context.terminals().window(child).orElseThrow(), Optional.empty());
                var spec = dialogs.spec(TITLE);
                assertThat(spec.owner()).as("the window that asked owns it").isEqualTo(context.terminals().window(child).orElseThrow());
                assertThat(spec.modal()).as("it floats over its window without blocking it").isFalse();
                assertThat(spec.presentation()).isEqualTo(DialogSpec.Presentation.CENTERED);
                assertThat(host.windows()).containsExactly("dialog|Credential Vault|true");
                assertThat(host.windowView(VAULT, TITLE)).containsSame(fake(manager.panel.view()));
                assertThat(host.contentTabs()).as("no tab").isEmpty();
                assertThat(context.terminals().windows()).as("no window holds it as a tab").allSatisfy(window -> assertThat(window.tabs()).isEmpty());
                manager.show(context.terminals().window(main).orElseThrow(), Optional.of(id));
                assertThat(dialogs.specs()).as("one dialog").hasSize(1);
                assertThat(dialogs.calls()).as("the second request raises the open dialog").containsExactly("show|Credential Vault", "toFront|Credential Vault");
                assertThat(host.openRequests()).as("each request first raises the dialog's window, restoring it when minimized")
                    .containsExactly("front|" + child, "front|" + child);
                assertThat(manager.panel.selected()).contains(id);
                manager.close();
                assertThat(host.windows()).isEmpty();
                assertThat(host.failures()).isEmpty();
            }
        });
    }

    @Test void doneAndEscapeCloseTheManagerAndClosingCancelsItsUnlockPrompt() throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
                var dialogs = new Dialogs();
                var context = dialogs.record(host.start(INFO, Set.of(), Set.of(), c -> { }));
                UUID main = host.addTerminalWindow(); WindowHandle owner = context.terminals().window(main).orElseThrow();
                var controller = new AtomicReference<VaultManagerDialog>();
                var service = service(f, context, main, prompt -> controller.get().showUnlock(prompt));
                var manager = new VaultManagerDialog(context, f.lock, f.manager, service, () -> STATUS, () -> { });
                controller.set(manager);
                manager.show(owner, Optional.empty());
                var done = manager.panel.view();
                assertThat(button(done, ManagerPanel.OKAY).label()).isEqualTo("Done");
                press(done, button(done, ManagerPanel.OKAY).action());
                assertThat(manager.isOpen()).as("Done closes it").isFalse();
                assertThat(host.windows()).isEmpty();
                assertThat(closed(done)).isTrue();
                manager.show(owner, Optional.empty());
                var escape = manager.panel.view();
                press(escape, node(escape, ManagerPanel.ROOT, Nodes.Form.class).cancelAction());
                assertThat(manager.isOpen()).as("Escape closes it").isFalse();
                assertThat(closed(escape)).isTrue();
                manager.show(owner, Optional.empty()); f.manager.invalidate(); f.lock.lock(); manager.changed();
                var prompting = manager.panel.view();
                int raised = dialogs.calls().size(), fronted = host.openRequests().size();
                var waiting = service.requestUnlock(owner);
                assertThat(manager.passwordPanel).isNotNull();
                assertThat(dialogs.calls().subList(raised, dialogs.calls().size())).as("the prompt raises the manager").containsExactly("toFront|Credential Vault");
                assertThat(host.openRequests()).as("but not its window, which would take the keyboard into a terminal").hasSize(fronted);
                type(prompting, manager.passwordPanel.password, "typed-then-closed");
                assertThat(host.requestClose("dialog")).as("its close button").isTrue();
                assertThat(closed(prompting)).as("closing disposes the view").isTrue();
                assertThat(manager.passwordPanel).isNull();
                assertThat(waiting).as("closing cancels the prompt").isCompletedWithValue(false);
                assertThat(host.failures()).isEmpty();
                assertThat(context.kit()).isInstanceOfSatisfying(dev.jasper.sdk.testing.FakeKit.class, kit -> assertThat(kit.failures()).isEmpty());
            }
        });
    }

    @Test void theSheetsAreOwnedByTheManagersDialog() throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
                var dialogs = new Dialogs();
                var context = dialogs.record(host.start(INFO, Set.of(), Set.of(), c -> { }));
                UUID main = host.addTerminalWindow();
                var manager = new VaultManagerDialog(context, f.lock, f.manager, service(f, context, main, p -> { }), () -> STATUS, () -> { });
                manager.show(context.terminals().window(main).orElseThrow(), Optional.empty());
                var sheets = new java.util.LinkedHashMap<String, Runnable>();
                sheets.put("Add Login", () -> manager.add(VaultManager.Type.LOGIN));
                sheets.put("Change Master Password", manager::changePassword);
                sheets.put("Generate SSH Key", () -> manager.dialog("Generate SSH Key", close -> new KeyGeneratorForm(context.kit(),
                    request -> java.util.concurrent.CompletableFuture.completedFuture(null), close)));
                sheets.forEach((title, open) -> {
                    open.run();
                    var spec = dialogs.spec(title);
                    assertThat(spec.presentation()).as(title).isEqualTo(DialogSpec.Presentation.ATTACHED_SHEET);
                    assertThat(spec.owner()).as("%s hangs from an anchor on the manager's view", title).isInstanceOf(UiAnchor.class);
                    var anchor = (UiAnchor) spec.owner();
                    assertThat(anchor.isOpen()).isTrue();
                    assertThat(host.windows()).containsExactly("dialog|Credential Vault|true", "dialog|" + title + "|true");
                    press(host.windowView(VAULT, title).orElseThrow(), EditorForm.CANCEL);
                    assertThat(host.windows()).as(title + " closed").containsExactly("dialog|Credential Vault|true");
                    assertThat(anchor.isOpen()).as("its anchor goes with it").isFalse();
                });
                manager.add(VaultManager.Type.NOTE);
                var anchor = (UiAnchor) dialogs.spec("Add Secure Note").owner();
                assertThat(host.requestClose("dialog")).as("the manager's close button").isTrue();
                assertThat(anchor.isOpen()).isFalse();
                assertThat(host.windows()).as("the sheet closes with the manager").isEmpty();
                assertThat(host.failures()).isEmpty();
            }
        });
    }

    /** A sheet the host refuses to make leaves no anchor on the manager's Done behind. */
    @Test void aRefusedSheetClosesItsAnchor() throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
                var dialogs = new Dialogs();
                var context = dialogs.record(host.start(INFO, Set.of(), Set.of(), c -> { }));
                UUID main = host.addTerminalWindow();
                var manager = new VaultManagerDialog(context, f.lock, f.manager, service(f, context, main, p -> { }), () -> STATUS, () -> { });
                manager.show(context.terminals().window(main).orElseThrow(), Optional.empty());
                dialogs.refused().add("Add Login");
                assertThatThrownBy(() -> manager.add(VaultManager.Type.LOGIN)).hasMessage("The host refused the dialog");
                assertThat(dialogs.spec("Add Login").owner()).isInstanceOfSatisfying(UiAnchor.class,
                    anchor -> assertThat(anchor.isOpen()).as("its anchor is closed").isFalse());
                assertThat(host.windows()).containsExactly("dialog|Credential Vault|true");
                manager.close();
            }
        });
    }

    @Test void retiredInlineUnlockClearsItsSecretWithoutCancellingTheSurvivingRequest() throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
                var context = host.start(INFO, Set.of(), Set.of(), c -> {});
                host.addTerminalWindow(); var terminal = context.terminals().windows().getFirst();
                boolean[] open = {true};
                var owner = new WindowHandle() {
                    public UUID id() { return terminal.id(); }
                    public java.util.List<dev.jasper.sdk.terminal.TabHandle> tabs() { return terminal.tabs(); }
                    public Optional<dev.jasper.sdk.terminal.TabHandle> activeTab() { return terminal.activeTab(); }
                    public boolean isActive() { return true; }
                    public boolean isOpen() { return open[0]; }
                    public void toFront() {}
                };
                var windowRef = new AtomicReference<VaultManagerDialog>();
                var represented = new AtomicReference<dev.jasper.vault.service.UnlockPrompt>();
                var service = new VaultService(f.lock, () -> Optional.of(owner), prompt -> {
                    if (prompt.owner() == owner) windowRef.get().showUnlock(prompt);
                    else represented.set(prompt);
                }, p -> {}, p -> {}, new FakeNotifications().group("dev.jasper.vault"));
                var window = new VaultManagerDialog(context, f.lock, f.manager, service,
                    () -> new VaultManagerDialog.Status("test", "5 min", "/synthetic/vault.jv"), () -> {});
                windowRef.set(window); window.show(terminal, Optional.empty()); f.lock.lock(); window.changed();
                var first = service.requestUnlock(owner); var shown = window.passwordPanel;
                type(window.panel.view(), shown.password, "retired-secret");
                var second = service.requestUnlock(terminal);
                open[0] = false; first.cancel(false);
                assertThat(represented.get()).isNotNull();
                assertThat(window.passwordPanel).isNull();
                assertThat(find(window.panel.view(), shown.password)).isEmpty();
                window.close();
                assertThat(second).isNotDone();
                represented.get().submit("test-password".toCharArray()); f.drain();
                assertThat(second).isCompletedWithValue(true);
                assertThat(host.failures()).isEmpty();
            }
        });
    }

    @Test void anchoredAndInlineJoinOrdersPreserveTheLiveWaiterEvenDuringUnlock() throws Exception {
        for(boolean anchoredFirst:new boolean[]{false,true})for(boolean pending:new boolean[]{false,true}) edt(()->{
            try(var f=new VaultUiFixture(directory.resolve(anchoredFirst+"-"+pending));var host=new FakePluginHost()) {
                var context=host.start(INFO,Set.of(),Set.of(),c->{});host.addTerminalWindow();var terminal=context.terminals().windows().getFirst();
                class Anchor implements dev.jasper.sdk.ui.UiAnchor {
                    boolean open=true;final java.util.List<Runnable> listeners=new java.util.ArrayList<>();
                    public boolean isOpen(){return open;} public dev.jasper.sdk.Subscription onClosed(Runnable action){listeners.add(action);return ()->listeners.remove(action);}
                    public void close(){open=false;java.util.List.copyOf(listeners).forEach(Runnable::run);}
                }
                var anchor=new Anchor();var windowRef=new AtomicReference<VaultManagerDialog>();
                var displayed=new AtomicReference<PasswordPanel>();var promptRef=new AtomicReference<dev.jasper.vault.service.UnlockPrompt>();
                var service=new VaultService(f.lock,()->Optional.of(terminal),prompt->{
                    promptRef.set(prompt);
                    if(!prompt.anchored()) {windowRef.get().showUnlock(prompt);displayed.set(windowRef.get().passwordPanel);}
                    else {var panel=PasswordPanel.unlock(context.kit(),prompt);displayed.set(panel);prompt.onPresentationRetired(panel::close);}
                },p->{},p->{},new FakeNotifications().group("dev.jasper.vault"));
                service.anchoredPicker(p->{throw new AssertionError("retired picker reopened");},a->{});
                var window=new VaultManagerDialog(context,f.lock,f.manager,service,()->new VaultManagerDialog.Status("test","off","/synthetic/vault.jv"),()->{});
                windowRef.set(window);window.show(terminal,Optional.empty());f.lock.lock();window.changed();
                var consumer=service.forConsumer(new PluginInfo("test.consumer","Consumer","1",Set.of()));
                java.util.concurrent.CompletableFuture<Boolean> survivor;
                java.util.concurrent.CompletableFuture<dev.jasper.vault.api.CredentialPickResult> picked;
                if(anchoredFirst) {picked=consumer.pick(terminal,new dev.jasper.vault.api.CredentialPickRequest(anchor,Optional.empty(),true));survivor=service.requestUnlock(terminal);}
                else {survivor=service.requestUnlock(terminal);picked=consumer.pick(terminal,new dev.jasper.vault.api.CredentialPickRequest(anchor,Optional.empty(),true));}
                var old=displayed.get();var oldView=anchoredFirst?old.view():window.panel.view();type(oldView,old.password,"test-password");
                if(pending) {press(oldView,old.submit);assertThat(promptRef.get().busy()).isTrue();}
                anchor.close();assertThat(picked.join()).isInstanceOf(dev.jasper.vault.api.CredentialPickResult.Cancelled.class);assertThat(survivor).isNotDone();
                assertThat(window.passwordPanel).isNotNull();
                if(anchoredFirst) assertThat(closed(oldView)).isTrue();
                else if(!pending) assertThat(secret(window.panel.view(),old.password)).isEqualTo("test-password");
                if(!pending) promptRef.get().submit("test-password".toCharArray());
                f.drain();assertThat(survivor).isCompletedWithValue(true);window.close();assertThat(host.failures()).isEmpty();
            }
        });
    }

    @Test void editorBrowseUsesTheSdkDialogChooser() throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
                var context = host.start(INFO, Set.of(), Set.of(), c -> { });
                host.addTerminalWindow();
                var owner = context.terminals().windows().getFirst();
                var service = new VaultService(f.lock, () -> Optional.of(owner), p -> { }, p -> { }, p -> { }, new FakeNotifications().group("dev.jasper.vault"));
                var window = new VaultManagerDialog(context, f.lock, f.manager, service,
                    () -> new VaultManagerDialog.Status("test", "5 min", "/synthetic/vault.jv"), () -> { });
                window.show(owner, Optional.empty());
                var editor = new AtomicReference<EntryEditor>();
                window.dialog("Add SSH Key", dismiss -> {
                    var form = EntryEditor.key(context.kit(), null, value -> java.util.concurrent.CompletableFuture.completedFuture(null), dismiss);
                    editor.set(form); return form;
                });
                assertThat(host.windowView("dev.jasper.vault", "Add SSH Key")).containsSame(fake(editor.get().view()));
                Path selected = directory.resolve("key with spaces");
                host.queueFileSelection(Optional.of(selected));
                press(editor.get().view(), EditorForm.BROWSE + EntryEditor.PRIVATE_PATH);
                assertThat(node(editor.get().view(), EntryEditor.PRIVATE_PATH, Nodes.Field.class).value()).isEqualTo(selected.toString());
                window.close();
                assertThat(closed(editor.get().view())).isTrue();
            }
        });
    }

    @Test void singletonSelectionDetailsAndCloseAreHeadless() throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
                PluginContext context = host.start(INFO, Set.of(), Set.of(), c -> { });
                host.addTerminalWindow();
                WindowHandle owner = context.terminals().windows().getFirst();
                var service = new VaultService(f.lock, () -> Optional.of(owner), p -> { }, p -> { }, p -> { }, new FakeNotifications().group("dev.jasper.vault"));
                var window = new VaultManagerDialog(context, f.lock, f.manager, service,
                    () -> new VaultManagerDialog.Status("test", "5 min", "/synthetic/vault.jv"), () -> { });
                UUID id = UUID.randomUUID();
                f.manager.saveNote(new Note(id, "Recovery", "line one\nline two".toCharArray(), Instant.EPOCH)); f.drain();
                window.show(owner, Optional.of(id)); window.show(owner, Optional.of(id));
                assertThat(host.windows()).containsExactly("dialog|Credential Vault|true");
                var view = host.windowView("dev.jasper.vault", "Credential Vault").orElseThrow();
                assertThat(view).isSameAs(fake(window.panel.view()));
                assertThat(window.panel.selected()).contains(id);
                assertThat(node(view, ManagerPanel.ROWS, Nodes.ItemList.class).selectedIds()).containsExactly(id.toString());
                assertThat(node(view, "row." + id + ".name", Nodes.Text.class).text()).isEqualTo("Recovery");
                assertThat(view.spec().toString()).doesNotContain("line one");
                assertThat(host.requestClose("dialog")).isTrue();
                assertThat(window.isOpen()).isFalse();
                assertThat(window.panel.visibleRows()).isEmpty();
                assertThat(closed(view)).isTrue();
            }
        });
    }

    @Test void copyPublicKeyConfirmsWithACardThatHasNoClearingLine() throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
                PluginContext context = host.start(INFO, Set.of(), Set.of(), c -> { });
                host.addTerminalWindow();
                WindowHandle owner = context.terminals().windows().getFirst();
                var service = new VaultService(f.lock, () -> Optional.of(owner), p -> { }, p -> { }, p -> { }, new FakeNotifications().group("dev.jasper.vault"));
                var window = new VaultManagerDialog(context, f.lock, f.manager, service,
                    () -> new VaultManagerDialog.Status("test", "5 min", "/synthetic/vault.jv"), () -> { });
                Path pub = java.nio.file.Files.writeString(directory.resolve("laptop.pub"), "ssh-ed25519 AAAAfixture laptop\n");
                UUID id = UUID.randomUUID();
                f.lock.vault().keys().add(new SshKey(id, "laptop", "ssh-ed25519", "SHA256:fixture", "", directory.resolve("laptop"), pub, Instant.EPOCH));
                window.show(owner, Optional.of(id));
                press(window.panel.view(), ManagerPanel.COPY_PUBLIC);
                f.drain();
                assertThat(host.clipboard().copies()).as("a public key is an ordinary copy").containsExactly("copy|dev.jasper.vault|ssh-ed25519 AAAAfixture laptop\n");
                assertThat(host.notifications().lines()).containsExactly("dev.jasper.vault.copies|INFO|Copied the public key for laptop|");
                window.close();
            }
        });
    }

    @Test void lockClearsDetailsAndEditorsWithoutCreatingAnUnlockWaiter() throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
                PluginContext context = host.start(INFO, Set.of(), Set.of(), c -> { });
                host.addTerminalWindow(); WindowHandle owner = context.terminals().windows().getFirst();
                int[] prompts = {0};
                var controller = new AtomicReference<VaultManagerDialog>();
                var service = new VaultService(f.lock, () -> Optional.of(owner), prompt -> {
                    prompts[0]++; assertThat(controller.get().showUnlock(prompt)).isTrue();
                }, p -> { }, p -> { }, new FakeNotifications().group("dev.jasper.vault"));
                var window = new VaultManagerDialog(context, f.lock, f.manager, service,
                    () -> new VaultManagerDialog.Status("test", "5 min", "/synthetic/vault.jv"), () -> { });
                controller.set(window);
                UUID id = UUID.randomUUID();
                f.manager.saveNote(new Note(id, "Recovery", "secret".toCharArray(), Instant.EPOCH)); f.drain();
                window.show(owner, Optional.of(id));
                var editor = new AtomicReference<EntryEditor>();
                window.dialog("Edit Secure Note", dismiss -> {
                    EntryEditor form = EntryEditor.note(context.kit(), (Note) f.manager.entry(id).orElseThrow(), f.manager::saveNote, dismiss);
                    editor.set(form); return form;
                });
                assertThat(secret(editor.get().view(), EntryEditor.NOTE)).isEqualTo("secret");
                f.manager.invalidate(); f.lock.lock(); window.changed();
                var view = window.panel.view();
                assertThat(window.panel.visibleRows()).isEmpty();
                assertThat(closed(editor.get().view())).as("locking closes editors and erases their drafts").isTrue();
                assertThat(host.windows()).as("the editor closed").containsExactly("dialog|Credential Vault|true");
                assertThat(prompts[0]).isZero();
                press(view, ManagerPanel.UNLOCK);
                assertThat(prompts[0]).isEqualTo(1);
                assertThat(window.passwordPanel).isNotNull();
                var root = node(view, ManagerPanel.ROOT, Nodes.Form.class);
                assertThat(root.actions()).as("Unlock is the window's default, so Enter on a footer button unlocks as at baseline")
                    .filteredOn(b -> b.kind() == Nodes.ButtonKind.PRIMARY).singleElement()
                    .satisfies(b -> { assertThat(b.label()).isEqualTo("Unlock"); assertThat(b.action()).isEqualTo(window.passwordPanel.submit); });
                assertThat(button(view, ManagerPanel.OKAY).kind()).isEqualTo(Nodes.ButtonKind.SECONDARY);
                assertThat(node(view, window.passwordPanel.password, Nodes.Secret.class).submitAction()).isEqualTo(window.passwordPanel.submit);
                assertThat(root.cancelAction()).as("Escape cancels the prompt first").isEqualTo(window.passwordPanel.cancel);
                type(view, window.passwordPanel.password, "test-password");
                assertThat(view.spec().toString()).doesNotContain("test-password");
                press(view, window.passwordPanel.submit); f.drain();
                assertThat(f.lock.state()).isEqualTo(dev.jasper.vault.api.LockState.UNLOCKED);
                assertThat(window.passwordPanel).isNull();
                assertThat(find(view, ManagerPanel.UNLOCK_PREFIX + "password")).isEmpty();
                assertThat(button(view, ManagerPanel.OKAY).kind()).as("Done is the default again").isEqualTo(Nodes.ButtonKind.PRIMARY);
                assertThat(node(view, ManagerPanel.ROOT, Nodes.Form.class).cancelAction()).as("Escape closes it again").isEqualTo(ManagerPanel.CLOSE);
                window.close();
            }
        });
    }

    @Test void cancellingTheInlineUnlockErasesItsFieldAndReturnsToTheLockedState() throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
                PluginContext context = host.start(INFO, Set.of(), Set.of(), c -> { });
                host.addTerminalWindow(); WindowHandle owner = context.terminals().windows().getFirst();
                var controller = new AtomicReference<VaultManagerDialog>();
                var service = new VaultService(f.lock, () -> Optional.of(owner), prompt -> controller.get().showUnlock(prompt),
                    p -> { }, p -> { }, new FakeNotifications().group("dev.jasper.vault"));
                var window = new VaultManagerDialog(context, f.lock, f.manager, service,
                    () -> new VaultManagerDialog.Status("test", "5 min", "/synthetic/vault.jv"), () -> { });
                controller.set(window);
                window.show(owner, Optional.empty()); f.manager.invalidate(); f.lock.lock(); window.changed();
                var view = window.panel.view();
                press(view, ManagerPanel.UNLOCK);
                var shown = window.passwordPanel;
                type(view, shown.password, "typed-then-cancelled");
                press(view, shown.cancel);
                assertThat(window.passwordPanel).isNull();
                assertThat(find(view, shown.password)).as("the field and its storage are gone").isEmpty();
                assertThat(node(view, ManagerPanel.LOCKED, Nodes.Empty.class).title()).isEqualTo("Vault locked");
                assertThat(f.lock.state()).isEqualTo(dev.jasper.vault.api.LockState.LOCKED);
                assertThat(window.isOpen()).as("Escape on the prompt leaves the manager open").isTrue();
                assertThat(host.failures()).isEmpty();
                assertThat(context.kit()).isInstanceOfSatisfying(dev.jasper.sdk.testing.FakeKit.class, kit -> assertThat(kit.failures()).isEmpty());
                window.close();
            }
        });
    }

    /** The passive Unlock asks over the dialog's owner, the window that asked for the manager. */
    @Test void thePassiveUnlockAsksOverTheDialogsWindow() throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
                PluginContext context = host.start(INFO, Set.of(), Set.of(), c -> { });
                host.addTerminalWindow();
                UUID child = host.addTerminalWindow();
                var owners = new java.util.ArrayList<UUID>();
                var controller = new AtomicReference<VaultManagerDialog>();
                var service = service(f, context, child, prompt -> { owners.add(prompt.owner().id()); controller.get().showUnlock(prompt); });
                var window = new VaultManagerDialog(context, f.lock, f.manager, service, () -> STATUS, () -> { });
                controller.set(window);
                window.show(context.terminals().window(child).orElseThrow(), Optional.empty());
                f.manager.invalidate(); f.lock.lock(); window.changed();
                press(window.panel.view(), ManagerPanel.UNLOCK);
                assertThat(owners).as("the prompt's owner").containsExactly(child);
                window.close();
                assertThat(host.failures()).isEmpty();
            }
        });
    }

    @Test void grantsShowCredentialNamesAndRevokeUpdatesTheList() throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
                PluginContext context = host.start(INFO, Set.of(), Set.of(), c -> { });
                host.addTerminalWindow(); WindowHandle owner = context.terminals().windows().getFirst();
                var service = new VaultService(f.lock, () -> Optional.of(owner), p -> { }, p -> { }, p -> { }, new FakeNotifications().group("dev.jasper.vault"));
                var window = new VaultManagerDialog(context, f.lock, f.manager, service,
                    () -> new VaultManagerDialog.Status("test", "5 min", "/synthetic/vault.jv"), () -> { });
                UUID id = UUID.randomUUID();
                f.manager.saveAccount(VaultManagerTest.account(id, "secret".toCharArray())); f.drain();
                Grant grant = new Grant("dev.jasper.ssh", id); f.lock.vault().grants().add(grant);
                window.show(owner, Optional.empty());
                var view = window.panel.view();
                assertThat(window.panel.grantLabel(grant)).isEqualTo("dev.jasper.ssh — Production");
                window.showGrants();
                assertThat(node(view, "grant-0.label", Nodes.Text.class).text()).isEqualTo("dev.jasper.ssh — Production");
                assertThat(button(view, ManagerPanel.REVOKE).state().enabled()).isFalse();
                emit(view, new UiEvent.Selected(ManagerPanel.GRANTS, Set.of("grant-0")));
                press(view, ManagerPanel.REVOKE); f.drain(); window.changed();
                assertThat(node(view, ManagerPanel.GRANTS, Nodes.ItemList.class).items()).isEmpty();
                press(view, ManagerPanel.BACK);
                assertThat(find(view, ManagerPanel.ROWS)).isPresent();
                window.close();
            }
        });
    }
}
