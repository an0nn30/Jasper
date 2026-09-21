/**
 * Immutable identity and validated notice values. Construction is thread-independent. Detail/activation callbacks run on EDT and must be nonblocking; retained notices and presentation snapshots may hold them until replacement or disposal.
 * <p>Allowed outgoing Jasper dependencies: no other Jasper package.
 * JDK-only module. The supported packages are config, notice and view; all internal packages are unsupported.
 */
package dev.jasper.buddy.notice;
