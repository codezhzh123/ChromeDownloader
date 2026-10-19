public enum ProxyType {
    HTTP("HTTP"),
    HTTPS("HTTPS"),
    SOCKS5("SOCKS5");
    
    private final String displayName;
    
    ProxyType(String displayName) {
        this.displayName = displayName;
    }
    
    public static ProxyType fromString(String type) {
        if (type == null) return null;
        
        switch (type.toUpperCase()) {
            case "HTTP": return HTTP;
            case "HTTPS": return HTTPS;
            case "SOCKS5": return SOCKS5;
            default: return null;
        }
    }
    
    public java.net.Proxy.Type toJavaProxyType() {
        switch (this) {
            case HTTP:
            case HTTPS:
                return java.net.Proxy.Type.HTTP;
            case SOCKS5:
                return java.net.Proxy.Type.SOCKS;
            default:
                return java.net.Proxy.Type.DIRECT;
        }
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}