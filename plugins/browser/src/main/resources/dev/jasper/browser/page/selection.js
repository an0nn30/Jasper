/*
 * The Browser's Copy on select: while the setting is on, the text the user selects in a page is copied once they finish
 * selecting it. Jasper runs this function, called with the longest text it copies, in its isolated world of the page's
 * main frame through DevTools (Runtime.evaluate), 100 ms after the user's last selection gesture, and reads back what it
 * returns: {kind: 'text', text} for the text selected, possibly empty; {kind: 'frame'} when the focus is in a frame of
 * another origin, whose document this frame cannot reach and whose selection Jasper does not copy; {kind: 'none'} for a
 * selection that has not changed since the last read, a password field or one CSS masks, a field whose type has no
 * selection, a selection longer than the limit, or a failure. The isolated world shares the page's nodes but none of its
 * scripts' globals, so no page script can replace getSelection, toString or a field's value to steer what is copied.
 *
 * The focus is followed from the main frame's document into same-origin frames (any element with a document of its own:
 * an iframe, a frame or an object) and open shadow roots. A focused <input> or <textarea> answers the selected part of
 * its value, and never a password field's or one whose -webkit-text-security masks it. Anything else answers the
 * selection of the deepest document reached. Non-breaking spaces copy as spaces, so a command copied from a page runs in
 * a shell.
 *
 * Only a changed selection is copied: a selectionchange since the last read, at the document or at the focused field,
 * which a listener in Jasper's world counts, so a drag of a scrollbar, a map or a slider, or a double click on a button,
 * does not copy the old selection again over what the user copied since. The first read of a document or a field, which
 * Jasper only then listens to, has nothing to compare against and answers what is selected. The listener and its flags
 * live in Jasper's world, which the page cannot see.
 */
(function (maxLength) {
  'use strict';
  // Far more frames and shadow roots than a page nests; the walk ends there whatever a page does.
  const MAX_STEPS = 100;
  const NONE = {kind: 'none'};
  // Each document's and field's flag: whether a selectionchange reached it since its last read.
  const flags = globalThis.jasperSelectionChanges || (globalThis.jasperSelectionChanges = new WeakMap());

  // Whether a selectionchange reached target since its last read; it then counts as read. The first read of a target,
  // which Jasper only now listens to, has nothing to compare against.
  function changed(target) {
    const flag = flags.get(target);
    if (!flag) {
      const fresh = {changed: false};
      flags.set(target, fresh);
      target.addEventListener('selectionchange', () => { fresh.changed = true; }, true);
      return true;
    }
    const was = flag.changed;
    flag.changed = false;
    return was;
  }

  function selected(value) {
    return value.length > maxLength ? NONE : {kind: 'text', text: value.replace(/\u00a0/g, ' ')};
  }

  try {
    let doc = document;
    let element = doc.activeElement;
    for (let step = 0; element; step++) {
      if (step >= MAX_STEPS) return NONE;
      if (element.shadowRoot && element.shadowRoot.activeElement) {
        element = element.shadowRoot.activeElement;
      } else if ('contentDocument' in element) {
        const inner = element.contentDocument; // null for a cross-origin frame
        if (!inner) return {kind: 'frame'};
        doc = inner;
        element = inner.activeElement;
      } else {
        break;
      }
    }
    if (element && (element.localName === 'input' || element.localName === 'textarea')) {
      const fieldChanged = changed(element), documentChanged = changed(doc);
      if (element.type === 'password') return NONE; // never a password field's text
      if (doc.defaultView.getComputedStyle(element).webkitTextSecurity !== 'none') return NONE; // nor one CSS masks
      if (!fieldChanged && !documentChanged) return NONE;
      const start = element.selectionStart, end = element.selectionEnd;
      if (typeof start !== 'number' || typeof end !== 'number') return NONE;
      return selected(String(element.value).slice(start, end));
    }
    if (!changed(doc)) return NONE;
    const selection = doc.getSelection();
    return selected(selection ? String(selection) : '');
  } catch (failed) {
    return NONE;
  }
})
