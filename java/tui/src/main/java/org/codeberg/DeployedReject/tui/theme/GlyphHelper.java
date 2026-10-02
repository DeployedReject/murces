package org.codeberg.DeployedReject.tui.theme;

import org.codeberg.DeployedReject.tui.config.ConfigManager;
import org.codeberg.DeployedReject.tui.config.TuiConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class GlyphHelper {

    private static Boolean cachedDetection = null;

    public static final String ICON_SERVER = "\uF233";
    public static final String ICON_PLAY = "\uF04B";
    public static final String ICON_STOP = "\uF04D";
    public static final String ICON_RESTART = "\uF021";
    public static final String ICON_CONFIG = "\uF013";
    public static final String ICON_OPTIONS = "\uF085";
    public static final String ICON_SAVE = "\uF0C7";
    public static final String ICON_USER = "\uF007";
    public static final String ICON_MOD = "\uF1B2";
    public static final String ICON_FOLDER = "\uF07B";
    public static final String ICON_THEME = "\uF1FC";
    public static final String ICON_TASKS = "\uF0AE";
    public static final String ICON_POWER = "\uF011";
    public static final String ICON_SEARCH = "\uF002";
    public static final String ICON_CHECK = "\uF00C";
    public static final String ICON_CROSS = "\uF00D";
    public static final String ICON_WARN = "\uF071";
    public static final String ICON_INFO = "\uF05A";
    public static final String ICON_TERMINAL = "\uF120";
    public static final String ICON_TUNNEL = "\uF1E6";
    public static final String ICON_FILE = "\uF15B";
    public static final String ICON_BUSY = "\uF110";
    public static final String ICON_BACK = "\uF060";
    public static final String ICON_PACKAGE = "\uF187";
    public static final String ICON_DOWNLOAD = "\uF019";
    public static final String ICON_EDIT = "\uF040";
    public static final String ICON_TOOL = "\uF0AD";

    private static final Pattern NERD_FONT_PATTERN = Pattern.compile("([\\uE000-\\uF8FF]|[\\uD800-\\uDBFF][\\uDC00-\\uDFFF])\\s?");

    private GlyphHelper() {}

    public static void invalidateCache() {
        cachedDetection = null;
    }

    public static boolean isNerdFontEnabled() {

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

        String term = System.getenv("TERM");
        if (term != null) {
            String lower = term.toLowerCase();
            if (lower.equals("linux") || lower.equals("dumb") || lower.equals("vt100") || lower.contains("cons")) {
                return false;
            }
        }

        if (cachedDetection == null) {
            cachedDetection = detectNerdFontSupport();
        }
        return cachedDetection;
    }

    private static boolean detectNerdFontSupport() {

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

    public static String apply(String text) {
        if (text == null) return null;
        if (isNerdFontEnabled()) {
            return text;
        }
        return toBasicFallback(text);
    }

    public static String toBasicFallback(String text) {
        if (text == null) return null;

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

        s = NERD_FONT_PATTERN.matcher(s).replaceAll("");

        return s;
    }
}
