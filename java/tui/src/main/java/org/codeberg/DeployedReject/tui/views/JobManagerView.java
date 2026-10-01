package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import org.codeberg.DeployedReject.tui.backend.JobTracker;
import org.codeberg.DeployedReject.tui.theme.LazyVimTheme;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.*;

/**
 * Dedicated view to monitor active background operations (server installation, mod downloads)
 * and cancel them with automatic cleanup of temporary/partial files.
 */
public class JobManagerView implements WorkspaceView {

    private final MainWindow mainWindow;
    private final Panel root;
    private final MurcesListBox jobListBox;
    private final Label detailsLabel;
    private final Button cancelBtn;
    private final Button cancelAllBtn;
    private final Button refreshBtn;
    private final Button backBtn;
    private final Map<Character, Runnable> hotkeys = new HashMap<>();

    private final List<JobTracker.TrackedJob> currentJobs = new ArrayList<>();

    public JobManagerView(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.root = new Panel(new LinearLayout(Direction.VERTICAL));

        Label headerLabel = new Label("Active background jobs (Server downloads/installs, Mod downloads).");
        headerLabel.setForegroundColor(LazyVimTheme.getAccentColor());
        root.addComponent(headerLabel);

        Label helpLabel = new Label("Select a job using Arrow Keys and press [C] to abort and delete partial files.");
        helpLabel.setForegroundColor(LazyVimTheme.getLogWarnColor());
        root.addComponent(helpLabel);

        root.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        // Job list
        jobListBox = new MurcesListBox(new TerminalSize(50, 7));
        jobListBox.setSelectionListener(idx -> {
            mainWindow.getGui().getGUIThread().invokeLater(this::updateSelectedDetails);
        });
        root.addComponent(jobListBox.withBorder(Borders.singleLine("Running Tasks [L]ist (↑/↓)")));

        // Details Panel
        detailsLabel = new Label("No active jobs running.");
        detailsLabel.setForegroundColor(MinecraftTheme.STONE_GRAY);
        Panel detailsPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        detailsPanel.addComponent(detailsLabel);
        root.addComponent(detailsPanel.withBorder(Borders.singleLine("Task Details")));

        root.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        // Actions
        Panel actionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        cancelBtn = new Button("[C]ancel Selected Job", this::onCancelSelected);
        cancelAllBtn = new Button("[K]ill All Jobs", this::onCancelAll);
        refreshBtn = new Button("[R]efresh", this::refreshJobs);
        backBtn = new Button("[B]ack to Main Menu", mainWindow::showMainMenu);

        actionPanel.addComponent(cancelBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(cancelAllBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(refreshBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(backBtn);
        root.addComponent(actionPanel);

        // Hotkeys
        hotkeys.put('C', KeyboardNavigationHelper.focus(cancelBtn, this::onCancelSelected));
        hotkeys.put('K', KeyboardNavigationHelper.focus(cancelAllBtn, this::onCancelAll));
        hotkeys.put('R', KeyboardNavigationHelper.focus(refreshBtn, this::refreshJobs));
        hotkeys.put('L', jobListBox::takeFocus);
        hotkeys.put('B', KeyboardNavigationHelper.focus(backBtn, mainWindow::showMainMenu));

        // Listen for live updates
        JobTracker.getInstance().addChangeListener(() -> {
            try {
                if (mainWindow.getGui() != null && mainWindow.getGui().getGUIThread() != null) {
                    mainWindow.getGui().getGUIThread().invokeLater(this::refreshJobs);
                }
            } catch (Exception ignored) {}
        });

        refreshJobs();
    }

    @Override
    public String getTitle() {
        return "Active Tasks & Job Manager";
    }

    @Override
    public Component getComponent() {
        return root;
    }

    @Override
    public Map<Character, Runnable> getHotkeys() {
        return hotkeys;
    }

    @Override
    public Interactable getDefaultFocus() {
        return jobListBox.getItemCount() > 0 ? jobListBox : backBtn;
    }

    @Override
    public void onActivated() {
        refreshJobs();
    }

    @Override
    public void onResized(TerminalSize newSize) {
        if (newSize == null) return;
        int cols = newSize.getColumns();
        int actWidth = Math.max(28, Math.min(65, (cols * 35) / 100));
        int wsWidth = Math.max(44, cols - actWidth - 6);
        jobListBox.setPreferredSize(new TerminalSize(Math.max(28, wsWidth - 6), Math.max(5, Math.min(10, newSize.getRows() - 16))));
    }

    public synchronized void refreshJobs() {
        currentJobs.clear();
        currentJobs.addAll(JobTracker.getInstance().getActiveJobs());

        jobListBox.clearItems();
        if (currentJobs.isEmpty()) {
            jobListBox.addItem("[No active background tasks running]", () -> {});
            detailsLabel.setText("No background jobs currently running.\nStart a server installation or mod download to see it here.");
            detailsLabel.setForegroundColor(MinecraftTheme.STONE_GRAY);
        } else {
            for (JobTracker.TrackedJob job : currentJobs) {
                String line = String.format("[%s] %s - %s",
                        job.getType(),
                        job.getName(),
                        job.getStatus() != null ? job.getStatus() : "Running");
                jobListBox.addItem(line, this::onCancelSelected);
            }
            updateSelectedDetails();
        }
        mainWindow.invalidate();
    }

    private void updateSelectedDetails() {
        int idx = jobListBox.getSelectedIndex();
        if (idx >= 0 && idx < currentJobs.size()) {
            JobTracker.TrackedJob job = currentJobs.get(idx);
            long elapsedSec = (System.currentTimeMillis() - job.getStartTime()) / 1000;
            String text = String.format("Task: %s\nType: %s\nRunning for: %d seconds\nStatus: %s\nProgress: %.1f%%\n\nPress [C] to abort this job and clean up temporary files.",
                    job.getName(),
                    job.getType(),
                    elapsedSec,
                    job.getStatus(),
                    job.getProgress());
            detailsLabel.setText(text);
            detailsLabel.setForegroundColor(LazyVimTheme.getLogWarnColor());
        }
    }

    private void onCancelSelected() {
        int idx = jobListBox.getSelectedIndex();
        if (idx >= 0 && idx < currentJobs.size()) {
            JobTracker.TrackedJob job = currentJobs.get(idx);
            ActivityLogger.warn("User requested cancellation of job: " + job.getName());
            job.cancel();
            refreshJobs();
        } else {
            ActivityLogger.info("No task selected to cancel.");
        }
    }

    private void onCancelAll() {
        if (!JobTracker.getInstance().hasActiveJobs()) {
            ActivityLogger.info("No running tasks to cancel.");
            return;
        }
        ActivityLogger.warn("Cancelling all active background tasks...");
        JobTracker.getInstance().cancelAll();
        refreshJobs();
    }
}
