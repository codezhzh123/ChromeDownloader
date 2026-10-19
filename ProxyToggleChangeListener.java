import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Toggle;

public class ProxyToggleChangeListener implements ChangeListener<Toggle> {
    private final RadioButton noProxyRadio;
    private final TextField proxyHostField;
    private final TextField proxyPortField;
    private final TextField proxyUserField;
    private final PasswordField proxyPassField;
    
    public ProxyToggleChangeListener(RadioButton noProxyRadio, TextField proxyHostField,
                                    TextField proxyPortField, TextField proxyUserField,
                                    PasswordField proxyPassField) {
        if (noProxyRadio == null || proxyHostField == null || proxyPortField == null || 
            proxyUserField == null || proxyPassField == null) {
            throw new IllegalArgumentException("All parameters must not be null");
        }
        
        this.noProxyRadio = noProxyRadio;
        this.proxyHostField = proxyHostField;
        this.proxyPortField = proxyPortField;
        this.proxyUserField = proxyUserField;
        this.proxyPassField = proxyPassField;
    }
    
    @Override
    public void changed(ObservableValue<? extends Toggle> obs, 
                       Toggle oldVal, 
                       Toggle newVal) {
        // Keep the entered values when "No Proxy" is selected so switching
        // back to a proxy does not force the user to retype everything.
        boolean useProxy = newVal != null && newVal != noProxyRadio;
        proxyHostField.setDisable(!useProxy);
        proxyPortField.setDisable(!useProxy);
        proxyUserField.setDisable(!useProxy);
        proxyPassField.setDisable(!useProxy);
    }
}