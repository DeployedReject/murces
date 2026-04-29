#include <dirent.h>
#include <stdlib.h>
#include <string.h>

#define MAX_STATIC_MOD_COUNT (256U)

int getMods(char ***modfiles) {
  if (modfiles == NULL)
    return 1;

  struct dirent *file;
  DIR *mods = opendir("./mods");
  if (mods == NULL)
    return 1;

  char *mdfls[MAX_STATIC_MOD_COUNT];
  char **sbuf = NULL;

  char **cl = mdfls;

  int mcnt = 0;
  char onheap = 0;

  while ((file = readdir(mods))) {
    if (mcnt >= MAX_STATIC_MOD_COUNT) {
      if (!onheap) {
        cl = (char **)malloc((mcnt + 5) * sizeof(char *));
        if (sbuf == NULL) {
          closedir(mods);
          return 1;
        }

        if (memcpy(cl, mdfls, mcnt * sizeof(char *)) == NULL) {
          closedir(mods);
          return 1;
        }
        onheap = 1;
      } else {
        if (realloc(cl, (mcnt + 5) * sizeof(char *)) == NULL) {
          if (cl != NULL)
            free(cl);
          closedir(mods);
          return 1;
        }
      }
    }
    cl[mcnt] = strdup(file->d_name);
    if (cl[mcnt++] == NULL) {
      if (cl != NULL)
        free(cl);
      closedir(mods);
      return 1;
    }
  }

  if (onheap)
    *modfiles = cl;
  else {
    char **fheap = (char **)malloc(mcnt * sizeof(char *));
    if (fheap == NULL) {
      if (cl != NULL)
        free(cl);
      closedir(mods);
      return 1;
    }

    if (memcpy(fheap, mdfls, mcnt * sizeof(char *)) == NULL) {
      if (cl != NULL)
        free(cl);
      closedir(mods);
      return 1;
    }
    *modfiles = fheap;
  }

  closedir(mods);

  return mcnt;
}
