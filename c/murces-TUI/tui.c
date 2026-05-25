#include "tui.h"
#include <../bridge/communciate.h>
#include <../bridge/router.h>
#include <ncurses.h>
#include <prc/prc_event.h>
#include <prc/prc_window.h>
#include <prc/prc_winpool.h>

#include <pthread.h>
#include <stdint.h>
#include <string.h>
#include <sys/select.h>
#include <unistd.h>
struct tui_layout0__ {
  struct prc_context *ctx;
  struct prc_window *content_win;
  unsigned char init;
} tui_layout0__ = {0};

struct prc_window *mtstdlogwin = NULL;
struct prc_window *mtstdbigwin = NULL;

pthread_t router;
int threadStarter(void);
void threadKiller(void);

static struct menu_items menu_items0__ = {
	.items = {
		/* 1 Start/Stop server*/
		'S' | A_UNDERLINE, 't', 'a', 'r', 't', '/',
		'S' | A_UNDERLINE, 't', 'o', 'p',
		' ', 's', 'e', 'r', 'v', 'e', 'r',
		0,
		/* 2 Configure server */
		'C' | A_UNDERLINE, 'o', 'n', 'f', 'i', 'g', 'u', 'r', 'e',
		' ', 's', 'e', 'r', 'v', 'e', 'r',
		0,
		/* 3 Install server */
		'I' | A_UNDERLINE, 'n', 's', 't', 'a', 'l', 'l',
		' ', 's', 'e', 'r', 'v', 'e', 'r',
		0,
		/* 4 Migrate player */
		'M' | A_UNDERLINE, 'i', 'g', 'r', 'a', 't', 'e',
		' ', 'p', 'l', 'a', 'y', 'e', 'r',
		0,
		/* 5 Create backup */
		'C', 'r', 'e', 'a', 't', 'e',
		' ', 'b' | A_UNDERLINE, 'a', 'c', 'k', 'u', 'p',
		0,
		/* 6 Browse mods */
		'B', 'r', 'o', 'w' | A_UNDERLINE, 's', 'e',
		' ', 'm', 'o', 'd', 's',
		0,
		/* 7 Delete mods */
		'D' | A_UNDERLINE, 'e', 'l', 'e', 't', 'e',
		' ', 'm', 'o', 'd', 's',
		0
	},
	.strterms = {17, 34, 49, 64, 78, 90, 102},
	.nitems = 7,
	.selected = 1
};

static int (*menus__[7])(struct tui_info *) = {
	NULL,
	NULL,
	menu3,
	NULL,
	NULL,
	menu6,
	NULL
};

int mm_init_windows(
	struct prc_window *desc_win, struct prc_window *content_win,
	struct prc_window *log_win,
	struct tui_info *info)
{
	if (tui_layout0__.init)
		return 0;

	int ret = 0;

	struct prc_context *ctx = &info->ctx;

	if (memset(&desc_win->wbord, 0, sizeof(struct prc_border_desc)) == NULL)
	{
		eputs("Error: Failed to initialize window border.");
		return -1;
	}

	desc_win->wpad.left = 5;
	desc_win->wpad.right = 5;
	desc_win->wpad.top = 2;
	desc_win->wpad.bottom = 1;
	desc_win->wpad.yes = TRUE;

	desc_win->walign = PRC_ALIGN_NONE;

	desc_win->title = MAIN_MENU_DESC_WNAME;
	desc_win->talign = PRC_ALIGN_TOP;

