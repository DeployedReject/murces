#!/bin/bash
echo "eula=true" > eula.txt

RAM="${1:-4G}"
if [[ "$RAM" =~ ^[0-9]+$ ]]; then
  RAM="${RAM}G"
fi

# Immediately terminate the tmux session when the Minecraft server exits or crashes
trap 'tmux kill-session -t mcsv 2>/dev/null; tmux kill-session -t mcServer 2>/dev/null; tmux kill-session 2>/dev/null || true' EXIT

if [ -f "run.sh" ]; then
  chmod +x run.sh
  ./run.sh nogui
  exit $?
fi

TARGET_JAR="server.jar"
if [ -f "fabric-server-launch.jar" ]; then
  TARGET_JAR="fabric-server-launch.jar"
fi

java -Xms"$RAM" -Xmx"$RAM" \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -XX:+AlwaysPreTouch \
  -XX:G1NewSizePercent=30 \
  -XX:G1MaxNewSizePercent=40 \
  -XX:G1HeapRegionSize=8M \
  -XX:G1ReservePercent=20 \
  -XX:G1HeapWastePercent=5 \
  -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=15 \
  -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 \
  -XX:+PerfDisableSharedMem \
  -XX:MaxTenuringThreshold=1 \
  -Dusing.aikars.flags=https://mcflags.emc.gs \
  -Daikars.new.flags=true \
  -jar "$TARGET_JAR" nogui


