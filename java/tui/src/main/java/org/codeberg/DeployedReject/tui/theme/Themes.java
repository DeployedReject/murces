package org.codeberg.DeployedReject.tui.theme;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.graphics.Theme;
import com.googlecode.lanterna.gui2.Border;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Window;

import java.util.*;

public class Themes {

    public static final String RANDOM_THEME = "Random (Every time)";

    public static class ThemePalette {
        public final String name;
        public final TextColor bg;
        public final TextColor surface;
        public final TextColor fg;
        public final TextColor selection;
        public final TextColor accent;
        public final TextColor success;
        public final TextColor warning;
        public final TextColor error;
        public final TextColor border;
        public final TextColor muted;

        public ThemePalette(String name,
                            TextColor bg, TextColor surface, TextColor fg,
                            TextColor selection, TextColor accent, TextColor success,
                            TextColor warning, TextColor error, TextColor border,
                            TextColor muted) {
            this.name = name;
            this.bg = bg;
            this.surface = surface;
            this.fg = fg;
            this.selection = selection;
            this.accent = accent;
            this.success = success;
            this.warning = warning;
            this.error = error;
            this.border = border;
            this.muted = muted;
        }
    }

    private static final Map<String, ThemePalette> PALETTES = new LinkedHashMap<>();
    private static volatile ThemePalette activePalette;
    private static volatile int activeTransparency = 0;

