import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

import java.util.List;

/**
 * Connects a {@link FetchLinksTask} to the user interface.
 */
public final class TaskStateHandlers {

    private TaskStateHandlers() {
    }

    /**
     * Installs all handlers for a fetch-links task. Handlers run on the JavaFX
     * application thread, so the controls can be updated directly.
     *
     * @param onFinished called exactly once when the task reaches a terminal
     *                   state, after the controls have been updated
     */
    public static void setupFetchHandlers(FetchLinksTask task,
                                          Label progressLabel,
                                          ListView<String> linkListView,
                                          List<String> currentDownloadLinks,
                                          Button fetchButton,
                                          Button downloadButton,
                                          Runnable onFinished) {

        task.setOnSucceeded(event -> {
            List<String> links = task.getValue();

            currentDownloadLinks.clear();
            if (links != null) {
                currentDownloadLinks.addAll(links);
            }

            linkListView.getItems().clear();
            for (int i = 0; i < currentDownloadLinks.size(); i++) {
                linkListView.getItems().add((i + 1) + ". " + currentDownloadLinks.get(i));
            }
            linkListView.setDisable(false);
            fetchButton.setDisable(false);

            if (currentDownloadLinks.isEmpty()) {
                progressLabel.setVisible(true);
                progressLabel.setText(I18n.get("status.noLinksFound"));
                progressLabel.setStyle("-fx-text-fill: red;");
                downloadButton.setDisable(true);
            } else {
                progressLabel.setVisible(true);
                progressLabel.setText(I18n.format("status.linksLoaded", currentDownloadLinks.size()));
                progressLabel.setStyle("-fx-text-fill: black;");
                linkListView.getSelectionModel().selectFirst();
            }
            finish(onFinished);
        });

        task.setOnFailed(event -> {
            Throwable error = task.getException();

            currentDownloadLinks.clear();
            linkListView.getItems().clear();
            linkListView.setDisable(false);
            fetchButton.setDisable(false);
            downloadButton.setDisable(true);

            String detail = error != null && error.getLocalizedMessage() != null
                    && !error.getLocalizedMessage().isBlank()
                    ? error.getLocalizedMessage()
                    : I18n.get("error.unknown");
            progressLabel.setVisible(true);
            progressLabel.setText(I18n.format("status.fetchFailed", detail));
            progressLabel.setStyle("-fx-text-fill: red;");
            finish(onFinished);
        });

        task.setOnCancelled(event -> {
            linkListView.setDisable(false);
            fetchButton.setDisable(false);
            progressLabel.setVisible(true);
            progressLabel.setText(I18n.get("status.fetchCancelled"));
            progressLabel.setStyle("-fx-text-fill: orange;");
            finish(onFinished);
        });
    }

    private static void finish(Runnable onFinished) {
        if (onFinished != null) {
            onFinished.run();
        }
    }
}
