package dev.jasper.remote.transfer;

import java.util.UUID;

/** Bounded queue summary; full user selections are fetched separately, never loaded by a jobs page. */
public record TransferJob(UUID id, String source, String destination, TransferState state, Intent intent,
                          long createdMillis, long totalEntries, long totalBytes, long confirmedBytes,
                          long completedEntries, long skippedEntries, long failedEntries, boolean scanned,
                          String detail, long cleanupPending, long metadataWarnings) {
    public TransferJob(UUID id,String source,String destination,TransferState state,Intent intent,long createdMillis,long totalEntries,long totalBytes,long confirmedBytes,long completedEntries,long skippedEntries,long failedEntries,boolean scanned,String detail,long cleanupPending) {
        this(id,source,destination,state,intent,createdMillis,totalEntries,totalBytes,confirmedBytes,completedEntries,skippedEntries,failedEntries,scanned,detail,cleanupPending,0);
    }
    public enum Intent { RUN, PAUSE, CANCEL }
}