    static {

        register(new ThemePalette("Tokyo Night",
                new TextColor.RGB(26, 27, 38),
                new TextColor.RGB(36, 40, 59),
                new TextColor.RGB(192, 202, 245),
                new TextColor.RGB(51, 70, 124),
                new TextColor.RGB(122, 162, 247),
                new TextColor.RGB(158, 206, 106),
                new TextColor.RGB(224, 175, 104),
                new TextColor.RGB(247, 118, 142),
                new TextColor.RGB(122, 162, 247),
                new TextColor.RGB(86, 95, 137)
        ));

        register(new ThemePalette("Catppuccin Mocha",
                new TextColor.RGB(30, 30, 46),
                new TextColor.RGB(49, 50, 68),
                new TextColor.RGB(205, 214, 244),
                new TextColor.RGB(69, 71, 90),
                new TextColor.RGB(203, 166, 247),
                new TextColor.RGB(166, 227, 161),
                new TextColor.RGB(249, 226, 175),
                new TextColor.RGB(243, 139, 168),
                new TextColor.RGB(203, 166, 247),
                new TextColor.RGB(108, 112, 134)
        ));

        register(new ThemePalette("Catppuccin Macchiato",
                new TextColor.RGB(36, 39, 58),
                new TextColor.RGB(54, 58, 79),
                new TextColor.RGB(202, 211, 245),
                new TextColor.RGB(73, 77, 100),
                new TextColor.RGB(198, 160, 246),
                new TextColor.RGB(166, 218, 149),
                new TextColor.RGB(238, 212, 159),
                new TextColor.RGB(237, 135, 150),
                new TextColor.RGB(198, 160, 246),
                new TextColor.RGB(110, 115, 141)
        ));

        register(new ThemePalette("Catppuccin Frappé",
                new TextColor.RGB(48, 52, 70),
                new TextColor.RGB(65, 69, 89),
                new TextColor.RGB(198, 208, 245),
                new TextColor.RGB(81, 87, 109),
                new TextColor.RGB(202, 158, 230),
                new TextColor.RGB(166, 209, 137),
                new TextColor.RGB(229, 200, 144),
                new TextColor.RGB(231, 130, 132),
                new TextColor.RGB(202, 158, 230),
                new TextColor.RGB(115, 121, 148)
        ));

        register(new ThemePalette("Catppuccin Latte",
                new TextColor.RGB(239, 241, 245),
                new TextColor.RGB(230, 233, 239),
                new TextColor.RGB(76, 79, 105),
                new TextColor.RGB(204, 208, 218),
                new TextColor.RGB(136, 57, 239),
                new TextColor.RGB(64, 160, 43),
                new TextColor.RGB(223, 142, 29),
                new TextColor.RGB(210, 15, 57),
                new TextColor.RGB(136, 57, 239),
                new TextColor.RGB(140, 143, 161)
        ));

        register(new ThemePalette("Gruvbox Dark",
                new TextColor.RGB(40, 40, 40),
                new TextColor.RGB(60, 56, 54),
                new TextColor.RGB(235, 219, 178),
                new TextColor.RGB(80, 73, 69),
                new TextColor.RGB(250, 189, 47),
                new TextColor.RGB(184, 187, 38),
                new TextColor.RGB(254, 128, 25),
                new TextColor.RGB(251, 73, 52),
                new TextColor.RGB(250, 189, 47),
                new TextColor.RGB(146, 131, 116)
        ));

        register(new ThemePalette("Nord",
                new TextColor.RGB(46, 52, 64),
                new TextColor.RGB(59, 66, 82),
                new TextColor.RGB(236, 239, 244),
                new TextColor.RGB(67, 76, 94),
                new TextColor.RGB(136, 192, 208),
                new TextColor.RGB(163, 190, 140),
                new TextColor.RGB(235, 203, 139),
                new TextColor.RGB(191, 97, 106),
                new TextColor.RGB(136, 192, 208),
                new TextColor.RGB(123, 136, 161)
        ));

        register(new ThemePalette("Kanagawa",
                new TextColor.RGB(31, 31, 40),
                new TextColor.RGB(42, 42, 55),
                new TextColor.RGB(220, 215, 186),
                new TextColor.RGB(45, 79, 103),
                new TextColor.RGB(126, 156, 216),
                new TextColor.RGB(118, 148, 106),
                new TextColor.RGB(192, 163, 110),
                new TextColor.RGB(195, 64, 67),
                new TextColor.RGB(126, 156, 216),
                new TextColor.RGB(114, 113, 105)
        ));

        register(new ThemePalette("Rose Pine",
                new TextColor.RGB(25, 23, 36),
                new TextColor.RGB(38, 35, 58),
                new TextColor.RGB(224, 222, 244),
                new TextColor.RGB(64, 61, 82),
                new TextColor.RGB(235, 188, 186),
                new TextColor.RGB(49, 116, 143),
                new TextColor.RGB(246, 193, 119),
                new TextColor.RGB(235, 111, 146),
                new TextColor.RGB(235, 188, 186),
                new TextColor.RGB(144, 140, 170)
        ));

        register(new ThemePalette("Solarized Osaka",
                new TextColor.RGB(0, 43, 54),
                new TextColor.RGB(7, 54, 66),
                new TextColor.RGB(131, 148, 150),
                new TextColor.RGB(14, 75, 90),
                new TextColor.RGB(42, 161, 152),
                new TextColor.RGB(133, 153, 0),
                new TextColor.RGB(181, 137, 0),
                new TextColor.RGB(220, 50, 47),
                new TextColor.RGB(42, 161, 152),
                new TextColor.RGB(101, 123, 131)
        ));

        register(new ThemePalette("Cyberdream",
                new TextColor.RGB(22, 24, 26),
                new TextColor.RGB(30, 33, 36),
                new TextColor.RGB(255, 255, 255),
                new TextColor.RGB(60, 64, 72),
                new TextColor.RGB(255, 94, 160),
                new TextColor.RGB(94, 255, 108),
                new TextColor.RGB(241, 255, 94),
                new TextColor.RGB(255, 110, 94),
                new TextColor.RGB(255, 94, 160),
                new TextColor.RGB(123, 132, 150)
        ));

        register(new ThemePalette("Minecraft Classic",
                new TextColor.RGB(27, 18, 12),
                new TextColor.RGB(58, 36, 20),
                new TextColor.RGB(224, 224, 224),
                new TextColor.RGB(92, 60, 36),
                new TextColor.RGB(85, 255, 255),
                new TextColor.RGB(85, 255, 85),
                new TextColor.RGB(255, 170, 0),
                new TextColor.RGB(255, 85, 85),
                new TextColor.RGB(85, 255, 255),
                new TextColor.RGB(170, 170, 170)
        ));

        activePalette = PALETTES.get("Gruvbox Dark");
    }

    private static void register(ThemePalette palette) {
        PALETTES.put(palette.name, palette);
    }

    public static List<String> getAvailableThemeNames() {
        List<String> list = new ArrayList<>(PALETTES.keySet());
        list.add(RANDOM_THEME);
        return list;
    }

