package dev.jasper.app.startup;

/** This process's page faults since it started and the bytes it holds in memory, as the operating system counts them. */
public record MemoryReading(long pageFaults, long workingSetBytes) { }
