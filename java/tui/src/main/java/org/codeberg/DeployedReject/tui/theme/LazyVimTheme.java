package org.codeberg.DeployedReject.tui.theme;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.graphics.Theme;
import com.googlecode.lanterna.gui2.Border;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Window;

import java.util.*;

/**
 * 24-bit TrueColor LazyVim and popular editor themes with configurable transparency.
 */
public class LazyVimTheme {

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
        // 1. Tokyo Night (LazyVim default)
        register(new ThemePalette("Tokyo Night",
                new TextColor.RGB(26, 27, 38),     // #1a1b26
                new TextColor.RGB(36, 40, 59),     // #24283b
                new TextColor.RGB(192, 202, 245),  // #c0caf5
                new TextColor.RGB(51, 70, 124),    // #33467c
                new TextColor.RGB(122, 162, 247),  // #7aa2f7
                new TextColor.RGB(158, 206, 106),  // #9ece6a
                new TextColor.RGB(224, 175, 104),  // #e0af68
                new TextColor.RGB(247, 118, 142),  // #f7768e
                new TextColor.RGB(59, 66, 97),     // #3b4261
                new TextColor.RGB(86, 95, 137)     // #565f89
        ));

        // 2. Catppuccin Mocha
        register(new ThemePalette("Catppuccin Mocha",
                new TextColor.RGB(30, 30, 46),     // #1e1e2e
                new TextColor.RGB(49, 50, 68),     // #313244
                new TextColor.RGB(205, 214, 244),  // #cdd6f4
                new TextColor.RGB(69, 71, 90),     // #45475a
                new TextColor.RGB(203, 166, 247),  // #cba6f7
                new TextColor.RGB(166, 227, 161),  // #a6e3a1
                new TextColor.RGB(249, 226, 175),  // #f9e2af
                new TextColor.RGB(243, 139, 168),  // #f38ba8
                new TextColor.RGB(88, 91, 112),    // #585b70
                new TextColor.RGB(108, 112, 134)   // #6c7086
        ));

        // 3. Catppuccin Macchiato
        register(new ThemePalette("Catppuccin Macchiato",
                new TextColor.RGB(36, 39, 58),     // #24273a
                new TextColor.RGB(54, 58, 79),     // #363a4f
                new TextColor.RGB(202, 211, 245),  // #cad3f5
                new TextColor.RGB(73, 77, 100),    // #494d64
                new TextColor.RGB(198, 160, 246),  // #c6a0f6
                new TextColor.RGB(166, 218, 149),  // #a6da95
                new TextColor.RGB(238, 212, 159),  // #eed49f
                new TextColor.RGB(237, 135, 150),  // #ed8796
                new TextColor.RGB(91, 96, 120),    // #5b6078
                new TextColor.RGB(110, 115, 141)   // #6e738d
        ));

        // 4. Catppuccin Frappé
        register(new ThemePalette("Catppuccin Frappé",
                new TextColor.RGB(48, 52, 70),     // #303446
                new TextColor.RGB(65, 69, 89),     // #414559
                new TextColor.RGB(198, 208, 245),  // #c6d0f5
                new TextColor.RGB(81, 87, 109),    // #51576d
                new TextColor.RGB(202, 158, 230),  // #ca9ee6
                new TextColor.RGB(166, 209, 137),  // #a6d189
                new TextColor.RGB(229, 200, 144),  // #e5c890
                new TextColor.RGB(231, 130, 132),  // #e78284
                new TextColor.RGB(98, 104, 128),   // #626880
                new TextColor.RGB(115, 121, 148)   // #737994
        ));

