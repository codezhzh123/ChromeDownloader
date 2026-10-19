# ChromeDownloader Tool
💕💕 Download Google Chrome offline installer for x64 Windows or macOS

## System Requirements
- **JDK**: Bellsoft Liberica Full JDK (includes JavaFX)
- **OS**: Windows or macOS
- **Architecture**: x64 (Windows) / Universal (macOS)

## Install Dependencies
1. Download and install Bellsoft Liberica Full JDK:
   [https://bell-sw.com/pages/downloads](https://bell-sw.com/pages/downloads)

## Compilation & Packaging

### Step 1: Compile Source Code
```bash
javac --add-modules javafx.controls,javafx.fxml *.java
```

### Step 2: Create Executable JAR
```bash
jar cfe app/ChromeDownloader.jar Main *.class i18n images
```

### Step 3: Create Native Application

**Windows Version:**
```bash
jpackage --type app-image --name "ChromeDownloader" --input app --main-jar ChromeDownloader.jar --main-class Main --icon images/*.ico --java-options "-Xmx256m" --java-options "-XX:+UseSerialGC"
```

**macOS Version:**
```bash
jpackage --type dmg --name "ChromeDownloader" --input app --main-jar ChromeDownloader.jar --main-class Main --icon images/*.icns --java-options "-Xmx256m" --java-options "-XX:+UseSerialGC"
```

> **Memory footprint:** the `-Xmx256m` option caps the Java heap and `-XX:+UseSerialGC`
> avoids the extra garbage-collector threads, which keeps the process footprint small.
> When running the JAR directly you can pass the same options:
> `java -Xmx256m -XX:+UseSerialGC -jar ChromeDownloader.jar`
>
> For an even smaller footprint add `-XX:TieredStopAtLevel=1` (C1-only JIT). It saves
> another ~7 MB and starts faster; downloads are network bound, so the lower JIT tier
> is not noticeable:
> `java -Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -jar ChromeDownloader.jar`
> The same flag can be handed to `jpackage` as one more `--java-options` argument.
>
> Measured working set on Windows 11, eight seconds after startup (minimum of several
> runs): ~177 MB with the JVM defaults, ~151 MB with `-Xmx256m -XX:+UseSerialGC` and
> ~144 MB with `-XX:TieredStopAtLevel=1` added.

## Supported Languages
The application ships with ten UI languages and pre-selects the language of the
operating system on first start (Chinese systems are matched to the traditional or the
simplified bundle by their region):

| Language | Bundle |
| --- | --- |
| English | `i18n/messages-en_US.properties` |
| 简体中文 | `i18n/messages-zh_CN.properties` |
| 繁體中文 | `i18n/messages-zh_TW.properties` |
| Français | `i18n/messages-fr_FR.properties` |
| Deutsch | `i18n/messages-de_DE.properties` |
| 日本語 | `i18n/messages-ja_JP.properties` |
| 한국어 | `i18n/messages-ko_KR.properties` |
| Español | `i18n/messages-es_ES.properties` |
| Русский | `i18n/messages-ru_RU.properties` |

The bundles are UTF-8 encoded, are read with `ResourceBundle` and must keep the same
keys. Text that is passed through `I18n.format(...)` is a `MessageFormat` pattern, so a
literal apostrophe has to be written twice (`''`). Only the bundle of the active
language is kept in memory, switching languages does not accumulate them.

## Line Endings
`.gitattributes` pins the line endings, so the working copy is identical no matter
which system checks it out (this overrides the local `core.autocrlf`, which would
otherwise store LF on Windows but CRLF on macOS):

- all text files (`*.java`, `*.properties`, `*.md`, `LICENSE`) use **LF**, stored and
  checked out, so no line-ending-only changes can show up in a diff,
- `*.bat` / `*.cmd` keep **CRLF** (cmd.exe rejects LF) and `*.sh` keeps **LF**,
- `images/*` and `*.jar` / `*.class` are binary and are never converted.

The sources are plain LF; the Java compiler, `jar`, `jpackage` and `ResourceBundle`
all read them the same on Windows and macOS. No text file is written by the
application itself, and the only place that needs a platform line break (copying all
links to the clipboard) uses `System.lineSeparator()`.

## Usage Instructions
1. Launch the ChromeDownloader application
2. Select your preferred Chrome version from the list
3. Click **Fetch Download Links** (or press **Enter**) to retrieve the offline installer URLs
4. Select the link you want and click **Download Selected Link**
5. When the download finishes, click **Open Folder** to jump to the saved installer,
   and press **Esc** to cancel a running download

The window can be resized; the link list grows with it, and the status bar at the
bottom always shows what the application is currently doing.

## Portable Chrome
If you need a portable version of Chrome browser, try:
- **Chrome++**: [https://github.com/Bush2021/chrome_plus](https://github.com/Bush2021/chrome_plus)


