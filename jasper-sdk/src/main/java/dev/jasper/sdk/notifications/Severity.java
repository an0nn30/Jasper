package dev.jasper.sdk.notifications;

/** How serious a notification is; it picks the default icon. */
public enum Severity {
    /** Something happened. */
    INFO,
    /** Something may need attention. */
    WARNING,
    /** Something failed. */
    ERROR
}
