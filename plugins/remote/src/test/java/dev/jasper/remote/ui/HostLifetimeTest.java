package dev.jasper.remote.ui;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.*;
import dev.jasper.vault.api.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.*;

class HostLifetimeTest {
    final CredentialDescriptor login = new CredentialDescriptor(UUID.randomUUID(), "Login", "user", Kind.ACCOUNT_PASSWORD);
    final CredentialDescriptor key = new CredentialDescriptor(UUID.randomUUID(), "Key", "SHA256:synthetic", Kind.SSH_KEY, true);
    static <T> CompletableFuture<T> stubborn() { return new CompletableFuture<>() { @Override public boolean cancel(boolean interrupt) { return false; } }; }
    static final class Mounted implements AutoCloseable {
        final FakePluginHost host = new FakePluginHost(); final PluginContext context; final PluginDialog dialog; final HostEditor editor;
        Mounted(Function<CredentialPickRequest,CompletableFuture<CredentialPickResult>> pick,
                Function<dev.jasper.remote.hosts.RemoteHost,CompletableFuture<Void>> save) {
            var contexts = new ArrayList<PluginContext>();
            host.start(new PluginInfo("test.host", "Host", "1.0", Set.of()),Set.of(),Set.of(),contexts::add);
            context=contexts.getFirst();var window=host.addTerminalWindow();
            dialog=context.windows().dialog(new DialogSpec("Host",context.terminals().window(window).orElseThrow(),true));
            editor=new HostEditor(context.kit(),UI,List.of(),Optional.empty(),true,id->Optional.empty(),pick,save,()->{},dialog::close);
            dialog.setContent(editor.view());dialog.onClosed(editor::close);dialog.show();
        }
        void pick(String id) { emit(editor.view(),new UiEvent.Action(id)); }
        void agent() { emit(editor.view(),new UiEvent.Selected(HostEditor.AUTH,Set.of(HostEditor.AGENT))); }
        void valid() { agent();type(editor.view(),HostEditor.NAME,"fixture");type(editor.view(),HostEditor.HOSTNAME,"fixture.invalid");type(editor.view(),HostEditor.USERNAME,"u"); }
        @Override public void close() { editor.close();host.close(); }
    }
    @Test void anchoredWorkerCompletionIsMarshalledAndLateWorkerAfterCloseIsIgnored() throws Exception {
        var futures=new ArrayList<CompletableFuture<CredentialPickResult>>();var owner=new Mounted[1];
        edt(()->{owner[0]=new Mounted(request->{var future=HostLifetimeTest.<CredentialPickResult>stubborn();futures.add(future);return future;},host->CompletableFuture.completedFuture(null));owner[0].pick(HostEditor.CHOOSE);});
        try {
            Thread.ofPlatform().start(()->futures.getFirst().complete(new CredentialPickResult.Chosen(login))).join();
            edt(()->{assertThat(owner[0].editor.credentialLabel()).isEqualTo(login.name());owner[0].pick(HostEditor.ADD_KEY);owner[0].editor.close();});
            Thread.ofPlatform().start(()->futures.getLast().complete(new CredentialPickResult.Chosen(key))).join();
            edt(()->assertThat(owner[0].editor.credentialLabel()).isEqualTo(login.name()));
        }finally{edt(owner[0]::close);}
    }
    @Test void anchoredChooseAndAppendCarrySeparateClearAndCurrentSemantics() throws Exception { edt(()->{
        var requests=new ArrayList<CredentialPickRequest>();var futures=new ArrayList<CompletableFuture<CredentialPickResult>>();
        try(var m=new Mounted(request->{requests.add(request);var f=new CompletableFuture<CredentialPickResult>();futures.add(f);return f;},h->CompletableFuture.completedFuture(null))){
            m.pick(HostEditor.CHOOSE);assertThat(requests.getLast().allowClear()).isTrue();assertThat(requests.getLast().current()).isEmpty();
            futures.getLast().complete(new CredentialPickResult.Chosen(login));
            m.pick(HostEditor.CHOOSE);assertThat(requests.getLast().current()).contains(login.id());
            futures.getLast().complete(new CredentialPickResult.Cancelled());assertThat(m.editor.credentialLabel()).isEqualTo(login.name());
            m.pick(HostEditor.ADD_KEY);assertThat(requests.getLast().allowClear()).isFalse();assertThat(requests.getLast().current()).isEmpty();
            futures.getLast().complete(new CredentialPickResult.Chosen(key));
            m.pick(HostEditor.CHOOSE);assertThat(requests.getLast().current()).isEmpty();futures.getLast().complete(new CredentialPickResult.Cleared());
            assertThat(node(m.editor.view(),HostEditor.KEYS,Nodes.ItemList.class).items()).extracting(ListItem::id).containsExactly(key.id().toString());
            assertThat(node(m.editor.view(),HostEditor.CHOOSE,Nodes.Choice.class).face().orElseThrow().primary()).isEmpty();
            m.pick(HostEditor.ADD_KEY);futures.getLast().complete(new CredentialPickResult.Chosen(login));assertThat(m.editor.message()).contains("managed SSH key");
            m.pick(HostEditor.ADD_KEY);futures.getLast().complete(new CredentialPickResult.Chosen(new CredentialDescriptor(UUID.randomUUID(),"path","",Kind.SSH_KEY,false)));
            assertThat(m.editor.message()).contains("managed SSH key");
            m.pick(HostEditor.ADD_KEY);futures.getLast().complete(new CredentialPickResult.Chosen(key));
            assertThat(node(m.editor.view(),HostEditor.KEYS,Nodes.ItemList.class).items()).singleElement().satisfies(item -> {
                assertThat(item.title()).hasSize(2);assertThat(item.subtitle()).isEmpty();assertThat(item.tooltip()).contains("SHA256:synthetic");
            });
        }
    }); }
    @Test void staleAndReentrantCompletionsCannotReplaceNewerIntent() throws Exception { edt(()->{
        var requests=new ArrayList<CompletableFuture<CredentialPickResult>>();
        try(var m=new Mounted(request->{var f=HostLifetimeTest.<CredentialPickResult>stubborn();requests.add(f);return f;},h->CompletableFuture.completedFuture(null))){
            m.pick(HostEditor.CHOOSE);m.pick(HostEditor.ADD_KEY);var current=requests.getLast();
            requests.getFirst().complete(new CredentialPickResult.Chosen(login));assertThat(m.editor.credentialLabel()).isEqualTo("none chosen");
            current.complete(new CredentialPickResult.Chosen(key));assertThat(m.editor.credentialLabel()).isEqualTo("1 Vault key(s)");
            m.pick(HostEditor.CHOOSE);var old=requests.getLast();m.agent();emit(m.editor.view(),new UiEvent.Selected(HostEditor.AUTH,Set.of(HostEditor.VAULT)));
            m.pick(HostEditor.CHOOSE);old.completeExceptionally(new IllegalStateException("stale"));assertThat(m.editor.message()).isEmpty();
            m.dialog.close();requests.getLast().complete(new CredentialPickResult.Chosen(login));
        }
    }); }
    @Test void pickerInvalidationCancelsAndAReentrantProducerCannotRetainItsReturnedRequest() throws Exception { edt(()->{
        var requests=new ArrayList<CredentialPickRequest>();var pending=new CompletableFuture<CredentialPickResult>();
        try(var m=new Mounted(request->{requests.add(request);return pending;},h->CompletableFuture.completedFuture(null))){
            m.pick(HostEditor.CHOOSE);requests.getFirst().anchor().close();assertThat(pending).isCancelled();
        }
        var instance=new Mounted[1];var late=new CompletableFuture<CredentialPickResult>();
        try(var m=new Mounted(request->{instance[0].agent();return late;},h->CompletableFuture.completedFuture(null))){
            instance[0]=m;m.pick(HostEditor.CHOOSE);assertThat(late).isCancelled();
        }
    }); }
    @Test void realPersistenceOwnsBusyRejectsDuplicateAndRecoversAfterFailure() throws Exception { edt(()->{
        var saves=new ArrayList<CompletableFuture<Void>>();
        try(var m=new Mounted(request->CompletableFuture.completedFuture(new CredentialPickResult.Cancelled()),h->{var f=new CompletableFuture<Void>();saves.add(f);return f;})){
            m.valid();m.pick(HostEditor.SAVE);m.pick(HostEditor.SAVE);assertThat(saves).hasSize(1);
            assertThat(button(m.editor.view(),HostEditor.SAVE).state().busy()).isTrue();assertThat(node(m.editor.view(),HostEditor.NAME,Nodes.Field.class).state().enabled()).isFalse();
            saves.getFirst().completeExceptionally(new IllegalStateException("Disk unavailable"));
            assertThat(m.editor.message()).isEqualTo("Disk unavailable");assertThat(button(m.editor.view(),HostEditor.SAVE).state().enabled()).isTrue();
            type(m.editor.view(),HostEditor.NAME,"retry");assertThat(m.editor.message()).isEmpty();m.pick(HostEditor.SAVE);assertThat(saves).hasSize(2);
            m.dialog.close();saves.getLast().complete(null);assertThat(m.host.failures()).isEmpty();
        }
    }); }
    @Test void releasingChosenAnchorCannotApplyTheOldChoiceAfterReentrantReplacement() throws Exception { edt(()->{
        var instance=new Mounted[1];var requests=new ArrayList<CompletableFuture<CredentialPickResult>>();
        try(var m=new Mounted(request->{var future=new CompletableFuture<CredentialPickResult>();requests.add(future);
            if(requests.size()==1)request.anchor().onClosed(()->instance[0].pick(HostEditor.ADD_KEY));return future;},h->CompletableFuture.completedFuture(null))){
            instance[0]=m;m.pick(HostEditor.CHOOSE);requests.getFirst().complete(new CredentialPickResult.Chosen(login));
            assertThat(requests).hasSize(2);assertThat(m.editor.credentialLabel()).isEqualTo("none chosen");
            requests.getLast().complete(new CredentialPickResult.Chosen(key));assertThat(m.editor.credentialLabel()).isEqualTo("1 Vault key(s)");
        }
    }); }
    @Test void synchronousPickerThrowAndFailureStayInlineAndCanRetry() throws Exception { edt(()->{
        int[] calls={0};try(var m=new Mounted(request->{if(calls[0]++==0)throw new IllegalStateException("Picker unavailable");return CompletableFuture.failedFuture(new IllegalStateException("Vault closed"));},h->CompletableFuture.completedFuture(null))){
            m.pick(HostEditor.CHOOSE);assertThat(m.editor.message()).isEqualTo("Picker unavailable");m.pick(HostEditor.CHOOSE);assertThat(m.editor.message()).isEqualTo("Vault closed");
        }
    }); }
}
