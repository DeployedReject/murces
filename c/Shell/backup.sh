#!/bin/bash

# --- Variables ---
SOURCE_FOLDER="world"
TARGET_FOLDER="backup"
LOG_FILE="backup.log"
STATUS_FILE="backup.status.log"
TIMESTAMP=$(date "+%Y-%m-%d_%H-%M-%S")

# --- Main Logic ---
trap './svctrl.sh -mc save-on' EXIT

# Check if source exists
if [ -d "$SOURCE_FOLDER" ]; then
  echo "Success: The folder '$SOURCE_FOLDER' exists in the current directory." | tee -a "$LOG_FILE" | tee "$STATUS_FILE"

  # Prevent race condition
  ./svctrl.sh -mc save-all
  ./svctrl.sh -mc save-off
  echo "Save Done" | tee -a "$LOG_FILE" | tee -a "$STATUS_FILE"
  # Ensure target folder exists
  mkdir -p "$TARGET_FOLDER"

  # Bundle the folder into a tarball WITHOUT compression (-cf instead of -czf)
  tar -cf "$TARGET_FOLDER/$TIMESTAMP.tar" "$SOURCE_FOLDER"
  ./svctrl.sh -mc save-on
  echo "Bundling Done" | tee -a "$LOG_FILE" | tee -a "$STATUS_FILE"
  # Cleanup old backups (Keep only the 3 newest)
  COUNT=$(ls -1 "$TARGET_FOLDER"/*.tar 2>/dev/null | wc -l)
  if [ "$COUNT" -gt 3 ]; then
    OLDEST=$(ls -1 "$TARGET_FOLDER"/*.tar 2>/dev/null | sort | head -n 1)
    rm -f "$OLDEST"
    echo "Deleted $OLDEST (Total: $COUNT)" | tee -a "$LOG_FILE" | tee -a "$STATUS_FILE"
  fi

  # Sync to Google Drive

  echo "Upload Started" | tee -a "$LOG_FILE" | tee -a "$STATUS_FILE"
  rclone sync "$TARGET_FOLDER" minecraftdrive:"$TARGET_FOLDER"

  echo "Upload Done" | tee -a "$LOG_FILE" | tee -a "$STATUS_FILE"
else
  echo "Error: Could not find a folder named '$SOURCE_FOLDER'." >>"$LOG_FILE"
  echo "Current directory contents:"
  ls -F
  exit 1
fi

exit 0
