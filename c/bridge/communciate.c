#include "communciate.h"
#include "cJSON.h"
#include "router.h"
#include <iso646.h>
#include <log.h>
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

char **modLoader(const char *version) {

  for (int i = 0; i < 3; i++) {
    if (IOP[i] == -1)
      return NULL;
  }

  cJSON *temp = cJSON_CreateObject();
  cJSON_AddStringToObject(temp, "type", "server");
  cJSON_AddStringToObject(temp, "serverType", "none");
  cJSON_AddStringToObject(temp, "gameVersion", version);
  cJSON_AddStringToObject(temp, "loaderVersion", "none");
  cJSON_AddNumberToObject(temp, "ram", 0);
  cJSON_AddNumberToObject(temp, "job", 3);

  char *request = cJSON_PrintUnformatted(temp);
  cJSON_Delete(temp);

  if (!request)
    return NULL;

  dprintf(IOP[0], "%s\n", request);
  free(request);

  cJSON *response = get_server();

  if (!response)
    return NULL;

  cJSON *statusItem = cJSON_GetObjectItem(response, "status");
  int status = statusItem ? statusItem->valueint : 0;
  if (status != 0) {
    cJSON_Delete(response);
    return NULL;
  }

  cJSON *serverList = cJSON_GetObjectItem(response, "serverList");
  if (!serverList) {
    cJSON_Delete(response);
    return NULL;
  }

  int count = cJSON_GetArraySize(serverList);

  char **results = malloc((count + 1) * sizeof(char *));
  if (!results) {
    cJSON_Delete(response);
    return NULL;
  }

  for (int i = 0; i < count; i++) {
    cJSON *item = cJSON_GetArrayItem(serverList, i);
    results[i] = strdup(item->valuestring);
  }
  results[count] = NULL;

  cJSON_Delete(response);

  mm_insert_text(mtstdlogwin, COLOR_PAIR(CPID_OK_MSG),
                 logMurces("OK:", results), 4, 4, 2);
  return results;
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
