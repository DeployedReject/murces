#include <prc/prc_event.h>
#include <prc/prc_winpool.h>
#include <prc/prc_window.h>

#include "tui.h"
#include "utlprc/types.h"

#include <string.h>

static struct tui_layout3__
{
	struct prc_context *ctx;
	struct prc_window *modloader_win;
	struct prc_window *version_win;
	unsigned char init;
} tui_layout3__ = {0};

static struct menu_items ver_menu_items__ = {
	.items = {
		'1', '.', '7', '.', '1', '0',
		0,
		'1', '.', '8',
		0,
		'1', '.', '8', '.', '1',
		0,
		'1', '.', '8', '.', '2',
		0,
		'1', '.', '8', '.', '3',
		0,
		'1', '.', '8', '.', '4',
		0,
		'1', '.', '8', '.', '5',
		0,
		'1', '.', '8', '.', '6',
		0,
		'1', '.', '8', '.', '7',
		0,
		'1', '.', '8', '.', '8',
		0,
		'1', '.', '8', '.', '9',
		0,
		'1', '.', '9',
		0,
		'1', '.', '9', '.', '1',
		0,
		'1', '.', '9', '.', '2',
		0,
		'1', '.', '9', '.', '3',
		0,
		'1', '.', '9', '.', '4',
		0,
		'1', '.', '1', '0',
		0,
		'1', '.', '1', '0', '.', '1',
		0,
		'1', '.', '1', '0', '.', '2',
		0,
		'1', '.', '1', '1',
		0,
		'1', '.', '1', '1', '.', '1',
		0,
		'1', '.', '1', '1', '.', '2',
		0,
		'1', '.', '1', '2',
		0,
		'1', '.', '1', '2', '.', '1',
		0,
		'1', '.', '1', '2', '.', '2',
		0,
		'1', '.', '1', '3',
		0,
		'1', '.', '1', '3', '.', '1',
		0,
		'1', '.', '1', '3', '.', '2',
		0,
		'1', '.', '1', '4',
		0,
		'1', '.', '1', '4', '.', '1',
		0,
		'1', '.', '1', '4', '.', '2',
		0,
		'1', '.', '1', '4', '.', '3',
		0,
		'1', '.', '1', '4', '.', '4',
		0,
		'1', '.', '1', '5',
		0,
		'1', '.', '1', '5', '.', '1',
		0,
		'1', '.', '1', '5', '.', '2',
		0,
		'1', '.', '1', '6',
		0,
		'1', '.', '1', '6', '.', '1',
		0,
		'1', '.', '1', '6', '.', '2',
		0,
		'1', '.', '1', '6', '.', '3',
		0,
		'1', '.', '1', '6', '.', '4',
		0,
		'1', '.', '1', '6', '.', '5',
		0,
		'1', '.', '1', '7',
		0,
		'1', '.', '1', '7', '.', '1',
		0,
		'1', '.', '1', '8',
		0,
		'1', '.', '1', '8', '.', '1',
		0,
		'1', '.', '1', '8', '.', '2',
		0,
		'1', '.', '1', '9',
		0,
		'1', '.', '1', '9', '.', '1',
		0,
		'1', '.', '1', '9', '.', '2',
		0,
		'1', '.', '1', '9', '.', '3',
		0,
		'1', '.', '1', '9', '.', '4',
		0,
		'1', '.', '2', '0',
		0,
		'1', '.', '2', '0', '.', '1',
		0,
		'1', '.', '2', '0', '.', '2',
		0,
		'1', '.', '2', '0', '.', '3',
		0,
		'1', '.', '2', '0', '.', '4',
		0,
		'1', '.', '2', '0', '.', '5',
		0,
		'1', '.', '2', '0', '.', '6',
		0,
		'1', '.', '2', '1',
		0,
		'1', '.', '2', '1', '.', '1',
		0,
		'1', '.', '2', '1', '.', '2',
		0,
		'1', '.', '2', '1', '.', '3',
		0,
		'1', '.', '2', '1', '.', '4',
		0,
		'1', '.', '2', '1', '.', '5',
		0,
		'1', '.', '2', '1', '.', '6',
		0,
		'1', '.', '2', '1', '.', '7',
		0,
		'1', '.', '2', '1', '.', '8',
		0,
		'1', '.', '2', '1', '.', '9',
		0,
		'1', '.', '2', '1', '.', '1', '0',
		0,
		'1', '.', '2', '1', '.', '1', '1',
		0,
		'2', '6', '.', '1',
		0,
		'2', '6', '.', '1', '.', '1',
		0,
		'2', '6', '.', '1', '.', '2',
		0,
		'2', '6', '.', '2',
		0,
		'2', '6', '.', '3',
		0
	},
	.strterms = {
		6, 10, 16, 22, 28, 34, 40, 46, 52, 58, 64, 68, 74, 80, 86, 92,
		97, 104, 111, 116, 123, 130, 135, 142, 149, 154, 161, 168, 173,
		180, 187, 194, 201, 206, 213, 220, 225, 232, 239, 246, 253, 260,
		265, 272, 277, 284, 291, 296, 303, 310, 317, 324, 329, 336, 343,
		350, 357, 364, 371, 376, 383, 390, 397, 404, 411, 418, 425, 432,
		439, 447, 455, 460, 467, 474, 479, 484
	},
	.nitems = 76,
	.selected = 1
};

