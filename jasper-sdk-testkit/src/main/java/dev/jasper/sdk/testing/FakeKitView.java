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
 /** Per field, the suggestion the person moved the selection to while it is listed; absent for the first. */
 private final Map<String,String> suggestionSelections=new HashMap<>();
 /** Fields whose suggestions a choice, Escape or the focus moving on hid until the person next edits the field. */
 private final Set<String> suggestionsHidden=new HashSet<>();
 /** Per token field, the tokens it shows: the descriptor's, then the person's changes. */
 private final Map<String,List<String>> tokenLists=new HashMap<>();
 /** Per token field, the text typed in it that is no token yet. */
 private final Map<String,String> tokenTexts=new HashMap<>();
 /** The token field whose change is being reported: an update meanwhile replaces its tokens whenever it differs from them. */
 private String notifyingTokens;
 /** Per token field, the token whose chip the keyboard has selected; absent while the typed text has the keys. */
 private final Map<String,String> tokenSelections=new HashMap<>();
 FakeKitView(FakeKit owner, ViewSpec spec, Consumer<UiEvent> events) {this.owner=owner;this.events=Objects.requireNonNull(events);update(spec);}
 void guard(){owner.guard();if(closed.get())throw new IllegalStateException("View is closed");}
 @Override public UiAnchor anchor(String id){guard();var anchor=new Attachment(id,null);if(!anchor.isOpen())throw new IllegalArgumentException("Unmounted or ineligible control");anchors.add(anchor);return anchor;}
 /** As the host: an anchor on an area inside a live Custom node's registered content, which a resize of the content closes. */
 @Override public UiAnchor anchorAt(String id,ContentArea area){guard();Objects.requireNonNull(id,"customId");Objects.requireNonNull(area,"area");var anchor=new Attachment(id,area);if(!anchor.isOpen())throw new IllegalArgumentException("Unmounted or ineligible Custom content");
  if(!anchor.inside()){anchor.close();throw new IllegalArgumentException("An area must lie inside its content");}anchors.add(anchor);return anchor;}
 /** The registered component of Custom node {@code id}, or null when it is no Custom node or has none. */
 private javax.swing.JComponent registered(String id){return mounted(spec.root()).get(id) instanceof Nodes.Custom custom && contents.get(custom.contentId()) instanceof CustomContent content && content.kind()==custom.kind() ? content.component() : null;}
 final class Attachment implements UiAnchor {
  final Object host=owner.anchorHost, mounted=mount;
  final String id;final List<String> ancestry;final Object kind;boolean done;final List<Runnable> listeners=new ArrayList<>();
  /** The area of a Custom node's content this anchor is on ({@code anchorAt}); null for a control's anchor. */
  final ContentArea area;
  /** An area anchor's content and its size when minted, as the host's anchor holds them. */
  private final javax.swing.JComponent content;private final java.awt.Dimension size;
  private final java.awt.event.ComponentListener resized=new java.awt.event.ComponentAdapter(){@Override public void componentResized(java.awt.event.ComponentEvent e){isOpen();}};
  Attachment(String id,ContentArea area){this.id=id;this.area=area;kind=kind(mounted(spec.root()).get(id));ancestry=path(spec.root(),id);
   content=area==null?null:registered(id);size=content==null?null:content.getSize();if(content!=null)content.addComponentListener(resized);}
  private Object kind(Nodes.Node node){if(area!=null)return node instanceof Nodes.Custom ? Nodes.Custom.class : null;return node instanceof Nodes.Button ? Nodes.Button.class : node instanceof Nodes.MenuButton ? Nodes.MenuButton.class : node instanceof Nodes.Choice c && c.kind()==Nodes.ChoiceKind.PICKER ? c.kind() : null;}
  private boolean contentHolds(){return area==null||content!=null&&registered(id)==content&&content.getSize().equals(size);}
  /** Whether the area lies inside the content's size at minting; an empty area on its edge does, as the host's. */
  boolean inside(){return area.x()>=0&&area.y()>=0&&(long)area.x()+area.width()<=size.width&&(long)area.y()+area.height()<=size.height;}
  private boolean eligible(Nodes.Node node,boolean parent){boolean live=parent&&node.state().visible()&&node.state().enabled()&&!node.state().busy();if(node.id().equals(id))return live;for(var child:mountedChildren(node))if(eligible(child,live))return true;return false;}
  FakeUi.FakeWindow ownerSurface(){return mounted instanceof FakeUi.SurfaceMount slot ? slot.surface() : mounted instanceof FakeUi.FakeWindow window ? window : null;}
  @Override public boolean isOpen(){if(!done&&(closed.get()||mounted==null||mount!=mounted||host==null||kind==null||!kind.equals(kind(mounted(spec.root()).get(id)))||!ancestry.equals(path(spec.root(),id))||!eligible(spec.root(),true)||!contentHolds()||ownerSurface()!=null&&(!ownerSurface().shown||ownerSurface().closed)))close();return !done;}
  @Override public dev.jasper.sdk.Subscription onClosed(Runnable listener){Objects.requireNonNull(listener);if(!isOpen()){listener.run();return () -> {}; }listeners.add(listener);return () -> listeners.remove(listener);}
  @Override public void close(){if(done)return;done=true;if(content!=null)content.removeComponentListener(resized);anchors.remove(this);for(var listener:List.copyOf(listeners))listener.run();listeners.clear();}
 }
 @Override public ViewSpec spec(){guard();return spec;}
 /** Host-only original plugin descriptor, before draft/search row projection. */
 ViewSpec sourceSpec(){guard();return sourceSpec;}
 @Override public void update(ViewSpec next){guard();Objects.requireNonNull(next);if(updating)throw new IllegalStateException("Reentrant view update");updating=true;try{owner.validateIcons(next);var source=next;next=hostSpec.apply(next);if(next!=source)owner.validateIcons(next);var nodes=mounted(next.root());
  secrets.entrySet().removeIf(e -> {if(nodes.get(e.getKey()) instanceof Nodes.Secret nextSecret && mounted(spec.root()).get(e.getKey()) instanceof Nodes.Secret oldSecret && nextSecret.kind()==oldSecret.kind())return false;Arrays.fill(e.getValue(),(char)0);return true;});
  for(var content:List.copyOf(contents.values()))if(nodes.values().stream().noneMatch(n -> n instanceof Nodes.Custom c && c.contentId().equals(content.id()) && c.kind()==content.kind() && mounted(spec.root()).get(c.id()) instanceof Nodes.Custom previous && previous.contentId().equals(content.id()) && previous.kind()==content.kind())){dispose(content);}
  fieldText.keySet().removeIf(id -> !(nodes.get(id) instanceof Nodes.Field));
  fieldSelections.keySet().removeIf(id -> !(nodes.get(id) instanceof Nodes.Field));
  suggestionSelections.keySet().removeIf(id -> !(nodes.get(id) instanceof Nodes.Field));
  suggestionsHidden.removeIf(id -> !(nodes.get(id) instanceof Nodes.Field));
  var previous=spec==null ? Map.<String,Nodes.Node>of() : mounted(spec.root());
  for(var node:nodes.values())if(node instanceof Nodes.Field field) {
   if(!(previous.get(field.id()) instanceof Nodes.Field old) || old.kind()!=field.kind() || !old.value().equals(field.value())) {
    fieldText.put(field.id(),field.value());fieldSelections.remove(field.id());
   }
   // The host makes a field of another kind afresh; a selection whose suggestion is gone falls back to the first.
   if(!(previous.get(field.id()) instanceof Nodes.Field old) || old.kind()!=field.kind()){suggestionsHidden.remove(field.id());suggestionSelections.remove(field.id());}
   var chosen=suggestionSelections.get(field.id());if(chosen!=null&&field.suggestions().stream().noneMatch(suggestion -> suggestion.id().equals(chosen)))suggestionSelections.remove(field.id());
  }
  // A token field shows a list an update changes, or one that differs while it reports a change; its typed text stays.
  tokenLists.keySet().removeIf(id -> !(nodes.get(id) instanceof Nodes.TokenField));
  tokenTexts.keySet().removeIf(id -> !(nodes.get(id) instanceof Nodes.TokenField));
  tokenSelections.keySet().removeIf(id -> !(nodes.get(id) instanceof Nodes.TokenField));
  for(var node:nodes.values())if(node instanceof Nodes.TokenField field){
   if(!(previous.get(field.id()) instanceof Nodes.TokenField old) || !old.tokens().equals(field.tokens()) || field.id().equals(notifyingTokens) && !field.tokens().equals(tokenLists.get(field.id())))
    tokenLists.put(field.id(),field.tokens());
   tokenTexts.putIfAbsent(field.id(),"");
   keepSelection(field.id());
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
 @Override public void focus(String id){guard();if(!mounted(spec.root()).containsKey(id))throw new IllegalArgumentException("Unknown node");moveFocus(id);}
 /**
  * The focus moves to {@code id}: a field it leaves hides its suggestions, and a token field it leaves adds its typed
  * text, as the host's fields do when the focus leaves them for good.
  */
 private void moveFocus(String id){String leaving=focused;if(leaving!=null&&!leaving.equals(id)&&mounted(spec.root()).get(leaving) instanceof Nodes.Field)suggestionsHidden.add(leaving);focused=id;
  if(leaving!=null&&!leaving.equals(id)&&mounted(spec.root()).get(leaving) instanceof Nodes.TokenField){commitToken(leaving);tokenSelections.remove(leaving);}}
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
 public void emit(UiEvent event){guard();if(event instanceof UiEvent.Edited edit && mounted(spec.root()).get(edit.id()) instanceof Nodes.Field field && field.readOnly())return;if(event instanceof UiEvent.Edited edit && mounted(spec.root()).get(edit.id()) instanceof Nodes.Field){fieldText.put(edit.id(),edit.text());fieldSelections.remove(edit.id());suggestionsHidden.remove(edit.id());suggestionSelections.remove(edit.id());}
  if(event instanceof UiEvent.SuggestionChosen chosen && mounted(spec.root()).get(chosen.fieldId()) instanceof Nodes.Field)suggestionsHidden.add(chosen.fieldId());
  if(event instanceof UiEvent.TokensChanged change && mounted(spec.root()).get(change.id()) instanceof Nodes.TokenField){tokenLists.put(change.id(),change.tokens());keepSelection(change.id());}try{if(hostEvents.test(event))return;events.accept(Objects.requireNonNull(event));}catch(Throwable failure){owner.failed();}}
 /** Simulate a person typing {@code text} into a field, replacing its text: it reports {@link UiEvent.Edited} as the host's field does, while a read-only field, or one that is hidden, disabled or busy (itself or a container above it), takes nothing.
 * Typed into a {@link Nodes.TokenField}, which takes the focus, it replaces the text typed there
 * ({@link #tokenText(String)}) and reports no edit, but each separator in it (a comma, a line break or a tab) adds the
 * text before it, and the changed list is
 * reported once as {@link UiEvent.TokensChanged}, as the host's token field does.
 * @param fieldId field or token field node
 * @param text the text typed */
 public void type(String fieldId,String text){guard();Objects.requireNonNull(text);if(mounted(spec.root()).get(fieldId) instanceof Nodes.TokenField){typeTokens(fieldId,text);return;}
  if(!(mounted(spec.root()).get(fieldId) instanceof Nodes.Field field)||field.presentation()==Nodes.FieldPresentation.SLIDER)throw new IllegalArgumentException("Not a field node");if(field.readOnly()||!live(fieldId))return;emit(new UiEvent.Edited(fieldId,text));}
 /**
 * Simulate a person clicking into a field, as the host's field takes the click: the field takes the focus
 * ({@link #focused()}); a {@code selectOnClick} field that did not have the focus selects all its text
 * ({@link #fieldSelection(String)}), and any other click selects nothing. A field that is hidden, disabled or busy
 * (itself or a container above it) takes no click.
 * @param fieldId field node
 * @since 0.27.0 */
 public void clickInto(String fieldId){guard();if(!(mounted(spec.root()).get(fieldId) instanceof Nodes.Field field)||field.presentation()==Nodes.FieldPresentation.SLIDER)throw new IllegalArgumentException("Not a field node");if(!live(fieldId))return;boolean entering=!fieldId.equals(focused);moveFocus(fieldId);if(field.selectOnClick()&&entering)fieldSelections.put(fieldId,new int[]{0,fieldText.get(fieldId).length()});else fieldSelections.remove(fieldId);}
 /**
 * The suggestions field {@code fieldId} shows in its list, as the host's field shows them: its descriptor's
 * {@code suggestions} while the field takes input (it and every container above it shown, enabled and not busy), until a
 * chosen suggestion, Escape or the focus moving to another node hides them; the person's next edit
 * ({@link #type(String, String)}, or an {@link UiEvent.Edited} emitted for the field) shows them again.
 * @param fieldId field node
 * @return the suggestions shown, in order; empty while none show
 * @since 0.27.0 */
 public List<Nodes.Suggestion> suggestions(String fieldId){var field=suggesting(fieldId);return listShows(field)?field.suggestions():List.of();}
 /**
 * The suggestion selected in field {@code fieldId}'s list: the first, until UP and DOWN ({@link #press(String, String)})
 * move the selection; an update keeps it while its suggestion is still listed, and an edit selects the first again.
 * @param fieldId field node
 * @return the selected suggestion's id; empty while no suggestion shows
 * @since 0.27.0 */
 public Optional<String> selectedSuggestion(String fieldId){var field=suggesting(fieldId);return listShows(field)&&!field.suggestions().isEmpty()?Optional.of(field.suggestions().get(selectedSuggestionRow(field)).id()):Optional.empty();}
 /**
 * Simulate a person pressing {@code key} in field {@code fieldId}, which takes the focus, as the host's field takes the
 * key. While the field shows suggestions, UP and DOWN move the selection within them, and TAB and ENTER hide them and
 * report {@link UiEvent.SuggestionChosen} for the selected one, {@code byTab} for TAB; while its list shows at all (also
 * while it only loads), ESCAPE hides it and reports the field's {@code cancelAction} when that is not blank. Otherwise
 * ENTER reports the {@code trailingAction} of a single-line field other than PATH, and ESCAPE the {@code cancelAction},
 * when not blank. A field that is hidden, disabled or busy (itself or a container above it) takes no key.
 * <p>In a {@link Nodes.TokenField}, as the host's: ENTER adds the typed text as a token while text other
 * than white space is typed, and reports nothing otherwise; BACK_SPACE deletes the typed text's last character, or with
 * nothing typed removes the last token; LEFT with nothing typed selects the last token's chip
 * ({@link #selectedToken(String)}). While a chip is selected, LEFT, RIGHT, HOME and END move among the chips (RIGHT past
 * the last returns to the typed text), BACK_SPACE and DELETE remove its token and select the one before or after it
 * (none when it was the last), ESCAPE returns to the typed text, and ENTER returns there and does what it does there.
 * Each change is reported as {@link UiEvent.TokensChanged}.
 * @param fieldId field or token field node
 * @param key UP, DOWN, TAB, ENTER or ESCAPE in a field; ENTER, BACK_SPACE, LEFT, RIGHT, HOME, END, DELETE or ESCAPE in a
 *     token field
 * @since 0.27.0 */
 public void press(String fieldId,String key){guard();if(mounted(spec.root()).get(fieldId) instanceof Nodes.TokenField){pressToken(fieldId,key);return;}
  var field=suggesting(fieldId);Objects.requireNonNull(key);
  if(!List.of("UP","DOWN","TAB","ENTER","ESCAPE").contains(key))throw new IllegalArgumentException("Not a key a field takes: "+key);
  if(!live(fieldId))return;moveFocus(fieldId);boolean shows=listShows(field),offers=shows&&!field.suggestions().isEmpty();var rows=field.suggestions();
  switch(key){
   case "UP","DOWN" -> {if(offers)suggestionSelections.put(fieldId,rows.get(Math.max(0,Math.min(rows.size()-1,selectedSuggestionRow(field)+(key.equals("UP")?-1:1)))).id());}
   case "TAB","ENTER" -> {if(offers){var chosen=rows.get(selectedSuggestionRow(field)).id();suggestionsHidden.add(fieldId);emit(new UiEvent.SuggestionChosen(fieldId,chosen,key.equals("TAB")));}
    else if(key.equals("ENTER")&&field.kind()!=Nodes.FieldKind.TEXTAREA&&field.kind()!=Nodes.FieldKind.PATH&&!field.trailingAction().isBlank())emit(new UiEvent.Action(field.trailingAction()));}
   default -> {if(shows)suggestionsHidden.add(fieldId);if(!field.cancelAction().isBlank())emit(new UiEvent.Action(field.cancelAction()));}
  }
 }
 /**
 * Simulate a person choosing an item of node {@code nodeId}'s menu, as the host's menus take the choice: a menu button's
 * items, or a button's hold menu. {@code path} names the item by its id, or a submenu's id and then one of its
 * children's, and so on down. An enabled item reports its action as {@link UiEvent.Action}. A disabled item, one under
 * a disabled submenu (which does not open), a separator, a submenu itself, and every item of a node that takes no input
 * (it or a container above it hidden, disabled or busy) report nothing.
 * @param nodeId a menu button, or a button with a hold menu
 * @param path item ids, from the menu down
 * @throws IllegalArgumentException when the node is neither, or the path names no item of its menu
 * @since 0.27.0 */
 public void choose(String nodeId,String... path){guard();Objects.requireNonNull(path);
  List<MenuItem> items=switch(mounted(spec.root()).get(nodeId)){case Nodes.MenuButton menu->menu.items();case Nodes.Button button->button.holdMenu();case null,default->throw new IllegalArgumentException("Not a menu button or button: "+nodeId);};
  if(path.length==0)throw new IllegalArgumentException("No menu item named");
  boolean reachable=true;MenuItem item=null;
  for(String id:path){if(item!=null){reachable&=item.enabled();items=item.children();}
   item=items.stream().filter(candidate->candidate.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("No menu item "+id+" in "+nodeId));}
  if(!reachable||!item.enabled()||item.separator()||!item.children().isEmpty()||!live(nodeId))return;
  emit(new UiEvent.Action(item.action()));
 }
 /**
 * The tokens token field {@code fieldId} shows, as the host's chips show them: its descriptor's, then each change the
 * person makes; an update that changes {@code tokens}, or one that comes while the field reports a change and differs
 * from what it shows, replaces them.
 * @param fieldId token field node
 * @return the tokens, in order
 * @since 0.29.0 */
 public List<String> tokens(String fieldId){tokenField(fieldId);return tokenLists.get(fieldId);}
 /**
 * The text typed in token field {@code fieldId} that is no token yet; an update keeps it.
 * @param fieldId token field node
 * @return the typed text; empty for none
 * @since 0.29.0 */
 public String tokenText(String fieldId){tokenField(fieldId);return tokenTexts.get(fieldId);}
 /**
 * Simulate a person clicking the remove button of token {@code token} in token field {@code fieldId}: it reports the list
 * without it as {@link UiEvent.TokensChanged}, while a token field that is hidden, disabled or busy (itself or a
 * container above it) takes no click.
 * @param fieldId token field node
 * @param token a token the field shows
 * @throws IllegalArgumentException when the field shows no such token
 * @since 0.29.0 */
 public void removeToken(String fieldId,String token){tokenField(fieldId);Objects.requireNonNull(token);var shown=tokenLists.get(fieldId);
  if(!shown.contains(token))throw new IllegalArgumentException("No token "+token+" in "+fieldId);if(!live(fieldId))return;var next=new ArrayList<>(shown);next.remove(token);changeTokens(fieldId,next);}
 /**
 * The token whose chip the keyboard has selected in token field {@code fieldId} ({@link #press(String, String)}), the
 * focus staying in the field; typing, a focus move, or a change that removes the token clears it.
 * @param fieldId token field node
 * @return the selected token; empty while the typed text has the keys
 * @since 0.29.0 */
 public Optional<String> selectedToken(String fieldId){tokenField(fieldId);return Optional.ofNullable(tokenSelections.get(fieldId));}
 private Nodes.TokenField tokenField(String fieldId){guard();if(!(mounted(spec.root()).get(fieldId) instanceof Nodes.TokenField field))throw new IllegalArgumentException("Not a token field node");return field;}
 private void typeTokens(String fieldId,String text){tokenField(fieldId);if(!live(fieldId))return;moveFocus(fieldId);tokenSelections.remove(fieldId);String[] parts=text.split("[,\\r\\n\\t]",-1);tokenTexts.put(fieldId,parts[parts.length-1]);
  if(parts.length>1)addTokens(fieldId,Arrays.asList(parts).subList(0,parts.length-1));}
 private void pressToken(String fieldId,String key){tokenField(fieldId);Objects.requireNonNull(key);
  if(!List.of("ENTER","BACK_SPACE","LEFT","RIGHT","HOME","END","DELETE","ESCAPE").contains(key))throw new IllegalArgumentException("Not a key a token field takes: "+key);
  if(!live(fieldId))return;moveFocus(fieldId);String typed=tokenTexts.get(fieldId);var shown=tokenLists.get(fieldId);String chosen=tokenSelections.get(fieldId);
  if(chosen!=null){int at=shown.indexOf(chosen);
   switch(key){
    case "LEFT" -> tokenSelections.put(fieldId,shown.get(Math.max(0,at-1)));
    case "RIGHT" -> {if(at+1<shown.size())tokenSelections.put(fieldId,shown.get(at+1));else tokenSelections.remove(fieldId);}
    case "HOME" -> tokenSelections.put(fieldId,shown.getFirst());
    case "END" -> tokenSelections.put(fieldId,shown.getLast());
    case "BACK_SPACE","DELETE" -> {var next=new ArrayList<>(shown);next.remove(at);
     if(next.isEmpty())tokenSelections.remove(fieldId);else tokenSelections.put(fieldId,next.get(key.equals("BACK_SPACE")?Math.max(0,at-1):Math.min(at,next.size()-1)));
     changeTokens(fieldId,next);}
    case "ESCAPE" -> tokenSelections.remove(fieldId);
    default -> {tokenSelections.remove(fieldId);if(!typed.isBlank())commitToken(fieldId);}
   }
   return;}
  switch(key){
   case "ENTER" -> {if(!typed.isBlank())commitToken(fieldId);}
   case "BACK_SPACE" -> {if(!typed.isEmpty())tokenTexts.put(fieldId,typed.substring(0,typed.offsetByCodePoints(typed.length(),-1)));else if(!shown.isEmpty())changeTokens(fieldId,shown.subList(0,shown.size()-1));}
   // The fake's caret is at the end of the typed text: Left reaches the chips only when nothing is typed.
   case "LEFT" -> {if(typed.isEmpty()&&!shown.isEmpty())tokenSelections.put(fieldId,shown.getLast());}
   default -> {}
  }}
 /** A selection whose token the field no longer shows is cleared. */
 private void keepSelection(String fieldId){var chosen=tokenSelections.get(fieldId);if(chosen!=null&&!tokenLists.get(fieldId).contains(chosen))tokenSelections.remove(fieldId);}
 /** The typed text becomes a token, or is dropped when the token rule refuses it; it is cleared either way. */
 private void commitToken(String fieldId){if(!live(fieldId))return;String typed=tokenTexts.get(fieldId);if(typed.isEmpty())return;tokenTexts.put(fieldId,"");addTokens(fieldId,List.of(typed));}
 private void addTokens(String fieldId,List<String> typed){var next=new ArrayList<>(tokenLists.get(fieldId));next.addAll(typed);changeTokens(fieldId,Nodes.TokenField.kept(next));}
 private void changeTokens(String fieldId,List<String> next){if(next.equals(tokenLists.get(fieldId)))return;var copy=List.copyOf(next);tokenLists.put(fieldId,copy);keepSelection(fieldId);String prior=notifyingTokens;notifyingTokens=fieldId;
  try{emit(new UiEvent.TokensChanged(fieldId,copy));}finally{notifyingTokens=prior;}}
 private Nodes.Field suggesting(String fieldId){guard();if(!(mounted(spec.root()).get(fieldId) instanceof Nodes.Field field)||field.presentation()==Nodes.FieldPresentation.SLIDER)throw new IllegalArgumentException("Not a field node");return field;}
 /** Whether the field's list shows: the field takes input, has suggestions or loads them, and nothing has hidden the list. */
 private boolean listShows(Nodes.Field field){return live(field.id())&&!suggestionsHidden.contains(field.id())&&(field.suggestionsLoading()||!field.suggestions().isEmpty());}
 private int selectedSuggestionRow(Nodes.Field field){var rows=field.suggestions();for(int row=0;row<rows.size();row++)if(rows.get(row).id().equals(suggestionSelections.get(field.id())))return row;return 0;}
 private boolean live(String id){var nodes=mounted(spec.root());for(String step:path(spec.root(),id)){var state=nodes.get(step).state();if(!state.visible()||!state.enabled()||state.busy())return false;}return true;}
 void contentFont(java.awt.Font font){for(var content:List.copyOf(contents.values()))content.component().setFont(font);}
 @Override public void registerContent(CustomContent content){guard();Objects.requireNonNull(content);
  if(mounted(spec.root()).values().stream().noneMatch(n -> n instanceof Nodes.Custom c && c.contentId().equals(content.id())&&c.kind()==content.kind()))throw new IllegalArgumentException("No matching Custom content");
  if(contents.containsKey(content.id()))throw new IllegalArgumentException("Content already registered");
  FakeKitContent.validate(content);if(CONTENT_OWNERS.containsKey(content.component())||content.component().getParent()!=null)throw new IllegalArgumentException("Content is already owned or mounted");CONTENT_OWNERS.put(content.component(),this);contents.put(content.id(),content);content.component().setFont(owner.contentFont());
 }
 private void dispose(CustomContent content){
  // Claim removal before invoking plugin code; reentrant close may consume the remaining snapshot.
  if(contents.get(content.id())!=content)return;contents.remove(content.id());CONTENT_OWNERS.remove(content.component(),this);try{content.lifetime().close();}catch(Throwable failure){owner.failed();}}
 @Override public void close(){if(!closed.compareAndSet(false,true))return;Runnable cleanup=() -> {invalidateAnchors();for(var value:secrets.values())Arrays.fill(value,(char)0);secrets.clear();fieldText.clear();fieldSelections.clear();suggestionSelections.clear();suggestionsHidden.clear();tokenLists.clear();tokenTexts.clear();tokenSelections.clear();for(var content:List.copyOf(contents.values()))dispose(content);contents.clear();events=e -> {};hostEvents=e -> false;hostSpec=v -> v;mount=null;owner.removed(this);};if(EventQueue.isDispatchThread())cleanup.run();else EventQueue.invokeLater(cleanup);}
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
