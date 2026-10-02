package org.codeberg.DeployedReject.tui.views;

import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class ActivityLogger {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_BUFFER = 200;
    private static final List<String> buffer = new ArrayList<>();
    private static final CopyOnWriteArrayList<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    static {

        OrchestratorBridge.getInstance().addLogListener(ActivityLogger::log);
    }

    public static synchronized void addListener(Consumer<String> listener) {
        listeners.add(listener);

        for (String msg : buffer) {
            listener.accept(msg);
        }
    }

    public static synchronized void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
    }

    public static synchronized void log(String message) {
        if (message == null || message.trim().isEmpty()) return;
        String line = message.trim();
        if (!buffer.isEmpty() && isMatchingProgress(line, buffer.get(buffer.size() - 1))) {
            buffer.set(buffer.size() - 1, line);
        } else {
            buffer.add(line);
            while (buffer.size() > MAX_BUFFER) {
                buffer.remove(0);
            }
        }
        for (Consumer<String> l : listeners) {
            try {
                l.accept(line);
            } catch (Exception ignored) {}
        }
    }

    public static boolean isMatchingProgress(String line1, String line2) {
        if (line1 == null || line2 == null) return false;
        String u1 = line1.toUpperCase();
        String u2 = line2.toUpperCase();
        if ((u1.startsWith("[PROG") && u2.startsWith("[PROG")) ||
            (u1.startsWith("[STATUS") && u2.startsWith("[STATUS"))) {
            String p1 = u1.replaceAll("[\\d%().\\s]+$", "").trim();
            String p2 = u2.replaceAll("[\\d%().\\s]+$", "").trim();
            return p1.equals(p2);
        }
        return false;
    }

    public static void info(String msg) {
        log("[INFO] " + msg);
    }

    public static void ok(String msg) {
        log("[OK:] " + msg);
    }

    public static void warn(String msg) {
        log("[WARN:] " + msg);
    }

    public static void err(String msg) {
        log("[ERR:] " + msg);
    }

    public static void prog(String msg) {
        log("[PROG] " + msg);
    }
}
