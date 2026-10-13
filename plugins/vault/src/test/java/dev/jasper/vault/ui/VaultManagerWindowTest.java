package dev.jasper.vault.ui;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.testing.FakeNotifications;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.Grant;
import dev.jasper.vault.service.VaultService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.vault.testsupport.KitViews.*;
import static org.assertj.core.api.Assertions.*;

class VaultManagerWindowTest {
    @TempDir Path directory;
    static final PluginInfo INFO = new PluginInfo("dev.jasper.vault", "Vault", "0.1.0", Set.of());

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
                var windowRef = new AtomicReference<VaultManagerWindow>();
                var represented = new AtomicReference<dev.jasper.vault.service.UnlockPrompt>();
                var service = new VaultService(f.lock, () -> Optional.of(owner), prompt -> {
                    if (prompt.owner() == owner) windowRef.get().showUnlock(prompt);
                    else represented.set(prompt);
                }, p -> {}, p -> {}, new FakeNotifications().group("dev.jasper.vault"));
                var window = new VaultManagerWindow(context, f.lock, f.manager, service,
                    () -> new VaultManagerWindow.Status("test", "5 min", "/synthetic/vault.jv"), () -> {});
                windowRef.set(window); window.show(owner, Optional.empty()); f.lock.lock(); window.changed();
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
                var anchor=new Anchor();var windowRef=new AtomicReference<VaultManagerWindow>();
                var displayed=new AtomicReference<PasswordPanel>();var promptRef=new AtomicReference<dev.jasper.vault.service.UnlockPrompt>();
                var service=new VaultService(f.lock,()->Optional.of(terminal),prompt->{
                    promptRef.set(prompt);
                    if(!prompt.anchored()) {windowRef.get().showUnlock(prompt);displayed.set(windowRef.get().passwordPanel);}
                    else {var panel=PasswordPanel.unlock(context.kit(),prompt);displayed.set(panel);prompt.onPresentationRetired(panel::close);}
                },p->{},p->{},new FakeNotifications().group("dev.jasper.vault"));
                service.anchoredPicker(p->{throw new AssertionError("retired picker reopened");},a->{});
                var window=new VaultManagerWindow(context,f.lock,f.manager,service,()->new VaultManagerWindow.Status("test","off","/synthetic/vault.jv"),()->{});
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
                var window = new VaultManagerWindow(context, f.lock, f.manager, service,
                    () -> new VaultManagerWindow.Status("test", "5 min", "/synthetic/vault.jv"), () -> { });
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
                var window = new VaultManagerWindow(context, f.lock, f.manager, service,
                    () -> new VaultManagerWindow.Status("test", "5 min", "/synthetic/vault.jv"), () -> { });
                UUID id = UUID.randomUUID();
                f.manager.saveNote(new Note(id, "Recovery", "line one\nline two".toCharArray(), Instant.EPOCH)); f.drain();
                window.show(owner, Optional.of(id)); window.show(owner, Optional.of(id));
                assertThat(host.windows()).containsExactly("dev.jasper.vault.manager|Credential Vault|true");
                var view = host.windowView("dev.jasper.vault", "Credential Vault").orElseThrow();
                assertThat(view).isSameAs(fake(window.panel.view()));
                assertThat(window.panel.selected()).contains(id);
                assertThat(node(view, ManagerPanel.ROWS, Nodes.ItemList.class).selectedIds()).containsExactly(id.toString());
                assertThat(node(view, "row." + id + ".name", Nodes.Text.class).text()).isEqualTo("Recovery");
                assertThat(view.spec().toString()).doesNotContain("line one");
                assertThat(host.requestClose("dev.jasper.vault.manager")).isTrue();
                assertThat(window.isOpen()).isFalse();
                assertThat(window.panel.visibleRows()).isEmpty();
                assertThat(closed(view)).isTrue();
            }
        });
    }

    @Test void lockClearsDetailsAndEditorsWithoutCreatingAnUnlockWaiter() throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
                PluginContext context = host.start(INFO, Set.of(), Set.of(), c -> { });
                host.addTerminalWindow(); WindowHandle owner = context.terminals().windows().getFirst();
                int[] prompts = {0};
                var controller = new AtomicReference<VaultManagerWindow>();
                var service = new VaultService(f.lock, () -> Optional.of(owner), prompt -> {
                    prompts[0]++; assertThat(controller.get().showUnlock(prompt)).isTrue();
                }, p -> { }, p -> { }, new FakeNotifications().group("dev.jasper.vault"));
                var window = new VaultManagerWindow(context, f.lock, f.manager, service,
                    () -> new VaultManagerWindow.Status("test", "5 min", "/synthetic/vault.jv"), () -> { });
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
                assertThat(host.windows()).containsExactly("dev.jasper.vault.manager|Credential Vault|true");
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
                assertThat(button(view, ManagerPanel.OKAY).kind()).isEqualTo(Nodes.ButtonKind.PRIMARY);
                window.close();
            }
        });
    }

    @Test void cancellingTheInlineUnlockErasesItsFieldAndReturnsToTheLockedState() throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
                PluginContext context = host.start(INFO, Set.of(), Set.of(), c -> { });
                host.addTerminalWindow(); WindowHandle owner = context.terminals().windows().getFirst();
                var controller = new AtomicReference<VaultManagerWindow>();
                var service = new VaultService(f.lock, () -> Optional.of(owner), prompt -> controller.get().showUnlock(prompt),
                    p -> { }, p -> { }, new FakeNotifications().group("dev.jasper.vault"));
                var window = new VaultManagerWindow(context, f.lock, f.manager, service,
                    () -> new VaultManagerWindow.Status("test", "5 min", "/synthetic/vault.jv"), () -> { });
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
                // Closing the window while a prompt shows cancels it without touching the disposed view.
                press(view, ManagerPanel.UNLOCK); type(view, window.passwordPanel.password, "typed-then-closed");
                assertThat(host.requestClose(VaultManagerWindow.ID)).isTrue();
                assertThat(closed(view)).isTrue();
                assertThat(window.passwordPanel).isNull();
                assertThat(host.failures()).isEmpty();
                assertThat(context.kit()).isInstanceOfSatisfying(dev.jasper.sdk.testing.FakeKit.class, kit -> assertThat(kit.failures()).isEmpty());
            }
        });
    }

    @Test void grantsShowCredentialNamesAndRevokeUpdatesTheList() throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
                PluginContext context = host.start(INFO, Set.of(), Set.of(), c -> { });
                host.addTerminalWindow(); WindowHandle owner = context.terminals().windows().getFirst();
                var service = new VaultService(f.lock, () -> Optional.of(owner), p -> { }, p -> { }, p -> { }, new FakeNotifications().group("dev.jasper.vault"));
                var window = new VaultManagerWindow(context, f.lock, f.manager, service,
                    () -> new VaultManagerWindow.Status("test", "5 min", "/synthetic/vault.jv"), () -> { });
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
