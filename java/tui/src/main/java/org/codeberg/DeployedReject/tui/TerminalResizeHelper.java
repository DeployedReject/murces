package org.codeberg.DeployedReject.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.screen.Screen;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class TerminalResizeHelper {

    private static final ScheduledExecutorService debounceScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ResizeDebouncer");
        t.setDaemon(true);
        return t;
    });

    private static ScheduledFuture<?> pendingResizeTask = null;
    private static final AtomicReference<TerminalSize> lastKnownSize = new AtomicReference<>(null);
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Directly queries the physical terminal dimensions from the OS kernel (/dev/tty or stty size).
     * This bypasses CPR escape codes and returns instantaneous results.
     */
    public static TerminalSize queryPhysicalTerminalSize() {
        try {
            File tty = new File("/dev/tty");
            ProcessBuilder pb = new ProcessBuilder("stty", "size");
            if (tty.exists() && tty.canRead()) {
                pb.redirectInput(tty);
            }
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String line;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                line = r.readLine();
            }
            p.waitFor(100, TimeUnit.MILLISECONDS);
            if (line != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2) {
                    int rows = Integer.parseInt(parts[0]);
                    int cols = Integer.parseInt(parts[1]);
                    if (cols >= 20 && rows >= 5) {
                        return new TerminalSize(cols, rows);
                    }
                }
            }
        } catch (Exception ignored) {}

        // Fallback to environment variables if available
        try {
            String colsStr = System.getenv("COLUMNS");
            String linesStr = System.getenv("LINES");
            if (colsStr != null && linesStr != null) {
                int c = Integer.parseInt(colsStr.trim());
                int r = Integer.parseInt(linesStr.trim());
                if (c >= 20 && r >= 5) {
                    return new TerminalSize(c, r);
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * Initializes global WINCH signal handler, debounced redraws, and background polling.
     */
    public static synchronized void setup(Screen screen, MultiWindowTextGUI gui, ResponsiveTerminal terminal) {
        if (initialized.getAndSet(true)) {
            return;
        }

        TerminalSize initial = queryPhysicalTerminalSize();
        if (initial != null) {
            lastKnownSize.set(initial);
            terminal.notifyResized(initial);
        } else {
            try {
                lastKnownSize.set(screen.getTerminalSize());
            } catch (Exception ignored) {}
        }

        // 1. Native WINCH signal handler
        try {
            sun.misc.Signal.handle(new sun.misc.Signal("WINCH"), sig -> {
                scheduleResize(screen, gui, terminal, 50);
            });
        } catch (Throwable ignored) {}

        // 2. Background polling watcher (detects resize even if signals are dropped or unsupported)
        Thread watcher = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(250);
                    TerminalSize current = queryPhysicalTerminalSize();
                    if (current != null) {
                        TerminalSize prev = lastKnownSize.get();
                        if (prev == null || !current.equals(prev)) {
                            scheduleResize(screen, gui, terminal, 30);
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {}
            }
        }, "TerminalResizeWatcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private static synchronized void scheduleResize(Screen screen, MultiWindowTextGUI gui, ResponsiveTerminal terminal, long delayMs) {
        if (pendingResizeTask != null && !pendingResizeTask.isDone()) {
            pendingResizeTask.cancel(false);
        }
        pendingResizeTask = debounceScheduler.schedule(() -> {
            performResize(screen, gui, terminal);
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private static void performResize(Screen screen, MultiWindowTextGUI gui, ResponsiveTerminal terminal) {
        try {
            TerminalSize realSize = queryPhysicalTerminalSize();
            if (realSize == null) {
                try {
                    realSize = terminal.getTerminalSize();
                } catch (Exception ignored) {}
            }
            if (realSize == null) {
                return;
            }

            lastKnownSize.set(realSize);
            terminal.notifyResized(realSize);

            // Execute GUI refresh on the GUI thread
            gui.getGUIThread().invokeLater(() -> {
                try {
                    // Let Screen adjust its internal buffer
                    screen.doResizeIfNecessary();

                    // Full clear of screen buffer to eliminate ghost characters and torn borders
                    screen.clear();

                    // Invalidate active windows for the new dimensions
                    for (Window w : gui.getWindows()) {
                        w.invalidate();
                    }

                    // Invalidate background canvas
                    gui.getBackgroundPane().invalidate();

                    // Force redraw to physical terminal
                    gui.updateScreen();
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }
}
