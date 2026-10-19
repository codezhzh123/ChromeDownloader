import javafx.concurrent.Task;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Locale;

/**
 * Task for downloading files with progress tracking and proxy support.
 */
public class DownloadFileTask extends Task<Void> {
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final long PROGRESS_STEP_BYTES = 512 * 1024;
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024 * 1024;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    /**
     * Shared transfer buffer. The single download worker is the only user, so the
     * same direct buffer is reused for every download instead of allocating (and
     * waiting for the GC to reclaim) a new multi-megabyte buffer each time.
     * It is allocated on the first download so that merely opening the window
     * does not reserve the memory.
     */
    private static ByteBuffer transferBuffer;

    private final String url;
    private final File outputFile;
    private final ProxySettings proxySettings;

    private HttpURLConnection connection;
    private ReadableByteChannel rbc;
    private FileOutputStream fos;
    private boolean outputStarted;
    private boolean completed;

    public DownloadFileTask(String url, File outputFile) {
        this(url, outputFile, new ProxySettings());
    }

    public DownloadFileTask(String url, File outputFile, ProxySettings proxySettings) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("Output file cannot be null");
        }

        this.url = url;
        this.outputFile = outputFile;
        this.proxySettings = proxySettings != null ? proxySettings : new ProxySettings();
    }

    /**
     * Returns the shared transfer buffer in a cleared state.
     */
    private static synchronized ByteBuffer acquireTransferBuffer() {
        if (transferBuffer == null) {
            transferBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        }
        transferBuffer.clear();
        return transferBuffer;
    }

    @Override
    protected Void call() throws Exception {
        try {
            downloadFile();
            return null;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(cleanErrorMessage(e), e);
        } finally {
            cleanupResources();
        }
    }

    private void downloadFile() throws IOException {
        if (isCancelled()) {
            return;
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IOException(I18n.format("error.invalidURL", url), e);
        }

        Proxy proxy = proxySettings.createProxy();
        connection = (HttpURLConnection) uri.toURL().openConnection(proxy);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);

        if (proxySettings.hasAuthentication()) {
            connection.setRequestProperty("Proxy-Authorization",
                    "Basic " + proxySettings.getProxyAuthorization());
        }

        updateMessage(I18n.format("status.connecting", url));
        int responseCode;
        try {
            responseCode = connection.getResponseCode();
        } catch (SocketTimeoutException e) {
            throw new IOException(proxySettings.isUseProxy()
                    ? I18n.get("error.proxyTimeout") : I18n.get("error.connectionTimeout"), e);
        } catch (ConnectException e) {
            throw new IOException(proxySettings.isUseProxy()
                    ? I18n.format("error.connectToProxy",
                            proxySettings.getProxyHost(), proxySettings.getProxyPort())
                    : I18n.get("error.cannotConnect"), e);
        }

        if (responseCode != HttpURLConnection.HTTP_OK) {
            String reason = connection.getResponseMessage();
            throw new IOException(I18n.format(
                    proxySettings.isUseProxy() ? "error.httpViaProxy" : "error.http",
                    responseCode, reason != null ? reason : ""));
        }

        long fileSize = connection.getContentLengthLong();
        if (fileSize <= 0) {
            updateMessage(I18n.get("status.unknownSize"));
        } else {
            if (fileSize > MAX_FILE_SIZE) {
                throw new IOException(I18n.format("error.fileTooLarge", formatFileSize(fileSize)));
            }
            updateMessage(I18n.format("status.totalSize", formatFileSize(fileSize)));
        }

        if (outputFile.exists()) {
            updateMessage(I18n.format("status.overwriteWarning", outputFile.getName()));
        }

        if (isCancelled()) {
            return;
        }

        long totalRead = 0;
        try {
            rbc = Channels.newChannel(connection.getInputStream());
            fos = new FileOutputStream(outputFile);
            outputStarted = true;

            ByteBuffer buffer = acquireTransferBuffer();
            long lastUpdateTime = System.currentTimeMillis();
            long lastBytesRead = 0;
            long lastProgressBytes = 0;
            int bytesRead;

            while ((bytesRead = rbc.read(buffer)) != -1) {
                if (isCancelled()) {
                    updateMessage(I18n.get("status.downloadCancelled"));
                    return;
                }
                if (bytesRead == 0) {
                    continue;
                }

                buffer.flip();
                while (buffer.hasRemaining()) {
                    fos.getChannel().write(buffer);
                }
                buffer.clear();
                totalRead += bytesRead;

                long now = System.currentTimeMillis();
                if (fileSize > 0) {
                    if (totalRead - lastProgressBytes >= PROGRESS_STEP_BYTES) {
                        updateProgress(totalRead, fileSize);
                        lastProgressBytes = totalRead;
                    }
                    if (now - lastUpdateTime >= 1000) {
                        double bytesPerSecond = (totalRead - lastBytesRead) * 1000.0
                                / (now - lastUpdateTime);
                        updateMessage(I18n.format("status.progressFormat",
                                totalRead * 100.0 / fileSize,
                                bytesPerSecond / (1024 * 1024)));
                        lastUpdateTime = now;
                        lastBytesRead = totalRead;
                    }
                } else if (now - lastUpdateTime >= 1000) {
                    updateMessage(I18n.format("status.downloaded", formatFileSize(totalRead)));
                    lastUpdateTime = now;
                }
            }

            if (fileSize > 0 && totalRead < fileSize) {
                throw new IOException(I18n.format("error.incompleteDownload",
                        formatFileSize(fileSize), formatFileSize(totalRead)));
            }
        } catch (SocketTimeoutException e) {
            if (isCancelled()) {
                updateMessage(I18n.get("status.downloadCancelled"));
                return;
            }
            throw new IOException(proxySettings.isUseProxy()
                    ? I18n.get("error.proxyTimeout") : I18n.get("error.connectionTimeout"), e);
        } catch (IOException e) {
            // Cancelling interrupts the worker thread, which closes the file
            // channel and makes the write below fail. Treat that as a normal
            // cancellation instead of a failed download.
            if (isCancelled()) {
                updateMessage(I18n.get("status.downloadCancelled"));
                return;
            }
            String message = e.getMessage() != null ? e.getMessage().toLowerCase(Locale.ROOT) : "";
            if (fileSize > 0 && (message.contains("premature eof")
                    || message.contains("unexpected end of stream"))) {
                throw new IOException(I18n.format("error.incompleteDownload",
                        formatFileSize(fileSize), formatFileSize(totalRead)), e);
            }
            throw e;
        }

        completed = true;
        updateProgress(1.0, 1.0);
        updateMessage(I18n.format("status.downloadCompleted", formatFileSize(totalRead)));
    }

    /**
     * Cleans up resources (streams, channels, connections) and removes partially
     * downloaded files. A pre-existing file is never deleted if the transfer did
     * not start, because closing an unopened stream cannot have modified it.
     */
    private void cleanupResources() {
        closeQuietly(fos);
        fos = null;

        closeQuietly(rbc);
        rbc = null;

        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception e) {
                System.err.println("Failed to disconnect connection: " + e.getMessage());
            } finally {
                connection = null;
            }
        }

        if (outputStarted && !completed && outputFile.exists() && !outputFile.delete()) {
            System.err.println("Could not delete incomplete file: " + outputFile);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            System.err.println("Failed to close resource: " + e.getMessage());
        }
    }

    /**
     * Produces a user-friendly message for unexpected exceptions. Messages that
     * are already localized by this class are returned unchanged.
     */
    private String cleanErrorMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return I18n.get("error.unknown");
        }

        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("timed out") || lower.contains("timeout")
                || lower.contains("getsockopt")) {
            return proxySettings.isUseProxy()
                    ? I18n.get("error.proxyTimeout") : I18n.get("error.connectionTimeout");
        }
        if (lower.contains("connection refused")) {
            return I18n.get("error.connectionRefused");
        }
        return message;
    }

    /**
     * Formats file size for display.
     */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024 * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
