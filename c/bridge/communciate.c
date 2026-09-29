#include "communciate.h"
#include "cJSON.h"
#include "router.h"
#include <curses.h>
#include <iso646.h>
#include <log.h>
#include <prc/prc_window.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <tui.h>
#include <unistd.h>

int IOP[3] = {-1, -1, -1};

int *init_orchestrator(int mode) {
  if (IOP[2] != -1)
    return NULL;

  if (mode == 0) {
    if (access("./murces-orchestrator", X_OK) == 0) {
      IOP[0] = -1;
      IOP[1] = -1;
      return IOP;
    } else {
      return NULL;
    }

    return 0;
  }

  int orcInput[2];
  int orcOutput[2];

  if (pipe(orcInput) == -1 || pipe(orcOutput) == -1) {
    perror("Pipe failed");
    return NULL;
  }

  IOP[2] = fork();

  if (IOP[2] == 0) {
    dup2(orcInput[0], STDIN_FILENO);
    dup2(orcOutput[1], STDOUT_FILENO);
    execlp("./murces-orchestrator", "./murces-orchestrator", NULL);
    perror("execlp failed");
    exit(1);
  } else if (IOP[2] > 0) {
    close(orcInput[0]);
    close(orcOutput[1]);
    IOP[0] = orcInput[1];
    IOP[1] = orcOutput[0];
    return IOP;
  } else {
    perror("fork failed");
    return NULL;
  }
}

char **search(const char *modBrowser, const char *query, const char *version,
              const char *modLoader) {

  for (int i = 0; i < 3; i++) {
    if (IOP[i] == -1)
      return NULL;
  }

  cJSON *temp = cJSON_CreateObject();
  cJSON_AddStringToObject(temp, "type", "modding");
  cJSON_AddStringToObject(temp, "modBrowser", modBrowser);
  cJSON_AddStringToObject(temp, "subType", "search");
  cJSON_AddStringToObject(temp, "modName", query);
  cJSON_AddStringToObject(temp, "version", version);
  cJSON_AddStringToObject(temp, "loader", modLoader);
  cJSON_AddStringToObject(temp, "modId", "");

  char *request = cJSON_PrintUnformatted(temp);
  cJSON_Delete(temp);

  if (!request)
    return NULL;

  dprintf(IOP[0], "%s\n", request);
  free(request);

  cJSON *response = get_search();

  if (!response)
    return NULL;

  int status = cJSON_GetObjectItem(response, "status")->valueint;
  if (status == 1)
    return NULL;

  cJSON *mods = cJSON_GetObjectItem(response, "mods");
  int count = cJSON_GetArraySize(mods);

  char **results = malloc((count + 1) * sizeof(char *));
  if (!results) {
    cJSON_Delete(response);
    return NULL;
  }

  for (int i = 0; i < count; i++) {
    cJSON *mod = cJSON_GetArrayItem(mods, i);
    if (cJSON_IsArray(mod) && cJSON_GetArraySize(mod) >= 2) {
      cJSON *title = cJSON_GetArrayItem(mod, 1);
      results[i] = strdup(title->valuestring);
    }
  }
  results[count] = NULL;

  cJSON_Delete(response);

  mm_insert_text(mtstdlogwin, COLOR_PAIR(CPID_OK_MSG),
                 logMurces("OK:", results), 4, 4, 2);
  return results;
}

