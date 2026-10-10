package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.input.KeyType;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.config.ConfigManager;
import org.codeberg.DeployedReject.tui.theme.GlyphHelper;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;
import org.codeberg.DeployedReject.tui.theme.Themes;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PluginBrowseView implements WorkspaceView {

  private final MainWindow mainWindow;
  private final Panel root;

  // Tabs: 0 = Browse & Download Plugins, 1 = Installed Plugins Manager
  private int currentTab = 0;
  private final Button tabBrowseBtn;
  private final Button tabManageBtn;
  private final Panel tabHeaderPanel;
  private final Panel tabContentPanel;

  // --- Tab 0: Browse & Download UI ---
  private final ComboBox<String> platformBox;
  private final ComboBox<String> serverPlatformBox;
  private final ComboBox<String> versionComboBox;
  private final TextBox searchBox;
  private final MurcesListBox resultsList;
  private final ComboBox<String> pluginVersionCombo;
  private final Label versionDetailLabel;
  private final ComboBox<String> descModeCombo;
  private final Label titleAuthorLabel;
  private final Label descContentLabel;
  private final Label statusLabel;
  private final MinecraftPickaxeAnimation pickaxeAnim;
  private final Panel detailsCard;
  private final Button searchBtn;
  private final Button downloadBtn;
  private final Button cancelBtn;
  private final Button backBtn;
  private final Button prevPageBtn;
  private final Button nextPageBtn;
  private final Label pageIndicatorLabel;
  private final Panel paginationPanel;
  private final Panel browsePanel;

  // --- Tab 1: Installed Plugins Manager UI ---
  private final Panel managePanel;
  private final MurcesListBox installedPluginsList;
  private final Label installedDetailLabel;
  private final Button deletePluginBtn;
  private final Button refreshPluginsBtn;
  private final List<OrchestratorBridge.PluginFileInfo> currentInstalledPlugins = new ArrayList<>();

  private final List<OrchestratorBridge.PluginResult> currentResults = new ArrayList<>();
  private final List<OrchestratorBridge.PluginVersionInfo> currentPluginVersions = new ArrayList<>();
  private final Map<Character, Runnable> hotkeys = new HashMap<>();
  private final Map<String, String> fullDescCache = new HashMap<>();
  private final Set<String> pendingFetches = Collections.synchronizedSet(new HashSet<>());

  private OrchestratorBridge.PluginResult selectedPlugin = null;
  private boolean isDownloading = false;
  private int descPageIndex = 0;
  private int currentTermWidth = 80;
  private int currentTermHeight = 24;
  private int descCardWidth = 46;
  private int descLinesPerPage = 8;
  private ScheduledExecutorService activeTicker = null;

  public PluginBrowseView(MainWindow mainWindow) {
    this.mainWindow = mainWindow;
    this.root = new Panel(new LinearLayout(Direction.VERTICAL));

    // Tab Header
    tabHeaderPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    tabBrowseBtn = new Button("[1] Browse & Download", () -> setTab(0));
    tabManageBtn = new Button("[2] Installed Plugins", () -> setTab(1));
    tabHeaderPanel.addComponent(tabBrowseBtn);
    tabHeaderPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    tabHeaderPanel.addComponent(tabManageBtn);
    root.addComponent(tabHeaderPanel);
    root.addComponent(new EmptySpace(new TerminalSize(1, 1)));

    tabContentPanel = new Panel(new LinearLayout(Direction.VERTICAL));
    root.addComponent(tabContentPanel);

    // ==========================================
    // TAB 0: Browse & Download UI Setup
    // ==========================================
    browsePanel = new Panel(new LinearLayout(Direction.VERTICAL));

    platformBox = new ComboBox<>("Modrinth", "CurseForge");
    serverPlatformBox = new ComboBox<>("paper", "spigot", "purpur", "folia", "velocity", "bungeecord", "all");
    versionComboBox = MinecraftVersionHelper.createVersionComboBox(mainWindow.getGui(), new TerminalSize(10, 1));
    searchBox = new TextBox(new TerminalSize(16, 1), "essentials");
    searchBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_SEARCH + " [S]earch"), this::onSearch);
    downloadBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_DOWNLOAD + " [D]ownload Plugin"), this::onDownload);
    resultsList = new MurcesListBox(new TerminalSize(38, 10));

    Panel filterPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    filterPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_SEARCH + " [P]lat:")));
    filterPanel.addComponent(platformBox);
    filterPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    filterPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_OPTIONS + " T[y]pe:")));
    filterPanel.addComponent(serverPlatformBox);
    filterPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    filterPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_PACKAGE + " [V]er ([C]ustom):")));
    filterPanel.addComponent(versionComboBox);
    browsePanel.addComponent(filterPanel);

    Panel searchBarPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    searchBarPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_SEARCH + " [Q] Query:")));
    searchBarPanel.addComponent(searchBox);
    searchBarPanel.addComponent(searchBtn);
    browsePanel.addComponent(searchBarPanel);

    searchBox.setInputFilter((interactable, keyStroke) -> {
      if (keyStroke.getKeyType() == KeyType.Enter) {
        onSearch();
        resultsList.takeFocus();
        return false;
      }
      if (keyStroke.getKeyType() == KeyType.Escape || keyStroke.getKeyType() == KeyType.ArrowDown) {
        resultsList.takeFocus();
        return false;
      }
      return true;
    });

    Panel midCols = new Panel(new LinearLayout(Direction.HORIZONTAL));
    Panel leftCol = new Panel(new LinearLayout(Direction.VERTICAL));
    leftCol.addComponent(resultsList.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_MOD + " Plugin Results"))));

    Panel rightCol = new Panel(new LinearLayout(Direction.VERTICAL));
    detailsCard = new Panel(new LinearLayout(Direction.VERTICAL));

    titleAuthorLabel = new Label("Select a plugin from the list to view details.");
    titleAuthorLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
    detailsCard.addComponent(titleAuthorLabel);

    Panel descTogglePanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    descTogglePanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Desc [M]ode:")));
    descModeCombo = new ComboBox<>("Summary", "Full Details");
    descModeCombo.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
      if (changedByUserInteraction) {
        descPageIndex = 0;
        if (selectedIndex == 1 && selectedPlugin != null) {
          fetchFullDescriptionIfNeeded(selectedPlugin);
        }
        updateDetailsDisplay();
      }
    });
    descTogglePanel.addComponent(descModeCombo);
    detailsCard.addComponent(descTogglePanel);

    descContentLabel = new Label("No plugin selected.");
    detailsCard.addComponent(descContentLabel);

    paginationPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    prevPageBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_BACK + " Prev [[]"), this::onPrevPage);
    nextPageBtn = new Button(GlyphHelper.apply("Next []] " + GlyphHelper.ICON_PLAY), this::onNextPage);
    pageIndicatorLabel = new Label("Page 1/1");
    paginationPanel.addComponent(prevPageBtn);
    paginationPanel.addComponent(pageIndicatorLabel);
    paginationPanel.addComponent(nextPageBtn);
    detailsCard.addComponent(paginationPanel);

    Panel versionSelectPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    versionSelectPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_OPTIONS + " [K] Version:")));
    pluginVersionCombo = new ComboBox<>("[Latest Compatible]");
    pluginVersionCombo.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
      if (changedByUserInteraction) {
        updateSelectedVersionDisplay();
      }
    });
    versionSelectPanel.addComponent(pluginVersionCombo);
    detailsCard.addComponent(versionSelectPanel);

    versionDetailLabel = new Label("");
    versionDetailLabel.setForegroundColor(Themes.getActivePalette().accent);
    detailsCard.addComponent(versionDetailLabel);

    rightCol.addComponent(detailsCard.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Plugin Information"))));

    midCols.addComponent(leftCol);
    midCols.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    midCols.addComponent(rightCol);
    browsePanel.addComponent(midCols);

    statusLabel = new Label("Ready to search plugins.");
    statusLabel.setForegroundColor(Themes.getLogSuccessColor());
    browsePanel.addComponent(statusLabel);

    pickaxeAnim = new MinecraftPickaxeAnimation();
    browsePanel.addComponent(pickaxeAnim);

    Panel actionRow = new Panel(new LinearLayout(Direction.HORIZONTAL));
    actionRow.addComponent(downloadBtn);
    cancelBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [X] Cancel Download"), this::cancelDownload);
    actionRow.addComponent(cancelBtn);
    backBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_BACK + " [B]ack to Menu"), mainWindow::showMainMenu);
    actionRow.addComponent(backBtn);
    browsePanel.addComponent(actionRow);

    // ==========================================
    // TAB 1: Installed Plugins Manager UI Setup
    // ==========================================
    managePanel = new Panel(new LinearLayout(Direction.VERTICAL));
    installedPluginsList = new MurcesListBox(new TerminalSize(42, 12));
    installedDetailLabel = new Label("Select an installed plugin to inspect.");

    Panel manageCols = new Panel(new LinearLayout(Direction.HORIZONTAL));
    manageCols.addComponent(installedPluginsList.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_PACKAGE + " Installed Plugins (plugins/)"))));
    manageCols.addComponent(new EmptySpace(new TerminalSize(1, 1)));

    Panel manageDetailsCard = new Panel(new LinearLayout(Direction.VERTICAL));
    manageDetailsCard.addComponent(installedDetailLabel);
    manageCols.addComponent(manageDetailsCard.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Plugin Details"))));
    managePanel.addComponent(manageCols);

    Panel manageActions = new Panel(new LinearLayout(Direction.HORIZONTAL));
    deletePluginBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [D]elete Selected Plugin"), this::onDeletePlugin);
    refreshPluginsBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_PLAY + " [R]efresh Plugins"), this::refreshInstalledPlugins);
    manageActions.addComponent(deletePluginBtn);
    manageActions.addComponent(refreshPluginsBtn);
    manageActions.addComponent(new Button(GlyphHelper.apply(GlyphHelper.ICON_BACK + " [B]ack to Menu"), mainWindow::showMainMenu));
    managePanel.addComponent(manageActions);

    // Show initial tab
    setTab(0);

    // Keybindings
    hotkeys.put('1', () -> setTab(0));
    hotkeys.put('2', () -> setTab(1));
    hotkeys.put('T', () -> setTab((currentTab + 1) % 2));
    hotkeys.put('P', this::cyclePlatform);
    hotkeys.put('Y', this::cycleServerPlatform);
    hotkeys.put('V', () -> MinecraftVersionHelper.cycleVersion(versionComboBox));
    hotkeys.put('C', () -> MinecraftVersionHelper.promptCustomVersion(mainWindow.getGui(), versionComboBox, versionComboBox.getSelectedIndex()));
    hotkeys.put('Q', searchBox::takeFocus);
    hotkeys.put('S', () -> { if (currentTab == 0) onSearch(); });
    hotkeys.put('D', () -> {
      if (currentTab == 0) onDownload();
      else onDeletePlugin();
    });
    hotkeys.put('K', this::cyclePluginVersion);
    hotkeys.put('M', this::cycleDescMode);
    hotkeys.put('[', this::onPrevPage);
    hotkeys.put(']', this::onNextPage);
    hotkeys.put('R', () -> { if (currentTab == 1) refreshInstalledPlugins(); });
    hotkeys.put('B', mainWindow::showMainMenu);
    hotkeys.put('X', () -> { if (currentTab == 0) cancelDownload(); });
  }

  private void cyclePlatform() {
    int count = platformBox.getItemCount();
    if (count > 0) {
      int next = (platformBox.getSelectedIndex() + 1) % count;
      platformBox.setSelectedIndex(next);
      statusLabel.setText("Platform changed to: " + platformBox.getItem(next) + ". Press [S] to search.");
      statusLabel.setForegroundColor(Themes.getLogWarnColor());
      mainWindow.invalidate();
    }
  }

  private void cycleServerPlatform() {
    int count = serverPlatformBox.getItemCount();
    if (count > 0) {
      int next = (serverPlatformBox.getSelectedIndex() + 1) % count;
      serverPlatformBox.setSelectedIndex(next);
      statusLabel.setText("Server engine filter changed to: " + serverPlatformBox.getItem(next) + ". Press [S] to search.");
      statusLabel.setForegroundColor(Themes.getLogWarnColor());
      mainWindow.invalidate();
    }
  }

  private void cyclePluginVersion() {
    int count = pluginVersionCombo.getItemCount();
    if (count > 0) {
      int next = (pluginVersionCombo.getSelectedIndex() + 1) % count;
      pluginVersionCombo.setSelectedIndex(next);
      updateSelectedVersionDisplay();
      mainWindow.invalidate();
    }
  }

  private void cycleDescMode() {
    int count = descModeCombo.getItemCount();
    if (count > 0) {
      int next = (descModeCombo.getSelectedIndex() + 1) % count;
      descModeCombo.setSelectedIndex(next);
      descPageIndex = 0;
      if (next == 1 && selectedPlugin != null) {
        fetchFullDescriptionIfNeeded(selectedPlugin);
      }
      updateDetailsDisplay();
      mainWindow.invalidate();
    }
  }

  public void setTab(int tabIndex) {
    this.currentTab = tabIndex;
    tabContentPanel.removeAllComponents();
    if (tabIndex == 0) {
      tabBrowseBtn.setEnabled(false);
      tabManageBtn.setEnabled(true);
      tabContentPanel.addComponent(browsePanel);
    } else {
      tabBrowseBtn.setEnabled(true);
      tabManageBtn.setEnabled(false);
      tabContentPanel.addComponent(managePanel);
      refreshInstalledPlugins();
    }
    mainWindow.invalidate();
  }

  private void refreshInstalledPlugins() {
    installedPluginsList.clearItems();
    currentInstalledPlugins.clear();
    List<OrchestratorBridge.PluginFileInfo> plugins = OrchestratorBridge.listInstalledPlugins();
    currentInstalledPlugins.addAll(plugins);
    if (plugins.isEmpty()) {
      installedDetailLabel.setText("No plugins found in plugins/ directory.\nUse [1] Browse & Download to install plugins.");
    } else {
      for (OrchestratorBridge.PluginFileInfo p : plugins) {
        installedPluginsList.addItem(GlyphHelper.apply(GlyphHelper.ICON_MOD + " " + p.getDisplayName()), () -> {
          onInstalledPluginSelected(p);
        });
      }
      installedPluginsList.setSelectedIndex(0);
      onInstalledPluginSelected(plugins.get(0));
    }
    mainWindow.invalidate();
  }

  private void onInstalledPluginSelected(OrchestratorBridge.PluginFileInfo p) {
    if (p == null) {
      installedDetailLabel.setText("No plugin selected.");
      return;
    }
    StringBuilder sb = new StringBuilder();
    sb.append("Plugin Name: ").append(p.name).append("\n");
    if (!p.version.isEmpty()) sb.append("Version: ").append(p.version).append("\n");
    if (!p.author.isEmpty()) sb.append("Author: ").append(p.author).append("\n");
    sb.append("File: ").append(p.filename).append(" (").append(formatSize(p.sizeBytes)).append(")\n");
    if (!p.description.isEmpty()) sb.append("\nDescription:\n").append(p.description);
    installedDetailLabel.setText(sb.toString());
    mainWindow.invalidate();
  }

  private void onDeletePlugin() {
    int sel = installedPluginsList.getSelectedIndex();
    if (sel >= 0 && sel < currentInstalledPlugins.size()) {
      OrchestratorBridge.PluginFileInfo p = currentInstalledPlugins.get(sel);
      boolean ok = OrchestratorBridge.deletePlugin(p.filename);
      if (ok) {
        ActivityLogger.ok("Deleted plugin: " + p.filename);
        refreshInstalledPlugins();
      } else {
        ActivityLogger.err("Failed to delete plugin: " + p.filename);
      }
    }
  }

  private void onSearch() {
    String query = searchBox.getText().trim();
    String platform = platformBox.getSelectedItem() != null ? platformBox.getSelectedItem().toLowerCase() : "modrinth";
    String version = MinecraftVersionHelper.getSelectedVersion(versionComboBox);
    String srvPlat = serverPlatformBox.getSelectedItem();

    statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_BUSY + " Searching " + platform + " plugins for '" + query + "'..."));
    statusLabel.setForegroundColor(Themes.getLogWarnColor());
    ActivityLogger.info("Searching plugins: '" + query + "' (" + version + " / " + srvPlat + ")");

    new Thread(() -> {
      try {
        List<OrchestratorBridge.PluginResult> plugins = OrchestratorBridge.getInstance()
            .searchPlugins(platform, query, version, srvPlat).get();
        mainWindow.getGui().getGUIThread().invokeLater(() -> {
          currentResults.clear();
          currentResults.addAll(plugins);
          resultsList.clearItems();
          selectedPlugin = null;

          if (plugins.isEmpty()) {
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_FILE + " No plugins found matching: \"" + query + "\""));
            statusLabel.setForegroundColor(Themes.getLogMutedColor());
          } else {
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " Found " + plugins.size() + " plugins."));
            statusLabel.setForegroundColor(Themes.getLogSuccessColor());
            for (OrchestratorBridge.PluginResult p : plugins) {
              resultsList.addItem(GlyphHelper.apply(GlyphHelper.ICON_MOD + " " + p.name + (p.author.isEmpty() ? "" : " • " + p.author)), () -> {
                onPluginSelected(p);
              });
            }
            resultsList.setSelectedIndex(0);
            onPluginSelected(plugins.get(0));
          }
          mainWindow.invalidate();
        });
      } catch (Exception e) {
        mainWindow.getGui().getGUIThread().invokeLater(() -> {
          statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " Search failed: " + e.getMessage()));
          statusLabel.setForegroundColor(Themes.getLogErrorColor());
          ActivityLogger.err("Plugin search failed: " + e.getMessage());
        });
      }
    }).start();
  }

  private void onPluginSelected(OrchestratorBridge.PluginResult plugin) {
    if (plugin == null) {
      selectedPlugin = null;
      updateDetailsDisplay();
      return;
    }
    this.selectedPlugin = plugin;
    this.descPageIndex = 0;
    updateDetailsDisplay();

    String platform = platformBox.getSelectedItem() != null ? platformBox.getSelectedItem().toLowerCase() : "modrinth";
    String version = MinecraftVersionHelper.getSelectedVersion(versionComboBox);
    String srvPlat = serverPlatformBox.getSelectedItem();

    pluginVersionCombo.clearItems();
    pluginVersionCombo.addItem("[Latest Compatible]");
    pluginVersionCombo.setSelectedIndex(0);
    currentPluginVersions.clear();

    new Thread(() -> {
      try {
        List<OrchestratorBridge.PluginVersionInfo> versions = OrchestratorBridge.getInstance()
            .getPluginVersions(platform, plugin.id, version, srvPlat).get();
        mainWindow.getGui().getGUIThread().invokeLater(() -> {
          if (selectedPlugin != null && plugin.id.equals(selectedPlugin.id)) {
            currentPluginVersions.clear();
            currentPluginVersions.addAll(versions);
            for (OrchestratorBridge.PluginVersionInfo v : versions) {
              pluginVersionCombo.addItem(v.toString());
            }
            updateSelectedVersionDisplay();
            mainWindow.invalidate();
          }
        });
      } catch (Exception ignored) {}
    }).start();
  }

  private void onDownload() {
    if (isDownloading) {
      statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " Another download is already running."));
      return;
    }
    if (selectedPlugin == null && resultsList.getSelectedIndex() >= 0 && resultsList.getSelectedIndex() < currentResults.size()) {
      selectedPlugin = currentResults.get(resultsList.getSelectedIndex());
    }
    if (selectedPlugin == null) {
      statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " Select a plugin from the list first!"));
      return;
    }

    String srvPlat = serverPlatformBox.getSelectedItem();
    String version = MinecraftVersionHelper.getSelectedVersion(versionComboBox);

    // Compatibility check
    var compat = OrchestratorBridge.checkPluginCompatibility(srvPlat, version);
    if (!compat.isCompatible()) {
      statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [ERR] " + compat.getMessage()));
      statusLabel.setForegroundColor(Themes.getLogErrorColor());
      ActivityLogger.err(compat.getMessage());
      MessageDialog.showMessageDialog(mainWindow.getGui(), "Incompatible Plugin", compat.getMessage(), MessageDialogButton.OK);
      return;
    }

    final OrchestratorBridge.PluginResult plugin = selectedPlugin;
    int verIndex = pluginVersionCombo.getSelectedIndex();
    OrchestratorBridge.PluginVersionInfo chosenVer = null;
    if (verIndex > 0 && (verIndex - 1) < currentPluginVersions.size()) {
      chosenVer = currentPluginVersions.get(verIndex - 1);
    } else if (!currentPluginVersions.isEmpty()) {
      chosenVer = currentPluginVersions.get(0);
    }

    if (chosenVer == null || chosenVer.downloadUrl.isEmpty()) {
      statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " No valid download file found for this plugin."));
      return;
    }

    String filename = chosenVer.filename;
    if (filename == null || filename.isEmpty()) {
      filename = plugin.slug + ".jar";
    }
    if (!filename.endsWith(".jar")) filename += ".jar";

    isDownloading = true;
    statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_BUSY + " Downloading plugin: " + plugin.name + "..."));
    statusLabel.setForegroundColor(Themes.getLogWarnColor());
    ActivityLogger.info("Downloading plugin: " + plugin.name + " -> plugins/" + filename);

    final String finalFilename = filename;
    OrchestratorBridge.getInstance().downloadPluginDirect(
        chosenVer.downloadUrl,
        finalFilename,
        srvPlat,
        version,
        info -> mainWindow.getGui().getGUIThread().invokeLater(() -> {
          pickaxeAnim.setProgress(info.percent);
          statusLabel.setText(GlyphHelper.apply(String.format("%s Downloading %s (%.1f%%) @ %s",
              GlyphHelper.ICON_DOWNLOAD, finalFilename, info.percent, info.formattedSpeed())));
          mainWindow.invalidate();
        }))
        .thenAccept(ok -> mainWindow.getGui().getGUIThread().invokeLater(() -> {
          isDownloading = false;
          statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " [OK] Plugin installed to plugins/" + finalFilename));
          statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
          ActivityLogger.ok("Plugin " + plugin.name + " installed to plugins/" + finalFilename);
          MessageDialog.showMessageDialog(mainWindow.getGui(), "Plugin Installed", "Plugin " + plugin.name + " successfully installed to plugins/" + finalFilename, MessageDialogButton.OK);
        }))
        .exceptionally(ex -> {
          mainWindow.getGui().getGUIThread().invokeLater(() -> {
            isDownloading = false;
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [ERR] Download failed: " + ex.getMessage()));
            statusLabel.setForegroundColor(Themes.getLogErrorColor());
            ActivityLogger.err("Plugin download failed: " + ex.getMessage());
          });
          return null;
        });
  }

  private void cancelDownload() {
    if (isDownloading) {
      isDownloading = false;
      statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " Download cancelled."));
      ActivityLogger.warn("Plugin download cancelled.");
    }
  }

  private void updateSelectedVersionDisplay() {
    if (versionDetailLabel == null) return;
    int sel = pluginVersionCombo.getSelectedIndex();
    if (sel > 0 && (sel - 1) < currentPluginVersions.size()) {
      OrchestratorBridge.PluginVersionInfo v = currentPluginVersions.get(sel - 1);
      versionDetailLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " " + v.filename + " (" + formatSize(v.sizeBytes) + ")"));
    } else {
      versionDetailLabel.setText("");
    }
  }

  private void updateDetailsDisplay() {
    if (selectedPlugin == null) {
      titleAuthorLabel.setText("Select a plugin from the list to view details.");
      descContentLabel.setText("No plugin selected.");
      return;
    }
    titleAuthorLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_MOD + " " + selectedPlugin.name + " by " + selectedPlugin.author + " [" + selectedPlugin.downloads + " downloads]"));
    descContentLabel.setText(selectedPlugin.description);
    mainWindow.invalidate();
  }

  private void fetchFullDescriptionIfNeeded(OrchestratorBridge.PluginResult plugin) {
    if (plugin == null || fullDescCache.containsKey(plugin.id)) return;
    String plat = platformBox.getSelectedItem() != null ? platformBox.getSelectedItem().toLowerCase() : "modrinth";
    OrchestratorBridge.getInstance().getPluginFullDescription(plat, plugin.id).thenAccept(desc -> {
      fullDescCache.put(plugin.id, desc);
      mainWindow.getGui().getGUIThread().invokeLater(this::updateDetailsDisplay);
    });
  }

  private void onPrevPage() {
    if (descPageIndex > 0) {
      descPageIndex--;
      updateDetailsDisplay();
    }
  }

  private void onNextPage() {
    descPageIndex++;
    updateDetailsDisplay();
  }

  private String formatSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    int exp = (int) (Math.log(bytes) / Math.log(1024));
    char unit = "KMGTPE".charAt(exp - 1);
    return String.format("%.1f %cB", bytes / Math.pow(1024, exp), unit);
  }

  @Override
  public String getTitle() {
    return GlyphHelper.apply(GlyphHelper.ICON_PACKAGE + " Plugins (Browse & Manage)");
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
    return currentTab == 0 ? resultsList : installedPluginsList;
  }

  @Override
  public void onActivated() {
    checkInstalledServerState();
  }

  private void checkInstalledServerState() {
    org.codeberg.DeployedReject.utils.ServerJarMetadata meta = OrchestratorBridge.getInstalledServerMetadata();
    if (meta == null) {
      statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " [WARN] No server installed! Please install Paper or Spigot [I]."));
      statusLabel.setForegroundColor(Themes.getLogWarnColor());
      ActivityLogger.warn("No server is installed. You must install a Paper or Spigot server engine first [I] before installing plugins.");
    } else {
      String sType = meta.getServerType();
      String sVer = meta.getGameVersion();
      if ("paper".equalsIgnoreCase(sType)) {
        serverPlatformBox.setSelectedItem("paper");
      } else if ("spigot".equalsIgnoreCase(sType)) {
        serverPlatformBox.setSelectedItem("spigot");
      } else if ("purpur".equalsIgnoreCase(sType)) {
        serverPlatformBox.setSelectedItem("purpur");
      }
      if (sVer != null && !sVer.isEmpty() && !"unknown".equalsIgnoreCase(sVer)) {
        MinecraftVersionHelper.setSelectedVersion(versionComboBox, sVer);
      }

      if ("paper".equalsIgnoreCase(sType) || "spigot".equalsIgnoreCase(sType) || "purpur".equalsIgnoreCase(sType)) {
        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " [SERVER] " + meta.getFormattedTitle() + " detected. Ready for plugins."));
        statusLabel.setForegroundColor(Themes.getLogSuccessColor());
      } else {
        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " [WARN] Installed server is " + meta.getFormattedTitle() + " (Mods server). Paper/Spigot required for plugins."));
        statusLabel.setForegroundColor(Themes.getLogWarnColor());
      }
    }
  }

  @Override
  public void onDeactivated() {}

  @Override
  public void onResized(TerminalSize newSize) {
    if (newSize == null) return;
    this.currentTermWidth = newSize.getColumns();
    this.currentTermHeight = newSize.getRows();
    int listWidth = Math.max(26, Math.min(38, (currentTermWidth * 42) / 100));
    int listHeight = Math.max(6, Math.min(14, currentTermHeight - 16));
    resultsList.setPreferredSize(new TerminalSize(listWidth, listHeight));
    installedPluginsList.setPreferredSize(new TerminalSize(listWidth, listHeight));
  }
}
