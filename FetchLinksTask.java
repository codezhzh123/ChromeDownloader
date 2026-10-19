import javafx.concurrent.Task;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Task for fetching download links from Google Update service.
 */
public class FetchLinksTask extends Task<List<String>> {
    private static final String SERVICE_URL = "https://tools.google.com/service/update2";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final String USER_AGENT = "Google Update/1.3.32.7;winhttp;cup-ecdsa";

    private final String versionLabel;
    private final ProxySettings proxySettings;

    public FetchLinksTask(String versionLabel) {
        this(versionLabel, new ProxySettings());
    }

    public FetchLinksTask(String versionLabel, ProxySettings proxySettings) {
        if (versionLabel == null || versionLabel.trim().isEmpty()) {
            throw new IllegalArgumentException("Version label cannot be null or empty");
        }

        this.versionLabel = versionLabel;
        this.proxySettings = proxySettings != null ? proxySettings : new ProxySettings();
    }

    @Override
    protected List<String> call() throws Exception {
        Map<String, String> config = PlatformUtils.getVersionMapping().get(versionLabel);
        if (config == null) {
            throw new IllegalArgumentException(
                    I18n.format("error.unsupportedVersion", versionLabel));
        }

        String channel = config.get("channel");
        String appid = config.get("appid");
        String platform = PlatformUtils.getPlatformForUpdateService();
        String arch = PlatformUtils.getArchForUpdateService();
        String osVersion = PlatformUtils.isWindows() ? "10.0" : "13.0";

        URI uri;
        try {
            uri = new URI(SERVICE_URL);
        } catch (URISyntaxException e) {
            throw new IOException(I18n.format("error.invalidURL", SERVICE_URL), e);
        }

        HttpURLConnection connection = null;
        try {
            Proxy proxy = proxySettings.createProxy();
            connection = (HttpURLConnection) uri.toURL().openConnection(proxy);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Content-Type", "text/xml; charset=UTF-8");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);

            String requestBody = buildRequestBody(channel, appid, platform, osVersion, arch);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

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

            Document doc;
            try (InputStream in = connection.getInputStream()) {
                doc = createDocumentBuilderFactory().newDocumentBuilder().parse(in);
            }
            return extractLinks(doc);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String buildRequestBody(String channel, String appid, String platform,
                                    String osVersion, String arch) {
        String sessionId = UUID.randomUUID().toString().toUpperCase();
        String requestId = UUID.randomUUID().toString().toUpperCase();

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<request protocol=\"3.0\" version=\"1.3.23.9\" shell_version=\"1.3.21.103\" ismachine=\"0\" "
                + "sessionid=\"" + sessionId + "\" installsource=\"ondemandcheckforupdate\" requestid=\""
                + requestId + "\" dedup=\"cr\">"
                + "<hw physmemory=\"1200000\" sse=\"1\" sse2=\"1\" sse3=\"1\" ssse3=\"1\" sse41=\"1\" "
                + "sse42=\"1\" avx=\"1\"/>"
                + "<os platform=\"" + platform + "\" version=\"" + osVersion + "\" arch=\"" + arch + "\"/>"
                + "<app appid=\"" + appid + "\" version=\"\" nextversion=\"\" ap=\"" + channel
                + "\" lang=\"en-US\">"
                + "<updatecheck/>"
                + "</app>"
                + "</request>";
    }

    private static DocumentBuilderFactory createDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private static List<String> extractLinks(Document doc) {
        NodeList appNodes = doc.getElementsByTagName("app");
        if (appNodes.getLength() == 0) {
            throw new IllegalStateException(I18n.get("error.noAppElement"));
        }

        Element appElement = (Element) appNodes.item(0);
        NodeList urlNodes = appElement.getElementsByTagName("url");
        NodeList packageNodes = appElement.getElementsByTagName("package");

        // A response can contain the same package for several codebases.
        Set<String> links = new LinkedHashSet<>();
        for (int i = 0; i < urlNodes.getLength(); i++) {
            String codebase = ((Element) urlNodes.item(i)).getAttribute("codebase").trim();
            if (codebase.isEmpty()) {
                continue;
            }
            if (!codebase.endsWith("/")) {
                codebase += "/";
            }

            for (int j = 0; j < packageNodes.getLength(); j++) {
                String name = ((Element) packageNodes.item(j)).getAttribute("name").trim();
                if (name.isEmpty()) {
                    continue;
                }

                String fullUrl = codebase + name;
                if (!fullUrl.startsWith("https://") && !fullUrl.startsWith("http://")) {
                    continue;
                }
                if (fullUrl.startsWith("https://www.google.com/dl/")
                        || fullUrl.startsWith("http://www.google.com/dl/")) {
                    continue;
                }
                links.add(fullUrl);
            }
        }

        if (links.isEmpty()) {
            throw new IllegalStateException(I18n.get("error.noLinks"));
        }
        return new ArrayList<>(links);
    }
}
