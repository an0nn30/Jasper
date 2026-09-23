package dev.jasper.remote.transfer;

/** Explicit, per-target decisions. A rename carries the chosen new name in the entry target. */
public enum ConflictDecision { ASK, REPLACE, SKIP, RENAME, MERGE }
