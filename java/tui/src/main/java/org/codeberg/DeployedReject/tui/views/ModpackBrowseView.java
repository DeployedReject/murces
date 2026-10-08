package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.input.KeyType;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.theme.GlyphHelper;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;
import org.codeberg.DeployedReject.tui.theme.Themes;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ModpackBrowseView implements WorkspaceView {

  private final MainWindow mainWindow;
  private final Panel root;
  private final ComboBox<String> platformBox;
  private final ComboBox<String> loaderBox;
  private final ComboBox<String> versionComboBox;
  private final TextBox searchBox;
  private final MurcesListBox resultsList;
  private final ComboBox<String> packVersionCombo;
  private final Label versionDetailLabel;
  private final ComboBox<String> descModeCombo;
  private final Label titleAuthorLabel;
  private final Label descContentLabel;
  private final Label dependenciesSummaryLabel;
  private final MurcesListBox dependenciesList;
  private final CheckBox includeClientCheckBox;
  private final Label statusLabel;
  private final MinecraftPickaxeAnimation pickaxeAnim;
  private final Panel detailsCard;
  private final Button searchBtn;
  private final Button installBtn;
  private final Button cancelBtn;
  private final Button backBtn;
  private final Button prevPageBtn;
  private final Button nextPageBtn;
  private final Label pageIndicatorLabel;
  private final Panel paginationPanel;

  private final List<OrchestratorBridge.ModpackResult> currentResults = new ArrayList<>();
  private final List<OrchestratorBridge.ModpackVersionInfo> currentPackVersions = new ArrayList<>();
  private final List<OrchestratorBridge.ModpackDependencyInfo> currentDependencies = new ArrayList<>();
  private final Map<Character, Runnable> hotkeys = new HashMap<>();
  private final Map<String, String> fullDescCache = new HashMap<>();
  private final Set<String> pendingFetches = Collections.synchronizedSet(new HashSet<>());

  private OrchestratorBridge.ModpackResult selectedPack = null;
  private boolean isInstalling = false;
  private int descPageIndex = 0;
  private int currentTermWidth = 80;
  private int currentTermHeight = 24;
  private int descCardWidth = 46;
  private int descLinesPerPage = 6;
  private ScheduledExecutorService activeTicker = null;

  private Panel midCols;
  private Panel leftCol;
  private Panel rightCol;
  private Panel downloadPanel;

  public ModpackBrowseView(MainWindow mainWindow) {
    this.mainWindow = mainWindow;
    this.root = new Panel(new LinearLayout(Direction.VERTICAL));

    platformBox = new ComboBox<>("Modrinth", "CurseForge");
    loaderBox = new ComboBox<>("fabric", "forge", "neoforge", "quilt");
    versionComboBox = MinecraftVersionHelper.createVersionComboBox(mainWindow.getGui(), new TerminalSize(10, 1));
    searchBox = new TextBox(new TerminalSize(16, 1), "cobblemon");
    searchBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_SEARCH + " [S]earch"), this::onSearch);
    installBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_PACKAGE + " [I]nstall Modpack"), this::onInstall);
    resultsList = new MurcesListBox(new TerminalSize(38, 10));

    platformBox.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
      if (changedByUserInteraction) {
        mainWindow.getGui().getGUIThread().invokeLater(loaderBox::takeFocus);
      }
    });

    loaderBox.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
      if (changedByUserInteraction) {
        mainWindow.getGui().getGUIThread().invokeLater(versionComboBox::takeFocus);
      }
    });

    versionComboBox.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
      if (selectedIndex >= 0 && selectedIndex < versionComboBox.getItemCount()) {
        String sel = versionComboBox.getItem(selectedIndex);
        if (!"Custom...".equals(sel) && changedByUserInteraction) {
          mainWindow.getGui().getGUIThread().invokeLater(searchBox::takeFocus);
        }
      }
    });

    Panel filterPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    filterPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_SEARCH + " [P]lat:")));
    filterPanel.addComponent(platformBox);
    filterPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    filterPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_OPTIONS + " L[o]ad:")));
    filterPanel.addComponent(loaderBox);
    filterPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    filterPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_CONFIG + " [V]er:")));
    filterPanel.addComponent(versionComboBox);
    root.addComponent(filterPanel);

    Panel searchPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    searchPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_SEARCH + " [Q] Query: ")));

    searchBox.setInputFilter((interactable, keyStroke) -> {
      if (keyStroke.getKeyType() == KeyType.Escape || keyStroke.getKeyType() == KeyType.ArrowDown) {
        if (resultsList.getItemCount() > 0) {
          resultsList.takeFocus();
        }
        return false;
      }
      if (keyStroke.getKeyType() == KeyType.Enter) {
        onSearch();
        return false;
      }
      return true;
    });

    searchPanel.addComponent(searchBox);
    searchPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    searchPanel.addComponent(searchBtn);
    searchPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    searchPanel.addComponent(installBtn);
    searchPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    cancelBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [X] Cancel Install"), this::cancelInstall);
    searchPanel.addComponent(cancelBtn);
    root.addComponent(searchPanel);

    statusLabel = new Label(
        GlyphHelper.apply(GlyphHelper.ICON_INFO + " Search modpacks, select a version, inspect dependencies, and press [I] to install."));
    statusLabel.setForegroundColor(Themes.getLogWarnColor());
    root.addComponent(statusLabel);

    midCols = new Panel(new LinearLayout(Direction.HORIZONTAL));

    leftCol = new Panel(new LinearLayout(Direction.VERTICAL));
    leftCol.addComponent(
        resultsList.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_PACKAGE + " Modpacks [L]ist (↑/↓)"))));
    midCols.addComponent(leftCol);

    midCols.addComponent(new EmptySpace(new TerminalSize(1, 1)));

    rightCol = new Panel(new LinearLayout(Direction.VERTICAL));
    detailsCard = new Panel(new LinearLayout(Direction.VERTICAL));

    titleAuthorLabel = new Label(
        GlyphHelper.apply(GlyphHelper.ICON_PACKAGE + " Modpack: -\n" + GlyphHelper.ICON_USER + " Author: -"));
    titleAuthorLabel.setForegroundColor(Themes.getAccentColor());
    detailsCard.addComponent(titleAuthorLabel);

    Panel versionRow = new Panel(new LinearLayout(Direction.HORIZONTAL));
    versionRow.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_OPTIONS + " Pack Version [K]: ")));
    packVersionCombo = new ComboBox<>("[Latest Compatible]");
    versionRow.addComponent(packVersionCombo);
    detailsCard.addComponent(versionRow);

    versionDetailLabel = new Label("");
    versionDetailLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
    detailsCard.addComponent(versionDetailLabel);

    packVersionCombo.setInputFilter((interactable, keyStroke) -> {
      if (keyStroke.getKeyType() == KeyType.Enter ||
          (keyStroke.getKeyType() == KeyType.Character && keyStroke.getCharacter() != null && keyStroke.getCharacter() == ' ')) {
        WideDropDownHelper.showWideDropDown(mainWindow.getGui(), packVersionCombo, 10, idx -> {
          updateSelectedVersionDisplay();
          installBtn.takeFocus();
        });
        return false;
      }
      return true;
    });

    packVersionCombo.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
      updateSelectedVersionDisplay();
    });

    Panel modeRow = new Panel(new LinearLayout(Direction.HORIZONTAL));
    modeRow.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_FILE + " View [M]ode: ")));
    descModeCombo = new ComboBox<>(GlyphHelper.apply("▾ Summary"), GlyphHelper.apply("▾ Read More (Description)"));
    descModeCombo.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
      descPageIndex = 0;
      if (selectedIndex == 1 && selectedPack != null) {
        fetchFullDescriptionIfNeeded(selectedPack);
      }
      relayoutForCurrentMode();
      updateDetailsDisplay();
      mainWindow.invalidate();
    });
    descModeCombo.setInputFilter((interactable, keyStroke) -> false);
    modeRow.addComponent(descModeCombo);
    detailsCard.addComponent(modeRow);

    descContentLabel = new Label("No modpack selected.\nSearch and select a modpack from the left list.");
    detailsCard.addComponent(descContentLabel);

    paginationPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
    prevPageBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_BACK + " Prev"), this::onPrevPage);
    nextPageBtn = new Button(GlyphHelper.apply("Next " + GlyphHelper.ICON_PLAY), this::onNextPage);
    pageIndicatorLabel = new Label("Page 1/1");
    pageIndicatorLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);

    paginationPanel.addComponent(prevPageBtn);
    paginationPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    paginationPanel.addComponent(pageIndicatorLabel);
    paginationPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
    paginationPanel.addComponent(nextPageBtn);
    paginationPanel.setVisible(false);
    detailsCard.addComponent(paginationPanel);

    // Dependencies Inspection section
    dependenciesSummaryLabel = new Label(GlyphHelper.apply(GlyphHelper.ICON_CONFIG + " Dependencies: [None]"));
    dependenciesSummaryLabel.setForegroundColor(MinecraftTheme.DIAMOND_CYAN);
    detailsCard.addComponent(dependenciesSummaryLabel);

    dependenciesList = new MurcesListBox(new TerminalSize(44, 4));
    detailsCard.addComponent(dependenciesList.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Detected Mod Dependencies"))));

    includeClientCheckBox = new CheckBox(GlyphHelper.apply("Include Client-Only Mods (Default: Unchecked for Server Stability)"));
    includeClientCheckBox.setChecked(false);
    detailsCard.addComponent(includeClientCheckBox);

    rightCol.addComponent(detailsCard.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_INFO + " Modpack & Dependency Details"))));
    midCols.addComponent(rightCol);
    root.addComponent(midCols);

    downloadPanel = new Panel(new LinearLayout(Direction.VERTICAL));
    pickaxeAnim = new MinecraftPickaxeAnimation();
    downloadPanel.addComponent(pickaxeAnim);
    downloadPanel.setVisible(false);
    root.addComponent(downloadPanel);

    backBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_BACK + " [B]ack to Main Menu"), mainWindow::showMainMenu);
    root.addComponent(backBtn);

    resultsList.setSelectionListener(selectedIndex -> {
      if (selectedIndex >= 0 && selectedIndex < currentResults.size()) {
        selectedPack = currentResults.get(selectedIndex);
        descPageIndex = 0;
        updateDetailsDisplay();
        fetchPackVersions(selectedPack);
      }
    });

    resultsList.setInputFilter((interactable, keyStroke) -> {
      if (keyStroke.getKeyType() == KeyType.Enter) {
        installBtn.takeFocus();
        return false;
      }
      return true;
    });

    hotkeys.put('S', KeyboardNavigationHelper.action(searchBtn, this::onSearch));
    hotkeys.put('I', KeyboardNavigationHelper.action(installBtn, this::onInstall));
    hotkeys.put('X', KeyboardNavigationHelper.action(cancelBtn, this::cancelInstall));
    hotkeys.put('Q', searchBox::takeFocus);
    hotkeys.put('P', platformBox::takeFocus);
    hotkeys.put('O', loaderBox::takeFocus);
    hotkeys.put('V', () -> MinecraftVersionHelper.cycleVersion(versionComboBox));
    hotkeys.put('L', resultsList::takeFocus);
    hotkeys.put('K', packVersionCombo::takeFocus);
    hotkeys.put('M', this::cycleDescMode);
    hotkeys.put('B', KeyboardNavigationHelper.action(backBtn, mainWindow::showMainMenu));

    setInstallationUIState(false);
  }

  private void cycleDescMode() {
    int next = (descModeCombo.getSelectedIndex() + 1) % descModeCombo.getItemCount();
    descModeCombo.setSelectedIndex(next);
    descPageIndex = 0;
    if (next == 1 && selectedPack != null) {
      fetchFullDescriptionIfNeeded(selectedPack);
    }
    relayoutForCurrentMode();
    updateDetailsDisplay();
    mainWindow.invalidate();
  }

  private void relayoutForCurrentMode() {
    boolean isFullMode = (descModeCombo.getSelectedIndex() == 1);
    paginationPanel.setVisible(isFullMode);
    dependenciesList.setVisible(!isFullMode);
  }

  private void onPrevPage() {
    if (descPageIndex > 0) {
      descPageIndex--;
      updateDetailsDisplay();
    }
  }

  private void onNextPage() {
    List<String> pages = getFullDescPages();
    if (descPageIndex < pages.size() - 1) {
      descPageIndex++;
      updateDetailsDisplay();
    }
  }

  private List<String> getFullDescPages() {
    if (selectedPack == null) return Collections.singletonList("");
    String full = fullDescCache.get(selectedPack.id);
    if (full == null) full = fullDescCache.get(selectedPack.slug);
    if (full == null || full.isEmpty()) {
      full = selectedPack.description != null ? selectedPack.description : "";
    }
    return paginateText(full, descCardWidth, descLinesPerPage);
  }

  private static List<String> paginateText(String text, int width, int linesPerPage) {
    if (text == null || text.trim().isEmpty()) {
      return Collections.singletonList("No description available.");
    }
    int maxW = Math.max(20, width);
    int maxL = Math.max(3, linesPerPage);

    List<String> wrappedLines = new ArrayList<>();
    String[] rawLines = text.split("\n", -1);
    for (String raw : rawLines) {
      if (raw.length() <= maxW) {
        wrappedLines.add(raw);
      } else {
        int start = 0;
        while (start < raw.length()) {
          int end = Math.min(start + maxW, raw.length());
          if (end < raw.length()) {
            int lastSpace = raw.lastIndexOf(' ', end);
            if (lastSpace > start + (maxW / 3)) {
              end = lastSpace + 1;
            }
          }
          wrappedLines.add(raw.substring(start, end).trim());
          start = end;
        }
      }
    }

    List<String> pages = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int count = 0;
    for (String line : wrappedLines) {
      if (count >= maxL) {
        pages.add(current.toString().trim());
        current = new StringBuilder();
        count = 0;
      }
      current.append(line).append("\n");
      count++;
    }
    if (current.length() > 0) {
      pages.add(current.toString().trim());
    }
    return pages.isEmpty() ? Collections.singletonList("No description available.") : pages;
  }

  private void fetchFullDescriptionIfNeeded(OrchestratorBridge.ModpackResult pack) {
    if (pack == null) return;
    String idOrSlug = !pack.slug.isEmpty() ? pack.slug : pack.id;
    if (fullDescCache.containsKey(pack.id) || fullDescCache.containsKey(idOrSlug) || pendingFetches.contains(idOrSlug)) {
      return;
    }
    pendingFetches.add(idOrSlug);
    String plat = platformBox.getSelectedItem();
    OrchestratorBridge.getInstance().getModFullDescription(plat, idOrSlug)
        .thenAccept(body -> {
          pendingFetches.remove(idOrSlug);
          if (body != null && !body.isEmpty()) {
            fullDescCache.put(pack.id, body);
            fullDescCache.put(idOrSlug, body);
            mainWindow.getGui().getGUIThread().invokeLater(() -> {
              if (selectedPack != null && (selectedPack.id.equals(pack.id) || selectedPack.slug.equals(pack.slug))) {
                updateDetailsDisplay();
                mainWindow.invalidate();
              }
            });
          }
        })
        .exceptionally(ex -> {
          pendingFetches.remove(idOrSlug);
          return null;
        });
  }

  private void fetchPackVersions(OrchestratorBridge.ModpackResult pack) {
    if (pack == null) return;
    String plat = platformBox.getSelectedItem();
    String ldr = loaderBox.getSelectedItem();
    String ver = MinecraftVersionHelper.getSelectedVersion(versionComboBox);
    String idOrSlug = !pack.slug.isEmpty() ? pack.slug : pack.id;

    packVersionCombo.clearItems();
    packVersionCombo.addItem("[Loading versions...]");
    packVersionCombo.setSelectedIndex(0);
    versionDetailLabel.setText("Fetching version metadata...");
    currentPackVersions.clear();
    currentDependencies.clear();
    dependenciesList.clearItems();
    dependenciesSummaryLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CONFIG + " Dependencies: [Fetching...]"));

    OrchestratorBridge.getInstance().getModpackVersions(plat, idOrSlug, ver, ldr)
        .thenAccept(versions -> mainWindow.getGui().getGUIThread().invokeLater(() -> {
          currentPackVersions.clear();
          currentPackVersions.addAll(versions);
          packVersionCombo.clearItems();
          if (currentPackVersions.isEmpty()) {
            packVersionCombo.addItem("[No matching versions]");
            versionDetailLabel.setText("No compatible version for " + ver + " (" + ldr + ")");
            dependenciesSummaryLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CONFIG + " Dependencies: [None]"));
          } else {
            for (OrchestratorBridge.ModpackVersionInfo v : currentPackVersions) {
              packVersionCombo.addItem(v.toString());
            }
            packVersionCombo.setSelectedIndex(0);
            updateSelectedVersionDisplay();
          }
          mainWindow.invalidate();
        }))
        .exceptionally(ex -> {
          mainWindow.getGui().getGUIThread().invokeLater(() -> {
            packVersionCombo.clearItems();
            packVersionCombo.addItem("[Failed to load versions]");
            versionDetailLabel.setText("Error loading versions: " + ex.getMessage());
          });
          return null;
        });
  }

  private void updateSelectedVersionDisplay() {
    int idx = packVersionCombo.getSelectedIndex();
    if (idx >= 0 && idx < currentPackVersions.size()) {
      OrchestratorBridge.ModpackVersionInfo v = currentPackVersions.get(idx);
      String sz = v.sizeBytes > 0 ? String.format(" | Size: %.1f MB", v.sizeBytes / (1024.0 * 1024.0)) : "";
      versionDetailLabel.setText("Build: " + (!v.versionName.isEmpty() ? v.versionName : v.versionId) + sz);

      // Fetch version dependencies metadata
      fetchDependenciesForVersion(v.versionId);
    }
  }

  private void fetchDependenciesForVersion(String versionId) {
    if (versionId == null || versionId.isEmpty()) return;
    String plat = platformBox.getSelectedItem();
    dependenciesSummaryLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_BUSY + " Inspecting dependencies section..."));
    dependenciesList.clearItems();

    OrchestratorBridge.getInstance().getModpackDependencies(plat, versionId)
        .thenAccept(deps -> mainWindow.getGui().getGUIThread().invokeLater(() -> {
          currentDependencies.clear();
          currentDependencies.addAll(deps);
          dependenciesList.clearItems();

          int serverMods = 0;
          int clientMods = 0;
          for (OrchestratorBridge.ModpackDependencyInfo d : currentDependencies) {
            if (d.isClientOnly()) {
              clientMods++;
              dependenciesList.addItem(GlyphHelper.apply("○ " + d.name + " [Client Only]"), () -> {});
            } else {
              serverMods++;
              dependenciesList.addItem(GlyphHelper.apply("● " + d.name + " [" + d.formattedEnvironment() + "]"), () -> {});
            }
          }

          if (currentDependencies.isEmpty()) {
            dependenciesSummaryLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " Dependencies: Pack includes direct archive / 0 external deps"));
          } else {
            dependenciesSummaryLabel.setText(GlyphHelper.apply(
                GlyphHelper.ICON_CONFIG + " Dependencies: " + currentDependencies.size() + " mods (" + serverMods + " Server/Universal, " + clientMods + " Client-Only)"));
          }
          mainWindow.invalidate();
        }))
        .exceptionally(ex -> {
          mainWindow.getGui().getGUIThread().invokeLater(() -> {
            dependenciesSummaryLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " Could not parse dependencies: " + ex.getMessage()));
          });
          return null;
        });
  }

  private void updateDetailsDisplay() {
    if (selectedPack == null) {
      titleAuthorLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_PACKAGE + " Modpack: -\n" + GlyphHelper.ICON_USER + " Author: -"));
      descContentLabel.setText("No modpack selected.\nSearch and select a modpack from the left list.");
      return;
    }

    String dls = selectedPack.downloads > 0 ? String.format(" (%,d downloads)", selectedPack.downloads) : "";
    titleAuthorLabel.setText(GlyphHelper.apply(
        GlyphHelper.ICON_PACKAGE + " " + selectedPack.name + "\n" +
        GlyphHelper.ICON_USER + " By: " + (!selectedPack.author.isEmpty() ? selectedPack.author : "Unknown") + dls));

    if (descModeCombo.getSelectedIndex() == 1) {
      List<String> pages = getFullDescPages();
      if (descPageIndex >= pages.size()) descPageIndex = Math.max(0, pages.size() - 1);
      descContentLabel.setText(pages.get(descPageIndex));
      pageIndicatorLabel.setText(String.format("Page %d/%d", descPageIndex + 1, pages.size()));
      prevPageBtn.setEnabled(descPageIndex > 0);
      nextPageBtn.setEnabled(descPageIndex < pages.size() - 1);
    } else {
      String desc = selectedPack.description != null ? selectedPack.description.trim() : "No summary provided.";
      descContentLabel.setText(desc);
    }
  }

  private void onSearch() {
    String query = searchBox.getText().trim();
    String plat = platformBox.getSelectedItem();
    String ldr = loaderBox.getSelectedItem();
    String ver = MinecraftVersionHelper.getSelectedVersion(versionComboBox);

    statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_BUSY + " Searching " + plat + " for modpack: '" + query + "' (" + ver + " / " + ldr + ")..."));
    statusLabel.setForegroundColor(Themes.getLogWarnColor());
    searchBtn.setEnabled(false);

    OrchestratorBridge.getInstance().searchModpacks(plat, query, ver, ldr)
        .thenAccept(results -> mainWindow.getGui().getGUIThread().invokeLater(() -> {
          searchBtn.setEnabled(true);
          currentResults.clear();
          currentResults.addAll(results);
          resultsList.clearItems();

          if (currentResults.isEmpty()) {
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " No modpacks found for '" + query + "'. Try a different search term."));
            statusLabel.setForegroundColor(Themes.getLogWarnColor());
          } else {
            for (int i = 0; i < currentResults.size(); i++) {
              OrchestratorBridge.ModpackResult r = currentResults.get(i);
              final int rIdx = i;
              String dStr = r.downloads > 0 ? " (" + formatCount(r.downloads) + ")" : "";
              resultsList.addItem(GlyphHelper.apply(GlyphHelper.ICON_PACKAGE + " " + r.name + dStr), () -> {
                selectedPack = r;
                descPageIndex = 0;
                updateDetailsDisplay();
                fetchPackVersions(selectedPack);
              });
            }
            resultsList.setSelectedIndex(0);
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " Found " + currentResults.size() + " modpack results."));
            statusLabel.setForegroundColor(Themes.getAccentColor());
          }
          mainWindow.invalidate();
        }))
        .exceptionally(ex -> {
          mainWindow.getGui().getGUIThread().invokeLater(() -> {
            searchBtn.setEnabled(true);
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " Search failed: " + ex.getMessage()));
            statusLabel.setForegroundColor(Themes.getErrorColor());
          });
          return null;
        });
  }

  private String formatCount(int c) {
    if (c >= 1_000_000) return String.format("%.1fM", c / 1_000_000.0);
    if (c >= 1_000) return String.format("%.1fK", c / 1_000.0);
    return String.valueOf(c);
  }

  private void onInstall() {
    if (isInstalling) {
      ActivityLogger.warn("A modpack installation is already in progress.");
      return;
    }
    if (selectedPack == null) {
      statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " Please select a modpack from the results list first."));
      return;
    }

    String plat = platformBox.getSelectedItem();
    String ldr = loaderBox.getSelectedItem();
    String ver = MinecraftVersionHelper.getSelectedVersion(versionComboBox);

    org.codeberg.DeployedReject.utils.ServerJarMetadata meta = OrchestratorBridge.getInstalledServerMetadata();
    if (meta == null) {
      statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [ERR] No server installed! Install a server engine first [I]."));
      statusLabel.setForegroundColor(Themes.getLogErrorColor());
      ActivityLogger.err("Cannot install modpack: No Minecraft server is installed. Please install a server engine first from [I] Install Server Engine.");
      MessageDialog.showMessageDialog(mainWindow.getGui(), "Server Required", "No Minecraft server is installed.\nPlease install a server engine first from [I] Install Server Engine.", MessageDialogButton.OK);
      return;
    }
    var compat = OrchestratorBridge.checkCompatibility(ldr, ver);
    if (!compat.isCompatible()) {
      statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [ERR] " + compat.getMessage()));
      statusLabel.setForegroundColor(Themes.getLogErrorColor());
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

    boolean includeClient = includeClientCheckBox.isChecked();
    setInstallationUIState(true);

    statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_BUSY + " Installing modpack: " + selectedPack.name + "..."));
    statusLabel.setForegroundColor(Themes.getAccentColor());
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
          statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_BUSY + " " + stepMsg));
          ActivityLogger.info(stepMsg);
        }),
        overallPct -> mainWindow.getGui().getGUIThread().invokeLater(() -> {
          pickaxeAnim.setProgress((int) Math.round(overallPct));
          mainWindow.invalidate();
        }),
        progressInfo -> mainWindow.getGui().getGUIThread().invokeLater(() -> {
          if (progressInfo.speedMBps > 0) {
            statusLabel.setText(GlyphHelper.apply(String.format("%s Downloading file (%.1f%%) @ %s (ETA: %s)",
                GlyphHelper.ICON_DOWNLOAD, progressInfo.percent, progressInfo.formattedSpeed(), progressInfo.formattedEta())));
          }
        }))
        .thenAccept(summary -> mainWindow.getGui().getGUIThread().invokeLater(() -> {
          setInstallationUIState(false);
          statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " " + summary.formattedSummary()));
          statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
          ActivityLogger.ok(summary.formattedSummary());
          MessageDialog.showMessageDialog(mainWindow.getGui(), "Modpack Installed", summary.formattedSummary(), MessageDialogButton.OK);
        }))
        .exceptionally(ex -> {
          mainWindow.getGui().getGUIThread().invokeLater(() -> {
            setInstallationUIState(false);
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " Installation aborted: " + ex.getMessage()));
            statusLabel.setForegroundColor(Themes.getErrorColor());
            ActivityLogger.err("Modpack installation failed: " + ex.getMessage());
          });
          return null;
        });
  }

  private void cancelInstall() {
    if (isInstalling) {
      setInstallationUIState(false);
      statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " Modpack installation cancelled by user."));
      statusLabel.setForegroundColor(Themes.getLogWarnColor());
      ActivityLogger.warn("Modpack installation cancelled by user.");
    }
  }

  private void setInstallationUIState(boolean installing) {
    this.isInstalling = installing;
    installBtn.setEnabled(!installing);
    searchBtn.setEnabled(!installing);
    cancelBtn.setEnabled(installing);
    downloadPanel.setVisible(installing);

    if (installing) {
      if (activeTicker == null || activeTicker.isShutdown()) {
        activeTicker = Executors.newSingleThreadScheduledExecutor(r -> {
          Thread t = new Thread(r, "ModpackPickaxeTicker");
          t.setDaemon(true);
          return t;
        });
        activeTicker.scheduleAtFixedRate(() -> {
          pickaxeAnim.tick();
          try {
            mainWindow.getGui().getGUIThread().invokeLater(mainWindow::invalidate);
          } catch (Exception ignored) {}
        }, 0, 150, TimeUnit.MILLISECONDS);
      }
    } else {
      if (activeTicker != null) {
        activeTicker.shutdownNow();
        activeTicker = null;
      }
      pickaxeAnim.setProgress(0);
    }
  }

  @Override
  public String getTitle() {
    return GlyphHelper.apply(GlyphHelper.ICON_PACKAGE + " Modpack Browser & Installer");
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
    return searchBox;
  }

  @Override
  public void onActivated() {
    relayoutForCurrentMode();
    checkInstalledServerState();
  }

  private void checkInstalledServerState() {
    org.codeberg.DeployedReject.utils.ServerJarMetadata meta = OrchestratorBridge.getInstalledServerMetadata();
    if (meta == null) {
      statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " [WARN] No server installed! Please install a server engine first [I]."));
      statusLabel.setForegroundColor(Themes.getLogWarnColor());
      ActivityLogger.warn("No Minecraft server is installed. You must install a server engine first from [I] Install Server Engine.");
      installBtn.setEnabled(false);
    } else {
      installBtn.setEnabled(true);
      String sType = meta.getServerType();
      String sVer = meta.getGameVersion();
      if ("fabric".equalsIgnoreCase(sType) || "quilt".equalsIgnoreCase(sType)) {
        loaderBox.setSelectedItem("fabric");
      } else if ("forge".equalsIgnoreCase(sType)) {
        loaderBox.setSelectedItem("forge");
      } else if ("neoforge".equalsIgnoreCase(sType)) {
        loaderBox.setSelectedItem("neoforge");
      }
      if (sVer != null && !sVer.isEmpty() && !"unknown".equalsIgnoreCase(sVer)) {
        MinecraftVersionHelper.setSelectedVersion(versionComboBox, sVer);
      }
      if ("vanilla".equalsIgnoreCase(sType)) {
        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " [WARN] Vanilla server detected (" + sVer + "). Vanilla does not support modpacks."));
        statusLabel.setForegroundColor(Themes.getLogWarnColor());
      } else if ("paper".equalsIgnoreCase(sType) || "spigot".equalsIgnoreCase(sType)) {
        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " [WARN] " + meta.getFormattedTitle() + " detected (Plugins only, modpacks unsupported)."));
        statusLabel.setForegroundColor(Themes.getLogWarnColor());
      } else {
        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " [SERVER] " + meta.getFormattedTitle() + " detected. Ready for modpack installation."));
        statusLabel.setForegroundColor(Themes.getLogSuccessColor());
      }
    }
  }

  @Override
  public void onDeactivated() {
    if (activeTicker != null) {
      activeTicker.shutdownNow();
      activeTicker = null;
    }
  }

  @Override
  public void onResized(TerminalSize newSize) {
    if (newSize == null) return;
    this.currentTermWidth = newSize.getColumns();
    this.currentTermHeight = newSize.getRows();

    int cols = currentTermWidth;
    int rows = currentTermHeight;
    int actWidth = Math.max(28, Math.min(65, (cols * 35) / 100));
    int wsWidth = Math.max(44, cols - actWidth - 6);

    int listWidth = Math.max(26, Math.min(38, (wsWidth * 42) / 100));
    int cardWidth = Math.max(26, wsWidth - listWidth - 4);
    this.descCardWidth = cardWidth;

    int listHeight = Math.max(6, Math.min(14, rows - 16));
    this.descLinesPerPage = Math.max(4, listHeight - 3);

    resultsList.setPreferredSize(new TerminalSize(listWidth, listHeight));
    dependenciesList.setPreferredSize(new TerminalSize(cardWidth - 2, Math.max(3, listHeight / 2)));
    detailsCard.setPreferredSize(new TerminalSize(cardWidth, listHeight + 4));

    updateDetailsDisplay();
  }
}
