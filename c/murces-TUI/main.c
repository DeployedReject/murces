#include <ncurses.h>

#include "prc/prc_context.h"
#include "prc/prc_event.h"
#include "prc/prc_window.h"
#include "prc/prc_winpool.h"
#include "utlprc/types.h"

#include <string.h>

fnresult_t eg_single_window(void) {
  fnresult_t result = FN_SUCCESS;

  struct prc_window *window = prc_get_freeaddr();
  if (window == NULL) {
    fputs("Error: Could not get a free window address.\n", stderr);
    result = FN_FAILURE;
    goto cleanup;
  }

  struct prc_context ctx;
  result = prc_get_context(&ctx);
  if (result != FN_SUCCESS) {
    fputs("Error: Could not create context.\n", stderr);
    goto cleanup;
  }

  if (noecho() != OK) {
    fputs("Error: noecho() failed.\n", stderr);
    result = FN_FAILURE;
    goto cleanup;
  }

  if (memset(&window->wbord, 0, sizeof(struct prc_border_desc)) == NULL) {
    fputs("Error: Could not initialize border.\n", stderr);
    goto cleanup;
  }

  window->wpad.left = 10;
  window->wpad.right = 10;
  window->wpad.top = 5;
  window->wpad.bottom = 2;
  window->wpad.yes = TRUE;

  window->walign = PRC_ALIGN_NONE;

  window->title = "Example: Single window";
  window->talign = PRC_ALIGN_TOP;

  result = prc_create_window(window, &ctx);
  if (result != FN_SUCCESS) {
    fputs("Error: Could not create window.\n", stderr);
    goto cleanup;
  }

  if (nodelay(window->win, TRUE) != OK) {
    fputs("Error: nodelay() failed.\n", stderr);
    result = FN_FAILURE;
    goto cleanup;
  }

  if (keypad(window->win, TRUE) != OK) {
    fputs("Error: keypad() failed.\n", stderr);
    result = FN_FAILURE;
    goto cleanup;
  }

  result = prc_draw_window_border(window);
  if (result != FN_SUCCESS) {
    fputs("Error: Could not draw window border.\n", stderr);
    goto cleanup;
  }

  result = prc_window_title(window, 0, 0, &ctx);
  if (result != FN_SUCCESS) {
    fputs("Error: Could not draw window's title.\n", stderr);
    goto cleanup;
  }

  refresh();
  wrefresh(window->win);

  uint16_t wy = 1;
  uint16_t wx = 1;

  int16_t c = '\0';
  struct prc_generic_event fevt;
  uint8_t resize_trgg = FALSE;
  do {
    prc_poll_for_event(window);

    while ((result = prc_get_first_event(&fevt)) == FN_SUCCESS) {
      c = fevt.detail;
      if (c == KEY_RESIZE)
        resize_trgg = TRUE;

      if (c == '\n' || c == '\r' || c == 10 || c == 13) {
        wx = 1;
        wmove(window->win, ++wy, wx);
      } else {
        if (wx >= window->width - 1) {
          wx = 1;
          ++wy;
        }

        if (wy >= window->height - 1)
          break;

        if (mvwaddch(window->win, wy, wx++, c) != OK) {
          fputs("Error: Could not add character.", stderr);
          result = FN_FAILURE;
          goto cleanup;
        }
      }

      prc_use_event();
    }

    if (resize_trgg) {
      prc_resize_context(&ctx);
      prc_resize_window(window, &ctx);

      clearok(stdscr, TRUE);

      if (wy >= window->height - 1)
        wy = window->height - 2;

      if (wx >= window->width - 1)
        wx = window->width - 2;

      refresh();
      resize_trgg = FALSE;
    }

    result = wnoutrefresh(window->win);
    if (result != OK) {
      fputs("Error: Could not refresh window.\n", stderr);
      goto cleanup;
    }

    result = doupdate();
    if (result != OK) {
      fputs("Error: Could not update physical window state.\n", stderr);
      goto cleanup;
    }

    wtimeout(window->win, 10);
  } while (c != 'Q' && c != 'q');

cleanup:
  prc_destroy_window(window, &ctx);
  prc_destroy_context(&ctx);
  prc_kill_mother();

  return result;
}
