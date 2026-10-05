package dev.jasper.sdk.testing;

import dev.jasper.sdk.ui.*;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Behavioral contract shared by the production host and functional fake. */
public abstract class KitContractTest {
    protected abstract UiKit kit(String owner);
    protected abstract void emit(UiView view, UiEvent event);
    protected abstract void secret(UiView view, String id, char[] value);
    protected abstract void owns(UiKit kit, UiView view);
    protected abstract void stop(UiKit kit);
    protected static void edt(Runnable body) throws Exception { SwingUtilities.invokeAndWait(body); }
    protected static ViewSpec spec(String title) { return new ViewSpec(new Nodes.Stack("root", ControlState.READY,
        Nodes.LayoutRole.VERTICAL, List.of(new Nodes.Text("title", ControlState.READY,title,Nodes.TextRole.BODY),
        new Nodes.Secret("secret",ControlState.READY,"","Password","")))); }
    @Test void disposalCannotReenterUpdate() throws Exception { edt(() -> {
        var kit=kit("owner"); var before=new ViewSpec(new Nodes.Custom("custom",ControlState.READY,"content",Nodes.CustomKind.EDITOR,"Editor"));
        var view=kit.create(before,e -> {}); var calls=new java.util.concurrent.atomic.AtomicInteger(); var rejected=new java.util.concurrent.atomic.AtomicBoolean();
        view.registerContent(new CustomContent("content",Nodes.CustomKind.EDITOR,new javax.swing.JTextArea(),() -> {
            calls.incrementAndGet(); try {view.update(spec("Nested"));} catch(IllegalStateException expected){rejected.set(true);}
        }));
        view.update(spec("Outer")); assertThat(rejected.get()).isTrue(); assertThat(view.spec()).isEqualTo(spec("Outer"));
        view.close(); stop(kit); assertThat(calls.get()).isEqualTo(1);
    }); }
    @Test void storedSecretDetailsOwnDefensiveTransfersAndWipeTemporaryCopies() throws Exception { edt(() -> {
        var kit=kit("owner");var events=new ArrayList<UiEvent>();
        var descriptor=new Nodes.Secret("detail",ControlState.READY,"","Passphrase","",Nodes.SecretKind.DETAIL,"copy-detail");
        var view=kit.create(new ViewSpec(descriptor),events::add);char[] source={'x','y'};view.replaceSecret("detail",source);source[0]='z';
        assertThat(events).isEmpty();char[][] held={null};
        assertThatThrownBy(() -> view.withSecret("detail",chars -> {held[0]=chars;assertThat(chars).containsExactly('x','y');throw new IllegalStateException();})).isInstanceOf(IllegalStateException.class);
        assertThat(held[0]).containsOnly((char)0);view.replaceSecret("detail",new char[]{'n'});view.withSecret("detail",chars -> assertThat(chars).containsExactly('n'));
        view.clearSecret("detail");view.withSecret("detail",chars -> assertThat(chars).isEmpty());
        view.replaceSecret("detail",new char[]{'q'});view.update(spec("Input"));view.update(new ViewSpec(descriptor));view.withSecret("detail",chars -> assertThat(chars).isEmpty());
        stop(kit);assertThatThrownBy(() -> view.replaceSecret("detail",new char[]{'s'})).isInstanceOf(IllegalStateException.class);
    }); }
    @Test void createUpdateActionAndClose() throws Exception { edt(() -> {
        var kit=kit("owner"); var events=new ArrayList<UiEvent>(); var view=kit.create(spec("Before"),events::add);
        view.update(spec("After")); assertThat(view.spec()).isEqualTo(spec("After"));
        emit(view,new UiEvent.Action("go")); assertThat(events).containsExactly(new UiEvent.Action("go"));
        view.close(); view.close(); assertThatThrownBy(view::spec).isInstanceOf(IllegalStateException.class); stop(kit);
    }); }
    @Test void preservesSecretsAcrossStableUpdateAndErasesCallbackCopies() throws Exception { edt(() -> {
        var kit=kit("owner"); var view=kit.create(spec("Before"), e -> {});
        secret(view,"secret",new char[]{'x','y'}); view.update(spec("After")); var held=new char[1][];
        assertThatThrownBy(() -> view.withSecret("secret", chars -> { held[0]=chars; assertThat(chars).containsExactly('x','y'); throw new IllegalStateException("callback"); })).isInstanceOf(IllegalStateException.class);
        assertThat(held[0]).containsExactly((char)0,(char)0);
        view.clearSecret("secret"); view.withSecret("secret", chars -> assertThat(chars).isEmpty()); stop(kit);
    }); }
    @Test void removingSecretDestroysItAndHandlerFailuresAreContained() throws Exception { edt(() -> {
        var kit=kit("owner"); var view=kit.create(spec("Before"),e -> { throw new IllegalStateException("handler"); });
        assertThatCode(() -> emit(view,new UiEvent.Action("go"))).doesNotThrowAnyException();
        secret(view,"secret",new char[]{'x'}); view.update(new ViewSpec(new Nodes.Text("title",ControlState.READY,"Only",Nodes.TextRole.BODY)));
        view.update(spec("Again")); view.withSecret("secret", chars -> assertThat(chars).isEmpty()); stop(kit);
    }); }
    @Test void rejectsCrossOwnerAndRetainedViewAfterStop() throws Exception { edt(() -> {
        var first=kit("first"); var second=kit("second"); var view=first.create(spec(""), e -> {});
        assertThatCode(() -> owns(first,view)).doesNotThrowAnyException();
        assertThatThrownBy(() -> owns(second,view)).isInstanceOf(IllegalArgumentException.class);
        stop(first); assertThatThrownBy(() -> view.update(spec("stale"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> first.create(spec("stale"),e -> {})).isInstanceOf(IllegalStateException.class);
        stop(second);
    }); }
    @Test void actualViewOperationsRequireEdtAndCloseMayBeOffEdt() throws Exception {
        var holder=new UiKit[1];var view=new UiView[1]; edt(() -> {holder[0]=kit("owner");view[0]=holder[0].create(spec(""),e -> {});});
        assertThatThrownBy(() -> holder[0].create(spec(""),e -> {})).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(view[0]::spec).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> view[0].update(spec(""))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> view[0].focus("secret")).isInstanceOf(IllegalStateException.class);
        view[0].close(); edt(() -> stop(holder[0]));
    }
    @Test void scopedIconsAndMalformedDescriptorsAreRejected() throws Exception { edt(() -> {
        var kit=kit("owner");
        assertThatThrownBy(() -> new IconAsset("owner:empty","M 1 1 Z M 15 15 Z")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> kit.registerIcon(new IconAsset("foreign:logo","M 1 1 L 15 15"))).isInstanceOf(IllegalArgumentException.class);
        var icon=kit.registerIcon(new IconAsset("owner:logo","M 1 1 L 15 15"));
        assertThatThrownBy(() -> kit.registerIcon(new IconAsset("owner:logo","M 1 1 L 15 15"))).isInstanceOf(IllegalArgumentException.class);
        icon.close();icon.close();kit.registerIcon(new IconAsset("owner:logo","M 1 1 L 15 15"));stop(kit);
        assertThatThrownBy(() -> new ViewSpec(new Nodes.Stack("root",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(spec("").root(),spec("").root())))).isInstanceOf(IllegalArgumentException.class);
    }); }
    @Test void throwingHandlersMayUpdateAndCloseTheirOwnView() throws Exception { edt(() -> {
        var kit=kit("owner");var handle=new UiView[1];handle[0]=kit.create(spec("Before"),e -> {handle[0].update(spec("After"));throw new IllegalStateException("after update");});
        emit(handle[0],new UiEvent.Action("update"));assertThat(handle[0].spec()).isEqualTo(spec("After"));handle[0].close();
        handle[0]=kit.create(spec("Before"),e -> {handle[0].close();throw new IllegalStateException("after close");});
        assertThatCode(() -> emit(handle[0],new UiEvent.Action("close"))).doesNotThrowAnyException();assertThatThrownBy(handle[0]::spec).isInstanceOf(IllegalStateException.class);stop(kit);
    }); }
    @Test void customContentCannotCrossOwnersAndDisposesOnRemoval() throws Exception { edt(() -> {
        var first=kit("first");var second=kit("second");var custom=new ViewSpec(new Nodes.Custom("node",ControlState.READY,"content",Nodes.CustomKind.EDITOR,"Editor"));
        var a=first.create(custom,e -> {});var b=second.create(custom,e -> {});var widget=new javax.swing.JTextArea("document");var disposed=new java.util.concurrent.atomic.AtomicInteger();
        a.registerContent(new CustomContent("content",Nodes.CustomKind.EDITOR,widget,disposed::incrementAndGet));
        assertThatThrownBy(() -> b.registerContent(new CustomContent("content",Nodes.CustomKind.EDITOR,widget,() -> {}))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> b.registerContent(new CustomContent("node",Nodes.CustomKind.EDITOR,new javax.swing.JTextArea(),() -> {}))).isInstanceOf(IllegalArgumentException.class);
        a.update(new ViewSpec(new Nodes.Custom("replacement",ControlState.READY,"content",Nodes.CustomKind.EDITOR,"Editor")));assertThat(disposed.get()).isEqualTo(1);stop(first);stop(second);assertThat(disposed.get()).isEqualTo(1);
    }); }
    @Test void reentrantCloseDuringRemovalDisposesEachContentExactlyOnce() throws Exception { edt(() -> {
        var kit=kit("owner");
        var tree=new ViewSpec(new Nodes.Stack("root",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(
            new Nodes.Custom("first",ControlState.READY,"first-content",Nodes.CustomKind.EDITOR,"First"),
            new Nodes.Custom("second",ControlState.READY,"second-content",Nodes.CustomKind.EDITOR,"Second"))));
        var view=kit.create(tree,e -> {});var first=new java.util.concurrent.atomic.AtomicInteger();var second=new java.util.concurrent.atomic.AtomicInteger();
        try {
            view.registerContent(new CustomContent("first-content",Nodes.CustomKind.EDITOR,new javax.swing.JTextArea(),() -> {first.incrementAndGet();view.close();}));
            view.registerContent(new CustomContent("second-content",Nodes.CustomKind.EDITOR,new javax.swing.JTextArea(),() -> {second.incrementAndGet();view.close();}));
            view.update(spec("Removed"));
            assertThat(first.get()).isEqualTo(1);assertThat(second.get()).isEqualTo(1);
            view.close();assertThat(first.get()).isEqualTo(1);assertThat(second.get()).isEqualTo(1);
        } finally {view.close();stop(kit);}
    }); }
}