        // 5. Catppuccin Latte (Light)
        register(new ThemePalette("Catppuccin Latte",
                new TextColor.RGB(239, 241, 245),  // #eff1f5
                new TextColor.RGB(230, 233, 239),  // #e6e9ef
                new TextColor.RGB(76, 79, 105),    // #4c4f69
                new TextColor.RGB(204, 208, 218),  // #ccd0da
                new TextColor.RGB(136, 57, 239),   // #8839ef
                new TextColor.RGB(64, 160, 43),    // #40a02b
                new TextColor.RGB(223, 142, 29),   // #df8e1d
                new TextColor.RGB(210, 15, 57),    // #d20f39
                new TextColor.RGB(188, 192, 204),  // #bcc0cc
                new TextColor.RGB(140, 143, 161)   // #8c8fa1
        ));

        // 6. Gruvbox Dark
        register(new ThemePalette("Gruvbox Dark",
                new TextColor.RGB(40, 40, 40),     // #282828
                new TextColor.RGB(60, 56, 54),     // #3c3836
                new TextColor.RGB(235, 219, 178),  // #ebdbb2
                new TextColor.RGB(80, 73, 69),     // #504945
                new TextColor.RGB(250, 189, 47),   // #fabd2f
                new TextColor.RGB(184, 187, 38),   // #b8bb26
                new TextColor.RGB(254, 128, 25),   // #fe8019
                new TextColor.RGB(251, 73, 52),    // #fb4934
                new TextColor.RGB(102, 92, 84),    // #665c54
                new TextColor.RGB(146, 131, 116)   // #928374
        ));

        // 7. Nord
        register(new ThemePalette("Nord",
                new TextColor.RGB(46, 52, 64),     // #2e3440
                new TextColor.RGB(59, 66, 82),     // #3b4252
                new TextColor.RGB(236, 239, 244),  // #eceff4
                new TextColor.RGB(67, 76, 94),     // #434c5e
                new TextColor.RGB(136, 192, 208),  // #88c0d0
                new TextColor.RGB(163, 190, 140),  // #a3be8c
                new TextColor.RGB(235, 203, 139),  // #ebcb8b
                new TextColor.RGB(191, 97, 106),   // #bf616a
                new TextColor.RGB(76, 86, 106),    // #4c566a
                new TextColor.RGB(123, 136, 161)   // #7b88a1
        ));

        // 8. Kanagawa
        register(new ThemePalette("Kanagawa",
                new TextColor.RGB(31, 31, 40),     // #1f1f28
                new TextColor.RGB(42, 42, 55),     // #2a2a37
                new TextColor.RGB(220, 215, 186),  // #dcd7ba
                new TextColor.RGB(45, 79, 103),    // #2d4f67
                new TextColor.RGB(126, 156, 216),  // #7e9cd8
                new TextColor.RGB(118, 148, 106),  // #76946a
                new TextColor.RGB(192, 163, 110),  // #c0a36e
                new TextColor.RGB(195, 64, 67),    // #c34043
                new TextColor.RGB(84, 84, 109),    // #54546d
                new TextColor.RGB(114, 113, 105)   // #727169
        ));

        // 9. Rose Pine
        register(new ThemePalette("Rose Pine",
                new TextColor.RGB(25, 23, 36),     // #191724
                new TextColor.RGB(38, 35, 58),     // #26233a
                new TextColor.RGB(224, 222, 244),  // #e0def4
                new TextColor.RGB(64, 61, 82),     // #403d52
                new TextColor.RGB(235, 188, 186),  // #ebbcba
                new TextColor.RGB(49, 116, 143),   // #31748f
                new TextColor.RGB(246, 193, 119),  // #f6c177
                new TextColor.RGB(235, 111, 146),  // #eb6f92
                new TextColor.RGB(82, 79, 103),    // #524f67
                new TextColor.RGB(144, 140, 170)   // #908caa
        ));

        // 10. Solarized Osaka
        register(new ThemePalette("Solarized Osaka",
                new TextColor.RGB(0, 43, 54),      // #002b36
                new TextColor.RGB(7, 54, 66),      // #073642
                new TextColor.RGB(131, 148, 150),  // #839496
                new TextColor.RGB(14, 75, 90),     // #0e4b5a
                new TextColor.RGB(42, 161, 152),   // #2aa198
                new TextColor.RGB(133, 153, 0),    // #859900
                new TextColor.RGB(181, 137, 0),    // #b58900
                new TextColor.RGB(220, 50, 47),    // #dc322f
                new TextColor.RGB(88, 110, 117),   // #586e75
                new TextColor.RGB(101, 123, 131)   // #657b83
        ));