char **download(const char *modBrowser, const char *modName,
                const char *version, const char *modLoader, const char *modId) {
  for (int i = 0; i < 3; i++) {
    if (IOP[i] == -1)
      return NULL;
  }

  cJSON *temp = cJSON_CreateObject();
  cJSON_AddStringToObject(temp, "type", "modding");
  cJSON_AddStringToObject(temp, "modBrowser", modBrowser);
  cJSON_AddStringToObject(temp, "subType", "download");
  cJSON_AddStringToObject(temp, "modName", modName ? modName : "none");
  cJSON_AddStringToObject(temp, "version", version);
  cJSON_AddStringToObject(temp, "loader", modLoader);
  cJSON_AddStringToObject(temp, "modId", modId ? modId : "0");

  char *request = cJSON_PrintUnformatted(temp);
  cJSON_Delete(temp);

  if (!request)
    return NULL;

  dprintf(IOP[0], "%s\n", request);
  free(request);

  char current_filename[256] = "mod";

  while (1) {
    cJSON *response = get_download();
    if (!response)
      break;

    cJSON *idItem = cJSON_GetObjectItem(response, "id");
    if (idItem && idItem->valuestring) {
      const char *slash = strrchr(idItem->valuestring, '/');
      strncpy(current_filename, slash ? (slash + 1) : idItem->valuestring,
              sizeof(current_filename) - 1);
      current_filename[sizeof(current_filename) - 1] = '\0';
    }

    cJSON *statusItem = cJSON_GetObjectItem(response, "status");
    int status = statusItem ? statusItem->valueint : -1;

    char *logged_msg = NULL;
    short color_pair = 0;

    if (status == 2) {
      char msg_buf[320];
      snprintf(msg_buf, sizeof(msg_buf), "Download started: %s",
               current_filename);
      char *msg_arr[2] = {msg_buf, NULL};
      logged_msg = logMurces("WRN:", msg_arr);
      color_pair = COLOR_PAIR(CPID_WRN_MSG);
    } else if (status == 0) {
      cJSON *progressItem = cJSON_GetObjectItem(response, "progress");
      double progress = progressItem ? progressItem->valuedouble : 0.0;
      char prog_str[320];
      if (progress < 0) {
        snprintf(prog_str, sizeof(prog_str),
                 "Downloading %s... Progress: Unknown", current_filename);
      } else {
        snprintf(prog_str, sizeof(prog_str),
                 "Downloading %s... Progress: %.1f%%", current_filename,
                 progress);
      }
      char *msg_arr[2] = {prog_str, NULL};
      logged_msg = logMurces("WRN:", msg_arr);
      color_pair = COLOR_PAIR(CPID_WRN_MSG);
    } else if (status == 3) {
      char msg_buf[320];
      snprintf(msg_buf, sizeof(msg_buf), "Download complete: %s",
               current_filename);
      char *msg_arr[2] = {msg_buf, NULL};
      logged_msg = logMurces("OK:", msg_arr);
      color_pair = COLOR_PAIR(CPID_OK_MSG);
    } else if (status == 1) {
      cJSON *errorItem = cJSON_GetObjectItem(response, "error");
      char *error_str = errorItem ? errorItem->valuestring : "Unknown error";
      char msg_buf[512];
      snprintf(msg_buf, sizeof(msg_buf), "%s: %s", current_filename, error_str);
      char *msg_arr[2] = {msg_buf, NULL};
      logged_msg = logMurces("ERR:", msg_arr);
      color_pair = COLOR_PAIR(CPID_ERR_MSG);
    }

    if (logged_msg) {
      if (mtstdlogwin && mtstdlogwin->win) {
        if (current_log_line > mtstdlogwin->height - 2) {
          werase(mtstdlogwin->win);
          prc_draw_window_border(mtstdlogwin);
          current_log_line = 2;
          wnoutrefresh(mtstdlogwin->win);
          doupdate();
        }
        mm_insert_text(mtstdlogwin, color_pair, logged_msg, 4, 4,
                       current_log_line);
        current_log_line++;
        wnoutrefresh(mtstdlogwin->win);
        doupdate();
      } else {
        printf("%s\n", logged_msg);
      }
      free(logged_msg);
    }

    if (status == 3 || status == 1) {
      cJSON_Delete(response);
      break;
    }
    cJSON_Delete(response);
  }

  return NULL;
}

