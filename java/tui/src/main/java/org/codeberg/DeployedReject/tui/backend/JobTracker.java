package org.codeberg.DeployedReject.tui.backend;

import org.codeberg.DeployedReject.tui.views.ActivityLogger;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central registry to track, observe, and cancel long-running background jobs
 * such as server installations and mod downloads. Automatically manages
 * cleanup of temporary and partial files upon cancellation or failure.
 */
public class JobTracker {

    public static class TrackedJob {
        private final String id;
        private final String name;
        private final String type; // "Server", "Mod", etc.
        private final long startTime;
        private volatile String status;
        private volatile double progress; // 0.0 to 100.0, or negative for indeterminate
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final Set<File> filesToCleanup = new CopyOnWriteArraySet<>();
        private final Runnable cancelAction;

        public TrackedJob(String id, String name, String type, Runnable cancelAction) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.cancelAction = cancelAction;
            this.startTime = System.currentTimeMillis();
            this.status = "Started";
            this.progress = 0.0;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getType() { return type; }
        public long getStartTime() { return startTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public double getProgress() { return progress; }
        public void setProgress(double progress) { this.progress = progress; }
        public boolean isCancelled() { return cancelled.get(); }

        public void addFileToCleanup(File file) {
            if (file != null) {
                filesToCleanup.add(file);
            }
        }

        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                this.status = "Cancelled";
                if (cancelAction != null) {
                    try {
                        cancelAction.run();
                    } catch (Exception ignored) {}
                }
                // Cleanup all registered temporary files
                for (File file : filesToCleanup) {
                    try {
                        if (file != null && file.exists()) {
                            file.delete();
                        }
                    } catch (Exception ignored) {}
                }
                ActivityLogger.ok("[CANCEL] Aborted '" + name + "' and cleaned up temporary files.");
            }
        }
    }

    private static final JobTracker INSTANCE = new JobTracker();
    private final Map<String, TrackedJob> jobs = new ConcurrentHashMap<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private JobTracker() {}

    public static JobTracker getInstance() {
        return INSTANCE;
    }

    public TrackedJob registerJob(String id, String name, String type, Runnable cancelAction) {
        TrackedJob job = new TrackedJob(id, name, type, cancelAction);
        jobs.put(id, job);
        notifyListeners();
        return job;
    }

    public void unregisterJob(String id) {
        if (id != null) {
            jobs.remove(id);
            notifyListeners();
        }
    }

    public TrackedJob getJob(String id) {
        return id != null ? jobs.get(id) : null;
    }

    public List<TrackedJob> getActiveJobs() {
        return new ArrayList<>(jobs.values());
    }

    public boolean hasActiveJobs() {
        return !jobs.isEmpty();
    }

    public boolean hasActiveServerJob() {
        for (TrackedJob job : jobs.values()) {
            if ("Server".equalsIgnoreCase(job.getType()) && !job.isCancelled()) {
                return true;
            }
        }
        return false;
    }

    public void cancelJob(String id) {
        TrackedJob job = jobs.remove(id);
        if (job != null) {
            job.cancel();
            notifyListeners();
        }
    }

    public void cancelAll() {
        List<TrackedJob> current = new ArrayList<>(jobs.values());
        jobs.clear();
        for (TrackedJob job : current) {
            job.cancel();
        }
        notifyListeners();
    }

    public void addChangeListener(Runnable listener) {
        if (listener != null && !changeListeners.contains(listener)) {
            changeListeners.add(listener);
        }
    }

    public void removeChangeListener(Runnable listener) {
        if (listener != null) {
            changeListeners.remove(listener);
        }
    }

    public void notifyListeners() {
        for (Runnable l : changeListeners) {
            try {
                l.run();
            } catch (Exception ignored) {}
        }
    }
}
