import java.text.MessageFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Internationalization utility class for loading and formatting localized messages.
 *
 * <p>The bundle is loaded once per locale and is only replaced when the application
 * language is changed. Reads are thread-safe so background tasks can use the same
 * helpers as the UI thread.</p>
 *
 * <p>Only the bundle of the active language is kept in memory: the JDK's internal
 * bundle cache is cleared after every language change, so switching through all
 * supported languages never accumulates resource bundles.</p>
 */
public final class I18n {
    // Supported locales with their resource file names
    public static final Locale ENGLISH_US = Locale.US;
    public static final Locale SIMPLIFIED_CHINESE = Locale.SIMPLIFIED_CHINESE;
    public static final Locale TRADITIONAL_CHINESE = Locale.forLanguageTag("zh-TW");
    public static final Locale FRENCH = Locale.FRANCE;
    public static final Locale GERMAN = Locale.GERMANY;
    public static final Locale JAPANESE = Locale.JAPAN;
    public static final Locale KOREAN = Locale.KOREA;
    public static final Locale SPANISH = Locale.forLanguageTag("es-ES");
    public static final Locale PORTUGUESE = Locale.forLanguageTag("pt");
    public static final Locale RUSSIAN = Locale.forLanguageTag("ru-RU");

    private static final Locale DEFAULT_LOCALE = ENGLISH_US;

    /**
     * A supported UI language.
     *
     * @param locale     The locale used by the application
     * @param bundleName The resource bundle base name (without the i18n. prefix)
     * @param nativeName The language name written in the language itself; this is
     *                   what the language menu shows, so it never needs translating
     */
    public record Language(Locale locale, String bundleName, String nativeName) {
    }

    private static final List<Language> LANGUAGES = List.of(
            new Language(ENGLISH_US, "messages-en_US", "English"),
            new Language(SIMPLIFIED_CHINESE, "messages-zh_CN", "简体中文"),
            new Language(TRADITIONAL_CHINESE, "messages-zh_TW", "繁體中文"),
            new Language(FRENCH, "messages-fr_FR", "Français"),
            new Language(GERMAN, "messages-de_DE", "Deutsch"),
            new Language(JAPANESE, "messages-ja_JP", "日本語"),
            new Language(KOREAN, "messages-ko_KR", "한국어"),
            new Language(SPANISH, "messages-es_ES", "Español"),
            new Language(PORTUGUESE, "messages-pt", "Português"),
            new Language(RUSSIAN, "messages-ru_RU", "Русский"));

    private static final Map<Locale, Language> LANGUAGES_BY_LOCALE = buildLocaleMap();

    private static volatile ResourceBundle bundle;
    private static volatile Locale currentLocale;

    static {
        // Initialize with detected locale
        setLocale(detectSystemLocale());
    }

    private I18n() {
    }

