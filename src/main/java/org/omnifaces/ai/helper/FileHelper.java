/*
 * Copyright OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.ai.helper;

import static java.lang.Math.min;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isWritable;
import static java.nio.file.Files.newByteChannel;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.StandardOpenOption.DELETE_ON_CLOSE;
import static java.nio.file.StandardOpenOption.READ;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.FINEST;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.omnifaces.ai.OmniHai;

/**
 * Utility class for file I/O operations.
 *
 * @author Bauke Scholtz
 * @since 1.3
 */
public final class FileHelper {

    private static final Logger logger = Logger.getLogger(FileHelper.class.getPackageName());

    private FileHelper() {
        throw new AssertionError();
    }

    /**
     * Returns whether the creation of temporary files is supported in the current environment.
     * <p>
     * This is determined by probing the default temp directory at class-loading time. When {@code false}, callers should fall back to in-memory processing.
     *
     * @return {@code true} if temporary files can be created, {@code false} otherwise.
     */
    public static boolean tempFilesSupported() {
        return TempFiles.SUPPORTED;
    }

    /**
     * Checks that a file can be written at the given path and returns it. Anything which writes through a temporary file beside the target and renames it into
     * place needs the directory to be writable rather than the target itself, as a rename replaces the target rather than opening it, so that is what this
     * checks. The check is a best effort: the file system may still refuse the write afterwards.
     *
     * @param path The path to check.
     * @return The given path, guaranteed to name a file in an existing writable directory.
     * @throws NullPointerException when path is null.
     * @throws IllegalArgumentException when path names no file, or its directory does not exist or cannot be written to.
     * @since 1.7
     */
    public static Path requireWritableFile(Path path) {
        if (requireNonNull(path, "path").getFileName() == null || isDirectory(path)) {
            throw new IllegalArgumentException("Path must denote a file, but was " + path);
        }

        var directory = path.toAbsolutePath().getParent();

        if (!isDirectory(directory)) {
            throw new IllegalArgumentException("Directory of path does not exist: " + path);
        }

        if (!isWritable(directory)) {
            throw new IllegalArgumentException("Directory of path is not writable: " + path);
        }

        return path;
    }

    /**
     * Creates an {@link InputStream} that reads a specific byte range from the given file.
     * <p>
     * The returned stream reads from {@code startOffset} (inclusive) up to {@code endOffset} (exclusive) and must be closed by the caller.
     *
     * @param source The file to read from, must not be {@code null}.
     * @param startOffset The byte offset to start reading from, must be &gt;= 0.
     * @param endOffset The byte offset to stop reading at (exclusive), must be &gt; {@code startOffset} and &lt;= file size.
     * @return An {@link InputStream} over the specified byte range.
     * @throws IllegalArgumentException If the offsets are invalid.
     * @throws IOException If the file cannot be opened.
     */
    public static InputStream newOffsetInputStream(Path source, long startOffset, long endOffset) throws IOException {
        return new OffsetInputStream(source, startOffset, endOffset);
    }

    /**
     * Creates an {@link InputStream} that deletes the underlying file when closed.
     * <p>
     * This is useful for consuming temporary files exactly once without leaving them on disk. The returned stream must be closed by the caller.
     *
     * @param source The file to read from, must not be {@code null}.
     * @return An {@link InputStream} that deletes the file on close.
     * @throws IOException If the file cannot be opened.
     */
    public static InputStream newDeleteOnCloseInputStream(Path source) throws IOException {
        return newInputStream(source, DELETE_ON_CLOSE);
    }

    /**
     * Attempts to delete the given paths, silently falling back to {@link java.io.File#deleteOnExit()} for any files that cannot be deleted immediately.
     * <p>
     * {@code null} entries in the array are ignored.
     *
     * @param paths The paths to clean up.
     */
    public static void cleanupFiles(Path... paths) {
        for (var path : paths) {
            if (path != null) {
                try {
                    if (deleteIfExists(path)) {
                        continue;
                    }
                }
                catch (Exception e) {
                    logger.log(FINEST, e, () -> "Cannot delete file " + path);
                }

                try {
                    path.toFile().deleteOnExit();
                }
                catch (Exception e) {
                    logger.log(FINEST, e, () -> "Cannot register delete on exit for file " + path);
                }
            }
        }
    }

    /**
     * Closes the given {@link Closeable} resource, silently ignoring any {@link IOException}.
     * <p>
     * This is intended for use in cleanup blocks (like {@code finally}) where a failure to close should not suppress or mask a primary exception. If the
     * argument is {@code null}, this method does nothing.
     *
     * @param closeable The resource to close, may be {@code null}.
     */
    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            }
            catch (Exception e) {
                logger.log(FINEST, e, () -> "Cannot close " + closeable);
            }
        }
    }

    private static class TempFiles {

        private static final boolean SUPPORTED = checkTempFileSupport();

        private static boolean checkTempFileSupport() {
            try {
                cleanupFiles(createTempFile(OmniHai.name() + "-probe-", ".tmp"));
                return true;
            }
            catch (Exception ignore) {
                return false;
            }
        }

    }

    private static class OffsetInputStream extends InputStream {

        private final SeekableByteChannel channel;
        private final InputStream delegate;
        private final long endOffset;

        public OffsetInputStream(Path source, long startOffset, long endOffset) throws IOException {
            requireNonNull(source, "source cannot be null");

            if (startOffset < 0) {
                throw new IllegalArgumentException("startOffset must be >= 0");
            }

            if (endOffset <= startOffset) {
                throw new IllegalArgumentException("endOffset must be > startOffset");
            }

            channel = newByteChannel(source, READ);

            try {
                long size = channel.size();

                if (endOffset > size) {
                    throw new IllegalArgumentException("endOffset must be <= file length");
                }

                channel.position(startOffset);
                delegate = Channels.newInputStream(channel);
                this.endOffset = endOffset;
            }
            catch (IOException e) {
                closeQuietly(channel);
                throw e;
            }
        }

        private long currentPosition() throws IOException {
            return channel.position();
        }

        private long remainingBytes() throws IOException {
            return endOffset - currentPosition();
        }

        private boolean isExhausted() throws IOException {
            return remainingBytes() <= 0;
        }

        @Override
        public int read() throws IOException {
            return isExhausted() ? -1 : delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return isExhausted() ? -1 : delegate.read(buffer, offset, (int) min(length, remainingBytes()));
        }

        @Override
        public long skip(long n) throws IOException {
            if (n <= 0 || isExhausted()) {
                return 0;
            }

            long skippedLength = min(n, remainingBytes());
            channel.position(currentPosition() + skippedLength);
            return skippedLength;
        }

        @Override
        public int available() throws IOException {
            return (int) min(remainingBytes(), Integer.MAX_VALUE);
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }

    }

}
