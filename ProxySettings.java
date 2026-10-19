import java.net.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Base64;

public class ProxySettings {
    private final boolean useProxy;
    private final ProxyType proxyType;
    private final String proxyHost;
    private final int proxyPort;
    private final String proxyUsername;
    private final String proxyPassword;
    
    public ProxySettings() {
        this.useProxy = false;
        this.proxyType = null;
        this.proxyHost = null;
        this.proxyPort = 0;
        this.proxyUsername = null;
        this.proxyPassword = null;
    }
    
    public ProxySettings(ProxyType proxyType, String proxyHost, int proxyPort) {
        validateProxyParams(proxyType, proxyHost, proxyPort);
        
        this.useProxy = true;
        this.proxyType = proxyType;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.proxyUsername = null;
        this.proxyPassword = null;
    }
    
    public ProxySettings(ProxyType proxyType, String proxyHost, int proxyPort, 
                        String proxyUsername, String proxyPassword) {
        validateProxyParams(proxyType, proxyHost, proxyPort);
        
        this.useProxy = true;
        this.proxyType = proxyType;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.proxyUsername = proxyUsername;
        this.proxyPassword = proxyPassword;
    }
    
    private void validateProxyParams(ProxyType proxyType, String proxyHost, int proxyPort) {
        Objects.requireNonNull(proxyType, "Proxy type cannot be null");
        Objects.requireNonNull(proxyHost, "Proxy host cannot be null");
        
        if (proxyHost.trim().isEmpty()) {
            throw new IllegalArgumentException("Proxy host cannot be empty");
        }
        
        if (proxyPort < 1 || proxyPort > 65535) {
            throw new IllegalArgumentException("Proxy port must be between 1 and 65535");
        }
    }
    
    public Proxy createProxy() {
        if (!useProxy || proxyType == null || proxyHost == null || proxyHost.trim().isEmpty()) {
            return Proxy.NO_PROXY;
        }
        
        InetSocketAddress address = new InetSocketAddress(proxyHost, proxyPort);
        return new Proxy(proxyType.toJavaProxyType(), address);
    }
    
    public String getProxyTypeString() {
        if (!useProxy) return I18n.get("proxyType.none");
        return proxyType.toString();
    }
    
    public String getProxyAuthorization() {
        if (!useProxy || proxyUsername == null || proxyUsername.isEmpty()) {
            return null;
        }
        
        String auth = proxyUsername + ":" + 
                     (proxyPassword != null ? proxyPassword : "");
        return Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }
    
    public boolean isUseProxy() { return useProxy; }
    public ProxyType getProxyType() { return proxyType; }
    public String getProxyHost() { return proxyHost; }
    public int getProxyPort() { return proxyPort; }
    public String getProxyUsername() { return proxyUsername; }
    public String getProxyPassword() { return proxyPassword; }
    
    public boolean isValid() {
        if (!useProxy) return true;
        
        if (proxyHost == null || proxyHost.trim().isEmpty()) {
            return false;
        }
        
        if (proxyPort < 1 || proxyPort > 65535) {
            return false;
        }
        
        return proxyType != null;
    }
    
    public boolean hasAuthentication() {
        return useProxy && proxyUsername != null && !proxyUsername.isEmpty();
    }
    
    @Override
    public String toString() {
        if (!useProxy) return I18n.get("proxyType.none");
        
        StringBuilder sb = new StringBuilder();
        sb.append(proxyType.toString().toLowerCase()).append("://");
        
        if (hasAuthentication()) {
            sb.append(proxyUsername).append(":***@");
        }
        
        sb.append(proxyHost).append(":").append(proxyPort);
        return sb.toString();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        ProxySettings that = (ProxySettings) o;
        
        if (useProxy != that.useProxy) return false;
        if (proxyPort != that.proxyPort) return false;
        if (proxyType != that.proxyType) return false;
        if (!Objects.equals(proxyHost, that.proxyHost)) return false;
        if (!Objects.equals(proxyUsername, that.proxyUsername)) return false;
        return Objects.equals(proxyPassword, that.proxyPassword);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(useProxy, proxyType, proxyHost, proxyPort, proxyUsername, proxyPassword);
    }
}