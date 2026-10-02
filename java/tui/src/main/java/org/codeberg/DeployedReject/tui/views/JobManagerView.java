package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import org.codeberg.DeployedReject.tui.backend.JobTracker;
import org.codeberg.DeployedReject.tui.theme.GlyphHelper;
import org.codeberg.DeployedReject.tui.theme.Themes;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.*;

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

        Label headerLabel = new Label(GlyphHelper.apply(GlyphHelper.ICON_TASKS + " Active background jobs (Server downloads/installs, Mod downloads)."));
        headerLabel.setForegroundColor(Themes.getAccentColor());
        root.addComponent(headerLabel);

        Label helpLabel = new Label(GlyphHelper.apply(GlyphHelper.ICON_INFO + " Select a job using Arrow Keys and press [C] to abort and delete partial files."));
        helpLabel.setForegroundColor(Themes.getLogWarnColor());
        root.addComponent(helpLabel);

        root.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        jobListBox = new MurcesListBox(new TerminalSize(50, 7));
        jobListBox.setSelectionListener(idx -> {
            mainWindow.getGui().getGUIThread().invokeLater(this::updateSelectedDetails);
        });
        root.addComponent(jobListBox.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_TASKS + " Running Tasks [L]ist (↑/↓)"))));

        detailsLabel = new Label("No active jobs running.");
        detailsLabel.setForegroundColor(MinecraftTheme.STONE_GRAY);
        Panel detailsPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        detailsPanel.addComponent(detailsLabel);
        root.addComponent(detailsPanel.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Task Details"))));

        root.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        Panel actionPanel = new Panel(new LinearLayout(Direction.VERTICAL));

        Panel row1 = new Panel(new LinearLayout(Direction.HORIZONTAL));
        cancelBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [C]ancel Selected Job"), this::onCancelSelected);
        cancelAllBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [K]ill All Jobs"), this::onCancelAll);
        row1.addComponent(cancelBtn);
        row1.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        row1.addComponent(cancelAllBtn);

        Panel row2 = new Panel(new LinearLayout(Direction.HORIZONTAL));
        refreshBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_RESTART + " [R]efresh"), this::refreshJobs);
        backBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_BACK + " [B]ack to Main Menu"), mainWindow::showMainMenu);
        row2.addComponent(refreshBtn);
        row2.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        row2.addComponent(backBtn);

        actionPanel.addComponent(row1);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(row2);
        root.addComponent(actionPanel);

        hotkeys.put('C', KeyboardNavigationHelper.focus(cancelBtn, this::onCancelSelected));
        hotkeys.put('K', KeyboardNavigationHelper.focus(cancelAllBtn, this::onCancelAll));
        hotkeys.put('R', KeyboardNavigationHelper.focus(refreshBtn, this::refreshJobs));
        hotkeys.put('L', jobListBox::takeFocus);
        hotkeys.put('B', KeyboardNavigationHelper.focus(backBtn, mainWindow::showMainMenu));

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
        return GlyphHelper.apply(GlyphHelper.ICON_TASKS + " Active Tasks & Job Manager");
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
        int rows = newSize.getRows();
        int actWidth = Math.max(28, Math.min(65, (cols * 35) / 100));
        int wsWidth = Math.max(44, cols - actWidth - 6);
        jobListBox.setPreferredSize(new TerminalSize(Math.max(38, wsWidth - 4), Math.max(6, rows - 16)));
    }

    public synchronized void refreshJobs() {
        currentJobs.clear();
        currentJobs.addAll(JobTracker.getInstance().getActiveJobs());

        jobListBox.clearItems();
        if (currentJobs.isEmpty()) {
            jobListBox.addItem(GlyphHelper.apply(GlyphHelper.ICON_TASKS + " [No active background tasks running]"), () -> {});
            detailsLabel.setText("No background jobs currently running.\nStart a server installation or mod download to see it here.");
            detailsLabel.setForegroundColor(MinecraftTheme.STONE_GRAY);
        } else {
            for (JobTracker.TrackedJob job : currentJobs) {
                String line = String.format("%s [%s] %s │ %s",
                        GlyphHelper.ICON_BUSY,
                        job.getType(),
                        job.getName(),
                        job.getStatus() != null ? job.getStatus() : "Running");
                jobListBox.addItem(GlyphHelper.apply(line), this::onCancelSelected);
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
            detailsLabel.setForegroundColor(Themes.getLogWarnColor());
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