	ret = prc_create_window(desc_win, ctx);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to create window.");
		return ret;
	}
	// wbkgd(desc_win->win, COLOR_PAIR(CPID_MM_DESC));

	content_win->parent = desc_win;
	if (memset(&content_win->wbord, 0, sizeof(struct prc_border_desc)) == NULL)
	{
		eputs("Error: Failed to initialize border.");
		return -1;
	}

	content_win->wpad.left = 2;
	content_win->wpad.right = desc_win->width * 3 / 10;
	content_win->wpad.top = 5;
	content_win->wpad.bottom = 1;
	content_win->wpad.yes = TRUE;

	content_win->walign = PRC_ALIGN_NONE;

	ret = prc_create_derwin(content_win, content_win->parent, ctx);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to create derived window.");
		return ret;
	}
	// wbkgd(content_win->win, COLOR_PAIR(CPID_MM_CONTENT));

	log_win->parent = desc_win;
	if (memset(&log_win->wbord, 0, sizeof(struct prc_border_desc)) == NULL)
	{
		eputs("Error: Failed to initialize border.");
		return -1;
	}
	log_win->wpad.left = content_win->wpad.left + content_win->width + 1;
	log_win->wpad.right = 2;
	log_win->wpad.top = 5;
	log_win->wpad.bottom = 1;
	log_win->wpad.yes = TRUE;

	ret = prc_create_derwin(log_win, log_win->parent, ctx);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to create derived window.");
		return ret;
	}
	// wbkgd(log_win->win, COLOR_PAIR(CPID_MM_CONTENT));

	ret = prc_draw_window_border(desc_win);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to draw window border.");
		return ret;
	}

	ret = prc_window_title(desc_win, 0, 0, ctx);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to write window title.");
		return ret;
	}

	ret = prc_draw_window_border(content_win);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to draw window border.");
		return ret;
	}

	ret = prc_draw_window_border(log_win);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to draw window border.");
		return ret;
	}

	mtstdbigwin = desc_win;
	tui_layout0__.content_win = content_win;
	mtstdlogwin = log_win;

	tui_layout0__.init = TRUE;

	return 0;
}

unsigned int mm_get_menu_idx_21__(unsigned int i,
	unsigned int j)
{
	return i * MAX_MENU_ITEM_COUNT + j;
}

int mm_print_menu_item(struct prc_window *win, unsigned int y,
	unsigned int x, struct menu_items *items,
	unsigned int n, int idx)
{
	if (win == NULL || items == NULL || idx < 0)
		return -1;
	if (!idx)
		return mvwaddchnstr(win->win, y, x,
			items->items, n);

	return mvwaddchnstr(win->win, y, x,
		items->items + items->strterms[idx - 1] + 1, n);
}

int mm_insert_menu_text(struct prc_window *win,
	struct menu_items *items, 
	int left, int right, int top)
{
	if (win == NULL || items == NULL)
		return -1;

	for (unsigned int idx = 0, y = 0; idx < items->nitems; ++idx, y += 2)
	{
		unsigned int ogsize = 0;
		if (!idx)
			ogsize = items->strterms[idx];
		else
			ogsize = items->strterms[idx] - items->strterms[idx - 1] - 1;

		int bufsize = win->width - right - left;
		if(bufsize < 0)
			return -1;
		else if ((unsigned int) bufsize >= ogsize)
		{
			mm_print_menu_item(win, top + y, left, items, ogsize, idx);
			continue;
		}

		unsigned int curptr = 0;
		if (!idx)
			curptr = 0;
		else
			curptr = items->strterms[idx - 1] + 1;
		
		for (int i = 0; curptr < ogsize; ++i)
		{
			mm_print_menu_item(win, top + i, left,
				items, bufsize, idx);
			curptr += bufsize;
		}
	}

	return 0;
}

int mm_draw_layout0(void)
{
	int ret = 0;

	ret = prc_draw_window_border(mtstdbigwin);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to draw window border.");
		return ret;
	}

	ret = prc_window_title(mtstdbigwin, 0, 0, tui_layout0__.ctx);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to write window title.");
		return ret;
	}

	ret = prc_draw_window_border(tui_layout0__.content_win);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to draw window border.");
		return ret;
	}

	ret = prc_draw_window_border(mtstdlogwin);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to draw window border.");
		return ret;
	}

	mm_restore_text0(mtstdbigwin, tui_layout0__.content_win);

	wnoutrefresh(tui_layout0__.content_win->win);
	wnoutrefresh(mtstdbigwin->win);
	wnoutrefresh(mtstdlogwin->win);
	doupdate();

	return 0;
}

int mtui_rmhl_menu_item(struct menu_items *items, int idx)
{
	if (items == NULL || idx < 0)
		return -1;

	unsigned int len = 0;
	if (!idx)
	{
		len = items->strterms[0];
		for (unsigned int i = 0; i < len; ++i)
			items->items[i] &= ~A_STANDOUT;
		return 0;
	}

	unsigned int start = items->strterms[idx - 1] + 1;
	len = start + items->strterms[idx] - items->strterms[idx - 1] - 1;
	for (unsigned int i = start; i < len; ++i)
		items->items[i] &= ~A_STANDOUT;

	return 0;
}

