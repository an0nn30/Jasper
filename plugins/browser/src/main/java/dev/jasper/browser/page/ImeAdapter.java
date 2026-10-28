// Adapted from JetBrains/jcef java_tests/tests (CefInputMethodAdapter.java) at 9df0879, under JCEF's BSD-3-Clause licence.
package dev.jasper.browser.page;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.font.TextHitInfo;
import java.awt.im.InputMethodRequests;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.CharacterIterator;
import java.util.List;
import javax.swing.JComponent;
import org.cef.browser.CefBrowser;
import org.cef.input.CefCompositionUnderline;
import org.cef.misc.CefRange;

/**
 * An input method's side of a page: hands the text an input method composes and commits to the page, and tells the
 * input method where the page's composition sits. Input method events arrive on the UI thread; the page reports its
 * composition and selection on Chromium's thread.
 */
final class ImeAdapter implements InputMethodRequests, InputMethodListener {
    private static final CefRange NO_RANGE = new CefRange(-1, -1);
    private final JComponent view;
    private volatile CefBrowser browser;
    private volatile String selectedText = "";
    private volatile Rectangle[] characterBounds = {};

    ImeAdapter(JComponent view) {
        this.view = view;
    }

    void setBrowser(CefBrowser browser) {
        this.browser = browser;
    }

    @Override public void inputMethodTextChanged(InputMethodEvent event) {
        CefBrowser page = browser;
        if (page == null) return;
        int committed = event.getCommittedCharacterCount();
        AttributedCharacterIterator text = event.getText();
        if (text == null) { // the input method removed its composed text
            page.ImeCancelComposing();
            event.consume();
            return;
        }
        var buffer = new StringBuilder();
        char c = text.first();
        for (int left = committed; left > 0 && c != CharacterIterator.DONE; left--) {
            buffer.append(c);
            c = text.next();
        }
        if (!buffer.isEmpty()) page.ImeCommitText(buffer.toString(), NO_RANGE, 0);
        buffer.setLength(0);
        while (c != CharacterIterator.DONE) {
            buffer.append(c);
            c = text.next();
        }
        String composition = buffer.toString();
        if (!composition.isEmpty()) {
            Color none = new Color(0, true);
            var underline = new CefCompositionUnderline(new CefRange(0, composition.length()), none, none, 0,
                CefCompositionUnderline.Style.SOLID);
            page.ImeSetComposition(composition, List.of(underline), NO_RANGE, NO_RANGE);
        } else if (committed == 0) {
            page.ImeCancelComposing();
        }
        event.consume();
    }

    @Override public void caretPositionChanged(InputMethodEvent event) { }

    /** Where the composition's first character is, on screen. */
    @Override public Rectangle getTextLocation(TextHitInfo offset) {
        Rectangle[] boxes = characterBounds;
        Rectangle result = boxes.length == 0 ? new Rectangle(0, 0) : new Rectangle(boxes[0]);
        if (view.isShowing()) {
            Point at = view.getLocationOnScreen();
            result.translate(at.x, at.y);
        }
        return result;
    }

    /** The composition's character under a point on screen, or null. */
    @Override public TextHitInfo getLocationOffset(int x, int y) {
        if (!view.isShowing()) return null;
        Point at = view.getLocationOnScreen();
        Point inView = new Point(x - at.x, y - at.y); // the page reports character bounds in view coordinates
        Rectangle[] boxes = characterBounds;
        for (int i = 0; i < boxes.length; i++)
            if (boxes[i].contains(inView)) return TextHitInfo.leading(i);
        return null;
    }

    @Override public int getInsertPositionOffset() { return 0; }

    @Override public AttributedCharacterIterator getCommittedText(int beginIndex, int endIndex,
                                                                  AttributedCharacterIterator.Attribute[] attributes) {
        return new AttributedString("").getIterator();
    }

    @Override public int getCommittedTextLength() { return 0; }

    @Override public AttributedCharacterIterator cancelLatestCommittedText(AttributedCharacterIterator.Attribute[] attributes) {
        return new AttributedString("").getIterator();
    }

    @Override public AttributedCharacterIterator getSelectedText(AttributedCharacterIterator.Attribute[] attributes) {
        return new AttributedString(selectedText).getIterator();
    }

    /** The page's composition moved. Chromium's thread. */
    void compositionChanged(Rectangle[] bounds) {
        characterBounds = bounds == null ? new Rectangle[0] : bounds.clone();
    }

    /** The page's selected text changed. Chromium's thread. */
    void selectionChanged(String text) {
        selectedText = text == null ? "" : text;
    }
}
