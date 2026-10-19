package dev.jasper.sdk;

/**
 * Something a dialog can be parented to: one of the application's Jasper windows, a plugin's content tab or
 * an anchor. Implemented only by the application and the testkit; it deliberately exposes no frame.
 */
public interface WindowOwner { }
