package dev.jasper.app.plugins;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.sdk.terminal.SessionState;
import dev.jasper.sdk.terminal.TerminalEvents;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Republishes the registry's facts as SDK topics. Publishing only enqueues, so plugin handlers run later on
 * the EDT like every other event, never inside the window code that reported the fact.
 */
final class TerminalBridge {
    private TerminalBridge() { }

    static Subscription connect(TerminalRegistry registry, EventBus bus) {
        return registry.onEvent(event -> {
            switch (event) {
                case TerminalEvent.WindowOpened fact -> bus.publish(EventBus.APP, TerminalEvents.WINDOW_OPENED, new TerminalEvents.WindowEvent(fact.windowId()));
                case TerminalEvent.WindowClosed fact -> bus.publish(EventBus.APP, TerminalEvents.WINDOW_CLOSED, new TerminalEvents.WindowEvent(fact.windowId()));
                case TerminalEvent.WindowActivated fact -> bus.publish(EventBus.APP, TerminalEvents.WINDOW_ACTIVATED, new TerminalEvents.WindowEvent(fact.windowId()));
                case TerminalEvent.TabOpened fact -> bus.publish(EventBus.APP, TerminalEvents.TAB_OPENED, new TerminalEvents.TabEvent(fact.windowId(), fact.tabId()));
                case TerminalEvent.TabClosed fact -> bus.publish(EventBus.APP, TerminalEvents.TAB_CLOSED, new TerminalEvents.TabEvent(fact.windowId(), fact.tabId()));
                case TerminalEvent.TabSelected fact -> bus.publish(EventBus.APP, TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(fact.windowId(), fact.tabId()));
                case TerminalEvent.PaneOpened fact -> bus.publish(EventBus.APP, TerminalEvents.PANE_OPENED, new TerminalEvents.PaneEvent(fact.tabId(), fact.paneId()));
                case TerminalEvent.PaneClosed fact -> bus.publish(EventBus.APP, TerminalEvents.PANE_CLOSED, new TerminalEvents.PaneEvent(fact.tabId(), fact.paneId()));
                case TerminalEvent.PaneFocused fact -> bus.publish(EventBus.APP, TerminalEvents.PANE_FOCUSED, new TerminalEvents.PaneEvent(fact.tabId(), fact.paneId()));
                case TerminalEvent.ActivePaneChanged fact -> bus.publish(EventBus.APP, TerminalEvents.ACTIVE_PANE_CHANGED, new TerminalEvents.ActivePaneChanged(fact.paneId()));
                case TerminalEvent.TitleChanged fact -> bus.publish(EventBus.APP, TerminalEvents.TITLE_CHANGED, new TerminalEvents.TitleChanged(fact.paneId(), fact.title()));
                case TerminalEvent.DirectoryChanged fact -> bus.publish(EventBus.APP, TerminalEvents.CWD_CHANGED,
                    new TerminalEvents.CwdChanged(fact.paneId(), fact.directory(), Optional.empty()));
                case TerminalEvent.CommandStarted fact -> bus.publish(EventBus.APP, TerminalEvents.COMMAND_STARTED, new TerminalEvents.CommandStarted(fact.paneId(), fact.command()));
                case TerminalEvent.CommandFinished fact -> bus.publish(EventBus.APP, TerminalEvents.COMMAND_FINISHED,
                    new TerminalEvents.CommandFinished(fact.paneId(), fact.command(), fact.exitStatus(), fact.duration(), fact.directory(), Optional.empty()));
                case TerminalEvent.SessionConnecting fact -> bus.publish(EventBus.APP, TerminalEvents.SESSION_STATE_CHANGED,
                    new TerminalEvents.SessionStateChanged(fact.paneId(), SessionState.CONNECTING, OptionalInt.empty()));
                case TerminalEvent.SessionStarted fact -> bus.publish(EventBus.APP, TerminalEvents.SESSION_STATE_CHANGED,
                    new TerminalEvents.SessionStateChanged(fact.paneId(), SessionState.RUNNING, OptionalInt.empty()));
                case TerminalEvent.SessionExited fact -> bus.publish(EventBus.APP, TerminalEvents.SESSION_STATE_CHANGED,
                    new TerminalEvents.SessionStateChanged(fact.paneId(), SessionState.EXITED, fact.exitStatus()));
                case TerminalEvent.Bell fact -> bus.publish(EventBus.APP, TerminalEvents.BELL, new TerminalEvents.PaneEvent(fact.tabId(), fact.paneId()));
            }
        });
    }
}