int mtui_highlight_menu_item(struct menu_items *items, int idx)
{
	if (items == NULL || idx < 0)
		return -1;

	unsigned int len = 0;
	if (!idx)
	{
		len = items->strterms[0];
		for (unsigned int i = 0; i < len; ++i)
			items->items[i] |= A_STANDOUT;
		return 0;
	}

	unsigned int start = items->strterms[idx - 1] + 1;
	len = start + items->strterms[idx] - items->strterms[idx - 1] - 1;
	for (unsigned int i = start; i < len; ++i)
		items->items[i] |= A_STANDOUT;

	return 0;
}

int mm_restore_text0(struct prc_window *desc_win,
	struct prc_window *content_win)
{
	char *dw_desc = "MurCes is a Minecraft server configuration"
	" tool. Press <Q> to quit. All navigations are bound to <LEFT>, <RIGHT>,"
	" <UP>, and <DOWN> keys.";
	
	if (mm_insert_text(desc_win, -1,
		dw_desc, 4, 4, 2) != 0)
		return -1;

	if (mtui_highlight_menu_item(&menu_items0__,
			__builtin_ctz(menu_items0__.selected)) != 0)
		return -1;
		
	if (mm_insert_menu_text(content_win, &menu_items0__,
		4, 4, 2) != 0)
		return -1;

	return 0;
}

int mm_insert_text(struct prc_window *win, short pair,
	char *s, int left, int right, int top)
{
	if (win == NULL || s == NULL)
		return -1;

	unsigned int ogsize = strlen(s);

	int bufsize = win->width - right - left;
	if(bufsize < 0)
		return -1;
	else if ((unsigned int) bufsize >= ogsize)
	{
		// wattron(win->win, pair);
		if (mvwaddstr(win->win, top, left, s) == ERR)
			return -1;
		// wattroff(win->win, pair);
		return 0;
	}

	unsigned int curptr = 0;
	for (int i = 0; curptr < ogsize; ++i)
	{
		if (pair >= 0)
			wattron(win->win, pair);
		mvwaddnstr(win->win, top + i, left, s + curptr, bufsize);
		if (pair >= 0)
			wattroff(win->win, pair);
		curptr += bufsize;
	}

	return 0;
}

int mtui_resize_windows0__(struct tui_layout0__ *layout)
{
	struct prc_context* ctx = layout->ctx;
	struct prc_window *desc_win = mtstdbigwin;
	struct prc_window *content_win = layout->content_win;
	struct prc_window *log_win = mtstdlogwin;

	int ret = 0;

	ret = prc_resize_context(ctx);
	if (ret != FN_SUCCESS)
		return ret;

	ret = prc_resize_window(desc_win, ctx);
	if (ret != FN_SUCCESS)
		return ret;

	ret = prc_resize_window(content_win, ctx);
	if (ret != FN_SUCCESS)
		return ret;

	ret = prc_resize_window(log_win, ctx);
	if (ret != FN_SUCCESS)
		return ret;

	ret = clearok(stdscr, TRUE);
	if (ret != OK)
		return ret;

	ret = mm_restore_text0(desc_win, content_win);
	if (ret != 0)
		return ret;
	
	ret = wnoutrefresh(stdscr);
	if (ret != OK)
		return ret;

	ret = wnoutrefresh(log_win->win);
	if (ret != OK)
		return ret;

	ret = wnoutrefresh(content_win->win);
	if (ret != OK)
		return ret;

	ret = wnoutrefresh(desc_win->win);
	if (ret != OK)
		return ret;

	ret = doupdate();
	if (ret != OK)
		return ret;

	return ret;
}

void mm_destroy_layout0(void)
{

	prc_destroy_window(tui_layout0__.content_win, tui_layout0__.ctx);
}

