package dev.jasper.app.testsupport;

import java.awt.Component;
import java.awt.Container;

/** Shared headless test fixture; never shipped. */
public final class LayoutTestSupport {
    public static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container nested) layoutTree(nested);
    }
}
