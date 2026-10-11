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

    public static TerminalSize queryPhysicalTerminalSize() {
        if (!org.codeberg.DeployedReject.utils.Platform.isWindows()) {
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
        } else {
            TerminalSize winSize = queryWindowsTerminalSize();
            if (winSize != null) {
                return winSize;
            }
        }

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

    private static volatile long lastWindowsQueryTime = 0;
    private static volatile TerminalSize cachedWindowsSize = null;

    private static TerminalSize queryWindowsTerminalSize() {
        long now = System.currentTimeMillis();
        if (cachedWindowsSize != null && (now - lastWindowsQueryTime) < 1500) {
            return cachedWindowsSize;
        }
        lastWindowsQueryTime = now;
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "mode con");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                output = sb.toString();
            }
            p.waitFor(150, TimeUnit.MILLISECONDS);

            int cols = -1;
            int rows = -1;
            java.util.regex.Matcher mCols = java.util.regex.Pattern.compile("Columns:\\s*(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(output);
            if (mCols.find()) {
                cols = Integer.parseInt(mCols.group(1));
            }
            java.util.regex.Matcher mLines = java.util.regex.Pattern.compile("Lines:\\s*(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(output);
            if (mLines.find()) {
                rows = Integer.parseInt(mLines.group(1));
            }

            if (cols >= 20 && rows >= 5) {
                cachedWindowsSize = new TerminalSize(cols, rows);
                return cachedWindowsSize;
            }
        } catch (Exception ignored) {}
        return cachedWindowsSize;
    }

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

        if (!org.codeberg.DeployedReject.utils.Platform.isWindows()) {
            try {
                Class<?> signalClass = Class.forName("sun.misc.Signal");
                Class<?> handlerClass = Class.forName("sun.misc.SignalHandler");
                Object winchSignal = signalClass.getConstructor(String.class).newInstance("WINCH");
                Object handlerProxy = java.lang.reflect.Proxy.newProxyInstance(
                    handlerClass.getClassLoader(),
                    new Class<?>[]{handlerClass},
                    (proxy, method, mArgs) -> {
                        if ("handle".equals(method.getName())) {
                            scheduleResize(screen, gui, terminal, 50);
                        }
                        return null;
                    }
                );
                signalClass.getMethod("handle", signalClass, handlerClass).invoke(null, winchSignal, handlerProxy);
            } catch (Throwable ignored) {}
        }

        Thread watcher = new Thread(() -> {
            long pollInterval = org.codeberg.DeployedReject.utils.Platform.isWindows() ? 1000 : 500;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(pollInterval);
                    TerminalSize current = queryPhysicalTerminalSize();
                    if (current != null) {
                        TerminalSize prev = lastKnownSize.get();
                        if (prev == null || !current.equals(prev)) {
                            scheduleResize(screen, gui, terminal, 50);
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

            final TerminalSize effectiveSize = realSize;

            gui.getGUIThread().invokeLater(() -> {
                try {

                    screen.doResizeIfNecessary();

                    for (Window w : gui.getWindows()) {
                        if (w instanceof org.codeberg.DeployedReject.tui.views.MainWindow) {
                            ((org.codeberg.DeployedReject.tui.views.MainWindow) w).updateLayoutDimensions(effectiveSize);
                        }
                        w.invalidate();
                    }

                    gui.getBackgroundPane().invalidate();

                    gui.updateScreen();
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }
}
