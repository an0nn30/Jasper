package dev.jasper.remote.transfer;

import dev.jasper.remote.sftp.FileEntry;
import java.util.Optional;
import java.util.UUID;

/** Durable per-entry protocol state, including creation proof and write-ahead publication evidence. */
public record TransferEntry(long id, UUID jobId, String relative, String source, String target, FileEntry sourceInfo,
                            String temporary, Optional<FileEntry> temporaryInfo, Phase phase, long confirmed,
                            String digest, Publication publication, Optional<FileEntry> expectedTarget,
                            ConflictDecision decision, Outcome outcome, String error) {
    public enum Phase { PENDING, PLANNED_TEMP, CREATED_TEMP, COPYING, PUBLISHING, COMPLETE, CLEANUP }
    public enum Publication { NONE, RENAME, LOCAL_LINK, LOCAL_SYMLINK, ATOMIC_REPLACE }
    public enum Outcome { PENDING, COMPLETE, SKIPPED, FAILED }
}
