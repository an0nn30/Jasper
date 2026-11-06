/*
 * The Browser's text insertion, behind Snippets in page fields: text another plugin inserts into a browser item goes
 * where the user is typing in its page. Jasper runs this function, called with the text, in an isolated world of the
 * page's main frame through DevTools (Runtime.evaluate), and reads back what it returns: true when the field took the
 * text, false when no field it reaches has the focus, and Jasper then pastes the text instead. The isolated world shares
 * the page's nodes but none of its scripts' globals, so no page script can replace execCommand, a value setter or
 * activeElement to steer the text or read it. The page still sees the input events the insertion fires, as it sees
 * typing.
 *
 * The focus is followed from the main frame's document into same-origin frames and open shadow roots. A cross-origin
 * frame, whose document this frame cannot reach, and a closed shadow root end the search with false. The field is an
 * <input> of a type that takes text (text, search, url, tel, email or password), a <textarea>, or an editable element
 * (contenteditable, or a document in design mode), and is neither disabled nor read-only. The text goes in as typing
 * would: document.execCommand('insertText') replaces the selection, fires beforeinput and input, and keeps the field's
 * undo. Where the document refuses that command, an <input> or <textarea> takes the text in its value, replacing its
 * selection, or at its end when its type has no selection, and an input event says so.
 */
(function (text) {
  'use strict';
  const TEXT_TYPES = new Set(['text', 'search', 'url', 'tel', 'email', 'password']);
  // Far more frames and shadow roots than a page nests; the walk ends there whatever a page does.
  const MAX_STEPS = 100;

  function focused() {
    let element = document.activeElement;
    for (let step = 0; element && step < MAX_STEPS; step++) {
      if (element.shadowRoot && element.shadowRoot.activeElement) {
        element = element.shadowRoot.activeElement;
      } else if (element.localName === 'iframe' || element.localName === 'frame') {
        const inner = element.contentDocument; // null for a cross-origin frame
        if (!inner) return null;
        element = inner.activeElement;
      } else {
        return element;
      }
    }
    return null;
  }

  function control(element) {
    return element.localName === 'textarea' || element.localName === 'input' && TEXT_TYPES.has(element.type);
  }

  function intoValue(element) {
    const start = element.selectionStart, end = element.selectionEnd;
    if (typeof start === 'number' && typeof end === 'number') element.setRangeText(text, start, end, 'end');
    else element.value += text;
    element.dispatchEvent(new InputEvent('input', {bubbles: true, inputType: 'insertText', data: text}));
  }

  try {
    const element = focused();
    if (!element) return false;
    const isControl = control(element);
    if (isControl ? element.disabled || element.readOnly : element.isContentEditable !== true) return false;
    let typed = false;
    try {
      typed = element.ownerDocument.execCommand('insertText', false, text);
    } catch (refused) {
      typed = false;
    }
    if (typed) return true;
    if (!isControl) return false;
    intoValue(element);
    return true;
  } catch (failed) {
    return false;
  }
})
