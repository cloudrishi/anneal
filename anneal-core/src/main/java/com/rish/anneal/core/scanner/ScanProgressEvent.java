package com.rish.anneal.core.scanner;

/**
 * Progress event emitted by {@link CodebaseScanner} during a scan.
 *
 * <p>Events are emitted in this order:
 * <ol>
 *   <li>{@link Type#FILE} — once per Java file after it has been scanned</li>
 *   <li>{@link Type#COMPLETE} — once when all files and build files have been processed</li>
 *   <li>{@link Type#ERROR} — instead of COMPLETE if the scan fails catastrophically</li>
 * </ol>
 *
 * <p>Used by {@code ScanProgressResource} to stream Server-Sent Events to the frontend.
 * Callers that don't need progress (e.g. tests, curl) pass a no-op consumer and
 * pay zero overhead.
 *
 * @param type         the event type — FILE, COMPLETE, or ERROR
 * @param file         short filename of the scanned file (FILE events only, null otherwise)
 * @param findingCount number of findings in this file (FILE events only)
 * @param filesScanned running total of files scanned so far
 * @param totalFiles   total number of Java files discovered (known after collection)
 * @param message      human-readable message (COMPLETE and ERROR events only, null for FILE)
 */
public record ScanProgressEvent(
        Type type,
        String file,
        int findingCount,
        int filesScanned,
        int totalFiles,
        String message
) {
    public enum Type { FILE, COMPLETE, ERROR }

    /** Convenience factory — FILE event after scanning one file. */
    public static ScanProgressEvent file(String fileName,
                                         int findingCount,
                                         int filesScanned,
                                         int totalFiles) {
        return new ScanProgressEvent(Type.FILE, fileName, findingCount,
                filesScanned, totalFiles, null);
    }

    /** Convenience factory — COMPLETE event after all files are processed. */
    public static ScanProgressEvent complete(int filesScanned,
                                             int totalFiles,
                                             int totalFindings) {
        return new ScanProgressEvent(Type.COMPLETE, null, totalFindings,
                filesScanned, totalFiles,
                "%d findings across %d files".formatted(totalFindings, filesScanned));
    }

    /** Convenience factory — ERROR event when the scan fails. */
    public static ScanProgressEvent error(String message) {
        return new ScanProgressEvent(Type.ERROR, null, 0, 0, 0, message);
    }
}
