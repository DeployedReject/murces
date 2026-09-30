package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.AbstractInteractableComponent;
import com.googlecode.lanterna.gui2.InteractableRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.input.MouseActionType;
import org.codeberg.DeployedReject.tui.theme.LazyVimTheme;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A custom high-performance Lanterna component that renders color-coded,
 * automatically word-wrapped log streams with interactive mouse wheel / arrow scrolling
 * and visual scrollbar tracking.
 */
public class ColoredLogView extends AbstractInteractableComponent<ColoredLogView> {

    private static final int MAX_HISTORY = 400;
    private final List<String> rawLines = new ArrayList<>();
    private final boolean isConsoleMode;

    private Runnable onUpdate;
    private int scrollOffsetFromBottom = 0;
    private int lastRenderHeight = 10;
    private int lastTotalLines = 0;

    public ColoredLogView() {
        this(false);
    }

    public ColoredLogView(boolean isConsoleMode) {
        this.isConsoleMode = isConsoleMode;
    }

    public void setOnUpdate(Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }

    public synchronized void scrollUp(int lines) {
        if (lines <= 0) return;
        int maxOffset = Math.max(0, lastTotalLines - lastRenderHeight);
        scrollOffsetFromBottom = Math.min(maxOffset, scrollOffsetFromBottom + lines);
        invalidate();
        if (onUpdate != null) {
            onUpdate.run();
        }
    }

    public synchronized void scrollDown(int lines) {
        if (lines <= 0) return;
        scrollOffsetFromBottom = Math.max(0, scrollOffsetFromBottom - lines);
        invalidate();
        if (onUpdate != null) {
            onUpdate.run();
        }
    }

    public synchronized void scrollToTop() {
        int maxOffset = Math.max(0, lastTotalLines - lastRenderHeight);
        scrollOffsetFromBottom = maxOffset;
        invalidate();
        if (onUpdate != null) {
            onUpdate.run();
        }
    }

    public synchronized void scrollToBottom() {
        scrollOffsetFromBottom = 0;
        invalidate();
        if (onUpdate != null) {
            onUpdate.run();
        }
    }

    public synchronized int getScrollOffsetFromBottom() {
        return scrollOffsetFromBottom;
    }

