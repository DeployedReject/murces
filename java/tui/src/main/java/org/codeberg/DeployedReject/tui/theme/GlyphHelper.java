package org.codeberg.DeployedReject.tui.theme;

import org.codeberg.DeployedReject.tui.config.ConfigManager;
import org.codeberg.DeployedReject.tui.config.TuiConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Utility to manage Nerd Font glyph rendering and automatic graceful fallback
 * to clean basic ASCII/Unicode on terminals and environments where Nerd Fonts are
 * not installed or supported.
 */
public final class GlyphHelper {

    private static Boolean cachedDetection = null;

    // Standard 16-bit BMP Nerd Font Glyphs (\uE000-\uF8FF, single 16-bit char, 1 terminal column, zero surrogates)
    public static final String ICON_SERVER = "\uF233";      //  (nf-fa-server)
    public static final String ICON_PLAY = "\uF04B";        //  (nf-fa-play)
    public static final String ICON_STOP = "\uF04D";        //  (nf-fa-stop)
    public static final String ICON_RESTART = "\uF021";     //  (nf-fa-refresh)
    public static final String ICON_CONFIG = "\uF013";      //  (nf-fa-cog)
    public static final String ICON_OPTIONS = "\uF085";     //  (nf-fa-cogs)
    public static final String ICON_SAVE = "\uF0C7";        //  (nf-fa-save)
    public static final String ICON_USER = "\uF007";        //  (nf-fa-user)
    public static final String ICON_MOD = "\uF1B2";         //  (nf-fa-cube)
    public static final String ICON_FOLDER = "\uF07B";      //  (nf-fa-folder)
    public static final String ICON_THEME = "\uF1FC";       //  (nf-fa-paint_brush)
    public static final String ICON_TASKS = "\uF0AE";       //  (nf-fa-tasks)
    public static final String ICON_POWER = "\uF011";       //  (nf-fa-power_off)
    public static final String ICON_SEARCH = "\uF002";      //  (nf-fa-search)
    public static final String ICON_CHECK = "\uF00C";       //  (nf-fa-check)
    public static final String ICON_CROSS = "\uF00D";       //  (nf-fa-times)
    public static final String ICON_WARN = "\uF071";        //  (nf-fa-warning)
    public static final String ICON_INFO = "\uF05A";        //  (nf-fa-info_circle)
    public static final String ICON_TERMINAL = "\uF120";    //  (nf-fa-terminal)
    public static final String ICON_TUNNEL = "\uF1E6";      //  (nf-fa-plug)
    public static final String ICON_FILE = "\uF15B";        //  (nf-fa-file)
    public static final String ICON_BUSY = "\uF110";        //  (nf-fa-spinner)
    public static final String ICON_BACK = "\uF060";        //  (nf-fa-arrow_left)
    public static final String ICON_PACKAGE = "\uF187";     //  (nf-fa-archive)
    public static final String ICON_DOWNLOAD = "\uF019";    //  (nf-fa-download)
    public static final String ICON_EDIT = "\uF040";        //  (nf-fa-pencil)
    public static final String ICON_TOOL = "\uF0AD";        //  (nf-fa-wrench)

    // Pattern matching Nerd Font Private Use Area glyphs (BMP PUA and Supplementary PUA surrogate pairs) with optional trailing space
    private static final Pattern NERD_FONT_PATTERN = Pattern.compile("([\\uE000-\\uF8FF]|[\\uD800-\\uDBFF][\\uDC00-\\uDFFF])\\s?");

    private GlyphHelper() {}

    /**
     * Invalidate cached detection (e.g. when user changes font setting in Customization view).
     */
    public static void invalidateCache() {
        cachedDetection = null;
    }

