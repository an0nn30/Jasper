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
 private Object mount;
 void unmount(Object target){if(mount==target)mount=null;}
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
 @Override public ViewSpec spec(){guard();return spec;}
 /** Host-only original plugin descriptor, before draft/search row projection. */
 ViewSpec sourceSpec(){guard();return sourceSpec;}
 @Override public void update(ViewSpec next){guard();Objects.requireNonNull(next);if(updating)throw new IllegalStateException("Reentrant view update");updating=true;try{var source=next;next=hostSpec.apply(next);var nodes=index(next.root());
  secrets.entrySet().removeIf(e -> {if(nodes.get(e.getKey()) instanceof Nodes.Secret nextSecret && index(spec.root()).get(e.getKey()) instanceof Nodes.Secret oldSecret && nextSecret.kind()==oldSecret.kind())return false;Arrays.fill(e.getValue(),(char)0);return true;});
  for(var content:List.copyOf(contents.values()))if(nodes.values().stream().noneMatch(n -> n instanceof Nodes.Custom c && c.contentId().equals(content.id()) && c.kind()==content.kind() && index(spec.root()).get(c.id()) instanceof Nodes.Custom previous && previous.contentId().equals(content.id()) && previous.kind()==content.kind())){dispose(content);}
  fieldText.keySet().removeIf(id -> !(nodes.get(id) instanceof Nodes.Field));
  fieldSelections.keySet().removeIf(id -> !(nodes.get(id) instanceof Nodes.Field));
  var previous=spec==null ? Map.<String,Nodes.Node>of() : index(spec.root());
  for(var node:nodes.values())if(node instanceof Nodes.Field field) {
   if(!(previous.get(field.id()) instanceof Nodes.Field old) || !old.value().equals(field.value())) {
    fieldText.put(field.id(),field.value());fieldSelections.remove(field.id());
   }
  }
  sourceSpec=source;spec=next;if(focused!=null&&!nodes.containsKey(focused))focused=null;}finally{updating=false;}
 }
 private static Map<String,Nodes.Node> index(Nodes.Node root){var result=new HashMap<String,Nodes.Node>();walk(root,result);return result;}
 private static void walk(Nodes.Node node,Map<String,Nodes.Node> result){result.put(node.id(),node);for(var child:children(node))walk(child,result);}
 private void requireSecret(String id){guard();if(!(index(spec.root()).get(id) instanceof Nodes.Secret))throw new IllegalArgumentException("Not a secret node");}
 /** Simulate typing into an INPUT or MULTILINE secret; the fake owns a copy and never includes it in diagnostics. */
 public void setSecret(String id,char[] value){requireSecret(id);if(((Nodes.Secret)index(spec.root()).get(id)).kind()==Nodes.SecretKind.DETAIL)throw new IllegalArgumentException("Secret detail is read-only");replaceSecret(id,value);emit(new UiEvent.SecretChanged(id));}
 @Override public void replaceSecret(String id,char[] value){requireSecret(id);Objects.requireNonNull(value);char[] next=value.clone();clearSecret(id);secrets.put(id,next);}
 @Override public void withSecret(String id,Consumer<char[]> use){requireSecret(id);Objects.requireNonNull(use);char[] copy=secrets.getOrDefault(id,new char[0]).clone();try{use.accept(copy);}finally{Arrays.fill(copy,(char)0);}}
 @Override public void clearSecret(String id){requireSecret(id);var old=secrets.remove(id);if(old!=null)Arrays.fill(old,(char)0);}
 @Override public void focus(String id){guard();if(!index(spec.root()).containsKey(id))throw new IllegalArgumentException("Unknown node");focused=id;}
 @Override public void selectAll(String id){guard();if(!(index(spec.root()).get(id) instanceof Nodes.Field))throw new IllegalArgumentException("Not a field node");fieldSelections.put(id,new int[]{0,fieldText.get(id).length()});}
 /** The fake's logical field selection, independently recorded from focus. */
 public int[] fieldSelection(String id){guard();if(!(index(spec.root()).get(id) instanceof Nodes.Field))throw new IllegalArgumentException("Not a field node");return fieldSelections.getOrDefault(id,new int[]{0,0}).clone();}
 private String revealed;
 @Override public void reveal(String id){guard();if(!index(spec.root()).containsKey(id))throw new IllegalArgumentException("Unknown node");revealed=id;}
 /** The node last scrolled into view; reveal never moves logical focus.
 * @return node id */
 public Optional<String> revealed(){guard();return Optional.ofNullable(revealed);}
 private final Set<String> composing=new HashSet<>();
 @Override public boolean composing(String fieldId){guard();if(!(index(spec.root()).get(fieldId) instanceof Nodes.Field))throw new IllegalArgumentException("Not a field node");return composing.contains(fieldId);}
 /** Simulate an input method composing (or committing) text in a field.
 * @param fieldId field node
 * @param value whether uncommitted text is pending */
 public void setComposing(String fieldId,boolean value){guard();if(!(index(spec.root()).get(fieldId) instanceof Nodes.Field))throw new IllegalArgumentException("Not a field node");if(value)composing.add(fieldId);else composing.remove(fieldId);}
 /** Requested logical focus. */
 public Optional<String> focused(){guard();return Optional.ofNullable(focused);}
 /** Deliver a typed event through containment. */
 public void emit(UiEvent event){guard();if(event instanceof UiEvent.Edited edit && index(spec.root()).get(edit.id()) instanceof Nodes.Field){fieldText.put(edit.id(),edit.text());fieldSelections.remove(edit.id());}try{if(hostEvents.test(event))return;events.accept(Objects.requireNonNull(event));}catch(Throwable failure){owner.failed();}}
 @Override public void registerContent(CustomContent content){guard();Objects.requireNonNull(content);
  if(index(spec.root()).values().stream().noneMatch(n -> n instanceof Nodes.Custom c && c.contentId().equals(content.id())&&c.kind()==content.kind()))throw new IllegalArgumentException("No matching Custom content");
  if(contents.containsKey(content.id()))throw new IllegalArgumentException("Content already registered");
  FakeKitContent.validate(content);if(CONTENT_OWNERS.containsKey(content.component())||content.component().getParent()!=null)throw new IllegalArgumentException("Content is already owned or mounted");CONTENT_OWNERS.put(content.component(),this);contents.put(content.id(),content);
 }
 private void dispose(CustomContent content){
  // Claim removal before invoking plugin code; reentrant close may consume the remaining snapshot.
  if(contents.get(content.id())!=content)return;contents.remove(content.id());CONTENT_OWNERS.remove(content.component(),this);try{content.lifetime().close();}catch(Throwable failure){owner.failed();}}
 @Override public void close(){if(!closed.compareAndSet(false,true))return;Runnable cleanup=() -> {for(var value:secrets.values())Arrays.fill(value,(char)0);secrets.clear();fieldText.clear();fieldSelections.clear();for(var content:List.copyOf(contents.values()))dispose(content);contents.clear();events=e -> {};hostEvents=e -> false;hostSpec=v -> v;mount=null;owner.removed(this);};if(EventQueue.isDispatchThread())cleanup.run();else EventQueue.invokeLater(cleanup);}
    static List<Nodes.Node> children(Nodes.Node node) {
        var result = new ArrayList<Nodes.Node>();
        switch (node) {
            case Nodes.Panel n -> { result.add(n.header()); n.searchOrNavigation().ifPresent(result::add); result.add(n.body()); n.footer().ifPresent(result::add); }
            case Nodes.Header n -> { n.context().ifPresent(result::add); n.primary().ifPresent(result::add); }
            case Nodes.Stack n -> result.addAll(n.children());
            case Nodes.ItemList n -> { n.addAction().ifPresent(result::add); for(var item:n.items()) { item.leading().ifPresent(result::add);result.addAll(item.title());result.addAll(item.subtitle());result.addAll(item.trailing()); } }
            case Nodes.Card n -> result.addAll(n.children());
            case Nodes.Split n -> result.addAll(n.children());
            case Nodes.FormRow n -> { result.add(n.control()); n.trailing().ifPresent(result::add); }
            case Nodes.SettingRow n -> result.add(n.control());
            case Nodes.Section n -> result.add(n.body());
            case Nodes.Empty n -> { result.add(n.icon()); result.addAll(n.actions()); }
            case Nodes.Form n -> { n.hero().ifPresent(result::add); result.addAll(n.sections()); n.preview().ifPresent(result::add); result.addAll(n.actions()); }
            default -> {}
        }
        return result;
    }
}
