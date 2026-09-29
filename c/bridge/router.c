#include "cJSON.h"
#include "log.h"
#include "tui.h"
#include <curses.h>
#include <poll.h>
#include <semaphore.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <unistd.h>

#define POOL_SIZE 256

static short flag = 0;

typedef struct {
  cJSON *pool[POOL_SIZE];
  int head;
  int tail;
  sem_t sem;
} responseQueue;

static responseQueue qSearch, qDownload, qServer;
// This is a lock to stop the reading and writing to the queue at the same time.
static sem_t semSuper;

void initQueue(responseQueue *q) {
  q->head = 0;
  q->tail = 0;
  // So this is a sephamore to make the read function wait if the queue is
  // empty.
  sem_init(&q->sem, 0, 0);
}

void enqueue(responseQueue *q, cJSON *response) {
  int next_tail = (q->tail + 1) % POOL_SIZE;
  if (next_tail == q->head) {
    cJSON_Delete(q->pool[q->head]);
    q->head = (q->head + 1) % POOL_SIZE;
    sem_trywait(&q->sem);
  }
  q->pool[q->tail] = response;
  q->tail = next_tail;
  sem_post(&q->sem);
}

cJSON *dequeue(responseQueue *q) {
  sem_wait(&q->sem);

  sem_wait(&semSuper);
  cJSON *response = NULL;
  if (q->head != q->tail) {
    response = q->pool[q->head];
    q->head = (q->head + 1) % POOL_SIZE;
  }
  sem_post(&semSuper);

  return response;
}
short ready = 0;

void *initRouter(void *temp) {

  int *IOP = (int *)temp;

  struct pollfd fds[1];
  fds[0].fd = IOP[1];
  fds[0].events = POLLIN;

  sem_init(&semSuper, 0, 1);
  initQueue(&qSearch);
  initQueue(&qDownload);
  initQueue(&qServer);

  char accum[32768];
  int accum_len = 0;
  accum[0] = '\0';

  while (flag) {
    int ret = poll(fds, 1, 100);
    if (ret <= 0)
      continue;

    sem_wait(&semSuper);

    if (fds[0].revents & POLLIN) {
      ssize_t bytes_read =
          read(IOP[1], accum + accum_len, sizeof(accum) - accum_len - 1);
      if (bytes_read <= 0) {
        sem_post(&semSuper);
        break;
      }
      accum_len += bytes_read;
      accum[accum_len] = '\0';

      int start = 0;
      for (int i = 0; i < accum_len; i++) {
        if (accum[i] == '\n') {
          accum[i] = '\0';
          char *line = accum + start;

          if (strlen(line) > 0) {
            cJSON *response = cJSON_Parse(line);
            if (response) {
              cJSON *statusItem = cJSON_GetObjectItem(response, "status");
              int status = statusItem ? statusItem->valueint : 0;

              if (status == 10)
                ready = 1;
              else if (status == 1) {
                cJSON *errorItem = cJSON_GetObjectItem(response, "error");
                char *error_str =
                    errorItem ? errorItem->valuestring : "Unknown error";
                char *err_array[2] = {error_str, NULL};
                char *logged_err = logMurces("ERR:", err_array);
                if (logged_err) {
                  mm_insert_text(mtstdlogwin, COLOR_PAIR(CPID_ERR_MSG),
                                 logged_err, 4, 4, 2);
                  free(logged_err);
                }
                enqueue(&qSearch, response);
              } else {
                cJSON *typeItem = cJSON_GetObjectItem(response, "type");
                char *type = typeItem ? typeItem->valuestring : "";

                if (strcmp(type, "query") == 0) {
                  enqueue(&qSearch, response);
                } else if (strcmp(type, "download") == 0) {
                  enqueue(&qDownload, response);
                } else if (strcmp(type, "server") == 0) {
                  enqueue(&qServer, response);
                } else {
                  if (status == 2 || status == 3) {
                    cJSON *dup = cJSON_Duplicate(response, 1);
                    enqueue(&qDownload, response);
                    if (dup) {
                      enqueue(&qServer, dup);
                    }
                  } else {
                    cJSON_Delete(response);
                  }
                }
              }
            }
          }
          start = i + 1;
        }
      }

      if (start > 0) {
        if (start < accum_len) {
          memmove(accum, accum + start, accum_len - start);
          accum_len -= start;
        } else {
          accum_len = 0;
        }
        accum[accum_len] = '\0';
      }

      if (accum_len >= (int)sizeof(accum) - 1) {
        accum_len = 0;
        accum[0] = '\0';
      }
    }

    sem_post(&semSuper);
  }
  return NULL;
}

void controlRouter(short i) { flag = i; }

void cleanupQueue(responseQueue *q) {
  while (q->head != q->tail) {
    cJSON_Delete(q->pool[q->head]);
    q->head = (q->head + 1) % POOL_SIZE;
  }
  sem_destroy(&q->sem);
}

void cleanupRouter() {
  sem_wait(&semSuper);
  cleanupQueue(&qSearch);
  cleanupQueue(&qDownload);
  cleanupQueue(&qServer);
  sem_post(&semSuper);
  sem_destroy(&semSuper);
}

cJSON *get_search() { return dequeue(&qSearch); }
cJSON *get_download() { return dequeue(&qDownload); }
cJSON *get_server() { return dequeue(&qServer); }
