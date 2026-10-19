import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Main application class for Chrome Offline Installer Downloader.
 * Provides GUI for fetching and downloading Chrome installers.
 */
public class Main extends Application {

    private static final List<String> VERSION_KEYS = List.of("Stable", "Beta", "Dev", "Canary");
    private static final Duration TEMP_MESSAGE_DURATION = Duration.seconds(3);
    private static final String DOWNLOADS_FOLDER = "Downloads";
    private static final String FALLBACK_FILE_NAME = "chrome_installer";
    private static final Pattern ILLEGAL_FILE_NAME_CHARS =
            Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    // UI components
    private ComboBox<String> versionChoiceBox;
    private ToggleGroup proxyToggleGroup;
    private RadioButton noProxyRadio, httpProxyRadio, httpsProxyRadio, socks5ProxyRadio;
    private TextField proxyHostField, proxyPortField, proxyUserField;
    private PasswordField proxyPassField;
    private Button fetchButton, downloadButton, cancelButton;
    private ListView<String> linkListView;
    private ProgressBar progressBar;
    private Label progressLabel, resultLabel;
    private Label linkListPlaceholder;
    private Hyperlink openFolderLink;

    // UI references for language updates
    private Label selectVersionLabel, availableLinksLabel, proxySettingsLabel;
    private Label proxyHostLabel, proxyPortLabel, proxyUserLabel, proxyPassLabel;
    private VBox proxySection;

    // Menu components
    private MenuBar menuBar;
    private Menu fileMenu, languageMenu, helpMenu;
    private MenuItem exitMenuItem, aboutMenuItem;
    private final Map<Locale, RadioMenuItem> languageMenuItems = new LinkedHashMap<>();

    private final List<String> currentDownloadLinks = new ArrayList<>();
    private DownloadFileTask currentDownloadTask;
    private ContextMenu linkListContextMenu;
    private MenuItem copyLinkMenuItem, copyAllLinksMenuItem;
    private ExecutorService executorService;
    private Stage primaryStage;

    private boolean fetchInProgress;
    private String lastSavedPath;