char **modLoader(const char *version) {

  for (int i = 0; i < 3; i++) {
    if (IOP[i] == -1)
      return NULL;
  }

  cJSON *temp = cJSON_CreateObject();
  cJSON_AddStringToObject(temp, "type", "query");
  cJSON_AddStringToObject(temp, "version", version);
  cJSON_AddStringToObject(temp, "subType", "modloader");
  char *request = cJSON_PrintUnformatted(temp);
  cJSON_Delete(temp);

  if (!request)
    return NULL;

  dprintf(IOP[0], "%s\n", request);
  free(request);

  cJSON *response = get_search();
  if (!response)
    return NULL;

  cJSON *modsArray = cJSON_GetObjectItem(response, "mods");
  if (!modsArray || !cJSON_IsArray(modsArray)) {
    cJSON_Delete(response);
    return NULL;
  }

  int count = cJSON_GetArraySize(modsArray);
  char **results = malloc((count + 1) * sizeof(char *));
  if (!results) {
    cJSON_Delete(response);
    return NULL;
  }

  for (int i = 0; i < count; i++) {
    cJSON *item = cJSON_GetArrayItem(modsArray, i);
    results[i] = item->valuestring ? strdup(item->valuestring) : strdup("");
  }
  results[count] = NULL;

  cJSON_Delete(response);

  mm_insert_text(mtstdlogwin, COLOR_PAIR(CPID_OK_MSG),
                 logMurces("OK:", results), 4, 4, 2);
  return results;
}

