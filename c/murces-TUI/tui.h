#ifndef MTUI_TUI_H
#define MTUI_TUI_H

#include <prc/prc_context.h>
#include <utlprc/math.h>

#include <stdint.h>

#define MAIN_MENU_DESC_WNAME "MurCes v1.0.0"

#ifndef NDEBUG
#define oputs(s) \
	do \
	{ \
		attron(COLOR_PAIR(CPID_OK_MSG)); \
		mvaddstr(0, 0, s); \
		attroff(COLOR_PAIR(CPID_OK_MSG)); \
		getch(); \
	} while (0)
#define oprintf(...) \
	do \
	{ \
		attron(COLOR_PAIR(CPID_OK_MSG)); \
		mvwprintw(stdscr, 0, 0, __VA_ARGS__); \
		attroff(COLOR_PAIR(CPID_OK_MSG)); \
		getch(); \
	} while (0)

#define wputs(s) \
	do \
	{ \
		attron(COLOR_PAIR(CPID_WRN_MSG)); \
		mvaddstr(0, 0, s); \
		attroff(COLOR_PAIR(CPID_WRN_MSG)); \
		getch(); \
	} while (0)
#define wprintf(...) \
	do \
	{ \
		attron(COLOR_PAIR(CPID_WRN_MSG)); \
		mvwprintw(stdscr, 0, 0, __VA_ARGS__); \
		attroff(COLOR_PAIR(CPID_WRN_MSG)); \
		getch(); \
	} while (0)

#define eputs(s) \
	do \
	{ \
		attron(COLOR_PAIR(CPID_ERR_MSG)); \
		mvaddstr(0, 0, s); \
		attroff(COLOR_PAIR(CPID_ERR_MSG)); \
		getch(); \
	} while (0)
#define eprintf(...) \
	do \
	{ \
		attron(COLOR_PAIR(CPID_ERR_MSG)); \
		mvwprintw(stdscr, 0, 0, __VA_ARGS__); \
		attroff(COLOR_PAIR(CPID_ERR_MSG)); \
		getch(); \
	} while (0)
#else
#define eputs(s)
#define eprintf(...)
#endif /* NDEBUG */

#define OPEN_MENU(n) \
	werase(mtstdbigwin->win); \
	wnoutrefresh(mtstdbigwin->win); \
	doupdate(); \
	menus__[n](info)

#define MAX_MENU_ITEM_COUNT (20)
#define MAX_MENU_ITEM_CHAR_COUNT (256)
#define MAX_STR_MENU_ITEMS (256)

enum cpid
{
	CPID_STDSCR = 1,
	CPID_MM_DESC,
	CPID_MM_CONTENT,
	CPID_OK_MSG,
	CPID_WRN_MSG,
	CPID_ERR_MSG,
};

enum mc_colors
{
	MC_COLOR_DARK_BLUE = 8,
	MC_COLOR_LIGHT_CYAN,
	MC_COLOR_LIGHT_GREY,
	MC_COLOR_BLACK_BROWN,
	MC_COLOR_VIOLET_BLUE,
	MC_COLOR_LITTLE_BROWN_RED
};

struct tui_info
{
	struct prc_context ctx;
	struct prc_window *desc_win;
	struct prc_window *content_win;
	struct prc_window *log_win;
};

struct menu_items
{
	chtype items[MAX_MENU_ITEM_CHAR_COUNT * MAX_MENU_ITEM_COUNT];
	unsigned long int strterms[MAX_MENU_ITEM_COUNT];
	unsigned int nitems;
	uint32_t selected;
};

struct str_menu_items
{
	char *items[MAX_STR_MENU_ITEMS];
	unsigned int nitems;
	uint64_t selected[(MAX_STR_MENU_ITEMS + 63) >> 6];
};

extern struct prc_window *mtstdlogwin;
extern struct prc_window *mtstdbigwin;

int mm_init_windows(
	struct prc_window *desc_win, struct prc_window *content_win,
	struct prc_window *log_win,
	struct tui_info *info);

int mm_print_menu_item(struct prc_window *win, unsigned int y,
	unsigned int x, struct menu_items *items,
	unsigned int n, int idx);

int mm_insert_menu_text(struct prc_window *win,
	struct menu_items *items, 
	int left, int right, int top);

int mtui_highlight_menu_item(struct menu_items *items, int idx);

int mm_insert_text(struct prc_window *win, short pair,
	char *s, int left, int right, int top);

int mm_restore_text0(struct prc_window *desc_win,
	struct prc_window *content_win);

int mtui_resize_windows(struct tui_info *info);

int main_menu(struct tui_info *info);

void mm_destroy_layout0(void);

/* MENU 1 */
int menu1(struct tui_info *info);
int m1_init_windows(struct tui_info *info);
void m1_destroy_layout1(void);
int m1_draw_layout1(void);

/* MENU 2 */
int menu2(struct tui_info *info);
int m2_init_windows(struct tui_info *info);
void m2_destroy_layout2(void);
int m2_draw_layout2(void);

/* MENU 3 */
int menu3(struct tui_info *info);
int m3_init_windows(struct tui_info *info);
void m3_destroy_layout3(void);
int m3_draw_layout3(void);

/* MENU 4 */
int menu4(struct tui_info *info);
int m4_init_windows(struct tui_info *info);
void m4_destroy_layout4(void);
int m4_draw_layout4(void);

/* MENU 5 */
int menu5(struct tui_info *info);
int m5_init_windows(struct tui_info *info);
void m5_destroy_layout5(void);
int m5_draw_layout5(void);

/* MENU 6 */
int menu6(struct tui_info *info);
int m6_init_windows(struct tui_info *info);
void m6_destroy_layout6(void);
int m6_draw_layout6(void);

/* MENU 7 */
int menu7(struct tui_info *info);
int m7_init_windows(struct tui_info *info);
void m7_destroy_layout7(void);
int m7_draw_layout7(void);


int threadStarter(void);
void threadKiller(void);
#endif /* MTUI_TUI_H */
