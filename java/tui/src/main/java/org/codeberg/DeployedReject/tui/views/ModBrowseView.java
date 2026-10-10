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

public class ModBrowseView implements WorkspaceView {

  private final MainWindow mainWindow;
  private final Panel root;

  // Tabs: 0 = Single Mods, 1 = Modpacks
  private int currentTab = 0;
  private final Button tabSingleModsBtn;
  private final Button tabModpacksBtn;
  private final Panel tabHeaderPanel;
  private final Panel tabContentPanel;

  // ==========================================
  // TAB 0: Single Mods UI State & Components
  // ==========================================
  private final Panel singleModsPanel;
  private final ComboBox<String> platformBox;
  private final ComboBox<String> loaderBox;
  private final ComboBox<String> versionComboBox;
  private final TextBox searchBox;
  private final MurcesListBox resultsList;
  private final ComboBox<String> modVersionCombo;
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

  private final List<OrchestratorBridge.ModResult> currentResults = new ArrayList<>();
  private final List<OrchestratorBridge.ModVersionInfo> currentModVersions = new ArrayList<>();
  private final Map<String, String> fullDescCache = new HashMap<>();
  private final Set<String> pendingFetches = Collections.synchronizedSet(new HashSet<>());
  private OrchestratorBridge.ModResult selectedMod = null;
  private boolean isDownloading = false;
  private int descPageIndex = 0;

  // ==========================================
  // TAB 1: Modpacks UI State & Components
  // ==========================================
  private final Panel modpacksPanel;
  private final ComboBox<String> packPlatformBox;
  private final ComboBox<String> packLoaderBox;
  private final ComboBox<String> packVersionComboBox;
  private final TextBox packSearchBox;
  private final MurcesListBox packResultsList;
  private final ComboBox<String> packVersionCombo;
  private final Label packVersionDetailLabel;
  private final Label packTitleAuthorLabel;
  private final Label packDescContentLabel;
  private final Label packDepsSummaryLabel;
  private final MurcesListBox packDepsList;
  private final CheckBox packIncludeClientCheck;
  private final Label packStatusLabel;
  private final MinecraftPickaxeAnimation packPickaxeAnim;
  private final Button packSearchBtn;
  private final Button packInstallBtn;
  private final Button packCancelBtn;

  private final List<OrchestratorBridge.ModpackResult> currentPackResults = new ArrayList<>();
  private final List<OrchestratorBridge.ModpackVersionInfo> currentPackVersions = new ArrayList<>();
  private final List<OrchestratorBridge.ModpackDependencyInfo> currentPackDeps = new ArrayList<>();
  private OrchestratorBridge.ModpackResult selectedPack = null;
  private boolean isPackInstalling = false;

  private final Map<Character, Runnable> hotkeys = new HashMap<>();
  private ScheduledExecutorService activeTicker = null;
  private ScheduledExecutorService packTicker = null;
  private int currentTermWidth = 80;
  private int currentTermHeight = 24;

