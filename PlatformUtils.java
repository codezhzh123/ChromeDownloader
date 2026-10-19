import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Platform detection helpers and Chrome update-service configuration.
 */
public final class PlatformUtils {

    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final String OS_ARCH = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    private static final Map<String, Map<String, String>> VERSION_MAPPING = buildVersionMapping();

    private PlatformUtils() {
    }

    public static boolean isWindows() {
        return OS_NAME.contains("win");
    }

    public static boolean isMac() {
        return OS_NAME.contains("mac") || OS_NAME.contains("darwin");
    }

    public static String getArchForUpdateService() {
        if (isWindows()) {
            // The channels below are the x64 ones, matching the Windows build.
            return "x64";
        }
        if (isMac()) {
            return OS_ARCH.contains("aarch64") || OS_ARCH.contains("arm64") ? "arm64" : "x64";
        }
        throw new UnsupportedOperationException(I18n.format("error.unsupportedOS", OS_NAME));
    }

    public static String getPlatformForUpdateService() {
        if (isWindows()) {
            return "win";
        }
        if (isMac()) {
            return "mac";
        }
        throw new UnsupportedOperationException(I18n.format("error.unsupportedOS", OS_NAME));
    }

    /**
     * Returns the update-service channel/appid mapping. The map is built once
     * because the platform cannot change while the application is running.
     */
    public static Map<String, Map<String, String>> getVersionMapping() {
        return VERSION_MAPPING;
    }

    private static Map<String, Map<String, String>> buildVersionMapping() {
        Map<String, Map<String, String>> map = new LinkedHashMap<>();

        if (isWindows()) {
            String stableAppId = "{8A69D345-D564-463C-AFF1-A69D9E530F96}";
            String canaryAppId = "{4EA16AC7-FD5A-47C3-875B-DBF4A2008C20}";
            map.put("Stable", Map.of("channel", "x64-stable-multi-chrome", "appid", stableAppId));
            map.put("Beta", Map.of("channel", "x64-beta-multi-chrome", "appid", stableAppId));
            map.put("Dev", Map.of("channel", "x64-dev-statsdef_1", "appid", stableAppId));
            map.put("Canary", Map.of("channel", "x64-canary", "appid", canaryAppId));
        } else if (isMac()) {
            map.put("Stable", Map.of("channel", "", "appid", "com.google.Chrome"));
            map.put("Beta", Map.of("channel", "betachannel", "appid", "com.google.Chrome.Beta"));
            map.put("Dev", Map.of("channel", "devchannel", "appid", "com.google.Chrome.Dev"));
            map.put("Canary", Map.of("channel", "canarychannel", "appid", "com.google.Chrome.Canary"));
        } else {
            return Collections.emptyMap();
        }

        return Collections.unmodifiableMap(map);
    }

    public static String getOSDisplayName() {
        if (isWindows()) {
            return "Windows";
        }
        if (isMac()) {
            return "macOS";
        }
        return "Unknown";
    }
}
