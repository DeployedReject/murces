package org.codeberg.DeployedReject.tui.config;

public class TuiConfig {

    private String theme = "Gruvbox Dark";
    private int transparencyPercent = 0;
    private boolean trueColor = true;
    private boolean enforceMinSize = true;
    private boolean pickaxeAnimation = true;
    private String nerdFontMode = "auto";

    public TuiConfig() {}

    public String getTheme() {
        return theme != null && !theme.trim().isEmpty() ? theme : "Gruvbox Dark";
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public int getTransparencyPercent() {
        return Math.max(0, Math.min(100, transparencyPercent));
    }

    public void setTransparencyPercent(int transparencyPercent) {
        this.transparencyPercent = Math.max(0, Math.min(100, transparencyPercent));
    }

    public boolean isTrueColor() {
        return trueColor;
    }

    public void setTrueColor(boolean trueColor) {
        this.trueColor = trueColor;
    }

    public boolean isEnforceMinSize() {
        return enforceMinSize;
    }

    public void setEnforceMinSize(boolean enforceMinSize) {
        this.enforceMinSize = enforceMinSize;
    }

    public boolean isPickaxeAnimation() {
        return pickaxeAnimation;
    }

    public void setPickaxeAnimation(boolean pickaxeAnimation) {
        this.pickaxeAnimation = pickaxeAnimation;
    }

    public String getNerdFontMode() {
        return nerdFontMode != null && !nerdFontMode.trim().isEmpty() ? nerdFontMode : "auto";
    }

    public void setNerdFontMode(String nerdFontMode) {
        this.nerdFontMode = nerdFontMode;
    }

    private String backupSourceFolder = "world";
    private String backupTargetFolder = "backup";
    private int backupRetentionLimit = 3;
    private boolean backupCloudSync = false;
    private String backupCloudRemote = "minecraftdrive";

    public String getBackupSourceFolder() {
        return backupSourceFolder != null && !backupSourceFolder.trim().isEmpty() ? backupSourceFolder.trim() : "world";
    }

    public void setBackupSourceFolder(String backupSourceFolder) {
        this.backupSourceFolder = backupSourceFolder != null && !backupSourceFolder.trim().isEmpty() ? backupSourceFolder.trim() : "world";
    }

    public String getBackupTargetFolder() {
        return backupTargetFolder != null && !backupTargetFolder.trim().isEmpty() ? backupTargetFolder.trim() : "backup";
    }

    public void setBackupTargetFolder(String backupTargetFolder) {
        this.backupTargetFolder = backupTargetFolder != null && !backupTargetFolder.trim().isEmpty() ? backupTargetFolder.trim() : "backup";
    }

    public int getBackupRetentionLimit() {
        return backupRetentionLimit > 0 ? backupRetentionLimit : 3;
    }

    public void setBackupRetentionLimit(int backupRetentionLimit) {
        this.backupRetentionLimit = backupRetentionLimit > 0 ? backupRetentionLimit : 3;
    }

    public boolean isBackupCloudSync() {
        return backupCloudSync;
    }

    public void setBackupCloudSync(boolean backupCloudSync) {
        this.backupCloudSync = backupCloudSync;
    }

    public String getBackupCloudRemote() {
        return backupCloudRemote != null && !backupCloudRemote.trim().isEmpty() ? backupCloudRemote.trim() : "minecraftdrive";
    }

    public void setBackupCloudRemote(String backupCloudRemote) {
        this.backupCloudRemote = backupCloudRemote != null && !backupCloudRemote.trim().isEmpty() ? backupCloudRemote.trim() : "minecraftdrive";
    }
}
