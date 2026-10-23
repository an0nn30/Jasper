package dev.jasper.sdk.ui;

import java.util.List;
import java.util.Objects;

/** An item rendered in the host status bar while its content is selected.
 * @param text literal visible text; empty shows the icon alone
 * @param tooltip literal descriptive text
 * @param run optional UI-thread action, null for plain text; not used while {@code menu} has entries
 * @param icon (since 0.21.0) a registered icon name shown before the text, or empty for none
 * @param dimmed (since 0.21.0) whether the host paints the item's icon and text in its disabled colour; the item stays
 *     clickable
 * @param menu (since 0.21.0) the entries a click opens in a menu anchored on the item, copied; empty for none
 * @since 0.8.5
 */
public record ContentStatus(String text,String tooltip,Runnable run,String icon,boolean dimmed,List<ContentStatus.MenuEntry> menu) {
    /** Validates text and copies the menu. */
    public ContentStatus {Objects.requireNonNull(text);Objects.requireNonNull(tooltip);Objects.requireNonNull(icon,"icon");menu=List.copyOf(menu);}

    /**
     * Plain text or a clickable item: no icon, not dimmed, no menu.
     *
     * @param text literal visible text
     * @param tooltip literal descriptive text
     * @param run optional UI-thread action, null for plain text
     */
    public ContentStatus(String text,String tooltip,Runnable run) {this(text,tooltip,run,"",false,List.of());}

    /**
     * One entry of a status item's menu: an item, a check item, or a separator line.
     *
     * @param label the entry's literal text; not blank unless {@code separator}
     * @param run the UI-thread action choosing the entry runs; null only for a separator
     * @param checkable whether the entry shows a check mark slot
     * @param checked whether the check mark shows; only when {@code checkable}
     * @param separator whether the entry is a separator line, which has no label, run or check mark
     * @since 0.21.0
     */
    public record MenuEntry(String label,Runnable run,boolean checkable,boolean checked,boolean separator) {
        /** Validates the label, the run and the check mark. */
        public MenuEntry {
            Objects.requireNonNull(label,"label");
            if(separator){if(run!=null||checkable||!label.isEmpty())throw new IllegalArgumentException("A separator has no label, run or check mark");}
            else {ModelChecks.label(label,"label");Objects.requireNonNull(run,"run");}
            if(checked&&!checkable)throw new IllegalArgumentException("Only a checkable entry is checked");
        }

        /**
         * An item.
         *
         * @param label the item's literal text
         * @param run the UI-thread action choosing it runs
         * @return the entry
         */
        public static MenuEntry item(String label,Runnable run) {return new MenuEntry(label,run,false,false,false);}

        /**
         * A check item.
         *
         * @param label the item's literal text
         * @param checked whether its check mark shows
         * @param run the UI-thread action choosing it runs
         * @return the entry
         */
        public static MenuEntry check(String label,boolean checked,Runnable run) {return new MenuEntry(label,run,true,checked,false);}

        /**
         * A separator line.
         *
         * @return the entry
         */
        public static MenuEntry separatorLine() {return new MenuEntry("",null,false,false,true);}
    }
}