    /**
     * Determines whether Nerd Font glyphs should be rendered or if the TUI
     * should fall back to basic text rendering.
     */
    public static boolean isNerdFontEnabled() {
        // 1. Check user persistent configuration
        try {
            TuiConfig config = ConfigManager.getInstance().getConfig();
            if (config != null && config.getNerdFontMode() != null) {
                String mode = config.getNerdFontMode().trim().toLowerCase();
                if ("enabled".equals(mode) || "force".equals(mode)) {
                    return true;
                }
                if ("disabled".equals(mode) || "basic".equals(mode) || "fallback".equals(mode)) {
                    return false;
                }
            }
        } catch (Exception ignored) {}

        // 2. Check explicit environment variable flags
        String noNerd = System.getenv("NO_NERD_FONT");
        if (noNerd == null) noNerd = System.getenv("NO_NERDFONT");
        if (noNerd != null && ("1".equals(noNerd) || "true".equalsIgnoreCase(noNerd))) {
            return false;
        }

        String forceNerd = System.getenv("FORCE_NERD_FONT");
        if (forceNerd == null) forceNerd = System.getenv("NERDFONT");
        if (forceNerd == null) forceNerd = System.getenv("NERD_FONT");
        if (forceNerd != null) {
            if ("1".equals(forceNerd) || "true".equalsIgnoreCase(forceNerd)) return true;
            if ("0".equals(forceNerd) || "false".equalsIgnoreCase(forceNerd)) return false;
        }

        // 3. Check TERM for dumb/linux/virtual console terminals
        String term = System.getenv("TERM");
        if (term != null) {
            String lower = term.toLowerCase();
            if (lower.equals("linux") || lower.equals("dumb") || lower.equals("vt100") || lower.contains("cons")) {
                return false;
            }
        }

        // 4. Auto-detect support
        if (cachedDetection == null) {
            cachedDetection = detectNerdFontSupport();
        }
        return cachedDetection;
    }

    /**
     * Inspects system environment, modern terminal emulators, and font installations.
     */
    private static boolean detectNerdFontSupport() {
        // 1. Direct filesystem font scan (fast pure Java, no external process)
        try {
            String home = System.getProperty("user.home", "");
            String winDir = System.getenv("WINDIR");
            String localAppData = System.getenv("LOCALAPPDATA");
            String[] standardFontPaths = {
                home + "/.local/share/fonts",
                home + "/.fonts",
                "/usr/share/fonts",
                "/usr/local/share/fonts",
                home + "/Library/Fonts",
                "/Library/Fonts",
                "/System/Library/Fonts",
                (winDir != null ? winDir + "\\Fonts" : "C:\\Windows\\Fonts"),
                (localAppData != null ? localAppData + "\\Microsoft\\Windows\\Fonts" : "")
            };
            for (String p : standardFontPaths) {
                if (p == null || p.isEmpty()) continue;
                File dir = new File(p);
                if (dir.exists() && dir.isDirectory()) {
                    if (scanDirForNerdFont(dir, 0)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2. Query fontconfig (fc-list) on Linux/Unix if available
        try {
            ProcessBuilder pb = new ProcessBuilder("fc-list", ":", "family");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(300, TimeUnit.MILLISECONDS);
            if (finished && p.exitValue() == 0) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String lower = line.toLowerCase();
                        if (lower.contains("nerd font") || lower.contains("nerdfont")) {
                            return true;
                        }
                    }
                }
            } else {
                p.destroyForcibly();
            }
        } catch (Exception ignored) {}

        // 3. Known terminal emulators that specifically package or alias Nerd Fonts
        if (System.getenv("KITTY_WINDOW_ID") != null ||
            System.getenv("GHOSTTY_RESOURCES_DIR") != null ||
            System.getenv("WEZTERM_EXECUTABLE") != null) {
            return true;
        }

        return false;
    }

    private static boolean scanDirForNerdFont(File dir, int depth) {
        if (depth > 3) return false;
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (name.contains("nerd") || name.contains("nf")) {
                return true;
            }
            if (f.isDirectory() && depth < 3) {
                if (scanDirForNerdFont(f, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Transforms input string: returns unchanged if Nerd Font is enabled;
     * strips Nerd Font icons or replaces them with basic ASCII/Unicode if disabled.
     */
    public static String apply(String text) {
        if (text == null) return null;
        if (isNerdFontEnabled()) {
            return text;
        }
        return toBasicFallback(text);
    }

    /**
     * Converts a string containing Nerd Font icons into clean basic ASCII/Unicode text.
     */
    public static String toBasicFallback(String text) {
        if (text == null) return null;

        // Custom replacements for clean formatting
        String s = text;
        s = s.replace("◀ Prev ([)", "<- Prev ([)")
             .replace("Next (]) ▶", "Next (]) ->")
             .replace("▾ Summary (Heading)", "Summary (Heading)")
             .replace("▾ Read More (Full Description)", "Read More (Full Description)")
             .replace("● [RUNNING]", "[RUNNING]")
             .replace("○ [STOPPED]", "[STOPPED]")
             .replace("✕ [NOT INSTALLED]", "[NOT INSTALLED]");

        if (s.contains("TERMINAL WINDOW TOO SMALL")) {
            s = s.replace(ICON_WARN, "[!]");
        }

        // Remove private-use unicode glyphs and at most one trailing space
        s = NERD_FONT_PATTERN.matcher(s).replaceAll("");

        return s;
    }
}
