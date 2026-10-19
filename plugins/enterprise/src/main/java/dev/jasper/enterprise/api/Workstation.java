package dev.jasper.enterprise.api;

/**
 * Published only while the Enterprise look is active; a plugin that finds it composes its classic layout. The look is
 * chosen when Jasper starts and changes only at a restart, so look the service up once in {@code start}.
 */
public interface Workstation { }