int server(const char *serverType, const char *gameVersion, const char *loaderVersion,
           int ram, int job) {
  for (int i = 0; i < 3; i++) {
    if (IOP[i] == -1)
      return 1;
  }

  cJSON *temp = cJSON_CreateObject();
  cJSON_AddStringToObject(temp, "type", "server");
  cJSON_AddStringToObject(temp, "serverType", serverType ? serverType : "vanilla");
  cJSON_AddStringToObject(temp, "gameVersion", gameVersion ? gameVersion : "1.20.4");
  cJSON_AddStringToObject(temp, "loaderVersion", loaderVersion ? loaderVersion : "none");
  cJSON_AddNumberToObject(temp, "ram", ram);
  cJSON_AddNumberToObject(temp, "job", job);

  char *request = cJSON_PrintUnformatted(temp);
  cJSON_Delete(temp);

  if (!request)
    return 1;

  dprintf(IOP[0], "%s\n", request);
  free(request);

  char current_filename[256] = "server";
  int return_code = 0;

  if (job == 0 || job == 1) {
    while (1) {
      cJSON *response = get_download();
      if (!response) break;

      cJSON *idItem = cJSON_GetObjectItem(response, "id");
      if (idItem && idItem->valuestring) {
        const char *slash = strrchr(idItem->valuestring, '/');
        strncpy(current_filename, slash ? (slash + 1) : idItem->valuestring, sizeof(current_filename) - 1);
        current_filename[sizeof(current_filename) - 1] = '\0';
      }

      cJSON *statusItem = cJSON_GetObjectItem(response, "status");
      int status = statusItem ? statusItem->valueint : -1;
      char *logged_msg = NULL;
      short color_pair = 0;

      if (status == 2) {
        char msg_buf[320];
        snprintf(msg_buf, sizeof(msg_buf), "Server download started: %s", current_filename);
        char *msg_arr[2] = {msg_buf, NULL};
        logged_msg = logMurces("WRN:", msg_arr);
        color_pair = COLOR_PAIR(CPID_WRN_MSG);
      } else if (status == 0) {
        cJSON *progressItem = cJSON_GetObjectItem(response, "progress");
        double progress = progressItem ? progressItem->valuedouble : 0.0;
        char prog_str[320];
        if (progress < 0) {
          snprintf(prog_str, sizeof(prog_str), "Downloading %s... Progress: Unknown", current_filename);
        } else {
          snprintf(prog_str, sizeof(prog_str), "Downloading %s... Progress: %.1f%%", current_filename, progress);
        }
        char *msg_arr[2] = {prog_str, NULL};
        logged_msg = logMurces("WRN:", msg_arr);
        color_pair = COLOR_PAIR(CPID_WRN_MSG);
      } else if (status == 3) {
        char msg_buf[320];
        snprintf(msg_buf, sizeof(msg_buf), "Server download complete: %s", current_filename);
        char *msg_arr[2] = {msg_buf, NULL};
        logged_msg = logMurces("OK:", msg_arr);
        color_pair = COLOR_PAIR(CPID_OK_MSG);
      } else if (status == 1) {
        cJSON *errorItem = cJSON_GetObjectItem(response, "error");
        char msg_buf[512];
        snprintf(msg_buf, sizeof(msg_buf), "%s: %s", current_filename, errorItem ? errorItem->valuestring : "Unknown error");
        char *msg_arr[2] = {msg_buf, NULL};
        logged_msg = logMurces("ERR:", msg_arr);
        color_pair = COLOR_PAIR(CPID_ERR_MSG);
        return_code = 1;
      }

      if (logged_msg) {
        if (mtstdlogwin && mtstdlogwin->win) {
          if (current_log_line > mtstdlogwin->height - 2) {
            werase(mtstdlogwin->win);
            prc_draw_window_border(mtstdlogwin);
            current_log_line = 2;
            wnoutrefresh(mtstdlogwin->win);
            doupdate();
          }
          mm_insert_text(mtstdlogwin, color_pair, logged_msg, 4, 4, current_log_line++);
          wnoutrefresh(mtstdlogwin->win);
          doupdate();
        } else {
          printf("%s\n", logged_msg);
        }
        free(logged_msg);
      }

      int done = (status == 3 || status == 1);
      cJSON_Delete(response);
      if (done) break;
    }
  }

  if (job == 1 || job == 2) {
    while (1) {
      cJSON *response = get_server();
      if (!response) break;

      cJSON *statusItem = cJSON_GetObjectItem(response, "status");
      int status = statusItem ? statusItem->valueint : -1;
      char *logged_msg = NULL;
      short color_pair = 0;

      if (status == 2) {
        char *msg_arr[2] = {"Server operation started.", NULL};
        logged_msg = logMurces("WRN:", msg_arr);
        color_pair = COLOR_PAIR(CPID_WRN_MSG);
      } else if (status == 0) {
        if (job == 2) {
          char *msg_arr[2] = {"Server stopped.", NULL};
          logged_msg = logMurces("OK:", msg_arr);
          color_pair = COLOR_PAIR(CPID_OK_MSG);
          status = 3;
        }
      } else if (status == 3) {
        char *msg_arr[2] = {"Server operation complete.", NULL};
        logged_msg = logMurces("OK:", msg_arr);
        color_pair = COLOR_PAIR(CPID_OK_MSG);
      } else if (status == 1) {
        cJSON *errorItem = cJSON_GetObjectItem(response, "error");
        char msg_buf[512];
        snprintf(msg_buf, sizeof(msg_buf), "Server error: %s", errorItem ? errorItem->valuestring : "Unknown error");
        char *msg_arr[2] = {msg_buf, NULL};
        logged_msg = logMurces("ERR:", msg_arr);
        color_pair = COLOR_PAIR(CPID_ERR_MSG);
        return_code = 1;
      }

      if (logged_msg) {
        if (mtstdlogwin && mtstdlogwin->win) {
          if (current_log_line > mtstdlogwin->height - 2) {
            werase(mtstdlogwin->win);
            prc_draw_window_border(mtstdlogwin);
            current_log_line = 2;
            wnoutrefresh(mtstdlogwin->win);
            doupdate();
          }
          mm_insert_text(mtstdlogwin, color_pair, logged_msg, 4, 4, current_log_line++);
          wnoutrefresh(mtstdlogwin->win);
          doupdate();
        } else {
          printf("%s\n", logged_msg);
        }
        free(logged_msg);
      }

      int done = (status == 3 || status == 1);
      cJSON_Delete(response);
      if (done) break;
    }
  }

  return return_code;
}

void cleanup_orchestrator() {
  if (IOP[2] != -1) {
    cJSON *kill_req = cJSON_CreateObject();
    cJSON_AddStringToObject(kill_req, "type", "kill");
    char *kill_str = cJSON_PrintUnformatted(kill_req);
    if (kill_str) {
      dprintf(IOP[0], "%s\n", kill_str);
      free(kill_str);
    }
    cJSON_Delete(kill_req);

    int status;
    waitpid(IOP[2], &status, 0);

    IOP[0] = IOP[1] = IOP[2] = -1;
  }
}
