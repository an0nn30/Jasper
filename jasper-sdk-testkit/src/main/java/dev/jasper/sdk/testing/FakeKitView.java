package dev.jasper.sdk.testing;
import dev.jasper.sdk.ui.*;
import java.util.*;
import java.awt.EventQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
/** Functional descriptor/secret/event owner for plugin tests. */
public final class FakeKitView implements UiView {
 private static final Map<javax.swing.JComponent,FakeKitView> CONTENT_OWNERS=new IdentityHashMap<>();
 final FakeKit owner;
 private ViewSpec spec;
 private ViewSpec sourceSpec;
 private boolean updating;
 Object mount;
 private final List<Attachment> anchors=new ArrayList<>();
 void invalidateAnchors(){for(var a:List.copyOf(anchors))a.close();}
 void unmount(Object target){if(mount==target){invalidateAnchors();mount=null;}}
 final javax.swing.JPanel component = new javax.swing.JPanel();
 /** The theme slot the host would paint this view from: CHROME, or WORKSPACE once mounted as a content tab (as the
  * application routes workspace content); package-private, read by the shared contract harness only. */
 String route="CHROME";
 void mount(Object target){guard();if(mount!=null && mount!=target)throw new IllegalArgumentException("View is already mounted");mount=target;}

 private Consumer<UiEvent> events;
 java.util.function.Predicate<UiEvent> hostEvents = event -> false;
 java.util.function.UnaryOperator<ViewSpec> hostSpec = value -> value;

