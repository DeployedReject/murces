#include <stdbool.h>
#include <stdio.h>
#include <string.h>

int setProperty(char *property, char *value) {
  FILE *serverProperties = fopen("server.properties", "r");
  FILE *fileBuffer = fopen("server.properties.temp", "w");

  if (serverProperties == NULL || fileBuffer == NULL || value == NULL)
    return 1;

  bool flag = false;

  char buffer[512];
  char *prop;
  char *temp;

  while (fgets(buffer, 512, serverProperties) != NULL) {

    prop = strtok(buffer, "=");
    if (prop[0] == '#')
      continue;

    if (!strcmp(prop, property)) {
      fprintf(fileBuffer, "%s=%s\n", prop, value);
      flag = true;
    } else {
      fprintf(fileBuffer, "%s=%s\n", prop, strtok(NULL, "="));
    }
  }

  fclose(serverProperties);
  fclose(fileBuffer);

  if (!flag)
    return 1;

  if (remove("server.properties"))
    return 1;
  if (rename("server.properties.temp", "server.properties"))
    return 1;

  return 0;
}
