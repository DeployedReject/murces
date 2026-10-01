#!/usr/bin/bash

SV_DIR=$(cd -- "$(dirname -- "$0")" && pwd)
cd "$SV_DIR"

TXSN_MCSV='mcsv'
TXSN_PLAYIT='playit'
SV_JARFILE='server.jar'
TIMESTAMP=$(date +%T)

print_usage() {
  echo "Usage:
        $0 start [--public/-p]     Start Minecraft (and Playit)
        $0 stop                    Stop Minecraft (and Playit)
        $0 --migrate <old> <new>   Migrate player UUIDs
        $0 -mc <command>           Send command to Minecraft console
        $0 -at <session-name>      Attach to a tmux session
        $0 init-playit             Start playit session only
        $0 --help                  Show this message"

  exit 0
}

init_playit() {
  if tmux has-session -t "$TXSN_PLAYIT" 2>/dev/null; then
    echo "svctrl: tmux session: '$TXSN_PLAYIT' is already running."
    exit 1
  fi

  tmux new-session -d -s "$TXSN_PLAYIT"
  tmux send-keys -t "$TXSN_PLAYIT" "./playit --secret_path ./playit.toml start" C-m
  echo "svctrl: tmux session: '$TXSN_PLAYIT' created."
}

stop_tmux_mc() {
  if tmux has-session -t "$TXSN_MCSV" 2>/dev/null; then
    echo "svctrl: Stopping minecraft server."
    tmux send-keys -t "$TXSN_MCSV" 'stop' C-m
    while pgrep -u "$USER" -f "$SV_JARFILE" >/dev/null; do
      echo -n "."
      sleep 1
    done
    echo -e "\nsvctrl: Minecraft server stopped."
    tmux send-keys -t "$TXSN_MCSV" 'exit' C-m
    echo "svctrl: tmux session: '$TXSN_MCSV' terminated."
  fi
}

migrate_player() {
  local world_name='world'

  local playerdata="${world_name}/playerdata"
  local stats="${world_name}/stats"
  local advancements="${world_name}/advancements"
  local usercache='usercache.json'

  stop_tmux_mc

  tar -cf "migration_bak_$(date +%s).tar" "$playerdata" "$stats" "$advancements" "$usercache"

  local old_name="$2"
  local old_uuid=$(jq -r ".[] | select(.name==\"$old_name\") | .uuid" "$usercache")

  local new_name="$3"
  local new_uuid=$(jq -r ".[] | select(.name==\"$new_name\") | .uuid" "$usercache")

  if [[ -z "$old_name" || -z "$new_name" ]]; then
    echo -e "svctrl: Missing arguments.\nUsage:
			./svctrl.sh --migrate <old-name> <new-name>"
  fi

  if [[ -f "${playerdata}/${old_uuid}.dat" && -f "${playerdata}/${old_uuid}.dat_old" ]]; then
    mv '--force' "${playerdata}/${old_uuid}.dat" "${playerdata}/${new_uuid}.dat"
    mv '--force' "${playerdata}/${old_uuid}.dat_old" "${playerdata}/${new_uuid}.dat_old"

    echo "svctrl: Migrated 'playerdata'."
  else
    echo "svctrl: Files with UUID: '$old_uuid' not found."
    return 1
  fi

  if [ -f "${stats}/${old_uuid}.json" ]; then
    mv '--force' "${stats}/${old_uuid}.json" "${stats}/${new_uuid}.json"

    echo "svctrl: Migrated player stats."
  else
    echo "svctrl: '${stats}/${old_uuid}.json': File not found."
    return 1
  fi

  if [ -f "${advancements}/${old_uuid}.json" ]; then
    mv '--force' "${advancements}/${old_uuid}.json" "${advancements}/${new_uuid}.json"

    echo "svctrl: Migrated player stats."
  else
    echo "svctrl: '${advancements}/${old_uuid}.json': File not found."
    return 1
  fi

  tmux new-session -d -s "$TXSN_MCSV"
  tmux send-keys -t "$TXSN_MCSV" "./sv_start.sh" C-m

  echo "svctrl: tmux session: '$TXSN_MCSV' restarted."

  return 0
}

case $1 in
'start')
  for SESSION in "$TXSN_MCSV"; do
    if tmux has-session -t "$SESSION" 2>/dev/null; then
      echo "svctrl: tmux session: '$SESSION' is already running."
      exit 1
    fi
  done

  if [[ "$2" == '--public' || "$2" == '-p' ]]; then
    init_playit
  fi

  tmux new-session -d -s "$TXSN_MCSV"
  tmux send-keys -t "$TXSN_MCSV" "./sv_start.sh" C-m
  echo "svctrl: tmux session: '$TXSN_MCSV' created."

  echo "svctrl: Use 'tmux ls' to list active sessions."

  exit 0
  ;;

'stop')
  stop_tmux_mc

  if tmux has-session -t "$TXSN_PLAYIT" 2>/dev/null; then
    echo "svctrl: tmux session: '$TXSN_PLAYIT' needs to be closed manually."
    echo "svctrl: Do the following:
				tmux attach -t $TXSN_PLAYIT
				Press Q on your keyboard
				Press Y on your keyboard
				exit"
  fi

  exit 0
  ;;

'-mc' | '--minecraft')
  shift
  MC_CMD="$*"
  if tmux has-session -t "$TXSN_MCSV" 2>/dev/null; then
    tmux send-keys -t "$TXSN_MCSV" "$MC_CMD" C-m

    timeout 30 tail -n 50 -F logs/latest.log | awk -v target="$TIMESTAMP" '
BEGIN { 
    found = 0 
}
{
    if ($0 ~ /^\[[0-9]{2}:[0-9]{2}:[0-9]{2}\]/) {
        log_time = substr($1, 2, 8) 
        
        if (log_time >= target) {
            print $0
            fflush()
            found = 1
            exit 0
        }
    }
}
END {
    if (found == 0) {
        print "Timeout, Command MAYBE suceeded. Check logs"
    }
}'
    exit 0
  else
    echo "svctrl: tmux session: '$TXSN_MCSV' not found."
  fi

  exit 1
  ;;

'-at' | '--attach')
  if tmux has-session -t "$2" 2>/dev/null; then
    tmux attach -t "$2"
    exit 0
  else
    echo "svctrl: tmux session: '$2' not found."
  fi

  exit 1
  ;;

'--help' | 'help' | '-h')
  print_usage
  exit 0
  ;;

'init-playit')
  if ! init_playit; then
    exit 1
  fi
  exit 0
  ;;

'--migrate' | '-mg')
  if ! migrate_player "$@"; then
    echo "svctrl: Migration failed. Either retry or start the server again manually."
    exit 1
  fi
  exit 0
  ;;
*)
  echo "svctrl: Invalid usage."
  print_usage
  exit 0
  ;;

esac

exit 1
