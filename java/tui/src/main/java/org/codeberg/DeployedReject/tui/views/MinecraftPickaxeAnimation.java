package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import org.codeberg.DeployedReject.tui.theme.GlyphHelper;
import org.codeberg.DeployedReject.tui.theme.LazyVimTheme;

/**
 * A Minecraft-themed loading animation rendering an advancing pickaxe on the
 * same row as the dirt blocks. The pickaxe moves forward across the track as
 * each block is broken, leaving a cleared tunnel behind it.
 */
public class MinecraftPickaxeAnimation extends AbstractComponent<MinecraftPickaxeAnimation> {

  private static final TextColor DIRT_BROWN = new TextColor.RGB(133, 82, 43);
  private static final TextColor CRACK_GRAY = new TextColor.RGB(170, 170, 170);
  private static final TextColor DIAMOND_TOOL = new TextColor.RGB(85, 255, 255);
  private static final TextColor PARTICLE_GOLD = new TextColor.RGB(255, 170, 0);

  private double progress = -1; // -1 for looping indeterminate, 0..100 for progress-bound
  private int tick = 0;
  private String customMessage = null;

  public MinecraftPickaxeAnimation() {
  }

  public synchronized void setProgress(double progress) {
    this.progress = progress;
    this.tick++;
    invalidate();
  }

  public synchronized double getProgress() {
    return progress;
  }

  public synchronized void tick() {
    this.tick++;
    invalidate();
  }

  public synchronized void setCustomMessage(String customMessage) {
    this.customMessage = customMessage;
    invalidate();
  }

  @Override
  protected ComponentRenderer<MinecraftPickaxeAnimation> createDefaultRenderer() {
    return new ComponentRenderer<>() {
      @Override
      public TerminalSize getPreferredSize(MinecraftPickaxeAnimation component) {
        return new TerminalSize(36, 2);
      }

      @Override
      public void drawComponent(TextGUIGraphics graphics, MinecraftPickaxeAnimation comp) {
        TerminalSize size = graphics.getSize();
        int width = Math.max(30, size.getColumns());
        int height = Math.max(1, size.getRows());

        graphics.setBackgroundColor(LazyVimTheme.getLogBackgroundColor());
        graphics.fill(' ');

        double effectiveProgress;
        int currentTick;
        String msg;

        synchronized (comp) {
          currentTick = comp.tick;
          msg = comp.customMessage;
          if (comp.progress < 0) {
            effectiveProgress = (currentTick * 3) % 101;
          } else {
            effectiveProgress = Math.max(0, Math.min(100, comp.progress));
          }
        }

        int slotWidth = 5; // "[██] " or " ⛏> "
        int totalSlots = Math.max(4, width / slotWidth);
        int totalBlocks = totalSlots - 1; // 1 slot allocated for the advancing pickaxe

        double blockProgress = (effectiveProgress / 100.0) * totalBlocks;
        int activeBlock = Math.min(totalBlocks - 1, (int) blockProgress);
        double subProgress = blockProgress - activeBlock;
        boolean isComplete = (effectiveProgress >= 100.0);

        int pickSlot = isComplete ? (totalBlocks - 1) : activeBlock;
        int animRow = 0;

        String toolIcon = GlyphHelper.isNerdFontEnabled() ? GlyphHelper.ICON_TOOL : "/";
        int swingFrame = Math.abs(currentTick) % 3;
        String swingSuffix;
        TextColor swingColor;

        if (isComplete) {
          swingSuffix = " ";
          swingColor = DIAMOND_TOOL;
        } else if (swingFrame == 0) {
          swingSuffix = "\\";
          swingColor = LazyVimTheme.getAccentColor();
        } else if (swingFrame == 1) {
          swingSuffix = ">";
          swingColor = LazyVimTheme.getAccentColor();
        } else {
          swingSuffix = "*";
          swingColor = PARTICLE_GOLD;
        }

        // Render slots: Mined Tunnel -> Pickaxe -> Active Block -> Remaining Blocks
        for (int slot = 0; slot < totalSlots; slot++) {
          int col = slot * slotWidth;
          if (col + 4 > width) {
            break;
          }

          if (isComplete) {
            if (slot < totalBlocks - 1) {
              graphics.setForegroundColor(LazyVimTheme.getMutedColor());
              graphics.putString(col, animRow, "  ·  ");
            } else if (slot == totalBlocks - 1) {
              graphics.setForegroundColor(DIAMOND_TOOL);
              graphics.putString(col + 1, animRow, toolIcon);
            } else {
              graphics.setForegroundColor(DIAMOND_TOOL);
              graphics.putString(col, animRow, "[◆] ");
            }
          } else {
            if (slot < pickSlot) {
              graphics.setForegroundColor(LazyVimTheme.getMutedColor());
              graphics.putString(col, animRow, "  ·  ");
            } else if (slot == pickSlot) {
              graphics.setForegroundColor(DIAMOND_TOOL);
              graphics.putString(col + 1, animRow, toolIcon);
              graphics.setForegroundColor(swingColor);
              graphics.putString(col + 2, animRow, swingSuffix);
            } else if (slot == pickSlot + 1) {
              graphics.setForegroundColor(DIRT_BROWN);
              graphics.putString(col, animRow, "[");
              graphics.putString(col + 3, animRow, "]");

              if (subProgress < 0.25) {
                graphics.setForegroundColor(DIRT_BROWN);
                graphics.putString(col + 1, animRow, "██");
              } else if (subProgress < 0.50) {
                graphics.setForegroundColor(CRACK_GRAY);
                graphics.putString(col + 1, animRow, "▓▓");
              } else if (subProgress < 0.75) {
                graphics.setForegroundColor(CRACK_GRAY);
                graphics.putString(col + 1, animRow, "▒▒");
              } else if (subProgress < 0.95) {
                graphics.setForegroundColor(CRACK_GRAY);
                graphics.putString(col + 1, animRow, "░░");
              } else {
                graphics.setForegroundColor(PARTICLE_GOLD);
                graphics.putString(col, animRow, "*░ *");
              }
            } else {
              graphics.setForegroundColor(DIRT_BROWN);
              graphics.putString(col, animRow, "[██]");
            }
          }
        }

        // Row 1: Status message and progress info
        if (height >= 2) {
          int textRow = height - 1;
          graphics.setForegroundColor(LazyVimTheme.getActivePalette().fg);
          String display;

          if (msg != null && !msg.isEmpty()) {
            display = GlyphHelper.apply(msg);
          } else if (isComplete) {
            display = String.format("Mining complete! 100.00%% [%d/%d blocks cleared]", totalBlocks, totalBlocks);
          } else {
            display = String.format("Mining... %5.2f%% [Block %d/%d]", effectiveProgress, activeBlock + 1, totalBlocks);
          }

          if (display.length() > width) {
            display = display.substring(0, width);
          }
          graphics.putString(0, textRow, display);
        }
      }
    };
  }
}
