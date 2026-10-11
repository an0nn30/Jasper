package dev.jasper.remote.ui;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.*;
class HostCompositionTest {
 @Test void groupsTheActualHostFieldsAndProjectsReadiness() throws Exception { edt(() -> {
  try(var editor = new HostEditor(new FakeKit("remote"), UI, List.of(), Optional.empty(), false, id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()), host -> {}, () -> {})) {
   assertThat(form(editor.view()).sections()).allMatch(n -> n instanceof Nodes.Section);
   assertThat(form(editor.view()).sections()).extracting(n -> ((Nodes.Section)n).title()).containsExactly("Connection", "Authentication", "Options");
   assertThat(node(editor.view(), "row."+HostEditor.HOSTNAME, Nodes.FormRow.class).trailing()).hasValueSatisfying(n -> assertThat(n.id()).isEqualTo(HostEditor.PORT));
   assertThat(node(editor.view(), HostEditor.CHOOSE, Nodes.Choice.class).face().orElseThrow().density()).isEqualTo(Nodes.PickerDensity.DEFAULT);
   assertThat(node(editor.view(),HostEditor.KEYS,Nodes.ItemList.class).addAction().orElseThrow().kind()).isEqualTo(Nodes.ButtonKind.LINK);
   assertThat(button(editor.view(), HostEditor.SAVE).kind()).isEqualTo(Nodes.ButtonKind.PRIMARY);
   assertThat(button(editor.view(), HostEditor.SAVE).state().enabled()).isFalse();
   type(editor.view(),HostEditor.NAME,"test");type(editor.view(),HostEditor.HOSTNAME,"test.invalid");type(editor.view(),HostEditor.USERNAME,"user");
   assertThat(button(editor.view(),HostEditor.SAVE).state().enabled()).isTrue();
   type(editor.view(),HostEditor.PORT,"65536");assertThat(button(editor.view(),HostEditor.SAVE).state().enabled()).isFalse();
  }
 }); }
 @Test void replacingPickerAndChangingAuthCancelThePreviousRequest() throws Exception { edt(() -> {
  var futures = new ArrayList<CompletableFuture<Optional<dev.jasper.vault.api.CredentialDescriptor>>>();
  try(var editor = new HostEditor(new FakeKit("remote"), UI, List.of(), Optional.empty(), true, id -> Optional.empty(), () -> {var f=new CompletableFuture<Optional<dev.jasper.vault.api.CredentialDescriptor>>();futures.add(f);return f;}, host -> {}, () -> {})) {
   emit(editor.view(),new UiEvent.Action(HostEditor.CHOOSE));emit(editor.view(),new UiEvent.Action(HostEditor.ADD_KEY));
   assertThat(futures.getFirst()).isCancelled();
   emit(editor.view(),new UiEvent.Selected(HostEditor.AUTH,Set.of(HostEditor.AGENT)));
   assertThat(futures.getLast()).isCancelled();
   assertThat(editor.credentialLabel()).isEqualTo("none chosen");
  }
 }); }
 @Test void orderedKeysKeepSelectedIdentityAcrossReorderAndNeighborRemoval() throws Exception { edt(() -> {
  var a=UUID.randomUUID();var b=UUID.randomUUID();var c=UUID.randomUUID();
  var host=dev.jasper.remote.hosts.RemoteHost.create("test","test.invalid",22,"u",new dev.jasper.remote.hosts.Auth.VaultKeys(List.of(a,b,c)),"",Optional.empty());
  try(var editor = new HostEditor(new FakeKit("remote"), UI, List.of(), Optional.of(host), true, id -> Optional.of(id.toString()), () -> CompletableFuture.completedFuture(Optional.empty()), h -> {}, () -> {})) {
   emit(editor.view(),new UiEvent.Selected(HostEditor.KEYS,Set.of(b.toString())));
   emit(editor.view(),new UiEvent.Reordered(HostEditor.KEYS,b.toString(),0));
   assertThat(node(editor.view(),HostEditor.KEYS,Nodes.ItemList.class).selectedIds()).containsExactly(b.toString());
   emit(editor.view(),new UiEvent.Removed(HostEditor.KEYS,a.toString()));
   assertThat(node(editor.view(),HostEditor.KEYS,Nodes.ItemList.class).selectedIds()).containsExactly(b.toString());
   emit(editor.view(),new UiEvent.Removed(HostEditor.KEYS,b.toString()));
   assertThat(node(editor.view(),HostEditor.KEYS,Nodes.ItemList.class).selectedIds()).containsExactly(c.toString());
  }
 }); }
}
