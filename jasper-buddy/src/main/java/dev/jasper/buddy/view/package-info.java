/**
 * Supported EDT facade. Owns bounded model and lazy native presentation; close releases every child and retained host callback.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.buddy.config, dev.jasper.buddy.internal.model, dev.jasper.buddy.internal.presentation, dev.jasper.buddy.notice.
 * JDK-only module. The supported packages are config, notice and view; all internal packages are unsupported.
 */
package dev.jasper.buddy.view;
