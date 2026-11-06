/*
 * The Browser's Copy on select: while the setting is on, the text the user selects in a page is copied once they finish
 * selecting it. Jasper runs this function, called with the longest text it copies, in its isolated world of the page's
 * main frame through DevTools (Runtime.evaluate), 100 ms after the user's last selection gesture, and reads back what it
 * returns: {kind: 'text', text} for the text selected, possibly empty; {kind: 'frame'} when the focus is in a frame of
 * another origin, whose document this frame cannot reach and whose selection Jasper does not copy; {kind: 'none'} for a
 * password field, a field whose type has no selection, a selection longer than the limit, or a failure. The isolated
 * world shares the page's nodes but none of its scripts' globals, so no page script can replace getSelection, toString
 * or a field's value to steer what is copied.
 *
 * The focus is followed from the main frame's document into same-origin frames and open shadow roots. A focused <input>
 * or <textarea> answers the selected part of its value, and never a password field's. Anything else answers the
 * selection of the deepest document reached.
 */
(function (maxLength) {
  'use strict';
  // Far more frames and shadow roots than a page nests; the walk ends there whatever a page does.
  const MAX_STEPS = 100;
  const NONE = {kind: 'none'};

  function selected(value) {
    return value.length > maxLength ? NONE : {kind: 'text', text: value};
  }

  try {
    let doc = document;
    let element = doc.activeElement;
    for (let step = 0; element; step++) {
      if (step >= MAX_STEPS) return NONE;
      if (element.shadowRoot && element.shadowRoot.activeElement) {
        element = element.shadowRoot.activeElement;
      } else if (element.localName === 'iframe' || element.localName === 'frame') {
        const inner = element.contentDocument; // null for a cross-origin frame
        if (!inner) return {kind: 'frame'};
        doc = inner;
        element = inner.activeElement;
      } else {
        break;
      }
    }
    if (element && (element.localName === 'input' || element.localName === 'textarea')) {
      if (element.type === 'password') return NONE; // never a password field's text
      const start = element.selectionStart, end = element.selectionEnd;
      if (typeof start !== 'number' || typeof end !== 'number') return NONE;
      return selected(String(element.value).slice(start, end));
    }
    const selection = doc.getSelection();
    return selected(selection ? String(selection) : '');
  } catch (failed) {
    return NONE;
  }
})
