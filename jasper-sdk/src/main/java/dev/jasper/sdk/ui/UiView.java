package dev.jasper.sdk.ui;
import dev.jasper.sdk.Subscription;
import java.util.function.Consumer;
/** Opaque owned view. All operations require EDT; close is idempotent and may enqueue teardown. */
public interface UiView extends Subscription {
 /** Reconcile stable ids and kinds.
 * @param spec next tree */
 void update(ViewSpec spec);
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
