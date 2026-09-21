package dev.jasper.app.commands;

import java.util.List;
import java.util.Objects;
import javax.swing.Action;
import javax.swing.Icon;

public record Command(String id, Action action, List<String> keywords) {
    public static final String TITLE = "jasper.command.title";
    public static final String ICON = "jasper.command.icon";

    public Command {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,127}"))
            throw new IllegalArgumentException("Invalid command ID");
        Objects.requireNonNull(action);
        keywords = List.copyOf(keywords);
        if (!(action.getValue(Action.NAME) instanceof String title) || title.isBlank())
            throw new IllegalArgumentException("Command needs a title");
    }

    public String title() {
        Object title = action.getValue(TITLE);
        return title instanceof String value && !value.isBlank()
            ? value : (String) action.getValue(Action.NAME);
    }

    public Icon icon() {
        Object icon = action.getValue(ICON);
        return icon instanceof Icon value ? value
            : action.getValue(Action.SMALL_ICON) instanceof Icon value ? value : null;
    }
}
