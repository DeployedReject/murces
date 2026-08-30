#include "communciate.h"
#include "tui.h"
#include <../bridge/log.h>
#include <assert.h>
#include <limits.h>
#include <prc/prc_context.h>
#include <prc/prc_event.h>
#include <stdio.h>

_Static_assert(CHAR_BIT <= 8, "Incompatible device.");

#include <unistd.h>

int main(int argc, char *argv[]) {

  int flag = 0;
  if (argc > 2) {
    switch (argv[1][0]) {
    //./murces server
    case 's':
      if (argc == 6) {
        threadStarter();
        flag = 1;
      }
      break;
    //./murces modding
    case 'm':
      if (argc == 7) {
        threadStarter();
        flag = 1;

        switch (argv[2][0]) {
        //./murces modding search modBrowser query version modLoader
        case 's':
          printf("%s\n", logMurces("RESULTS:",
                                   search(argv[3], argv[4], argv[5], argv[6])));
          break;
        }
      }
      break;
    default:
      printf("Messy Parameters, ignoring");
    }
  }

  if (flag) {
    threadKiller();
    return 0;
  }

  struct tui_info info = {0};
  int ret = 0;

  if (prc_get_context(&info.ctx) != FN_SUCCESS) {
    ret = -1;
    goto kill_em;
  }

  if (noecho() != OK) {
    ret = -1;
    goto kill_em;
  }

  if (info.ctx.term_has_color) {
    start_color();

    init_color(MC_COLOR_DARK_BLUE, 0, 0, 666);
    init_color(MC_COLOR_LIGHT_CYAN, 329, 992, 996);
    init_color(MC_COLOR_LIGHT_GREY, 666, 666, 666);
    init_color(MC_COLOR_BLACK_BROWN, 250, 120, 40);
    init_color(MC_COLOR_VIOLET_BLUE, 372, 372, 956);
    init_color(MC_COLOR_LITTLE_BROWN_RED, 666, 250, 80);
    init_color(MC_COLOR_DARK_BLUE, 0, 0, 666);
    init_color(MC_COLOR_LIGHT_CYAN, 329, 992, 996);
    init_color(MC_COLOR_LIGHT_GREY, 666, 666, 666);
    init_color(MC_COLOR_BLACK_BROWN, 86, 86, 86);
    init_color(MC_COLOR_VIOLET_BLUE, 372, 372, 956);
    init_color(MC_COLOR_LITTLE_BROWN_RED, 666, 0, 0);

    init_pair(CPID_STDSCR, MC_COLOR_LIGHT_CYAN, MC_COLOR_DARK_BLUE);
    init_pair(CPID_MM_DESC, MC_COLOR_BLACK_BROWN, MC_COLOR_LIGHT_GREY);
    init_pair(CPID_MM_CONTENT, MC_COLOR_BLACK_BROWN, MC_COLOR_LIGHT_GREY);
    init_pair(CPID_OK_MSG, COLOR_WHITE, COLOR_BLUE);
    init_pair(CPID_WRN_MSG, COLOR_WHITE, COLOR_YELLOW);
    init_pair(CPID_ERR_MSG, COLOR_WHITE, COLOR_RED);

    // bkgd(COLOR_PAIR(CPID_STDSCR));
  }

  if (main_menu(&info) != 0) {
    ret = -1;
    goto kill_em;
  }

kill_em:
  m3_destroy_layout3();
  m6_destroy_layout6();
  mm_destroy_layout0();
  prc_destroy_context(&info.ctx);
  prc_kill_mother();

  return ret;
}