 private final Map<String,char[]> secrets=new HashMap<>();
 private final Map<String,CustomContent> contents=new HashMap<>();
 private final AtomicBoolean closed=new AtomicBoolean();
 private String focused;
 private final Map<String,String> fieldText=new HashMap<>();
 private final Map<String,int[]> fieldSelections=new HashMap<>();
 FakeKitView(FakeKit owner, ViewSpec spec, Consumer<UiEvent> events) {this.owner=owner;this.events=Objects.requireNonNull(events);update(spec);}
 void guard(){owner.guard();if(closed.get())throw new IllegalStateException("View is closed");}
 @Override public UiAnchor anchor(String id){guard();var anchor=new Attachment(id);if(!anchor.isOpen())throw new IllegalArgumentException("Unmounted or ineligible control");anchors.add(anchor);return anchor;}
 final class Attachment implements UiAnchor {
  final Object host=owner.anchorHost, mounted=mount;
  final String id;final List<String> ancestry;final Object kind;boolean done;final List<Runnable> listeners=new ArrayList<>();
  Attachment(String id){this.id=id;kind=kind(mounted(spec.root()).get(id));ancestry=path(spec.root(),id);}
  private Object kind(Nodes.Node node){return node instanceof Nodes.Button ? Nodes.Button.class : node instanceof Nodes.Choice c && c.kind()==Nodes.ChoiceKind.PICKER ? c.kind() : null;}
  private boolean eligible(Nodes.Node node,boolean parent){boolean live=parent&&node.state().visible()&&node.state().enabled()&&!node.state().busy();if(node.id().equals(id))return live;for(var child:mountedChildren(node))if(eligible(child,live))return true;return false;}
  FakeUi.FakeWindow ownerSurface(){return mounted instanceof FakeUi.SurfaceMount slot ? slot.surface() : mounted instanceof FakeUi.FakeWindow window ? window : null;}
  @Override public boolean isOpen(){if(!done&&(closed.get()||mounted==null||mount!=mounted||host==null||kind==null||!kind.equals(kind(mounted(spec.root()).get(id)))||!ancestry.equals(path(spec.root(),id))||!eligible(spec.root(),true)||ownerSurface()!=null&&(!ownerSurface().shown||ownerSurface().closed)))close();return !done;}
  @Override public dev.jasper.sdk.Subscription onClosed(Runnable listener){Objects.requireNonNull(listener);if(!isOpen()){listener.run();return () -> {}; }listeners.add(listener);return () -> listeners.remove(listener);}
  @Override public void close(){if(done)return;done=true;anchors.remove(this);for(var listener:List.copyOf(listeners))listener.run();listeners.clear();}
 }
 @Override public ViewSpec spec(){guard();return spec;}
 /** Host-only original plugin descriptor, before draft/search row projection. */
 ViewSpec sourceSpec(){guard();return sourceSpec;}
 @Override public void update(ViewSpec next){guard();Objects.requireNonNull(next);if(updating)throw new IllegalStateException("Reentrant view update");updating=true;try{owner.validateIcons(next);var source=next;next=hostSpec.apply(next);if(next!=source)owner.validateIcons(next);var nodes=mounted(next.root());
  secrets.entrySet().removeIf(e -> {if(nodes.get(e.getKey()) instanceof Nodes.Secret nextSecret && mounted(spec.root()).get(e.getKey()) instanceof Nodes.Secret oldSecret && nextSecret.kind()==oldSecret.kind())return false;Arrays.fill(e.getValue(),(char)0);return true;});
  for(var content:List.copyOf(contents.values()))if(nodes.values().stream().noneMatch(n -> n instanceof Nodes.Custom c && c.contentId().equals(content.id()) && c.kind()==content.kind() && mounted(spec.root()).get(c.id()) instanceof Nodes.Custom previous && previous.contentId().equals(content.id()) && previous.kind()==content.kind())){dispose(content);}
  fieldText.keySet().removeIf(id -> !(nodes.get(id) instanceof Nodes.Field));
  fieldSelections.keySet().removeIf(id -> !(nodes.get(id) instanceof Nodes.Field));
  var previous=spec==null ? Map.<String,Nodes.Node>of() : mounted(spec.root());
  for(var node:nodes.values())if(node instanceof Nodes.Field field) {
   if(!(previous.get(field.id()) instanceof Nodes.Field old) || old.kind()!=field.kind() || !old.value().equals(field.value())) {
    fieldText.put(field.id(),field.value());fieldSelections.remove(field.id());
   }
  }
  sourceSpec=source;spec=next;for(var a:List.copyOf(anchors))a.isOpen();if(focused!=null&&!nodes.containsKey(focused))focused=null;}finally{updating=false;}
 }
 private static List<String> path(Nodes.Node root,String id){if(root.id().equals(id))return List.of(root.id());for(var child:mountedChildren(root)){var nested=path(child,id);if(!nested.isEmpty()){var result=new ArrayList<String>();result.add(root.id());result.addAll(nested);return result;}}return List.of();}
 /** The nodes the host mounts, by id: every node but a table's empty state (and what it holds) while that table has rows; ids and icons are still checked over the whole descriptor. */
 private static Map<String,Nodes.Node> mounted(Nodes.Node root){var result=new HashMap<String,Nodes.Node>();mountedWalk(root,result);return result;}
 private static void mountedWalk(Nodes.Node node,Map<String,Nodes.Node> result){result.put(node.id(),node);for(var child:mountedChildren(node))mountedWalk(child,result);}
 /** A node's mounted children: a table with rows leaves its empty state unmounted, as the host does. */
 static List<Nodes.Node> mountedChildren(Nodes.Node node){var result=children(node);if(node instanceof Nodes.Table table&&!table.rows().isEmpty()&&table.empty().isPresent())result.remove(table.empty().get());return result;}
 private void requireSecret(String id){guard();if(!(mounted(spec.root()).get(id) instanceof Nodes.Secret))throw new IllegalArgumentException("Not a secret node");}
 /** Simulate typing into an INPUT or MULTILINE secret; the fake owns a copy and never includes it in diagnostics. */
 public void setSecret(String id,char[] value){requireSecret(id);if(((Nodes.Secret)mounted(spec.root()).get(id)).kind()==Nodes.SecretKind.DETAIL)throw new IllegalArgumentException("Secret detail is read-only");replaceSecret(id,value);emit(new UiEvent.SecretChanged(id));}
 @Override public void replaceSecret(String id,char[] value){requireSecret(id);Objects.requireNonNull(value);char[] next=value.clone();clearSecret(id);secrets.put(id,next);}
 @Override public void withSecret(String id,Consumer<char[]> use){requireSecret(id);Objects.requireNonNull(use);char[] copy=secrets.getOrDefault(id,new char[0]).clone();try{use.accept(copy);}finally{Arrays.fill(copy,(char)0);}}
 @Override public void clearSecret(String id){requireSecret(id);var old=secrets.remove(id);if(old!=null)Arrays.fill(old,(char)0);}
 @Override public void focus(String id){guard();if(!mounted(spec.root()).containsKey(id))throw new IllegalArgumentException("Unknown node");focused=id;}
 @Override public void selectAll(String id){guard();if(!(mounted(spec.root()).get(id) instanceof Nodes.Field field) || field.presentation()==Nodes.FieldPresentation.SLIDER)throw new IllegalArgumentException("Not a field node");fieldSelections.put(id,new int[]{0,fieldText.get(id).length()});}
 /** The fake's logical field selection, independently recorded from focus. */
 public int[] fieldSelection(String id){guard();if(!(mounted(spec.root()).get(id) instanceof Nodes.Field field) || field.presentation()==Nodes.FieldPresentation.SLIDER)throw new IllegalArgumentException("Not a field node");return fieldSelections.getOrDefault(id,new int[]{0,0}).clone();}
 private String revealed;
 @Override public void reveal(String id){guard();if(!mounted(spec.root()).containsKey(id))throw new IllegalArgumentException("Unknown node");revealed=id;}
 /** The node last scrolled into view; reveal never moves logical focus.
 * @return node id */
 public Optional<String> revealed(){guard();return Optional.ofNullable(revealed);}
 private final Set<String> composing=new HashSet<>();
 @Override public boolean composing(String fieldId){guard();if(!(mounted(spec.root()).get(fieldId) instanceof Nodes.Field field)||field.presentation()==Nodes.FieldPresentation.SLIDER)throw new IllegalArgumentException("Not a field node");return composing.contains(fieldId);}
 /** Simulate an input method composing (or committing) text in a field.
 * @param fieldId field node
 * @param value whether uncommitted text is pending */
 public void setComposing(String fieldId,boolean value){guard();if(!(mounted(spec.root()).get(fieldId) instanceof Nodes.Field field)||field.presentation()==Nodes.FieldPresentation.SLIDER)throw new IllegalArgumentException("Not a field node");if(value)composing.add(fieldId);else composing.remove(fieldId);}
 /** Requested logical focus. */
 public Optional<String> focused(){guard();return Optional.ofNullable(focused);}
 /** Deliver a typed event through containment; an edit of a read-only field, which a person cannot make, is dropped. */
 public void emit(UiEvent event){guard();if(event instanceof UiEvent.Edited edit && mounted(spec.root()).get(edit.id()) instanceof Nodes.Field field && field.readOnly())return;if(event instanceof UiEvent.Edited edit && mounted(spec.root()).get(edit.id()) instanceof Nodes.Field){fieldText.put(edit.id(),edit.text());fieldSelections.remove(edit.id());}try{if(hostEvents.test(event))return;events.accept(Objects.requireNonNull(event));}catch(Throwable failure){owner.failed();}}
 /** Simulate a person typing {@code text} into a field, replacing its text: it reports {@link UiEvent.Edited} as the host's field does, while a read-only field, or one that is hidden, disabled or busy (itself or a container above it), takes nothing.
 * @param fieldId field node
 * @param text the text typed */
 public void type(String fieldId,String text){guard();Objects.requireNonNull(text);if(!(mounted(spec.root()).get(fieldId) instanceof Nodes.Field field)||field.presentation()==Nodes.FieldPresentation.SLIDER)throw new IllegalArgumentException("Not a field node");if(field.readOnly()||!live(fieldId))return;emit(new UiEvent.Edited(fieldId,text));}
 private boolean live(String id){var nodes=mounted(spec.root());for(String step:path(spec.root(),id)){var state=nodes.get(step).state();if(!state.visible()||!state.enabled()||state.busy())return false;}return true;}
 @Override public void registerContent(CustomContent content){guard();Objects.requireNonNull(content);
  if(mounted(spec.root()).values().stream().noneMatch(n -> n instanceof Nodes.Custom c && c.contentId().equals(content.id())&&c.kind()==content.kind()))throw new IllegalArgumentException("No matching Custom content");
  if(contents.containsKey(content.id()))throw new IllegalArgumentException("Content already registered");
  FakeKitContent.validate(content);if(CONTENT_OWNERS.containsKey(content.component())||content.component().getParent()!=null)throw new IllegalArgumentException("Content is already owned or mounted");CONTENT_OWNERS.put(content.component(),this);contents.put(content.id(),content);
 }
 private void dispose(CustomContent content){
  // Claim removal before invoking plugin code; reentrant close may consume the remaining snapshot.
  if(contents.get(content.id())!=content)return;contents.remove(content.id());CONTENT_OWNERS.remove(content.component(),this);try{content.lifetime().close();}catch(Throwable failure){owner.failed();}}
 @Override public void close(){if(!closed.compareAndSet(false,true))return;Runnable cleanup=() -> {invalidateAnchors();for(var value:secrets.values())Arrays.fill(value,(char)0);secrets.clear();fieldText.clear();fieldSelections.clear();for(var content:List.copyOf(contents.values()))dispose(content);contents.clear();events=e -> {};hostEvents=e -> false;hostSpec=v -> v;mount=null;owner.removed(this);};if(EventQueue.isDispatchThread())cleanup.run();else EventQueue.invokeLater(cleanup);}
    static List<Nodes.Node> children(Nodes.Node node) {
        var result = new ArrayList<Nodes.Node>();
        switch (node) {
            case Nodes.Panel n -> { result.add(n.header()); n.searchOrNavigation().ifPresent(result::add); result.add(n.body()); n.footer().ifPresent(result::add); }
            case Nodes.Header n -> { n.context().ifPresent(result::add); n.primary().ifPresent(result::add); }
            case Nodes.Stack n -> result.addAll(n.children());
            case Nodes.ItemList n -> { n.addAction().ifPresent(result::add); for(var item:n.items()) { item.leading().ifPresent(result::add);result.addAll(item.title());result.addAll(item.subtitle());result.addAll(item.trailing()); } }
            case Nodes.Table n -> { for(var row:n.rows()) result.addAll(row.cellNodes().values()); n.empty().ifPresent(result::add); }
            case Nodes.Card n -> result.addAll(n.children());
            case Nodes.Split n -> result.addAll(n.children());
            case Nodes.FormRow n -> { result.add(n.control()); n.trailing().ifPresent(result::add); }
            case Nodes.SettingRow n -> result.add(n.control());
            case Nodes.Section n -> result.add(n.body());
            case Nodes.Empty n -> { result.add(n.icon()); result.addAll(n.actions()); }
            case Nodes.Notice n -> result.addAll(n.actions());
            case Nodes.Form n -> { n.hero().ifPresent(result::add); result.addAll(n.sections()); n.preview().ifPresent(result::add); result.addAll(n.actions()); }
            default -> {}
        }
        return result;
    }
}
