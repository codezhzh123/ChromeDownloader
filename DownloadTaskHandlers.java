import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;

import java.io.File;

/**
 * Handlers for download task state changes and UI updates.
 */
public final class DownloadTaskHandlers {

    private DownloadTaskHandlers() {
    }

    public static void setupDownloadHandlers(DownloadFileTask task,
                                             ProgressBar progressBar,
                                             Label progressLabel,
                                             Label resultLabel,
                                             Hyperlink openFolderLink,
                                             File outputFile,
                                             Runnable cleanupAction) {

        // A negative progress means "unknown size" and renders as an
        // indeterminate progress bar.
        progressBar.setProgress(task.getProgress());
        task.progressProperty().addListener((obs, oldVal, newVal) ->
                progressBar.setProgress(newVal.doubleValue()));

        task.messageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                progressLabel.setText(newVal);
                progressLabel.setStyle("-fx-text-fill: black;");
            }
        });

        task.setOnSucceeded(event ->
                finishSucceeded(progressBar, progressLabel, resultLabel,
                        openFolderLink, outputFile, cleanupAction));

        task.setOnFailed(event ->
                finishFailed(progressLabel, resultLabel, openFolderLink,
                        task.getException(), cleanupAction));

        task.setOnCancelled(event ->
                finishCancelled(progressLabel, resultLabel, openFolderLink, cleanupAction));
    }

    private static void finishSucceeded(ProgressBar progressBar, Label progressLabel,
                                        Label resultLabel, Hyperlink openFolderLink,
                                        File outputFile, Runnable cleanupAction) {
        progressBar.setProgress(1.0);
        progressLabel.setText(I18n.get("result.completed"));
        progressLabel.setStyle("-fx-text-fill: green;");

        if (resultLabel != null) {
            resultLabel.setText(I18n.format("result.savedTo", outputFile.getAbsolutePath()));
            resultLabel.setVisible(true);
        }
        if (openFolderLink != null) {
            openFolderLink.setVisible(true);
        }
        notifyFinished(cleanupAction);
    }

    private static void finishFailed(Label progressLabel, Label resultLabel,
                                     Hyperlink openFolderLink, Throwable error,
                                     Runnable cleanupAction) {
        String detail = error != null && error.getLocalizedMessage() != null
                && !error.getLocalizedMessage().isBlank()
                ? error.getLocalizedMessage()
                : I18n.get("error.unknown");

        progressLabel.setText(I18n.format("result.failed", detail));
        progressLabel.setStyle("-fx-text-fill: red;");
        hideResult(resultLabel, openFolderLink);
        notifyFinished(cleanupAction);
    }

    private static void finishCancelled(Label progressLabel, Label resultLabel,
                                        Hyperlink openFolderLink, Runnable cleanupAction) {
        progressLabel.setText(I18n.get("result.cancelled"));
        progressLabel.setStyle("-fx-text-fill: orange;");
        hideResult(resultLabel, openFolderLink);
        notifyFinished(cleanupAction);
    }

    private static void hideResult(Label resultLabel, Hyperlink openFolderLink) {
        if (resultLabel != null) {
            resultLabel.setVisible(false);
        }
        if (openFolderLink != null) {
            openFolderLink.setVisible(false);
        }
    }

    private static void notifyFinished(Runnable cleanupAction) {
        if (cleanupAction != null) {
            cleanupAction.run();
        }
    }
}
