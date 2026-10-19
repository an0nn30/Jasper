/**
 * Long-running work a plugin volunteers to show: begin an activity, report progress, end it once; or announce a
 * connection, report its state as it changes and close it. Jasper posts a Buddy card for each running activity and
 * each connection, which the user sees while Jasper (Show Jasper, {@code buddy.enabled}, off by default) is shown.
 * Depends on {@code dev.jasper.sdk.events}.
 */
package dev.jasper.sdk.activity;
