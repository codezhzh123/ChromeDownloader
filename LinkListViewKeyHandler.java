import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.ListView;
import java.util.List;

public class LinkListViewKeyHandler implements EventHandler<KeyEvent> {
    private final ListView<String> linkListView;
    private final List<String> currentDownloadLinks;
    private final Runnable copySelectedAction;
    private final Runnable copyAllAction;
    
    public LinkListViewKeyHandler(ListView<String> linkListView, 
                                 List<String> currentDownloadLinks,
                                 Runnable copySelectedAction,
                                 Runnable copyAllAction) {
        if (linkListView == null || currentDownloadLinks == null || 
            copySelectedAction == null || copyAllAction == null) {
            throw new IllegalArgumentException("All parameters must not be null");
        }
        
        this.linkListView = linkListView;
        this.currentDownloadLinks = currentDownloadLinks;
        this.copySelectedAction = copySelectedAction;
        this.copyAllAction = copyAllAction;
    }
    
    @Override
    public void handle(KeyEvent event) {
        // isShortcutDown() maps to Command on macOS and Control elsewhere.
        if (event.getCode() == KeyCode.C && event.isShortcutDown()) {
            if (event.isShiftDown()) {
                copyAllAction.run();
            } else {
                copySelectedAction.run();
            }
            event.consume();
        }
    }
}