static struct menu_items modld_menu_items__ = {
	.items = {
		'C', 'u', 'r', 's', 'e', 'F', 'o', 'r', 'g', 'e',
		0,
		'M', 'o', 'd', 'r', 'i', 'n', 't', 'h',
		0
	},
	.strterms = {10, 19},
	.nitems = 2,
	.selected = 1
};

int m3_init_windows(struct tui_info *info)
{
	if (tui_layout3__.init)
		return 0;

	struct prc_context *ctx = &info->ctx;
	int ret = 0;

	struct prc_window *modloader_win = tui_layout3__.modloader_win;
	struct prc_window *version_win = tui_layout3__.version_win;

	if (memset(&modloader_win->wbord, 0, sizeof(struct prc_border_desc))
		== NULL)
	{
		eputs("Error: Failed to initialize window border.");
		return -1;
	}

	unsigned int width_occup = mtstdlogwin->width + mtstdlogwin->wpad.right;
	// unsigned int width_remain = mtstdbigwin->width - width_occup;

	modloader_win->wpad.left = mtstdlogwin->wpad.right;
	modloader_win->wpad.right = mtstdlogwin->wpad.left;
	modloader_win->wpad.top = mtstdlogwin->wpad.top;
	modloader_win->wpad.bottom = mtstdlogwin->wpad.bottom;
	modloader_win->wpad.yes = TRUE;

	modloader_win->walign = PRC_ALIGN_NONE;

	ret = prc_create_derwin(modloader_win, mtstdbigwin, ctx);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to create derived window.");
		return ret;
	}

	if (memset(&version_win->wbord, 0, sizeof(struct prc_border_desc))
		== NULL)
	{
		eputs("Error: Failed to initialize window border.");
		return -1;
	}	

	version_win->wpad.left = width_occup;
	version_win->wpad.right = width_occup;
	version_win->wpad.top = mtstdlogwin->wpad.top;
	version_win->wpad.bottom = mtstdlogwin->wpad.bottom;
	version_win->wpad.yes = TRUE;

	ret = prc_create_derwin(version_win, mtstdbigwin, ctx);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to create derived window.");
		return ret;
	}

	tui_layout3__.init = TRUE;

	return ret;
}

void m3_destroy_layout3(void)
{
	if (!tui_layout3__.init)
		return;
	
	prc_destroy_window(tui_layout3__.modloader_win, tui_layout3__.ctx);	
	prc_destroy_window(tui_layout3__.version_win, tui_layout3__.ctx);
}

int m3_draw_layout3(void)
{
	struct prc_context *ctx = tui_layout3__.ctx;
	int ret = 0;

	struct prc_window *modloader_win = tui_layout3__.modloader_win;
	struct prc_window *version_win = tui_layout3__.version_win;

	ret = prc_draw_window_border(mtstdbigwin);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to draw window border.");
		return ret;
	}
	
	mtstdbigwin->talign = PRC_ALIGN_TOP;
	ret = prc_window_title(mtstdbigwin, 0, 0, ctx);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to write window title.");
		return ret;
	}

	ret = prc_draw_window_border(modloader_win);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to draw window border.");
		return ret;
	}

	ret = prc_draw_window_border(version_win);
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

	return ret;
}