    private static Map<Locale, Language> buildLocaleMap() {
        Map<Locale, Language> map = new LinkedHashMap<>();
        for (Language language : LANGUAGES) {
            map.put(language.locale(), language);
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Gets all supported languages in menu order.
     *
     * @return Immutable list of supported languages
     */
    public static List<Language> getLanguages() {
        return LANGUAGES;
    }

    /**
     * Detects the best matching locale for the system.
     *
     * @return The detected locale
     */
    public static Locale detectSystemLocale() {
        Locale systemLocale = Locale.getDefault();

        // Check if we have an exact match
        Language exact = LANGUAGES_BY_LOCALE.get(systemLocale);
        if (exact != null) {
            return exact.locale();
        }

        // Check language only (ignore country)
        return matchLanguage(systemLocale.getLanguage(), systemLocale.getCountry());
    }

    private static Locale matchLanguage(String language, String country) {
        if ("zh".equals(language)) {
            // Chinese needs the region: zh_TW, zh_HK and zh_MO use the
            // traditional characters, everything else uses the simplified ones.
            boolean traditional = "TW".equals(country) || "HK".equals(country) || "MO".equals(country);
            return traditional ? TRADITIONAL_CHINESE : SIMPLIFIED_CHINESE;
        }
        for (Language candidate : LANGUAGES) {
            if (candidate.locale().getLanguage().equals(language)) {
                return candidate.locale();
            }
        }
        return DEFAULT_LOCALE;
    }

    /**
     * Sets the locale for internationalization.
     *
     * @param locale The locale to set
     */
    public static void setLocale(Locale locale) {
        Locale target = locale != null ? locale : DEFAULT_LOCALE;
        Language language = LANGUAGES_BY_LOCALE.get(target);

        if (language == null) {
            target = matchLanguage(target.getLanguage(), target.getCountry());
            language = LANGUAGES_BY_LOCALE.get(target);
        }

        ResourceBundle loaded = loadBundle(language.bundleName(), target);
        if (loaded == null && !DEFAULT_LOCALE.equals(target)) {
            System.err.println("Failed to load locale '" + target
                    + "', falling back to default");
            language = LANGUAGES_BY_LOCALE.get(DEFAULT_LOCALE);
            target = DEFAULT_LOCALE;
            loaded = loadBundle(language.bundleName(), target);
        }

        bundle = loaded;
        currentLocale = target;
    }

    private static ResourceBundle loadBundle(String bundleName, Locale locale) {
        try {
            ResourceBundle loaded = ResourceBundle.getBundle("i18n." + bundleName, locale);
            // Keep only the bundle of the active language in memory instead of
            // letting the JDK cache every language the user has switched to.
            ResourceBundle.clearCache();
            return loaded;
        } catch (Exception e) {
            System.err.println("Failed to load resource bundle '" + bundleName + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets the current locale.
     *
     * @return The current locale
     */
    public static Locale getCurrentLocale() {
        Locale locale = currentLocale;
        return locale != null ? locale : DEFAULT_LOCALE;
    }

    /**
     * Gets available locales.
     *
     * @return Array of available locales
     */
    public static Locale[] getAvailableLocales() {
        return LANGUAGES_BY_LOCALE.keySet().toArray(new Locale[0]);
    }

    /**
     * Gets locale display name in its own language.
     *
     * @param locale The locale
     * @return Display name
     */
    public static String getLocaleDisplayName(Locale locale) {
        Language language = locale != null ? LANGUAGES_BY_LOCALE.get(locale) : null;
        return language != null ? language.nativeName() : "";
    }

    /**
     * Gets the resource bundle file name for a locale.
     *
     * @param locale The locale
     * @return Bundle file name without path
     */
    public static String getBundleFileName(Locale locale) {
        Language language = locale != null ? LANGUAGES_BY_LOCALE.get(locale) : null;
        return language != null ? language.bundleName() : LANGUAGES_BY_LOCALE.get(DEFAULT_LOCALE).bundleName();
    }

    /**
     * Gets a localized string for the given key.
     *
     * @param key The message key
     * @return The localized string, or the key itself if not found
     */
    public static String get(String key) {
        ResourceBundle current = bundle;
        if (current == null) {
            return key;
        }
        try {
            return current.containsKey(key) ? current.getString(key) : key;
        } catch (Exception e) {
            return key;
        }
    }

    /**
     * Gets a localized string with arguments.
     *
     * @param key  The message key
     * @param args Arguments to format into the message
     * @return The formatted localized string
     */
    public static String format(String key, Object... args) {
        ResourceBundle current = bundle;
        if (current == null) {
            return key;
        }

        String pattern;
        try {
            if (!current.containsKey(key)) {
                return key;
            }
            pattern = current.getString(key);
        } catch (Exception e) {
            return key;
        }

        try {
            return MessageFormat.format(pattern, args);
        } catch (Exception e) {
            return pattern;
        }
    }

    /**
     * Gets the localized OS display name.
     *
     * @param osName The OS name from PlatformUtils
     * @return Localized OS name
     */
    public static String getLocalizedOSName(String osName) {
        if (osName == null) {
            return get("os.unknown");
        }
        switch (osName.toLowerCase(Locale.ROOT)) {
            case "windows":
                return get("os.windows");
            case "macos":
                return get("os.macos");
            default:
                return get("os.unknown");
        }
    }

    /**
     * Gets the localized version name.
     *
     * @param version The version key (Stable, Beta, Dev, Canary)
     * @return Localized version name
     */
    public static String getLocalizedVersion(String version) {
        String key = "version." + version.toLowerCase(Locale.ROOT);
        String localized = get(key);
        return localized.equals(key) ? version : localized;
    }

    /**
     * Gets the localized proxy type string.
     *
     * @param proxyType The proxy type string
     * @return Localized proxy type
     */
    public static String getLocalizedProxyType(String proxyType) {
        if (proxyType == null || "No Proxy".equals(proxyType)) {
            return get("proxyType.none");
        }
        String key = "proxyType." + proxyType.toLowerCase(Locale.ROOT);
        String localized = get(key);
        return localized.equals(key) ? proxyType : localized;
    }
}