    // Temporary status messages (clipboard feedback, language change)
    private final PauseTransition temporaryMessageTimer = new PauseTransition(TEMP_MESSAGE_DURATION);
    private String temporaryMessageText;
    private String temporaryPreviousText;
    private String temporaryPreviousStyle;
    private boolean temporaryPreviousVisible;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // Setup executor service with a single daemon worker thread; the UI only
        // ever runs one fetch or download at a time.
        executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "chrome-downloader-worker");
            thread.setDaemon(true);
            return thread;
        });

        createMenuBar();

        String localizedOS = I18n.getLocalizedOSName(PlatformUtils.getOSDisplayName());
        String arch = PlatformUtils.getArchForUpdateService();

        // Create action buttons
        fetchButton = new Button(I18n.get("button.fetch"));
        fetchButton.setOnAction(e -> fetchDownloadLinks());
        fetchButton.setDefaultButton(true);

        downloadButton = new Button(I18n.get("button.download"));
        downloadButton.setOnAction(e -> startDownload());
        downloadButton.setDisable(true);

        cancelButton = new Button(I18n.get("button.cancel"));
        cancelButton.setOnAction(e -> cancelDownload());
        cancelButton.setDisable(true);
        cancelButton.setCancelButton(true);

        // Version selection row: label + combo box on the left, the primary
        // fetch action on the right.
        selectVersionLabel = new Label(I18n.get("label.selectVersion"));
        versionChoiceBox = new ComboBox<>();
        versionChoiceBox.setPrefWidth(180);
        populateVersionChoiceBox(null);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox headerRow = new HBox(10, selectVersionLabel, versionChoiceBox, headerSpacer, fetchButton);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        // Create proxy settings UI
        createProxySettingsUI();

        // Create download links list view
        availableLinksLabel = new Label(I18n.get("label.availableLinks"));
        availableLinksLabel.setStyle("-fx-font-weight: bold;");

        linkListPlaceholder = new Label(I18n.get("list.placeholder"));
        linkListPlaceholder.setStyle("-fx-text-fill: gray;");

        linkListView = new ListView<>();
        linkListView.setPrefHeight(150);
        linkListView.setPlaceholder(linkListPlaceholder);
        linkListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        createLinkListContextMenu();

        // Setup keyboard shortcuts for copying links
        LinkListViewKeyHandler keyHandler = new LinkListViewKeyHandler(
                linkListView, currentDownloadLinks,
                this::copySelectedLinkToClipboard,
                this::copyAllLinksToClipboard);
        linkListView.setOnKeyPressed(keyHandler);

        // Enable download button when a link is selected
        linkListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> updateDownloadButtonState());

        // Create progress tracking components. The status text keeps a fixed
        // width and is always visible so that showing the progress bar or a
        // longer message never makes the layout jump.
        progressBar = new ProgressBar();
        progressBar.setVisible(false);
        progressBar.setProgress(0.0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        progressLabel = new Label(I18n.get("status.ready"));
        progressLabel.setStyle("-fx-text-fill: black;");
        progressLabel.setAlignment(Pos.CENTER_LEFT);
        progressLabel.setPrefWidth(340);

        resultLabel = new Label();
        resultLabel.setVisible(false);
        resultLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        resultLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        HBox.setHgrow(resultLabel, Priority.ALWAYS);

        openFolderLink = new Hyperlink(I18n.get("result.openFolder"));
        openFolderLink.setVisible(false);
        openFolderLink.setFocusTraversable(false);
        openFolderLink.setOnAction(e -> openDownloadsFolder());

        // Status row: status text on the left, progress bar filling the rest
        HBox progressBox = new HBox(10, progressLabel, progressBar);
        progressBox.setAlignment(Pos.CENTER_LEFT);

        // Buttons row: result message on the left, actions on the right
        HBox bottomRowBox = new HBox(10, resultLabel, openFolderLink, downloadButton, cancelButton);
        bottomRowBox.setAlignment(Pos.CENTER_RIGHT);

        VBox bottomContainer = new VBox(10);
        bottomContainer.setPadding(new Insets(10, 0, 0, 0));
        bottomContainer.getChildren().addAll(progressBox, bottomRowBox);

        VBox content = new VBox(10);
        content.setPadding(new Insets(12));
        VBox.setVgrow(linkListView, Priority.ALWAYS);
        content.getChildren().addAll(
                headerRow,
                proxySection,
                availableLinksLabel,
                linkListView,
                bottomContainer);

        BorderPane root = new BorderPane();
        root.setTop(menuBar);
        root.setCenter(content);

        Scene scene = new Scene(root, 1000, 560);

        updateWindowTitle(primaryStage, localizedOS, arch);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(720);
        primaryStage.setMinHeight(480);

        loadApplicationIcons(primaryStage);

        primaryStage.show();
    }

    /**
     * Creates the menu bar with language selection.
     */
    private void createMenuBar() {
        menuBar = new MenuBar();

        // File menu
        fileMenu = new Menu(I18n.get("menu.file"));
        exitMenuItem = new MenuItem(I18n.get("menu.exit"));
        exitMenuItem.setOnAction(e -> Platform.exit());
        fileMenu.getItems().add(exitMenuItem);

        // Language menu. Radio menu items keep the selected language visible
        // without manually managing check marks. Every language is listed with
        // its native name so the menu stays readable in any UI language.
        languageMenu = new Menu(I18n.get("menu.language"));
        ToggleGroup languageGroup = new ToggleGroup();

        for (I18n.Language language : I18n.getLanguages()) {
            RadioMenuItem item = new RadioMenuItem(language.nativeName());
            item.setToggleGroup(languageGroup);
            item.setSelected(I18n.getCurrentLocale().equals(language.locale()));
            item.setOnAction(e -> switchLanguage(language.locale()));
            languageMenu.getItems().add(item);
            languageMenuItems.put(language.locale(), item);
        }

        // Help menu
        helpMenu = new Menu(I18n.get("menu.help"));
        aboutMenuItem = new MenuItem(I18n.get("menu.about"));
        aboutMenuItem.setOnAction(e -> createAboutDialog());
        helpMenu.getItems().add(aboutMenuItem);

        menuBar.getMenus().addAll(fileMenu, languageMenu, helpMenu);

        // Align menu bar to left like Windows applications
        menuBar.setUseSystemMenuBar(false);
    }

    /**
     * Switches application language.
     */
    private void switchLanguage(Locale locale) {
        I18n.setLocale(locale);
        updateUIForLanguage();

        showTemporaryMessage(I18n.format("status.languageChanged",
                I18n.getLocaleDisplayName(I18n.getCurrentLocale())));
    }

    /**
     * Updates all UI elements for the current language.
     */
    private void updateUIForLanguage() {
        updateWindowTitle(primaryStage,
                I18n.getLocalizedOSName(PlatformUtils.getOSDisplayName()),
                PlatformUtils.getArchForUpdateService());

        // Update menu bar
        fileMenu.setText(I18n.get("menu.file"));
        languageMenu.setText(I18n.get("menu.language"));
        helpMenu.setText(I18n.get("menu.help"));
        exitMenuItem.setText(I18n.get("menu.exit"));
        aboutMenuItem.setText(I18n.get("menu.about"));

        // Language names are already written in their own language, only the
        // selection has to follow the current locale.
        for (Map.Entry<Locale, RadioMenuItem> entry : languageMenuItems.entrySet()) {
            entry.getValue().setSelected(I18n.getCurrentLocale().equals(entry.getKey()));
        }

        // Update UI labels
        selectVersionLabel.setText(I18n.get("label.selectVersion"));
        updateVersionChoiceBoxText();

        // Update proxy section
        proxySettingsLabel.setText(I18n.get("label.proxySettings"));
        noProxyRadio.setText(I18n.get("radio.noProxy"));
        httpProxyRadio.setText(I18n.get("radio.httpProxy"));
        httpsProxyRadio.setText(I18n.get("radio.httpsProxy"));
        socks5ProxyRadio.setText(I18n.get("radio.socks5Proxy"));

        proxyHostLabel.setText(I18n.get("label.proxyHost"));
        proxyHostField.setPromptText(I18n.get("placeholder.proxyHost"));

        proxyPortLabel.setText(I18n.get("label.proxyPort"));
        proxyPortField.setPromptText(I18n.get("placeholder.proxyPort"));

        proxyUserLabel.setText(I18n.get("label.proxyUser"));
        proxyUserField.setPromptText(I18n.get("placeholder.proxyUser"));

        proxyPassLabel.setText(I18n.get("label.proxyPass"));
        proxyPassField.setPromptText(I18n.get("placeholder.proxyPass"));

        // Update buttons and labels
        fetchButton.setText(I18n.get("button.fetch"));
        downloadButton.setText(I18n.get("button.download"));
        cancelButton.setText(I18n.get("button.cancel"));
        availableLinksLabel.setText(I18n.get("label.availableLinks"));
        linkListPlaceholder.setText(I18n.get("list.placeholder"));
        openFolderLink.setText(I18n.get("result.openFolder"));

        // Reset the status text only when nothing is running and no temporary
        // message is being displayed.
        if (temporaryMessageText == null && currentDownloadTask == null && !fetchInProgress) {
            progressLabel.setText(I18n.get("status.ready"));
            progressLabel.setStyle("-fx-text-fill: black;");
        }

        if (resultLabel.isVisible() && lastSavedPath != null) {
            resultLabel.setText(I18n.format("result.savedTo", lastSavedPath));
        }

        // Update context menu
        createLinkListContextMenu();
    }

    /**
     * Updates window title with current language.
     */
    private void updateWindowTitle(Stage stage, String osName, String arch) {
        stage.setTitle(I18n.format("app.title", I18n.get("app.version"), osName, arch));
    }

    /**
     * (Re)populates the version combo box with localized names and keeps the
     * selection identified by its stable key instead of its displayed text.
     */
    private void populateVersionChoiceBox(String selectedKey) {
        versionChoiceBox.getItems().clear();
        for (String key : VERSION_KEYS) {
            versionChoiceBox.getItems().add(I18n.getLocalizedVersion(key));
        }
        int index = selectedKey != null ? VERSION_KEYS.indexOf(selectedKey) : -1;
        versionChoiceBox.getSelectionModel().select(index >= 0 ? index : 0);
    }

    /**
     * Re-localizes the existing combo box entries in place. Replacing the item
     * text instead of rebuilding the list avoids allocating new list cells and
     * keeps the current selection untouched.
     */
    private void updateVersionChoiceBoxText() {
        List<String> items = versionChoiceBox.getItems();
        if (items.size() != VERSION_KEYS.size()) {
            populateVersionChoiceBox(getSelectedVersionKey());
            return;
        }
        for (int i = 0; i < VERSION_KEYS.size(); i++) {
            items.set(i, I18n.getLocalizedVersion(VERSION_KEYS.get(i)));
        }
    }

    /**
     * Returns the language independent version key currently selected.
     */
    private String getSelectedVersionKey() {
        int index = versionChoiceBox.getSelectionModel().getSelectedIndex();
        return index >= 0 && index < VERSION_KEYS.size() ? VERSION_KEYS.get(index) : VERSION_KEYS.get(0);
    }

    /**
     * Creates proxy settings UI components.
     */
    private void createProxySettingsUI() {
        proxyToggleGroup = new ToggleGroup();
        noProxyRadio = new RadioButton(I18n.get("radio.noProxy"));
        noProxyRadio.setToggleGroup(proxyToggleGroup);
        noProxyRadio.setSelected(true);

        httpProxyRadio = new RadioButton(I18n.get("radio.httpProxy"));
        httpProxyRadio.setToggleGroup(proxyToggleGroup);

        httpsProxyRadio = new RadioButton(I18n.get("radio.httpsProxy"));
        httpsProxyRadio.setToggleGroup(proxyToggleGroup);

        socks5ProxyRadio = new RadioButton(I18n.get("radio.socks5Proxy"));
        socks5ProxyRadio.setToggleGroup(proxyToggleGroup);

        HBox proxyTypeBox = new HBox(18, noProxyRadio, httpProxyRadio, httpsProxyRadio, socks5ProxyRadio);
        proxyTypeBox.setAlignment(Pos.CENTER_LEFT);

        proxyHostLabel = new Label(I18n.get("label.proxyHost"));
        proxyHostField = new TextField();
        proxyHostField.setPromptText(I18n.get("placeholder.proxyHost"));
        proxyHostField.setDisable(true);

        proxyPortLabel = new Label(I18n.get("label.proxyPort"));
        proxyPortField = new TextField();
        proxyPortField.setPromptText(I18n.get("placeholder.proxyPort"));
        proxyPortField.setPrefWidth(90);
        proxyPortField.setMaxWidth(90);
        proxyPortField.setDisable(true);

        proxyUserLabel = new Label(I18n.get("label.proxyUser"));
        proxyUserField = new TextField();
        proxyUserField.setPromptText(I18n.get("placeholder.proxyUser"));
        proxyUserField.setDisable(true);

        proxyPassLabel = new Label(I18n.get("label.proxyPass"));
        proxyPassField = new PasswordField();
        proxyPassField.setPromptText(I18n.get("placeholder.proxyPass"));
        proxyPassField.setDisable(true);

        GridPane proxyGrid = new GridPane();
        proxyGrid.setHgap(10);
        proxyGrid.setVgap(10);
        GridPane.setFillWidth(proxyPortField, false);

        // Input columns keep a comfortable width on wide windows and shrink
        // when the window is narrow; labels never truncate localized text.
        ColumnConstraints labelColumn = new ColumnConstraints();
        ColumnConstraints inputColumn = new ColumnConstraints();
        inputColumn.setPrefWidth(300);
        inputColumn.setMinWidth(110);
        ColumnConstraints labelColumn2 = new ColumnConstraints();
        ColumnConstraints inputColumn2 = new ColumnConstraints();
        inputColumn2.setPrefWidth(300);
        inputColumn2.setMinWidth(110);
        proxyGrid.getColumnConstraints().addAll(
                labelColumn, inputColumn, labelColumn2, inputColumn2);

        for (Label label : new Label[]{proxyHostLabel, proxyPortLabel, proxyUserLabel, proxyPassLabel}) {
            label.setMinWidth(Region.USE_PREF_SIZE);
        }

        proxyGrid.add(proxyHostLabel, 0, 0);
        proxyGrid.add(proxyHostField, 1, 0);
        proxyGrid.add(proxyPortLabel, 2, 0);
        proxyGrid.add(proxyPortField, 3, 0);
        proxyGrid.add(proxyUserLabel, 0, 1);
        proxyGrid.add(proxyUserField, 1, 1);
        proxyGrid.add(proxyPassLabel, 2, 1);
        proxyGrid.add(proxyPassField, 3, 1);

        ProxyToggleChangeListener proxyToggleListener = new ProxyToggleChangeListener(
                noProxyRadio, proxyHostField, proxyPortField, proxyUserField, proxyPassField);
        proxyToggleGroup.selectedToggleProperty().addListener(proxyToggleListener);

        proxySettingsLabel = new Label(I18n.get("label.proxySettings"));
        proxySettingsLabel.setStyle("-fx-font-weight: bold;");
        proxySection = new VBox(10);
        proxySection.setPadding(new Insets(10));
        proxySection.setStyle("-fx-border-color: lightgray; -fx-border-radius: 5; -fx-background-color: #f9f9f9;");
        proxySection.getChildren().addAll(proxySettingsLabel, proxyTypeBox, proxyGrid);
    }

    /**
     * Creates and shows the about dialog.
     */
    private void createAboutDialog() {
        Alert aboutDialog = new Alert(Alert.AlertType.INFORMATION);
        configureDialog(aboutDialog);

        aboutDialog.setTitle(I18n.format("about.title", I18n.get("app.name")));
        aboutDialog.setHeaderText(I18n.format("about.header",
                I18n.get("app.name"), I18n.get("app.version")));

        String localizedOS = I18n.getLocalizedOSName(PlatformUtils.getOSDisplayName());
        String arch = PlatformUtils.getArchForUpdateService();

        aboutDialog.setContentText(I18n.format("about.content",
                I18n.get("app.name"), I18n.get("app.version"),
                localizedOS, arch));

        aboutDialog.showAndWait();
    }

    @Override
    public void stop() {
        if (currentDownloadTask != null && currentDownloadTask.isRunning()) {
            currentDownloadTask.cancel();
        }

        if (executorService != null) {
            executorService.shutdownNow();
        }

        temporaryMessageTimer.stop();

        if (linkListContextMenu != null) {
            linkListContextMenu.hide();
            linkListView.setContextMenu(null);
            linkListContextMenu.getItems().clear();
        }
    }

    /**
     * Creates (once) and re-localizes the link list context menu. Reusing the
     * existing menu items avoids building a new {@link ContextMenu} every time
     * the language changes.
     */
    private void createLinkListContextMenu() {
        if (linkListContextMenu == null) {
            linkListContextMenu = new ContextMenu();

            copyLinkMenuItem = new MenuItem();
            copyLinkMenuItem.setOnAction(e -> copySelectedLinkToClipboard());

            copyAllLinksMenuItem = new MenuItem();
            copyAllLinksMenuItem.setOnAction(e -> copyAllLinksToClipboard());

            linkListContextMenu.getItems().addAll(copyLinkMenuItem, copyAllLinksMenuItem);
            linkListView.setContextMenu(linkListContextMenu);
        }

        copyLinkMenuItem.setText(I18n.get("contextMenu.copySelected"));
        copyAllLinksMenuItem.setText(I18n.get("contextMenu.copyAll"));
    }

    /**
     * Copies selected link to clipboard.
     */
    private void copySelectedLinkToClipboard() {
        int selectedIndex = linkListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < currentDownloadLinks.size()) {
            copyToClipboard(currentDownloadLinks.get(selectedIndex),
                    I18n.get("clipboard.copiedSelected"));
        } else {
            showAlert(I18n.get("alert.noSelection.title"),
                    I18n.get("alert.noSelection.content"));
        }
    }

    /**
     * Copies all links to clipboard.
     */
    private void copyAllLinksToClipboard() {
        if (currentDownloadLinks.isEmpty()) {
            showAlert(I18n.get("alert.noLinks.title"),
                    I18n.get("alert.noLinks.content"));
            return;
        }

        copyToClipboard(String.join(System.lineSeparator(), currentDownloadLinks),
                I18n.get("clipboard.copiedAll"));
    }

    /**
     * Copies text to clipboard and shows success message.
     */
    private void copyToClipboard(String text, String successMessage) {
        try {
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
            showTemporaryMessage(successMessage);
        } catch (Exception ex) {
            showAlert(I18n.get("alert.clipboardError.title"),
                    I18n.format("alert.clipboardError.content", ex.getMessage()));
        }
    }

    /**
     * Shows a temporary message in the progress label and restores the previous
     * content after a short delay. Runs entirely on the JavaFX thread.
     */
    private void showTemporaryMessage(String message) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> showTemporaryMessage(message));
            return;
        }

        if (temporaryMessageText == null) {
            temporaryPreviousText = progressLabel.getText();
            temporaryPreviousStyle = progressLabel.getStyle();
            temporaryPreviousVisible = progressLabel.isVisible();
        }
        temporaryMessageText = message;

        progressLabel.setVisible(true);
        progressLabel.setText(message);
        progressLabel.setStyle("-fx-text-fill: blue;");

        temporaryMessageTimer.stop();
        temporaryMessageTimer.setOnFinished(e -> {
            if (message.equals(progressLabel.getText())) {
                progressLabel.setText(temporaryPreviousText);
                progressLabel.setStyle(temporaryPreviousStyle);
                progressLabel.setVisible(temporaryPreviousVisible);
            }
            temporaryMessageText = null;
        });
        temporaryMessageTimer.playFromStart();
    }

    /**
     * Loads application icons.
     */
    private void loadApplicationIcons(Stage stage) {
        // Only the sizes the window manager actually needs: each 512x512 icon
        // alone would keep ~1 MB of pixel data alive for the whole session.
        String[] iconPaths = {"/images/256.png", "/images/128.png", "/images/64.png"};

        for (String path : iconPaths) {
            try (InputStream is = Main.class.getResourceAsStream(path)) {
                if (is != null) {
                    Image icon = new Image(is);
                    if (!icon.isError()) {
                        stage.getIcons().add(icon);
                    }
                }
            } catch (Exception ignored) {
                // A missing icon is not fatal.
            }
        }
    }

    /**
     * Centers a dialog on the main window and applies the application icon.
     * The icon is applied when the dialog is shown, because the dialog's stage
     * does not exist before that.
     */
    private void configureDialog(Dialog<?> dialog) {
        if (primaryStage == null) {
            return;
        }
        if (dialog.getOwner() == null) {
            dialog.initOwner(primaryStage);
        }
        dialog.setOnShowing(event -> {
            if (dialog.getDialogPane().getScene() != null
                    && dialog.getDialogPane().getScene().getWindow() instanceof Stage dialogStage) {
                dialogStage.getIcons().setAll(primaryStage.getIcons());
            }
        });
    }

    /**
     * Creates ProxySettings from UI inputs.
     */
    private ProxySettings createProxySettings() {
        Toggle selectedToggle = proxyToggleGroup.getSelectedToggle();

        if (selectedToggle == null || selectedToggle == noProxyRadio) {
            return new ProxySettings();
        }

        String host = proxyHostField.getText().trim();
        String portText = proxyPortField.getText().trim();
        String username = proxyUserField.getText().trim();
        String password = proxyPassField.getText();

        if (host.isEmpty() || portText.isEmpty()) {
            showAlert(I18n.get("alert.proxyError.title"),
                    I18n.get("alert.proxyError.hostPort"));
            return null;
        }

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            showAlert(I18n.get("alert.proxyError.title"),
                    I18n.get("alert.proxyError.portNumber"));
            return null;
        }

        if (port < 1 || port > 65535) {
            showAlert(I18n.get("alert.proxyError.title"),
                    I18n.get("alert.proxyError.portRange"));
            return null;
        }

        ProxyType proxyType;
        if (selectedToggle == httpProxyRadio) {
            proxyType = ProxyType.HTTP;
        } else if (selectedToggle == httpsProxyRadio) {
            proxyType = ProxyType.HTTPS;
        } else if (selectedToggle == socks5ProxyRadio) {
            proxyType = ProxyType.SOCKS5;
        } else {
            return new ProxySettings();
        }

        return username.isEmpty()
                ? new ProxySettings(proxyType, host, port)
                : new ProxySettings(proxyType, host, port, username, password);
    }

    /**
     * Shows an alert dialog.
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        configureDialog(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Fetches download links for selected version.
     */
    private void fetchDownloadLinks() {
        if (fetchInProgress || currentDownloadTask != null) {
            return;
        }

        ProxySettings proxySettings = createProxySettings();
        if (proxySettings == null) {
            return;
        }

        fetchInProgress = true;
        fetchButton.setDisable(true);
        downloadButton.setDisable(true);
        linkListView.setDisable(true);
        resultLabel.setVisible(false);
        openFolderLink.setVisible(false);
        progressBar.setVisible(false);

        progressLabel.setText(proxySettings.isUseProxy()
                ? I18n.format("status.fetchingVia",
                        I18n.getLocalizedProxyType(proxySettings.getProxyTypeString()))
                : I18n.get("status.fetching"));
        progressLabel.setStyle("-fx-text-fill: blue;");

        String versionKey = getSelectedVersionKey();
        FetchLinksTask task = new FetchLinksTask(versionKey, proxySettings);

        TaskStateHandlers.setupFetchHandlers(
                task, progressLabel, linkListView, currentDownloadLinks,
                fetchButton, downloadButton,
                () -> {
                    fetchInProgress = false;
                    updateDownloadButtonState();
                });

        executorService.submit(task);
    }

    /**
     * Starts downloading selected file.
     */
    private void startDownload() {
        int selectedIndex = linkListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= currentDownloadLinks.size()) {
            return;
        }
        if (currentDownloadTask != null || fetchInProgress) {
            return;
        }

        String url = currentDownloadLinks.get(selectedIndex);
        File downloadsDir = new File(System.getProperty("user.home"), DOWNLOADS_FOLDER);
        if (!downloadsDir.isDirectory() && !downloadsDir.mkdirs()) {
            showAlert(I18n.get("alert.downloadsDir.title"),
                    I18n.format("alert.downloadsDir.content", downloadsDir.getAbsolutePath()));
            return;
        }

        File outputFile = new File(downloadsDir, deriveFileName(url));

        if (outputFile.exists()) {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            configureDialog(confirmAlert);
            confirmAlert.setTitle(I18n.get("alert.fileExists.title"));
            confirmAlert.setHeaderText(I18n.get("alert.fileExists.header"));
            confirmAlert.setContentText(I18n.format("alert.fileExists.content", outputFile.getName()));
            if (confirmAlert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }

        ProxySettings proxySettings = createProxySettings();
        if (proxySettings == null) {
            return;
        }

        resultLabel.setVisible(false);
        openFolderLink.setVisible(false);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        progressLabel.setText(proxySettings.isUseProxy()
                ? I18n.format("status.startingVia",
                        I18n.getLocalizedProxyType(proxySettings.getProxyTypeString()))
                : I18n.get("status.starting"));
        progressLabel.setStyle("-fx-text-fill: black;");

        fetchButton.setDisable(true);
        downloadButton.setDisable(true);
        cancelButton.setDisable(false);
        linkListView.setDisable(true);

        lastSavedPath = outputFile.getAbsolutePath();
        currentDownloadTask = new DownloadFileTask(url, outputFile, proxySettings);

        DownloadTaskHandlers.setupDownloadHandlers(
                currentDownloadTask,
                progressBar,
                progressLabel,
                resultLabel,
                openFolderLink,
                outputFile,
                this::resetUIAfterDownload);

        executorService.submit(currentDownloadTask);
    }

    /**
     * Cancels the current download.
     */
    private void cancelDownload() {
        if (currentDownloadTask != null && currentDownloadTask.isRunning()) {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            configureDialog(confirmAlert);
            confirmAlert.setTitle(I18n.get("alert.cancelConfirm.title"));
            confirmAlert.setHeaderText(I18n.get("alert.cancelConfirm.header"));
            confirmAlert.setContentText(I18n.get("alert.cancelConfirm.content"));

            if (confirmAlert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                currentDownloadTask.cancel();
            }
        }
    }

    /**
     * Resets UI after download completion/cancellation/failure. Invoked from the
     * JavaFX thread by the download task handlers.
     */
    private void resetUIAfterDownload() {
        currentDownloadTask = null;
        fetchButton.setDisable(false);
        cancelButton.setDisable(true);
        linkListView.setDisable(false);
        progressBar.setVisible(false);
        updateDownloadButtonState();
    }

    /**
     * Opens the folder the downloaded installer was saved to.
     *
     * <p>The platform's file manager is started as a separate process instead of
     * using {@code java.awt.Desktop}: that keeps the AWT toolkit (and its native
     * libraries and thread) out of this JavaFX-only application.</p>
     */
    private void openDownloadsFolder() {
        File folder = new File(System.getProperty("user.home"), DOWNLOADS_FOLDER);
        List<String> command;
        if (PlatformUtils.isWindows()) {
            command = List.of("explorer.exe", folder.getAbsolutePath());
        } else if (PlatformUtils.isMac()) {
            command = List.of("open", folder.getAbsolutePath());
        } else {
            command = List.of("xdg-open", folder.getAbsolutePath());
        }

        try {
            new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (Exception e) {
            showAlert(I18n.get("alert.openFolder.title"),
                    I18n.format("alert.openFolder.content",
                            e.getMessage() != null ? e.getMessage() : folder.getAbsolutePath()));
        }
    }

    /**
     * Keeps the download button state consistent with the current selection and
     * running operations.
     */
    private void updateDownloadButtonState() {
        boolean hasSelection = linkListView.getSelectionModel().getSelectedItem() != null;
        downloadButton.setDisable(!hasSelection || currentDownloadTask != null || fetchInProgress);
    }

    /**
     * Derives a safe local file name from a download URL.
     */
    private static String deriveFileName(String url) {
        String path = null;
        try {
            path = new URI(url).getPath();
        } catch (Exception ignored) {
            // Fall back to a manual split below.
        }
        if (path == null || path.isEmpty()) {
            int queryIndex = url.indexOf('?');
            path = queryIndex >= 0 ? url.substring(0, queryIndex) : url;
        }

        int slashIndex = path.lastIndexOf('/');
        String name = slashIndex >= 0 ? path.substring(slashIndex + 1) : path;

        try {
            name = URLDecoder.decode(name, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Keep the raw name if it is not valid percent-encoding.
        }

        name = ILLEGAL_FILE_NAME_CHARS.matcher(name).replaceAll("_").trim();
        return name.isEmpty() ? FALLBACK_FILE_NAME : name;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
