package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.*;
import dev.jasper.sdk.testing.*;
import dev.jasper.sdk.ui.*;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.vault.api.*;
import java.util.function.Function;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ServerLifetimeTest {
    @TempDir Path root;
    @Test void authProtocolReplacementAndCloseWithdrawPendingRequests() {
        UiTestAccess.edt(() -> {
            var requests = new ArrayList<CompletableFuture<Optional<ServerEditor.Account>>>();
            var kit=new FakeKit("dev.jasper.fileserver");
            var editor=new ServerEditor(kit,List.of(),Optional.empty(),false,EnumSet.allOf(Protocol.class),List.of(),true,id->Optional.empty(),
                ()->{var f=new CompletableFuture<Optional<ServerEditor.Account>>();requests.add(f);return f;},Optional::empty,ServerEditor.KeySources.NONE,false,d->{},()->{});
            var view=(FakeKitView)editor.view();
            view.emit(new UiEvent.Selected(ServerEditor.LOGIN,Set.of("vault")));
            view.emit(new UiEvent.Action(ServerEditor.CHOOSE_ACCOUNT));
            view.emit(new UiEvent.Action(ServerEditor.CHOOSE_ACCOUNT));
            assertThat(requests.getFirst().isCancelled()).isTrue();
            view.emit(new UiEvent.Selected(ServerEditor.LOGIN,Set.of("session")));
            assertThat(requests.getLast().isCancelled()).isTrue();
            view.emit(new UiEvent.Selected(ServerEditor.LOGIN,Set.of("vault")));
            view.emit(new UiEvent.Action(ServerEditor.CHOOSE_ACCOUNT));
            view.emit(new UiEvent.Selected(ServerEditor.PROTOCOL,Set.of("TFTP")));
            assertThat(requests.getLast().isCancelled()).isTrue();
            view.emit(new UiEvent.Selected(ServerEditor.PROTOCOL,Set.of("FTP")));
            view.emit(new UiEvent.Action(ServerEditor.CHOOSE_ACCOUNT));
            editor.close();assertThat(requests.getLast().isCancelled()).isTrue();assertThat(kit.failures()).isEmpty();
        });
    }
    static <T> CompletableFuture<T> stubborn() { return new CompletableFuture<>() { @Override public boolean cancel(boolean interrupt) { return false; } }; }
    final class Mounted implements AutoCloseable {
        final FakePluginHost host = new FakePluginHost(); final PluginDialog dialog; final ServerEditor editor;
        int completed;
        Mounted(Function<CredentialPickRequest,CompletableFuture<CredentialPickResult>> pick, Function<ServerDefinition,CompletableFuture<Void>> save) {
            var contexts=new ArrayList<PluginContext>();
            host.start(new PluginInfo("test.server","Server","1.0",Set.of()),Set.of(),Set.of(),contexts::add);
            var context=contexts.getFirst();var window=host.addTerminalWindow();
            dialog=context.windows().dialog(new DialogSpec("Server",context.terminals().window(window).orElseThrow(),true));
            editor=new ServerEditor(context.kit(),List.of(),Optional.empty(),false,EnumSet.allOf(Protocol.class),List.of(),true,id->Optional.empty(),pick,
                ()->Optional.of(root),ServerEditor.KeySources.NONE,false,save,()->completed++,dialog::close);
            dialog.setContent(editor.view());dialog.onClosed(editor::close);dialog.show();
        }
        void emit(UiEvent event) { ((FakeKitView)editor.view()).emit(event); }
        void pick() { emit(new UiEvent.Action(ServerEditor.CHOOSE_ACCOUNT)); }
        void vault() { emit(new UiEvent.Selected(ServerEditor.LOGIN,Set.of("vault"))); }
        Nodes.Node node(String id) {return UiTestAccess.node((FakeKitView)editor.view(),id);}
        String face() {return ((Nodes.Choice)node(ServerEditor.CHOOSE_ACCOUNT)).face().orElseThrow().primary();}
        void valid() {emit(new UiEvent.Edited(ServerEditor.NAME,"fixture"));emit(new UiEvent.Action(ServerEditor.CHOOSE_FOLDER));}
        @Override public void close() {editor.close();host.close();}
    }
    @Test void anchoredChoicesPreserveEligibilityClearCancelAndOriginalUuid() {
        UiTestAccess.edt(()->{
            var requests=new ArrayList<CredentialPickRequest>();var futures=new ArrayList<CompletableFuture<CredentialPickResult>>();
            try(var m=new Mounted(request->{requests.add(request);var f=new CompletableFuture<CredentialPickResult>();futures.add(f);return f;},d->CompletableFuture.completedFuture(null))) {
                m.vault();m.valid();
                for(var kind:List.of(Kind.ACCOUNT_PASSWORD,Kind.ACCOUNT_KEY_AND_PASSWORD)) {
                    m.pick();var id=UUID.randomUUID();futures.getLast().complete(new CredentialPickResult.Chosen(new CredentialDescriptor(id,kind.name(),"user",kind)));
                    assertThat(m.editor.build().login()).isEqualTo(new Login.Vault(id));assertThat(m.face()).isEqualTo(kind.name());
                    m.pick();assertThat(requests.getLast().current()).contains(id);assertThat(requests.getLast().allowClear()).isTrue();
                    futures.getLast().complete(new CredentialPickResult.Cancelled());assertThat(m.editor.build().login()).isEqualTo(new Login.Vault(id));
                    for(var rejected:List.of(new CredentialDescriptor(UUID.randomUUID(),"key","user",Kind.ACCOUNT_KEY),new CredentialDescriptor(UUID.randomUUID(),"ssh","fingerprint",Kind.SSH_KEY),new CredentialDescriptor(UUID.randomUUID(),"blank"," ",Kind.ACCOUNT_PASSWORD))) {
                        m.pick();futures.getLast().complete(new CredentialPickResult.Chosen(rejected));
                        assertThat(m.editor.build().login()).isEqualTo(new Login.Vault(id));
                        assertThat(((Nodes.Text)m.node(ServerEditor.MESSAGE)).text()).contains("username and password");
                    }
                }
                m.pick();futures.getLast().complete(new CredentialPickResult.Cleared());assertThat(m.face()).isEmpty();
                assertThatThrownBy(m.editor::build).hasMessageContaining("Choose a Vault account");
            }
        });
    }
    @Test void asynchronousSaveIsSingleBusyOperationWithErrorRetryAndClosedSuppression() throws Exception {
        var saves=new ArrayList<CompletableFuture<Void>>();var owner=new Mounted[1];
        UiTestAccess.edt(()->{
            owner[0]=new Mounted(r->CompletableFuture.completedFuture(new CredentialPickResult.Cancelled()),d->{var f=ServerLifetimeTest.<Void>stubborn();saves.add(f);return f;});
            var m=owner[0];m.valid();m.emit(new UiEvent.Action(ServerEditor.SAVE));m.emit(new UiEvent.Action(ServerEditor.SAVE));
            assertThat(saves).hasSize(1);assertThat(m.node(ServerEditor.SAVE).state().busy()).isTrue();
            m.emit(new UiEvent.Edited(ServerEditor.NAME,"mutated"));assertThat(((Nodes.Field)m.node(ServerEditor.NAME)).value()).isEqualTo("fixture");
        });
        Thread.ofPlatform().start(()->saves.getFirst().completeExceptionally(new IllegalStateException("disk unavailable"))).join();
        UiTestAccess.edt(()->{
            var m=owner[0];assertThat(m.node(ServerEditor.SAVE).state().busy()).isFalse();assertThat(((Nodes.Text)m.node(ServerEditor.MESSAGE)).text()).contains("disk unavailable");
            m.emit(new UiEvent.Action(ServerEditor.SAVE));assertThat(saves).hasSize(2);m.dialog.close();
        });
        Thread.ofPlatform().start(()->saves.getLast().complete(null)).join();
        UiTestAccess.edt(()->{assertThat(owner[0].completed).isZero();owner[0].close();});
    }
    @Test void reentrantProducerInvalidationAndLateWorkerCannotReplaceNewIntent() throws Exception {
        var futures=new ArrayList<CompletableFuture<CredentialPickResult>>();var requests=new ArrayList<CredentialPickRequest>();var owner=new Mounted[1];
        UiTestAccess.edt(()->{
            owner[0]=new Mounted(r->{requests.add(r);var f=ServerLifetimeTest.<CredentialPickResult>stubborn();futures.add(f);return f;},d->CompletableFuture.completedFuture(null));
            var m=owner[0];m.vault();m.pick();m.pick();
            futures.getFirst().complete(new CredentialPickResult.Chosen(new CredentialDescriptor(UUID.randomUUID(),"stale","user",Kind.ACCOUNT_PASSWORD)));
            assertThat(m.face()).isEmpty();
            requests.getLast().anchor().close();
        });
        Thread.ofPlatform().start(()->futures.getLast().complete(new CredentialPickResult.Chosen(new CredentialDescriptor(UUID.randomUUID(),"late","user",Kind.ACCOUNT_PASSWORD)))).join();
        UiTestAccess.edt(()->{var m=owner[0];assertThat(m.face()).isEmpty();m.pick();m.close();});
        Thread.ofPlatform().start(()->futures.getLast().completeExceptionally(new IllegalStateException("late failure"))).join();
        UiTestAccess.edt(()->{});
        UiTestAccess.edt(()->{
            var m=new Mounted(r->{owner[0].emit(new UiEvent.Selected(ServerEditor.LOGIN,Set.of("session")));return CompletableFuture.completedFuture(new CredentialPickResult.Chosen(new CredentialDescriptor(UUID.randomUUID(),"reentrant","user",Kind.ACCOUNT_PASSWORD)));},d->CompletableFuture.completedFuture(null));
            owner[0]=m;m.vault();m.pick();assertThat(m.face()).isEmpty();m.close();
        });
    }

}