        // 11. Cyberdream
        register(new ThemePalette("Cyberdream",
                new TextColor.RGB(22, 24, 26),     // #16181a
                new TextColor.RGB(30, 33, 36),     // #1e2124
                new TextColor.RGB(255, 255, 255),  // #ffffff
                new TextColor.RGB(60, 64, 72),     // #3c4048
                new TextColor.RGB(255, 94, 160),   // #ff5ea0
                new TextColor.RGB(94, 255, 108),   // #5eff6c
                new TextColor.RGB(241, 255, 94),   // #f1ff5e
                new TextColor.RGB(255, 110, 94),   // #ff6e5e
                new TextColor.RGB(60, 64, 72),     // #3c4048
                new TextColor.RGB(123, 132, 150)   // #7b8496
        ));

        // 12. Minecraft Classic
        register(new ThemePalette("Minecraft Classic",
                new TextColor.RGB(27, 18, 12),     // Dirt dark
                new TextColor.RGB(58, 36, 20),     // Dirt lighter
                new TextColor.RGB(224, 224, 224),  // Stone white
                new TextColor.RGB(92, 60, 36),     // Wood selection
                new TextColor.RGB(85, 255, 255),   // Diamond cyan
                new TextColor.RGB(85, 255, 85),    // Emerald green
                new TextColor.RGB(255, 170, 0),    // Gold yellow
                new TextColor.RGB(255, 85, 85),    // Redstone red
                new TextColor.RGB(133, 82, 43),    // Oak border
                new TextColor.RGB(170, 170, 170)   // Stone gray
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
        if (name == null || name.equalsIgnoreCase(RANDOM_THEME) || name.equalsIgnoreCase("Random")) {
            List<ThemePalette> all = new ArrayList<>(PALETTES.values());
            return all.get(new Random().nextInt(all.size()));
        }
        ThemePalette p = PALETTES.get(name);
        return p != null ? p : PALETTES.get("Gruvbox Dark");
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

        // Background transparency mapping
        TextColor baseBg;
        TextColor surfaceBg;
        TextColor buttonBg;

        if (transparency == 0) {
            baseBg = trueColor ? p.bg : TextColor.ANSI.BLACK;
            surfaceBg = trueColor ? p.surface : TextColor.ANSI.BLACK;
            buttonBg = trueColor ? p.surface : TextColor.ANSI.BLACK;
        } else if (transparency <= 25) {
            baseBg = TextColor.ANSI.DEFAULT;
            surfaceBg = trueColor ? p.surface : TextColor.ANSI.BLACK;
            buttonBg = trueColor ? p.surface : TextColor.ANSI.BLACK;
        } else if (transparency <= 50) {
            baseBg = TextColor.ANSI.DEFAULT;
            surfaceBg = TextColor.ANSI.DEFAULT;
            buttonBg = trueColor ? p.surface : TextColor.ANSI.BLACK;
        } else {
            // >= 75% or 100% full transparent
            baseBg = TextColor.ANSI.DEFAULT;
            surfaceBg = TextColor.ANSI.DEFAULT;
            buttonBg = TextColor.ANSI.DEFAULT;
        }

        SimpleTheme theme = SimpleTheme.makeTheme(
                true,
                accent, selection,
                fg, selection,
                fg, baseBg,
                muted
        );

        theme.addOverride(Panel.class, fg, surfaceBg);
        theme.addOverride(Window.class, fg, baseBg);
        theme.addOverride(Border.class, border, baseBg);
        theme.addOverride(Button.class, fg, buttonBg);

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

    public static TextColor getLogBackgroundColor() {
        if (activeTransparency >= 50) {
            return TextColor.ANSI.DEFAULT;
        }
        return getActivePalette().bg;
    }
}
