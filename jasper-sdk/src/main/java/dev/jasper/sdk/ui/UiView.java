package dev.jasper.sdk.ui;
import dev.jasper.sdk.Subscription;
import java.util.function.Consumer;
/** Opaque owned view. All operations require EDT; close is idempotent and may enqueue teardown. */
public interface UiView extends Subscription {
 /** Reconcile stable ids and kinds.
 * @param spec next tree */
 void update(ViewSpec spec);
 /**
  * Mint attachment authority from an eligible mounted action or PICKER: a {@link Nodes.Button}, a PICKER
  * {@link Nodes.Choice} or, since 0.29.0, a {@link Nodes.MenuButton}, whose popovers attach to its button.
  *
  * @param nodeId control id
  * @return scoped capability
  */
 UiAnchor anchor(String nodeId);
 /**
  * Mint attachment authority from an area of a mounted, visible, enabled, not busy {@link Nodes.Custom} node with
  * registered content, in the content component's coordinates, such as a field on a page. A popover opens below the
  * area, or above it when the screen has no room below, at least as wide as the area; a dialog may take the anchor as
  * its owner. A press inside the area closes the popover and goes no further; a press elsewhere closes it and reaches
  * the content. Hiding, disabling, removing or moving the node closes the anchor, and so does resizing the content: a
  * page laid out again has moved what was in the area.
  *
  * @param customId a Custom node's id
  * @param area the area of its content
  * @return scoped capability
  * @throws IllegalArgumentException when the id names no such node, or the view is not mounted
  * @throws UnsupportedOperationException if the host predates area anchors
  * @since 0.32.0
  */
 default UiAnchor anchorAt(String customId, ContentArea area) {
  throw new UnsupportedOperationException("Area anchors are unavailable");
 }
 /** Current descriptor, without pending edits or secrets.
 * @return immutable tree */
 ViewSpec spec();
 /** Borrow an erased-in-finally secret copy.
 * @param fieldId secret node
 * @param use synchronous callback */
 void withSecret(String fieldId, Consumer<char[]> use);
 /** Erase live secret storage.
 * @param fieldId secret node */
 void clearSecret(String fieldId);
 /** Replace secret storage with a synchronous defensive copy, without emitting an edit.
  * The caller retains ownership of value and must erase it when finished.
  * @param fieldId input or detail secret node
  * @param value caller-owned characters */
 void replaceSecret(String fieldId, char[] value);
 /** Focus a node.
 * @param id node id */
 void focus(String id);
 /** Select the current live text of a Field without editing it or moving focus. Non-Field ids are rejected.
 * @param fieldId ordinary text field node */
 void selectAll(String fieldId);
 /** Scroll a node into view without moving keyboard focus, such as the first row a search matches.
 * @param id node id */
 void reveal(String id);
 /** Whether an input method is composing uncommitted text in a field; a plugin routing keys around the field
 * leaves them to the input method while this is true.
 * @param fieldId field node
 * @return true while composed text is uncommitted */
 boolean composing(String fieldId);
 /** Register approved specialized content.
 * @param content content matching a Custom node's contentId and kind */
 void registerContent(CustomContent content);
}
