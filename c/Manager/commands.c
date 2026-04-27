#include <curses.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>

int svctrl(char *args) {

  char *argsC = strdup(args);
  if (argsC == NULL)
    return 1;

  int size = strlen(args) / 2 + 3;
  char *sargs[size];

  sargs[0] = "./svctrl.sh";
  char *arg = strtok(argsC, " ");

  int j = 1;
  while (arg != NULL) {
    sargs[j++] = arg;
    arg = strtok(NULL, " ");
  }
  sargs[j] = NULL;
  int pid = fork();

  if (pid < 0) {
    perror("Command Initialization failed:");
    return 1;
  } else if (pid == 0) {
    execvp(sargs[0], sargs);
    perror("Command Execution Failed:");
    exit(1);
  } else {
    int status;
    free(argsC);
    waitpid(pid, &status, 0);
    return 0;
  }
}

int sv_StopServer() { return svctrl("stop"); }

int sv_Console(char *command) {
  char args[512];
  strcpy(args, "--mc ");
  strcat(args, command);
  return svctrl(args);
}

int sv_Migrate(char *oldID, char *newID) {
  char args[80];

  int r = snprintf(args, sizeof args, "--migrate %s %s",
    oldID, newID);
  if (r < 0 || r >= sizeof args)
    return 1;

  return svctrl(args);
}