int main_menu(struct tui_info *info)
{
	struct prc_context *ctx = &info->ctx;
	tui_layout0__.ctx = ctx;
	int ret = 0;

	struct prc_window *desc_win = prc_get_freeaddr();
	if (desc_win == NULL)
	{
		eputs("Error: No memory in window pool.");
		return -1;
	}

	struct prc_window *content_win = prc_get_freeaddr();
	if (content_win == NULL)
	{
		eputs("Error: No memory in window pool.");
		ret = -1;
	}

	struct prc_window *log_win = prc_get_freeaddr();
	if (log_win == NULL)
	{
		eputs("Error: No memory in window pool.");
		ret = -1;
		return ret;
	}
	else if (content_win == NULL)
		return ret;

	ret = mm_init_windows(desc_win, content_win, log_win, info);
	if (ret == -1)
		return ret;

	if (nodelay(content_win->win, TRUE) != OK)
	{
		ret = -1;
		return ret;
	}

	if (keypad(content_win->win, TRUE) != OK)
	{
		ret = -1;
		return ret;
	}

	mm_insert_text(log_win, -1,
		"VERY VERY BIG BIG LONG LONG TEXT TO TEST THE HOW LONG A MESSAGE IT CAN WRAP! I AM NOT GOING TO ADD LOGIC TO CHECK FOR WORDS AND SPACES TO WRAP WORDS PROPERLY SINCE IT IS TO GODDAMN COMPLICATED!",
		4, 4, 2);
	
	// eputs("This is a test message.");

	wnoutrefresh(stdscr);

	mm_restore_text0(desc_win, content_win);

	wnoutrefresh(log_win->win);
	wnoutrefresh(content_win->win);
	wnoutrefresh(desc_win->win);

	doupdate();

	uint32_t c = 0;
	unsigned char item_selected = 0;
	unsigned char chselect = 0;
	struct prc_generic_event fevt = {0};
	do
	{
		wmove(content_win->win, 0, 0);
		prc_poll_for_event(content_win);
		while ((ret = prc_get_first_event(&fevt)) == FN_SUCCESS)
		{
			c = fevt.detail;
			
			switch(c)
			{
				case KEY_UP:
				{
					if (item_selected == 0)
					{
						prc_use_event();
						continue;
					}

					chselect = TRUE;
					menu_items0__.selected = 0;

					if (mtui_rmhl_menu_item(&menu_items0__, item_selected)
						!= 0)
						return ret;

					--item_selected;
					PRC_SETBIT_1(menu_items0__.selected, item_selected);

					break;
				}
				case KEY_DOWN:
				{
					if (item_selected == menu_items0__.nitems - 1)
					{
						prc_use_event();
						continue;
					}
					
					chselect = TRUE;
					menu_items0__.selected = 0;

					if (mtui_rmhl_menu_item(&menu_items0__, item_selected)
						!= 0)
						return ret;

					++item_selected;
					PRC_SETBIT_1(menu_items0__.selected, item_selected);

					break;
				}
				case KEY_RESIZE:
					if (mtui_resize_windows0__(&tui_layout0__) != 0)
						return ret;
					
					break;

				case KEY_ENTER:
				case '\n':
				case '\r':
					OPEN_MENU(item_selected);
					werase(mtstdbigwin->win);
					mm_draw_layout0();
					break;

				case 'i':
				case 'I':
					OPEN_MENU(2);
					werase(mtstdbigwin->win);
					mm_draw_layout0();
					break;

				case 'w':
				case 'W':
					OPEN_MENU(5);
					werase(mtstdbigwin->win);
					mm_draw_layout0();
					break;

				default:
					break;
			}

			if (chselect)
			{
				werase(content_win->win);
				prc_draw_window_border(content_win);

				if (mtui_highlight_menu_item(&menu_items0__, item_selected)
					!= 0)
					return ret;

				if (mm_insert_menu_text(content_win, &menu_items0__,
					4, 4, 2) != 0)
					return -1;

				wnoutrefresh(content_win->win);
			}

			prc_use_event();
		}
		mvwprintw(stdscr, 0, 0, "%d", item_selected + 1);
		wnoutrefresh(stdscr);
		wnoutrefresh(mtstdlogwin->win);
		doupdate();
		wtimeout(content_win->win, 10);
	} while (c != 'q' && c != 'Q');

	return ret;
}

int threadStarter(void) {
  ready = 0;
  void *IOP = init_orchestrator(1);

  controlRouter(1);

  if (pthread_create(&router, NULL, initRouter, IOP) != 0)
    return -1;

  for (int i = 0; ready == 0 && i < 10; i++)
    sleep(1);
  return 0;
}

void threadKiller(void) {
  controlRouter(0);
  pthread_join(router, NULL);
  cleanupRouter();
  cleanup_orchestrator();
}