    public synchronized void addLine(String line) {
        if (line == null || line.trim().isEmpty()) return;
        String[] lines = line.split("\\r?\\n");
        for (String l : lines) {
            String trimmed = l.trim();
            if (!trimmed.isEmpty()) {
                if (!rawLines.isEmpty() && ActivityLogger.isMatchingProgress(trimmed, rawLines.get(rawLines.size() - 1))) {
                    rawLines.set(rawLines.size() - 1, trimmed);
                } else {
                    rawLines.add(trimmed);
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

    public synchronized void setContent(String fullText) {
        rawLines.clear();
        if (fullText != null && !fullText.trim().isEmpty()) {
            String[] lines = fullText.split("\\r?\\n");
            for (String l : lines) {
                String trimmed = l.trim();
                if (!trimmed.isEmpty()) {
                    if (!rawLines.isEmpty() && ActivityLogger.isMatchingProgress(trimmed, rawLines.get(rawLines.size() - 1))) {
                        rawLines.set(rawLines.size() - 1, trimmed);
                    } else {
                        rawLines.add(trimmed);
                    }
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
        scrollOffsetFromBottom = 0;
        invalidate();
        if (onUpdate != null) {
            onUpdate.run();
        }
    }

    @Override
    protected InteractableRenderer<ColoredLogView> createDefaultRenderer() {
        return new InteractableRenderer<>() {
            @Override
            public TerminalPosition getCursorLocation(ColoredLogView component) {
                return null;
            }

            @Override
            public TerminalSize getPreferredSize(ColoredLogView component) {
                return new TerminalSize(30, 6);
            }

            @Override
            public void drawComponent(TextGUIGraphics graphics, ColoredLogView component) {
                TerminalSize size = graphics.getSize();
                int width = Math.max(10, size.getColumns());
                int height = Math.max(1, size.getRows());

                component.lastRenderHeight = height;

                // Clear background with theme log background (or transparent DEFAULT)
                graphics.setBackgroundColor(LazyVimTheme.getLogBackgroundColor());
                graphics.fill(' ');

                // Leave 2 characters on right edge for scrollbar track
                int contentWidth = Math.max(6, width - 2);

                // Wrap and colorize lines
                List<LineEntry> wrapped = new ArrayList<>();
                synchronized (component) {
                    for (String raw : component.rawLines) {
                        TextColor color = getColorForLine(raw);
                        List<String> segments = wrapLine(raw, contentWidth);
                        for (String seg : segments) {
                            wrapped.add(new LineEntry(seg, color));
                        }
                    }
                }

                component.lastTotalLines = wrapped.size();

                int maxOffset = Math.max(0, wrapped.size() - height);
                int effectiveOffset = Math.min(maxOffset, component.scrollOffsetFromBottom);
                int start = Math.max(0, wrapped.size() - height - effectiveOffset);

                for (int i = 0; i < height && (start + i) < wrapped.size(); i++) {
                    LineEntry entry = wrapped.get(start + i);
                    graphics.setForegroundColor(entry.color);
                    graphics.putString(0, i, entry.text);
                }

                // Render scrollbar track along column (width - 1) if history exceeds viewport
                if (wrapped.size() > height) {
                    int barCol = width - 1;
                    boolean isFocused = component.isFocused();
                    boolean isScrolledUp = effectiveOffset > 0;

                    // Top indicator / arrow
                    graphics.setForegroundColor(isScrolledUp ? LazyVimTheme.getWarningColor() : LazyVimTheme.getMutedColor());
                    graphics.putString(barCol, 0, "▲");

                    // Bottom indicator / arrow
                    graphics.setForegroundColor(isScrolledUp ? LazyVimTheme.getMutedColor() : LazyVimTheme.getSuccessColor());
                    graphics.putString(barCol, height - 1, "▼");

                    if (height > 2) {
                        int trackLen = height - 2;
                        double ratio = maxOffset > 0 ? ((double) start / maxOffset) : 1.0;
                        int thumbY = 1 + (int) Math.round(ratio * (trackLen - 1));

                        for (int y = 1; y < height - 1; y++) {
                            if (y == thumbY) {
                                graphics.setForegroundColor(isFocused ? LazyVimTheme.getSuccessColor() : (isScrolledUp ? LazyVimTheme.getWarningColor() : LazyVimTheme.getAccentColor()));
                                graphics.putString(barCol, y, "█");
                            } else {
                                graphics.setForegroundColor(LazyVimTheme.getMutedColor());
                                graphics.putString(barCol, y, "░");
                            }
                        }
                    }
                }
            }
        };
    }

    @Override
    protected Result handleKeyStroke(KeyStroke keyStroke) {
        if (keyStroke instanceof MouseAction) {
            MouseAction ma = (MouseAction) keyStroke;
            if (ma.getActionType() == MouseActionType.SCROLL_UP) {
                scrollUp(3);
                return Result.HANDLED;
            } else if (ma.getActionType() == MouseActionType.SCROLL_DOWN) {
                scrollDown(3);
                return Result.HANDLED;
            } else if (ma.getActionType() == MouseActionType.CLICK_DOWN) {
                takeFocus();
                return Result.HANDLED;
            }
        }

        KeyType type = keyStroke.getKeyType();
        if (type == KeyType.ArrowUp) {
            scrollUp(1);
            return Result.HANDLED;
        } else if (type == KeyType.ArrowDown) {
            scrollDown(1);
            return Result.HANDLED;
        } else if (type == KeyType.PageUp) {
            scrollUp(Math.max(1, lastRenderHeight - 2));
            return Result.HANDLED;
        } else if (type == KeyType.PageDown) {
            scrollDown(Math.max(1, lastRenderHeight - 2));
            return Result.HANDLED;
        } else if (type == KeyType.Home) {
            scrollToTop();
            return Result.HANDLED;
        } else if (type == KeyType.End) {
            scrollToBottom();
            return Result.HANDLED;
        } else if (type == KeyType.Tab) {
            return Result.MOVE_FOCUS_NEXT;
        } else if (type == KeyType.ReverseTab) {
            return Result.MOVE_FOCUS_PREVIOUS;
        }

        return Result.UNHANDLED;
    }

    private TextColor getColorForLine(String line) {
        if (line == null || line.isEmpty()) return LazyVimTheme.getActivePalette().fg;
        String upper = line.toUpperCase();

        if (upper.startsWith("[OK") || upper.startsWith("OK:") || upper.contains("SUCCESS") || upper.contains("DONE (")) {
            return LazyVimTheme.getSuccessColor();
        } else if (upper.startsWith("[ERR") || upper.startsWith("ERR:") || upper.contains("ERROR") || upper.contains("EXCEPTION") || upper.contains("FATAL")) {
            return LazyVimTheme.getErrorColor();
        } else if (upper.startsWith("[WARN") || upper.startsWith("[WRN") || upper.startsWith("WRN:") || upper.contains("WARN")) {
            return LazyVimTheme.getWarningColor();
        } else if (upper.startsWith("[PROG") || upper.startsWith("[BUSY") || upper.startsWith("[STATUS") || upper.startsWith("[SELECTED")) {
            return LazyVimTheme.getAccentColor();
        } else if (upper.contains("[SERVER NOT STARTED") || upper.contains("[SERVER STOPPED")) {
            return LazyVimTheme.getMutedColor();
        }
        return LazyVimTheme.getActivePalette().fg;
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