    public static ThemePalette getPalette(String name) {
        if (name != null && (name.equalsIgnoreCase(RANDOM_THEME) || name.equalsIgnoreCase("Random"))) {
            List<ThemePalette> all = new ArrayList<>(PALETTES.values());
            return all.get(new Random().nextInt(all.size()));
        }
        if (name != null && PALETTES.containsKey(name)) {
            return PALETTES.get(name);
        }
        return PALETTES.get("Gruvbox Dark");
    }

    public static synchronized Theme createTheme(String themeName, int transparency, boolean trueColor) {
        ThemePalette p = getPalette(themeName);
        activePalette = p;
        activeTransparency = Math.max(0, Math.min(100, transparency));

        TextColor fg = trueColor ? p.fg : TextColor.ANSI.WHITE;
        TextColor accent = trueColor ? p.accent : TextColor.ANSI.CYAN;
        TextColor selection = trueColor ? p.selection : TextColor.ANSI.BLUE;
        TextColor muted = trueColor ? p.muted : TextColor.ANSI.BLACK_BRIGHT;
        TextColor border = trueColor ? p.border : TextColor.ANSI.WHITE;

        TextColor baseBg;
        if (activeTransparency == 0) {
            baseBg = trueColor ? p.bg : TextColor.ANSI.BLACK;
        } else {
            baseBg = TextColor.ANSI.DEFAULT;
        }

        SimpleTheme theme = new SimpleTheme(fg, baseBg);
        theme.getDefaultDefinition()
                .setSelected(trueColor ? p.bg : TextColor.ANSI.BLACK, accent, com.googlecode.lanterna.SGR.BOLD)
                .setActive(trueColor ? p.bg : TextColor.ANSI.BLACK, accent, com.googlecode.lanterna.SGR.BOLD)
                .setPreLight(fg, selection)
                .setInsensitive(muted, baseBg);

        theme.addOverride(Panel.class, fg, baseBg);
        theme.addOverride(Window.class, fg, baseBg);
        theme.addOverride(Border.class, border, baseBg);

        return theme;
    }

    public static ThemePalette getActivePalette() {
        return activePalette != null ? activePalette : PALETTES.get("Gruvbox Dark");
    }

    public static TextColor getSuccessColor() {
        return getActivePalette().success;
    }

    public static TextColor getErrorColor() {
        return getActivePalette().error;
    }

    public static TextColor getWarningColor() {
        return getActivePalette().warning;
    }

    public static TextColor getAccentColor() {
        return getActivePalette().accent;
    }

    public static TextColor getMutedColor() {
        return getActivePalette().muted;
    }

    public static TextColor getBorderColor() {
        return getActivePalette().border;
    }

    public static final TextColor LOG_BG = new TextColor.RGB(16, 16, 18);
    public static final TextColor LOG_FG_NORMAL = new TextColor.RGB(240, 240, 245);
    public static final TextColor LOG_SUCCESS = new TextColor.RGB(85, 255, 85);
    public static final TextColor LOG_ERROR = new TextColor.RGB(255, 85, 85);
    public static final TextColor LOG_WARN = new TextColor.RGB(255, 215, 0);
    public static final TextColor LOG_PROG = new TextColor.RGB(85, 255, 255);
    public static final TextColor LOG_INFO = new TextColor.RGB(137, 180, 250);
    public static final TextColor LOG_MUTED = new TextColor.RGB(140, 140, 140);

    public static TextColor getLogBackgroundColor() {
        if (activeTransparency >= 50) {
            return TextColor.ANSI.DEFAULT;
        }
        return LOG_BG;
    }

    public static TextColor getLogTextColor() {
        return LOG_FG_NORMAL;
    }

    public static TextColor getLogSuccessColor() {
        return LOG_SUCCESS;
    }

    public static TextColor getLogErrorColor() {
        return LOG_ERROR;
    }

    public static TextColor getLogWarnColor() {
        return LOG_WARN;
    }

    public static TextColor getLogProgColor() {
        return LOG_PROG;
    }

    public static TextColor getLogInfoColor() {
        return LOG_INFO;
    }

    public static TextColor getLogMutedColor() {
        return LOG_MUTED;
    }
}