int m3_restore_text(void)
{
	char *desc_win = "Install game modifications. Press <Q> to go back.";

	mm_insert_text(mtstdbigwin, -1, desc_win, 4, 4, 2);
	// mm_insert_text(tui_layout3__.modloader_win, -1, "MLWIN", 4, 4, 2);
	// mm_insert_text(tui_layout3__.version_win, -1, "VWIN", 4, 4, 2);

	mtui_highlight_menu_item(&modld_menu_items__, 0);

	mm_insert_menu_text(tui_layout3__.modloader_win, &modld_menu_items__,
			    4, 4, 2);

	return 0;
}

int menu3(struct tui_info *info)
{
	fnresult_t ret = 0;

	if (!tui_layout3__.init)
	{
		struct prc_context *ctx = &info->ctx;
		int ret = 0;

		struct prc_window *modloader_win = prc_get_freeaddr();
		if (modloader_win == NULL)
		{
			eputs("Error: No memory in window pool.");
			return -1;
		}

		struct prc_window *version_win = prc_get_freeaddr();
		if (version_win == NULL)
		{
			eputs("Error: No memory in window pool.");
			prc_destroy_window(modloader_win, ctx);
			return -1;
		}
		tui_layout3__.modloader_win = modloader_win;
		tui_layout3__.version_win = version_win;
		tui_layout3__.ctx = &info->ctx;

		ret = m3_init_windows(info);
		if (ret != 0)
		{
			eputs("Failed to initialize layout.");
			return -1;
		}
	}

	if (m3_draw_layout3() != 0)
	{
		eputs("Failed to draw layout.");
		return -1;
	}

	struct prc_window *modloader_win = tui_layout3__.modloader_win;
	struct prc_window *version_win = tui_layout3__.version_win;
	// struct prc_context *ctx = tui_layout3__.ctx;

	if (nodelay(tui_layout3__.modloader_win->win, TRUE) != OK)
	{
		ret = -1;
		return ret;
	}

	if (keypad(tui_layout3__.modloader_win->win, TRUE) != OK)
	{
		ret = -1;
		return ret;
	}

	m3_restore_text();

	wnoutrefresh(mtstdbigwin->win);
	wnoutrefresh(modloader_win->win);
	wnoutrefresh(version_win->win);
	doupdate();

	uint32_t c;
	struct prc_generic_event fevt = {0};
	unsigned char item_selected = 0;
	unsigned char chselect = 0;
	// while((c = getch()))
	// {
	// 	if (c == 'q' || c == 'Q')
	// 		return 0;
	// }

	do
	{
		wmove(mtstdbigwin->win, 0, 0);
		prc_poll_for_event(tui_layout3__.modloader_win);
		while ((ret = prc_get_first_event(&fevt)) == FN_SUCCESS)
		{
			c = fevt.detail;
			switch(c)
			{
				case KEY_UP:
					if (!item_selected)
					{
						prc_use_event();
						continue;
					}
					chselect = TRUE;
					modld_menu_items__.selected = 0;

					if (mtui_rmhl_menu_item(&modld_menu_items__,
						item_selected)
						!= 0)
						return ret;

					--item_selected;
					PRC_SETBIT_1(modld_menu_items__.selected, item_selected);

					break;

				case KEY_DOWN:
					if (item_selected == modld_menu_items__.nitems - 1)
					{
						prc_use_event();
						continue;
					}

					chselect = TRUE;
					modld_menu_items__.selected = 0;

					if (mtui_rmhl_menu_item(&modld_menu_items__, item_selected)
						!= 0)
						return ret;

					++item_selected;
					PRC_SETBIT_1(modld_menu_items__.selected, item_selected);

					break;

				case KEY_ENTER:
				case '\n':
				case '\r':
					break;

				default:
					break;
			}

			if (chselect)
			{
				werase(tui_layout3__.modloader_win->win);
				prc_draw_window_border(tui_layout3__.modloader_win);

				if (mtui_highlight_menu_item(&modld_menu_items__, item_selected)
					!= 0)
					return ret;

				if (mm_insert_menu_text(tui_layout3__.modloader_win,
					&modld_menu_items__,
					4, 4, 2) != 0)
					return -1;

				wnoutrefresh(tui_layout3__.modloader_win->win);
			}

			prc_use_event();
		}

		mvwprintw(stdscr, 0, 0, "%d", item_selected + 1);
		wnoutrefresh(stdscr);
		wnoutrefresh(mtstdlogwin->win);
		doupdate();
		wtimeout(tui_layout3__.modloader_win->win, 10);

	} while (c != 'q' && c != 'Q');

	return 0;
}