  public ModBrowseView(MainWindow mainWindow) {
    this.mainWindow = mainWindow;
    this.root = new Panel(new LinearLayout(Direction.VERTICAL));

    // Tab Header Switcher
    tabHeaderPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    tabSingleModsBtn = new Button(GlyphHelper.apply("[1] Single Mods"), () -> setTab(0));
    tabModpacksBtn = new Button(GlyphHelper.apply("[2] Modpacks"), () -> setTab(1));
    tabHeaderPanel.addComponent(tabSingleModsBtn);
    tabHeaderPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    tabHeaderPanel.addComponent(tabModpacksBtn);
    root.addComponent(tabHeaderPanel);
    root.addComponent(new EmptySpace(new TerminalSize(1, 1)));

    tabContentPanel = new Panel(new LinearLayout(Direction.VERTICAL));
    root.addComponent(tabContentPanel);

    // ==========================================
    // Build Tab 0: Single Mods Panel
    // ==========================================
    singleModsPanel = new Panel(new LinearLayout(Direction.VERTICAL));

    platformBox = new ComboBox<>("Modrinth", "CurseForge");
    loaderBox = new ComboBox<>("fabric", "forge", "neoforge", "quilt");
    versionComboBox = MinecraftVersionHelper.createVersionComboBox(mainWindow.getGui(), new TerminalSize(10, 1));
    searchBox = new TextBox(new TerminalSize(16, 1), "jei");
    searchBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_SEARCH + " [S]earch"), this::onSearch);
    downloadBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_DOWNLOAD + " [D]ownload"), this::onDownload);
    resultsList = new MurcesListBox(new TerminalSize(38, 10));

    Panel filterPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    filterPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_SEARCH + " [P]lat:")));
    filterPanel.addComponent(platformBox);
    filterPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    filterPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_OPTIONS + " L[o]ad:")));
    filterPanel.addComponent(loaderBox);
    filterPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    filterPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_CONFIG + " [V]er:")));
    filterPanel.addComponent(versionComboBox);
    singleModsPanel.addComponent(filterPanel);

    Panel searchPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    searchPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_SEARCH + " [Q] Query: ")));
    searchPanel.addComponent(searchBox);
    searchPanel.addComponent(searchBtn);
    singleModsPanel.addComponent(searchPanel);

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
    leftCol.addComponent(resultsList.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_MOD + " Mod Results"))));

    Panel rightCol = new Panel(new LinearLayout(Direction.VERTICAL));
    detailsCard = new Panel(new LinearLayout(Direction.VERTICAL));

    titleAuthorLabel = new Label("Select a mod from the list to view details.");
    titleAuthorLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
    detailsCard.addComponent(titleAuthorLabel);

    Panel descTogglePanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    descTogglePanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Desc [M]ode:")));
    descModeCombo = new ComboBox<>("Summary", "Full Details");
    descModeCombo.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
      if (changedByUserInteraction) {
        descPageIndex = 0;
        if (selectedIndex == 1 && selectedMod != null) {
          fetchFullDescriptionIfNeeded(selectedMod);
        }
        updateDetailsDisplay();
      }
    });
    descTogglePanel.addComponent(descModeCombo);
    detailsCard.addComponent(descTogglePanel);

    descContentLabel = new Label("No mod selected.");
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
    modVersionCombo = new ComboBox<>("[Latest Compatible]");
    modVersionCombo.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
      if (changedByUserInteraction) {
        updateSelectedVersionDisplay();
      }
    });
    versionSelectPanel.addComponent(modVersionCombo);
    detailsCard.addComponent(versionSelectPanel);

    versionDetailLabel = new Label("");
    versionDetailLabel.setForegroundColor(Themes.getActivePalette().accent);
    detailsCard.addComponent(versionDetailLabel);

    rightCol.addComponent(detailsCard.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Mod Details"))));

    midCols.addComponent(leftCol);
    midCols.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    midCols.addComponent(rightCol);
    singleModsPanel.addComponent(midCols);

    statusLabel = new Label("Ready to browse mods.");
    statusLabel.setForegroundColor(Themes.getLogSuccessColor());
    singleModsPanel.addComponent(statusLabel);

    pickaxeAnim = new MinecraftPickaxeAnimation();
    singleModsPanel.addComponent(pickaxeAnim);

    Panel actionRow = new Panel(new LinearLayout(Direction.HORIZONTAL));
    actionRow.addComponent(downloadBtn);
    cancelBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [X] Cancel Download"), this::cancelDownload);
    actionRow.addComponent(cancelBtn);
    backBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_BACK + " [B]ack to Main Menu"), mainWindow::showMainMenu);
    actionRow.addComponent(backBtn);
    singleModsPanel.addComponent(actionRow);

    // ==========================================
    // Build Tab 1: Modpacks Panel
    // ==========================================
    modpacksPanel = new Panel(new LinearLayout(Direction.VERTICAL));

    packPlatformBox = new ComboBox<>("Modrinth", "CurseForge");
    packLoaderBox = new ComboBox<>("fabric", "forge", "neoforge", "quilt");
    packVersionComboBox = MinecraftVersionHelper.createVersionComboBox(mainWindow.getGui(), new TerminalSize(10, 1));
    packSearchBox = new TextBox(new TerminalSize(16, 1), "cobblemon");
    packSearchBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_SEARCH + " [S]earch Packs"), this::onPackSearch);
    packInstallBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_PACKAGE + " [I]nstall Modpack"), this::onPackInstall);
    packResultsList = new MurcesListBox(new TerminalSize(38, 10));

    Panel packFilterPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    packFilterPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_SEARCH + " [P]lat:")));
    packFilterPanel.addComponent(packPlatformBox);
    packFilterPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    packFilterPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_OPTIONS + " L[o]ad:")));
    packFilterPanel.addComponent(packLoaderBox);
    packFilterPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    packFilterPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_CONFIG + " [V]er:")));
    packFilterPanel.addComponent(packVersionComboBox);
    modpacksPanel.addComponent(packFilterPanel);

    Panel packSearchPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    packSearchPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_SEARCH + " [Q] Query: ")));
    packSearchPanel.addComponent(packSearchBox);
    packSearchPanel.addComponent(packSearchBtn);
    modpacksPanel.addComponent(packSearchPanel);

    packSearchBox.setInputFilter((interactable, keyStroke) -> {
      if (keyStroke.getKeyType() == KeyType.Enter) {
        onPackSearch();
        packResultsList.takeFocus();
        return false;
      }
      if (keyStroke.getKeyType() == KeyType.Escape || keyStroke.getKeyType() == KeyType.ArrowDown) {
        packResultsList.takeFocus();
        return false;
      }
      return true;
    });

    Panel packMidCols = new Panel(new LinearLayout(Direction.HORIZONTAL));
    Panel packLeftCol = new Panel(new LinearLayout(Direction.VERTICAL));
    packLeftCol.addComponent(packResultsList.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_PACKAGE + " Modpacks"))));

    Panel packRightCol = new Panel(new LinearLayout(Direction.VERTICAL));
    Panel packDetailsCard = new Panel(new LinearLayout(Direction.VERTICAL));

    packTitleAuthorLabel = new Label("Select a modpack from the list.");
    packTitleAuthorLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
    packDetailsCard.addComponent(packTitleAuthorLabel);

    packDescContentLabel = new Label("No modpack selected.");
    packDetailsCard.addComponent(packDescContentLabel);

    Panel packVerRow = new Panel(new LinearLayout(Direction.HORIZONTAL));
    packVerRow.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_OPTIONS + " Pack Ver: ")));
    packVersionCombo = new ComboBox<>("[Latest Compatible]");
    packVersionCombo.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
      if (changedByUserInteraction) {
        onPackVersionSelected();
      }
    });
    packVerRow.addComponent(packVersionCombo);
    packDetailsCard.addComponent(packVerRow);

    packVersionDetailLabel = new Label("");
    packVersionDetailLabel.setForegroundColor(Themes.getActivePalette().accent);
    packDetailsCard.addComponent(packVersionDetailLabel);

    packDepsSummaryLabel = new Label("Dependencies: N/A");
    packDepsSummaryLabel.setForegroundColor(MinecraftTheme.DIAMOND_CYAN);
    packDetailsCard.addComponent(packDepsSummaryLabel);

    packDepsList = new MurcesListBox(new TerminalSize(42, 4));
    packDetailsCard.addComponent(packDepsList.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_MOD + " Included Mods"))));

    packIncludeClientCheck = new CheckBox("Include client-only mods (cosmetics/shaders)");
    packIncludeClientCheck.setChecked(false);
    packDetailsCard.addComponent(packIncludeClientCheck);

    packRightCol.addComponent(packDetailsCard.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Pack Manifest & Dependencies"))));

    packMidCols.addComponent(packLeftCol);
    packMidCols.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    packMidCols.addComponent(packRightCol);
    modpacksPanel.addComponent(packMidCols);

    packStatusLabel = new Label("Ready to search modpacks.");
    packStatusLabel.setForegroundColor(Themes.getLogSuccessColor());
    modpacksPanel.addComponent(packStatusLabel);

    packPickaxeAnim = new MinecraftPickaxeAnimation();
    modpacksPanel.addComponent(packPickaxeAnim);

    Panel packActions = new Panel(new LinearLayout(Direction.HORIZONTAL));
    packActions.addComponent(packInstallBtn);
    packCancelBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [X] Cancel Install"), this::cancelPackInstall);
    packActions.addComponent(packCancelBtn);
    packActions.addComponent(new Button(GlyphHelper.apply(GlyphHelper.ICON_BACK + " [B]ack to Main Menu"), mainWindow::showMainMenu));
    modpacksPanel.addComponent(packActions);

    // Initial Tab
    setTab(0);

    // Hotkeys
    hotkeys.put('1', () -> setTab(0));
    hotkeys.put('2', () -> setTab(1));
    hotkeys.put('T', () -> setTab((currentTab + 1) % 2));
    hotkeys.put('P', this::cyclePlatform);
    hotkeys.put('O', this::cycleLoader);
    hotkeys.put('V', this::cycleVersion);
    hotkeys.put('C', this::promptCustomVersion);
    hotkeys.put('Q', this::focusSearchBox);
    hotkeys.put('S', () -> {
      if (currentTab == 0) onSearch();
      else onPackSearch();
    });
    hotkeys.put('D', () -> { if (currentTab == 0) onDownload(); });
    hotkeys.put('I', () -> { if (currentTab == 1) onPackInstall(); });
    hotkeys.put('K', this::cycleModOrPackVersion);
    hotkeys.put('M', this::cycleDescMode);
    hotkeys.put('[', this::onPrevPage);
    hotkeys.put(']', this::onNextPage);
    hotkeys.put('X', () -> {
      if (currentTab == 0) cancelDownload();
      else cancelPackInstall();
    });
    hotkeys.put('B', mainWindow::showMainMenu);
  }

  private void cyclePlatform() {
    if (currentTab == 0) {
      int count = platformBox.getItemCount();
      if (count > 0) {
        int next = (platformBox.getSelectedIndex() + 1) % count;
        platformBox.setSelectedIndex(next);
        statusLabel.setText("Platform changed to: " + platformBox.getItem(next) + ". Press [S] to search.");
        statusLabel.setForegroundColor(Themes.getLogWarnColor());
        mainWindow.invalidate();
      }
    } else {
      int count = packPlatformBox.getItemCount();
      if (count > 0) {
        int next = (packPlatformBox.getSelectedIndex() + 1) % count;
        packPlatformBox.setSelectedIndex(next);
        packStatusLabel.setText("Platform changed to: " + packPlatformBox.getItem(next) + ". Press [S] to search.");
        packStatusLabel.setForegroundColor(Themes.getLogWarnColor());
        mainWindow.invalidate();
      }
    }
  }

  private void cycleLoader() {
    if (currentTab == 0) {
      int count = loaderBox.getItemCount();
      if (count > 0) {
        int next = (loaderBox.getSelectedIndex() + 1) % count;
        loaderBox.setSelectedIndex(next);
        statusLabel.setText("Loader changed to: " + loaderBox.getItem(next) + ". Press [S] to search.");
        statusLabel.setForegroundColor(Themes.getLogWarnColor());
        mainWindow.invalidate();
      }
    } else {
      int count = packLoaderBox.getItemCount();
      if (count > 0) {
        int next = (packLoaderBox.getSelectedIndex() + 1) % count;
        packLoaderBox.setSelectedIndex(next);
        packStatusLabel.setText("Loader changed to: " + packLoaderBox.getItem(next) + ". Press [S] to search.");
        packStatusLabel.setForegroundColor(Themes.getLogWarnColor());
        mainWindow.invalidate();
      }
    }
  }

  private void cycleVersion() {
    if (currentTab == 0) {
      MinecraftVersionHelper.cycleVersion(versionComboBox);
    } else {
      MinecraftVersionHelper.cycleVersion(packVersionComboBox);
    }
  }

  private void promptCustomVersion() {
    if (currentTab == 0) {
      MinecraftVersionHelper.promptCustomVersion(mainWindow.getGui(), versionComboBox, versionComboBox.getSelectedIndex());
    } else {
      MinecraftVersionHelper.promptCustomVersion(mainWindow.getGui(), packVersionComboBox, packVersionComboBox.getSelectedIndex());
    }
  }

  private void focusSearchBox() {
    if (currentTab == 0) {
      searchBox.takeFocus();
    } else {
      packSearchBox.takeFocus();
    }
  }

  private void cycleModOrPackVersion() {
    if (currentTab == 0) {
      int count = modVersionCombo.getItemCount();
      if (count > 0) {
        int next = (modVersionCombo.getSelectedIndex() + 1) % count;
        modVersionCombo.setSelectedIndex(next);
        updateSelectedVersionDisplay();
        mainWindow.invalidate();
      }
    } else {
      int count = packVersionCombo.getItemCount();
      if (count > 0) {
        int next = (packVersionCombo.getSelectedIndex() + 1) % count;
        packVersionCombo.setSelectedIndex(next);
        onPackVersionSelected();
        mainWindow.invalidate();
      }
    }
  }

  private void cycleDescMode() {
    if (currentTab == 0) {
      int count = descModeCombo.getItemCount();
      if (count > 0) {
        int next = (descModeCombo.getSelectedIndex() + 1) % count;
        descModeCombo.setSelectedIndex(next);
        descPageIndex = 0;
        if (next == 1 && selectedMod != null) {
          fetchFullDescriptionIfNeeded(selectedMod);
        }
        updateDetailsDisplay();
        mainWindow.invalidate();
      }
    }
  }

  public void setTab(int tabIndex) {
    this.currentTab = tabIndex;
    tabContentPanel.removeAllComponents();
    if (tabIndex == 0) {
      tabSingleModsBtn.setEnabled(false);
      tabModpacksBtn.setEnabled(true);
      tabContentPanel.addComponent(singleModsPanel);
    } else {
      tabSingleModsBtn.setEnabled(true);
      tabModpacksBtn.setEnabled(false);
      tabContentPanel.addComponent(modpacksPanel);
    }
    mainWindow.invalidate();
  }

  // ==========================================
  // Single Mods Logic
  // ==========================================
  private void onSearch() {
    String query = searchBox.getText().trim();
    String platform = platformBox.getSelectedItem() != null ? platformBox.getSelectedItem().toLowerCase() : "modrinth";
    String version = MinecraftVersionHelper.getSelectedVersion(versionComboBox);
    String loader = loaderBox.getSelectedItem() != null ? loaderBox.getSelectedItem() : "fabric";

    statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_BUSY + " Searching " + platform + " for '" + query + "'..."));
    statusLabel.setForegroundColor(Themes.getLogWarnColor());
    ActivityLogger.info("Searching " + platform + " for '" + query + "' (MC " + version + ", " + loader + ")");

    new Thread(() -> {
      try {
        List<OrchestratorBridge.ModResult> mods = OrchestratorBridge.getInstance().searchMods(platform, query, version, loader).get();
        mainWindow.getGui().getGUIThread().invokeLater(() -> {
          currentResults.clear();
          currentResults.addAll(mods);
          resultsList.clearItems();
          selectedMod = null;

          if (mods.isEmpty()) {
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_FILE + " No mods found matching: \"" + query + "\""));
            statusLabel.setForegroundColor(Themes.getLogMutedColor());
          } else {
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " Found " + mods.size() + " mods."));
            statusLabel.setForegroundColor(Themes.getLogSuccessColor());
            for (OrchestratorBridge.ModResult m : mods) {
              resultsList.addItem(GlyphHelper.apply(GlyphHelper.ICON_MOD + " " + m.name + (m.author.isEmpty() ? "" : " • " + m.author)), () -> {
                onModSelected(m);
              });
            }
            resultsList.setSelectedIndex(0);
            onModSelected(mods.get(0));
          }
          mainWindow.invalidate();
        });
      } catch (Exception e) {
        mainWindow.getGui().getGUIThread().invokeLater(() -> {
          statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " Search failed: " + e.getMessage()));
          statusLabel.setForegroundColor(Themes.getLogErrorColor());
          ActivityLogger.err("Mod search failed: " + e.getMessage());
        });
      }
    }).start();
  }

  private void onModSelected(OrchestratorBridge.ModResult mod) {
    if (mod == null) {
      selectedMod = null;
      updateDetailsDisplay();
      return;
    }
    this.selectedMod = mod;
    this.descPageIndex = 0;
    updateDetailsDisplay();

    String platform = platformBox.getSelectedItem() != null ? platformBox.getSelectedItem().toLowerCase() : "modrinth";
    String version = MinecraftVersionHelper.getSelectedVersion(versionComboBox);
    String loader = loaderBox.getSelectedItem() != null ? loaderBox.getSelectedItem() : "fabric";

    modVersionCombo.clearItems();
    modVersionCombo.addItem("[Latest Compatible]");
    modVersionCombo.setSelectedIndex(0);
    currentModVersions.clear();

    new Thread(() -> {
      try {
        List<OrchestratorBridge.ModVersionInfo> versions = OrchestratorBridge.getInstance()
            .getModVersions(platform, mod.id, version, loader).get();
        mainWindow.getGui().getGUIThread().invokeLater(() -> {
          if (selectedMod != null && mod.id.equals(selectedMod.id)) {
            currentModVersions.clear();
            currentModVersions.addAll(versions);
            for (OrchestratorBridge.ModVersionInfo v : versions) {
              modVersionCombo.addItem(v.toString());
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
    if (selectedMod == null && resultsList.getSelectedIndex() >= 0 && resultsList.getSelectedIndex() < currentResults.size()) {
      selectedMod = currentResults.get(resultsList.getSelectedIndex());
    }
    if (selectedMod == null) {
      statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " Select a mod from the list first!"));
      return;
    }

    String loader = loaderBox.getSelectedItem();
    String version = MinecraftVersionHelper.getSelectedVersion(versionComboBox);

    org.codeberg.DeployedReject.utils.ServerJarMetadata meta = OrchestratorBridge.getInstalledServerMetadata();
    if (meta == null) {
      statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [ERR] No server installed! Install a server engine first [I]."));
      statusLabel.setForegroundColor(Themes.getLogErrorColor());
      ActivityLogger.err("Cannot install mod: No Minecraft server is installed. Please install a server engine first [I].");
      return;
    }
    var compat = OrchestratorBridge.checkCompatibility(loader, version);
    if (!compat.isCompatible()) {
      statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [ERR] " + compat.getMessage()));
      statusLabel.setForegroundColor(Themes.getLogErrorColor());
      ActivityLogger.err(compat.getMessage());
      MessageDialog.showMessageDialog(mainWindow.getGui(), "Incompatible Mod", compat.getMessage(), MessageDialogButton.OK);
      return;
    }

    final OrchestratorBridge.ModResult mod = selectedMod;
    int verIndex = modVersionCombo.getSelectedIndex();
    OrchestratorBridge.ModVersionInfo chosenVer = null;
    if (verIndex > 0 && (verIndex - 1) < currentModVersions.size()) {
      chosenVer = currentModVersions.get(verIndex - 1);
    } else if (!currentModVersions.isEmpty()) {
      chosenVer = currentModVersions.get(0);
    }

    if (chosenVer == null || chosenVer.downloadUrl.isEmpty()) {
      statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " No valid download URL found."));
      return;
    }

    String filename = chosenVer.filename;
    if (filename == null || filename.isEmpty()) filename = mod.name.toLowerCase().replaceAll("[^a-z0-9_-]", "") + ".jar";
    if (!filename.endsWith(".jar")) filename += ".jar";

    isDownloading = true;
    statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_BUSY + " Downloading mod: " + mod.name + "..."));
    statusLabel.setForegroundColor(Themes.getLogWarnColor());
    ActivityLogger.info("Downloading mod: " + mod.name + " -> mods/" + filename);

    final String finalFn = filename;
    OrchestratorBridge.getInstance().downloadModDirect(
        chosenVer.downloadUrl,
        finalFn,
        loader,
        version,
        info -> mainWindow.getGui().getGUIThread().invokeLater(() -> {
          pickaxeAnim.setProgress(info.percent);
          statusLabel.setText(GlyphHelper.apply(String.format("%s Downloading %s (%.1f%%) @ %s",
              GlyphHelper.ICON_DOWNLOAD, finalFn, info.percent, info.formattedSpeed())));
          mainWindow.invalidate();
        }))
        .thenAccept(ok -> mainWindow.getGui().getGUIThread().invokeLater(() -> {
          isDownloading = false;
          statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " [OK] Mod installed to mods/" + finalFn));
          statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
          ActivityLogger.ok("Mod " + mod.name + " installed to mods/" + finalFn);
          MessageDialog.showMessageDialog(mainWindow.getGui(), "Mod Installed", "Mod " + mod.name + " successfully installed to mods/" + finalFn, MessageDialogButton.OK);
        }))
        .exceptionally(ex -> {
          mainWindow.getGui().getGUIThread().invokeLater(() -> {
            isDownloading = false;
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [ERR] Download failed: " + ex.getMessage()));
            statusLabel.setForegroundColor(Themes.getLogErrorColor());
            ActivityLogger.err("Mod download failed: " + ex.getMessage());
          });
          return null;
        });
  }

  private void cancelDownload() {
    if (isDownloading) {
      isDownloading = false;
      statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " Download cancelled."));
      ActivityLogger.warn("Mod download cancelled.");
    }
  }

  private void updateSelectedVersionDisplay() {
    if (versionDetailLabel == null) return;
    int sel = modVersionCombo.getSelectedIndex();
    if (sel > 0 && (sel - 1) < currentModVersions.size()) {
      OrchestratorBridge.ModVersionInfo v = currentModVersions.get(sel - 1);
      versionDetailLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " " + v.filename + " (" + formatSize(v.sizeBytes) + ")"));
    } else {
      versionDetailLabel.setText("");
    }
  }

  private void updateDetailsDisplay() {
    if (selectedMod == null) {
      titleAuthorLabel.setText("Select a mod from the list to view details.");
      descContentLabel.setText("No mod selected.");
      return;
    }
    titleAuthorLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_MOD + " " + selectedMod.name + " by " + selectedMod.author));
    descContentLabel.setText(selectedMod.description);
    mainWindow.invalidate();
  }

  private void fetchFullDescriptionIfNeeded(OrchestratorBridge.ModResult mod) {
    if (mod == null || fullDescCache.containsKey(mod.id)) return;
    String plat = platformBox.getSelectedItem() != null ? platformBox.getSelectedItem().toLowerCase() : "modrinth";
    OrchestratorBridge.getInstance().getModFullDescription(plat, mod.id).thenAccept(desc -> {
      fullDescCache.put(mod.id, desc);
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

  // ==========================================
  // Modpacks Logic (Tab 1)
  // ==========================================
  private void onPackSearch() {
    String query = packSearchBox.getText().trim();
    String plat = packPlatformBox.getSelectedItem() != null ? packPlatformBox.getSelectedItem().toLowerCase() : "modrinth";
    String ver = MinecraftVersionHelper.getSelectedVersion(packVersionComboBox);
    String ldr = packLoaderBox.getSelectedItem();

    packStatusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_BUSY + " Searching modpacks for '" + query + "'..."));
    packStatusLabel.setForegroundColor(Themes.getLogWarnColor());
    ActivityLogger.info("Searching modpacks: '" + query + "' (" + ver + " / " + ldr + ")");

    new Thread(() -> {
      try {
        List<OrchestratorBridge.ModpackResult> packs = OrchestratorBridge.getInstance()
            .searchModpacks(plat, query, ver, ldr).get();
        mainWindow.getGui().getGUIThread().invokeLater(() -> {
          currentPackResults.clear();
          currentPackResults.addAll(packs);
          packResultsList.clearItems();
          selectedPack = null;

          if (packs.isEmpty()) {
            packStatusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_FILE + " No modpacks found matching: \"" + query + "\""));
            packStatusLabel.setForegroundColor(Themes.getLogMutedColor());
          } else {
            packStatusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " Found " + packs.size() + " modpacks."));
            packStatusLabel.setForegroundColor(Themes.getLogSuccessColor());
            for (OrchestratorBridge.ModpackResult p : packs) {
              packResultsList.addItem(GlyphHelper.apply(GlyphHelper.ICON_PACKAGE + " " + p.name + " • " + p.author), () -> {
                onPackSelected(p);
              });
            }
            packResultsList.setSelectedIndex(0);
            onPackSelected(packs.get(0));
          }
          mainWindow.invalidate();
        });
      } catch (Exception e) {
        mainWindow.getGui().getGUIThread().invokeLater(() -> {
          packStatusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " Modpack search failed: " + e.getMessage()));
          packStatusLabel.setForegroundColor(Themes.getLogErrorColor());
          ActivityLogger.err("Modpack search error: " + e.getMessage());
        });
      }
    }).start();
  }

  private void onPackSelected(OrchestratorBridge.ModpackResult pack) {
    if (pack == null) {
      selectedPack = null;
      packTitleAuthorLabel.setText("No modpack selected.");
      packDescContentLabel.setText("");
      return;
    }
    this.selectedPack = pack;
    packTitleAuthorLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_PACKAGE + " " + pack.name + " by " + pack.author + " [" + pack.downloads + " dls]"));
    packDescContentLabel.setText(pack.description);

    String plat = packPlatformBox.getSelectedItem() != null ? packPlatformBox.getSelectedItem().toLowerCase() : "modrinth";
    String ver = MinecraftVersionHelper.getSelectedVersion(packVersionComboBox);
    String ldr = packLoaderBox.getSelectedItem();

    packVersionCombo.clearItems();
    packVersionCombo.addItem("[Latest Compatible]");
    packVersionCombo.setSelectedIndex(0);
    currentPackVersions.clear();

    new Thread(() -> {
      try {
        List<OrchestratorBridge.ModpackVersionInfo> versions = OrchestratorBridge.getInstance()
            .getModpackVersions(plat, pack.id, ver, ldr).get();
        mainWindow.getGui().getGUIThread().invokeLater(() -> {
          if (selectedPack != null && pack.id.equals(selectedPack.id)) {
            currentPackVersions.clear();
            currentPackVersions.addAll(versions);
            for (OrchestratorBridge.ModpackVersionInfo v : versions) {
              packVersionCombo.addItem(v.toString());
            }
            onPackVersionSelected();
          }
        });
      } catch (Exception ignored) {}
    }).start();
  }

  private void onPackVersionSelected() {
    int sel = packVersionCombo.getSelectedIndex();
    if (sel >= 0 && sel < currentPackVersions.size()) {
      OrchestratorBridge.ModpackVersionInfo v = currentPackVersions.get(sel);
      packVersionDetailLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " Version: " + v.versionName + " (" + formatSize(v.sizeBytes) + ")"));
      fetchPackDependencies(v.versionId);
    } else {
      packVersionDetailLabel.setText("");
      packDepsSummaryLabel.setText("Dependencies: N/A");
      packDepsList.clearItems();
    }
    mainWindow.invalidate();
  }

  private void fetchPackDependencies(String versionId) {
    if (versionId == null || versionId.isEmpty()) return;
    String plat = packPlatformBox.getSelectedItem() != null ? packPlatformBox.getSelectedItem().toLowerCase() : "modrinth";
    packDepsSummaryLabel.setText("Resolving dependencies...");
    packDepsList.clearItems();

    new Thread(() -> {
      try {
        List<OrchestratorBridge.ModpackDependencyInfo> deps = OrchestratorBridge.getInstance()
            .getModpackDependencies(plat, versionId).get();
        mainWindow.getGui().getGUIThread().invokeLater(() -> {
          currentPackDeps.clear();
          currentPackDeps.addAll(deps);
          int srv = 0, cli = 0;
          for (OrchestratorBridge.ModpackDependencyInfo d : deps) {
            if (d.isClientOnly()) cli++;
            else srv++;
            packDepsList.addItem(d.name + " (" + d.formattedEnvironment() + ")", () -> {});
          }
          packDepsSummaryLabel.setText(String.format("Dependencies: %d total (%d server-safe, %d client-only)", deps.size(), srv, cli));
          mainWindow.invalidate();
        });
      } catch (Exception ignored) {}
    }).start();
  }

  private void onPackInstall() {
    if (isPackInstalling) {
      packStatusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " A modpack installation is already in progress."));
      return;
    }
    if (selectedPack == null) {
      packStatusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " Select a modpack first!"));
      return;
    }

    String plat = packPlatformBox.getSelectedItem();
    String ldr = packLoaderBox.getSelectedItem();
    String ver = MinecraftVersionHelper.getSelectedVersion(packVersionComboBox);

    org.codeberg.DeployedReject.utils.ServerJarMetadata meta = OrchestratorBridge.getInstalledServerMetadata();
    if (meta == null) {
      packStatusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [ERR] No server installed! Install a server engine first [I]."));
      packStatusLabel.setForegroundColor(Themes.getLogErrorColor());
      ActivityLogger.err("Cannot install modpack: No Minecraft server is installed. Please install a server engine first [I].");
      MessageDialog.showMessageDialog(mainWindow.getGui(), "Server Required", "No Minecraft server is installed.\nPlease install a server engine first from [I] Install Server Engine.", MessageDialogButton.OK);
      return;
    }
    var compat = OrchestratorBridge.checkCompatibility(ldr, ver);
    if (!compat.isCompatible()) {
      packStatusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [ERR] " + compat.getMessage()));
      packStatusLabel.setForegroundColor(Themes.getLogErrorColor());
      ActivityLogger.err(compat.getMessage());
      MessageDialog.showMessageDialog(mainWindow.getGui(), "Incompatible Modpack", compat.getMessage(), MessageDialogButton.OK);
      return;
    }

    String versionId = "";
    String mrpackUrl = "";
    int selIdx = packVersionCombo.getSelectedIndex();
    if (selIdx >= 0 && selIdx < currentPackVersions.size()) {
      OrchestratorBridge.ModpackVersionInfo v = currentPackVersions.get(selIdx);
      versionId = v.versionId;
      mrpackUrl = v.mrpackUrl;
    } else {
      versionId = selectedPack.slug;
    }

    boolean includeClient = packIncludeClientCheck.isChecked();
    isPackInstalling = true;
    packStatusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_BUSY + " Installing modpack: " + selectedPack.name + "..."));
    packStatusLabel.setForegroundColor(Themes.getLogWarnColor());
    ActivityLogger.info("Starting installation of modpack: " + selectedPack.name + " (" + ver + " / " + ldr + ")");

    OrchestratorBridge.getInstance().installModpack(
        plat,
        selectedPack.name,
        versionId,
        mrpackUrl,
        ver,
        ldr,
        includeClient,
        stepMsg -> mainWindow.getGui().getGUIThread().invokeLater(() -> {
          packStatusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_BUSY + " " + stepMsg));
          ActivityLogger.info(stepMsg);
        }),
        overallPct -> mainWindow.getGui().getGUIThread().invokeLater(() -> {
          packPickaxeAnim.setProgress(overallPct);
          mainWindow.invalidate();
        }),
        progressInfo -> mainWindow.getGui().getGUIThread().invokeLater(() -> {
          if (progressInfo.speedMBps > 0) {
            packStatusLabel.setText(GlyphHelper.apply(String.format("%s Downloading file (%.1f%%) @ %s (ETA: %s)",
                GlyphHelper.ICON_DOWNLOAD, progressInfo.percent, progressInfo.formattedSpeed(), progressInfo.formattedEta())));
          }
        }))
        .thenAccept(summary -> mainWindow.getGui().getGUIThread().invokeLater(() -> {
          isPackInstalling = false;
          packStatusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " " + summary.formattedSummary()));
          packStatusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
          ActivityLogger.ok(summary.formattedSummary());
          MessageDialog.showMessageDialog(mainWindow.getGui(), "Modpack Installed", summary.formattedSummary(), MessageDialogButton.OK);
        }))
        .exceptionally(ex -> {
          mainWindow.getGui().getGUIThread().invokeLater(() -> {
            isPackInstalling = false;
            packStatusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " Installation failed: " + ex.getMessage()));
            packStatusLabel.setForegroundColor(Themes.getLogErrorColor());
            ActivityLogger.err("Modpack install failed: " + ex.getMessage());
          });
          return null;
        });
  }

  private void cancelPackInstall() {
    if (isPackInstalling) {
      isPackInstalling = false;
      packStatusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " Modpack install cancelled."));
      ActivityLogger.warn("Modpack installation cancelled by user.");
    }
  }

  private String formatSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    int exp = (int) (Math.log(bytes) / Math.log(1024));
    char unit = "KMGTPE".charAt(exp - 1);
    return String.format("%.1f %cB", bytes / Math.pow(1024, exp), unit);
  }

  @Override
  public String getTitle() {
    return GlyphHelper.apply(GlyphHelper.ICON_DOWNLOAD + " Browse Mods & Modpacks");
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
    return currentTab == 0 ? resultsList : packResultsList;
  }

  @Override
  public void onActivated() {
    checkInstalledServerState();
  }

  private void checkInstalledServerState() {
    org.codeberg.DeployedReject.utils.ServerJarMetadata meta = OrchestratorBridge.getInstalledServerMetadata();
    if (meta == null) {
      statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " [WARN] No server installed! Please install a server engine first [I]."));
      statusLabel.setForegroundColor(Themes.getLogWarnColor());
      packStatusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " [WARN] No server installed! Please install a server engine first [I]."));
      packStatusLabel.setForegroundColor(Themes.getLogWarnColor());
      ActivityLogger.warn("No Minecraft server is installed. You must install a server engine first from [I] Install Server Engine.");
    } else {
      String sType = meta.getServerType();
      String sVer = meta.getGameVersion();
      if ("fabric".equalsIgnoreCase(sType) || "quilt".equalsIgnoreCase(sType)) {
        loaderBox.setSelectedItem("fabric");
        packLoaderBox.setSelectedItem("fabric");
      } else if ("forge".equalsIgnoreCase(sType)) {
        loaderBox.setSelectedItem("forge");
        packLoaderBox.setSelectedItem("forge");
      } else if ("neoforge".equalsIgnoreCase(sType)) {
        loaderBox.setSelectedItem("neoforge");
        packLoaderBox.setSelectedItem("neoforge");
      }
      if (sVer != null && !sVer.isEmpty() && !"unknown".equalsIgnoreCase(sVer)) {
        MinecraftVersionHelper.setSelectedVersion(versionComboBox, sVer);
        MinecraftVersionHelper.setSelectedVersion(packVersionComboBox, sVer);
      }
      if ("vanilla".equalsIgnoreCase(sType)) {
        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " [WARN] Vanilla server detected (" + sVer + "). Vanilla does not support mods."));
        statusLabel.setForegroundColor(Themes.getLogWarnColor());
      } else if ("paper".equalsIgnoreCase(sType) || "spigot".equalsIgnoreCase(sType)) {
        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " [WARN] " + meta.getFormattedTitle() + " detected (Plugins only, mods unsupported)."));
        statusLabel.setForegroundColor(Themes.getLogWarnColor());
      } else {
        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " [SERVER] " + meta.getFormattedTitle() + " detected. Ready for mods."));
        statusLabel.setForegroundColor(Themes.getLogSuccessColor());
        packStatusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " [SERVER] " + meta.getFormattedTitle() + " detected. Ready for modpacks."));
        packStatusLabel.setForegroundColor(Themes.getLogSuccessColor());
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
    packResultsList.setPreferredSize(new TerminalSize(listWidth, listHeight));
  }
}
