#include "log.h"
#include <stdlib.h>
#include <string.h>

int current_log_line = 2;

char *logMurces(const char *prefix, char *const array[]) {
  if (!prefix) {
    prefix = "";
  }

  size_t prefix_len = strlen(prefix);
  size_t total_len = prefix_len;

  if (array) {
    for (int i = 0; array[i] != NULL; i++) {
      total_len += strlen(array[i]);
    }
  }

  char *result = malloc(total_len + 1);
  if (!result) {
    return NULL;
  }

  strcpy(result, prefix);
  char *dest = result + prefix_len;

  if (array) {
    for (int i = 0; array[i] != NULL; i++) {
      size_t len = strlen(array[i]);
      memcpy(dest, array[i], len);
      dest += len;
    }
  }
  *dest = '\0';

  return result;
}
