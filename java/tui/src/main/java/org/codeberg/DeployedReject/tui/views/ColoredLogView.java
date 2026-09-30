package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A custom high-performance Lanterna component that renders color-coded,
 * automatically word-wrapped log streams without horizontal scrollbars.
 */
public class ColoredLogView extends AbstractComponent<ColoredLogView> {

    private static final int MAX_HISTORY = 400;
    private final List<String> rawLines = new ArrayList<>();
    private final boolean isConsoleMode;

    private Runnable onUpdate;

    public ColoredLogView() {
        this(false);
    }

    public ColoredLogView(boolean isConsoleMode) {
        this.isConsoleMode = isConsoleMode;
    }

    public void setOnUpdate(Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }

    public synchronized void addLine(String line) {
        if (line == null || line.trim().isEmpty()) return;
        String[] lines = line.split("\\r?\\n");
        for (String l : lines) {
            String trimmed = l.trim();
            if (!trimmed.isEmpty()) {
                rawLines.add(trimmed);
            }
        }
        while (rawLines.size() > MAX_HISTORY) {
            rawLines.remove(0);
        }
        invalidate();
        if (onUpdate != null) {
            onUpdate.run();
        }
    }

    public synchronized void setContent(String fullText) {
        rawLines.clear();
        if (fullText != null && !fullText.trim().isEmpty()) {
            String[] lines = fullText.split("\\r?\\n");
            for (String l : lines) {
                if (!l.trim().isEmpty()) {
                    rawLines.add(l.trim());
                }
            }
        }
        while (rawLines.size() > MAX_HISTORY) {
            rawLines.remove(0);
        }
        invalidate();
        if (onUpdate != null) {
            onUpdate.run();
        }
    }

    public synchronized void clear() {
        rawLines.clear();
        invalidate();
        if (onUpdate != null) {
            onUpdate.run();
        }
    }

    @Override
    protected ComponentRenderer<ColoredLogView> createDefaultRenderer() {
        return new ComponentRenderer<>() {
            @Override
            public TerminalSize getPreferredSize(ColoredLogView component) {
                return new TerminalSize(30, 6);
            }

            @Override
            public void drawComponent(TextGUIGraphics graphics, ColoredLogView component) {
                TerminalSize size = graphics.getSize();
                int width = Math.max(10, size.getColumns());
                int height = Math.max(1, size.getRows());

                // Clear background with dark Minecraft gray / black
                graphics.setBackgroundColor(MinecraftTheme.DEEP_BLACK);
                graphics.fill(' ');

                // Wrap and colorize lines
                List<LineEntry> wrapped = new ArrayList<>();
                synchronized (component) {
                    for (String raw : component.rawLines) {
                        TextColor color = getColorForLine(raw);
                        List<String> segments = wrapLine(raw, width);
                        for (String seg : segments) {
                            wrapped.add(new LineEntry(seg, color));
                        }
                    }
                }

                // Render latest entries anchored to the bottom
                int start = Math.max(0, wrapped.size() - height);
                for (int i = 0; i < height && (start + i) < wrapped.size(); i++) {
                    LineEntry entry = wrapped.get(start + i);
                    graphics.setForegroundColor(entry.color);
                    graphics.putString(0, i, entry.text);
                }
            }
        };
    }

    private TextColor getColorForLine(String line) {
        if (line == null || line.isEmpty()) return TextColor.ANSI.WHITE;
        String upper = line.toUpperCase();

        if (upper.startsWith("[OK") || upper.startsWith("OK:") || upper.contains("SUCCESS") || upper.contains("DONE (")) {
            return MinecraftTheme.CREEPER_GREEN;
        } else if (upper.startsWith("[ERR") || upper.startsWith("ERR:") || upper.contains("ERROR") || upper.contains("EXCEPTION") || upper.contains("FATAL")) {
            return MinecraftTheme.REDSTONE_RED;
        } else if (upper.startsWith("[WARN") || upper.startsWith("[WRN") || upper.startsWith("WRN:") || upper.contains("WARN")) {
            return MinecraftTheme.GOLD_YELLOW;
        } else if (upper.startsWith("[PROG") || upper.startsWith("[BUSY") || upper.startsWith("[STATUS") || upper.startsWith("[SELECTED")) {
            return MinecraftTheme.DIAMOND_CYAN;
        } else if (upper.contains("[SERVER NOT STARTED") || upper.contains("[SERVER STOPPED")) {
            return MinecraftTheme.STONE_GRAY;
        }
        return TextColor.ANSI.WHITE;
    }

    private static List<String> wrapLine(String text, int width) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            result.add("");
            return result;
        }
        if (text.length() <= width) {
            result.add(text);
            return result;
        }

        String remaining = text;
        boolean first = true;
        while (!remaining.isEmpty()) {
            int maxLen = first ? width : Math.max(5, width - 2);
            if (remaining.length() <= maxLen) {
                result.add(first ? remaining : "  " + remaining);
                break;
            }

            int split = remaining.lastIndexOf(' ', maxLen);
            if (split <= 0) {
                split = maxLen;
            }

            String part = remaining.substring(0, split).trim();
            result.add(first ? part : "  " + part);
            remaining = remaining.substring(split).trim();
            first = false;
        }
        return result;
    }

    private static class LineEntry {
        final String text;
        final TextColor color;
        LineEntry(String text, TextColor color) {
            this.text = text;
            this.color = color;
        }
    }
}
