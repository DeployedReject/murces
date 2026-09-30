package org.codeberg.DeployedReject.tui.theme;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.PropertyTheme;

import java.io.InputStream;
import java.util.Properties;

public class MinecraftTheme extends PropertyTheme {

    public static final TextColor DIAMOND_CYAN  = TextColor.ANSI.CYAN;
    public static final TextColor GOLD_YELLOW   = TextColor.ANSI.YELLOW;
    public static final TextColor CREEPER_GREEN = TextColor.ANSI.GREEN;
    public static final TextColor REDSTONE_RED  = TextColor.ANSI.RED;
    public static final TextColor LAPIS_BLUE    = TextColor.ANSI.BLUE;
    public static final TextColor STONE_WHITE   = TextColor.ANSI.WHITE;
    public static final TextColor STONE_GRAY    = TextColor.ANSI.WHITE;
    public static final TextColor DEEP_BLACK    = TextColor.ANSI.BLACK;
    public static final TextColor DARK_PANEL    = TextColor.ANSI.BLACK;

    public MinecraftTheme() {
        super(loadProperties(), true);
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream is = MinecraftTheme.class.getResourceAsStream("/minecraft-theme.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (Exception ignored) {}
        return props;
    }
}
