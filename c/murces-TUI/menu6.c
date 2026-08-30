#include <prc/prc_winpool.h>
#include <prc/prc_window.h>

#include "tui.h"

#include <string.h>

struct tui_layout6__
{
	struct prc_context *ctx;
	struct prc_window *modlib_win;
	struct prc_window *modloader_win;
	struct prc_window *search_win;
	struct prc_window *modlist_win;
	unsigned char init;
} tui_layout6__ = {0};

int m6_init_windows(struct tui_info *info)
{
	if (tui_layout6__.init)
		return 0;

	struct prc_context *ctx = &info->ctx;
	int ret = 0;

	struct prc_window *modlib_win = tui_layout6__.modlib_win;
	struct prc_window *modloader_win = tui_layout6__.modloader_win;
	struct prc_window *search_win = tui_layout6__.search_win;
	struct prc_window *modlist_win = tui_layout6__.modlist_win;

	if (memset(&modloader_win->wbord, 0, sizeof(struct prc_border_desc))
		== NULL)
	{
		eputs("Error: Failed to initialize window border.");
		return -1;
	}

	modloader_win->wpad.left = 2;
	modloader_win->wpad.right = ((mtstdbigwin->width << 1) / 3) + 1;
	modloader_win->wpad.top = (mtstdbigwin->height >> 1) + 1;
	modloader_win->wpad.bottom = 1;
	modloader_win->wpad.yes = TRUE;

	modloader_win->walign = PRC_ALIGN_NONE;

	ret = prc_create_derwin(modloader_win, mtstdbigwin, ctx);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to create derived window.");
		return ret;
	}

	if (memset(&modlib_win->wbord, 0, sizeof(struct prc_border_desc))
		== NULL)
	{
		eputs("Error: Failed to initialize window border.");
		return -1;
	}
	
	modlib_win->wpad.left = 2;
	modlib_win->wpad.right = modloader_win->wpad.right;
	modlib_win->wpad.top = mtstdlogwin->wpad.top;
	modlib_win->wpad.bottom = modloader_win->height + modloader_win->wpad.bottom;
	modlib_win->wpad.yes = TRUE;
	
	modlib_win->walign = PRC_ALIGN_NONE;

	ret = prc_create_derwin(modlib_win, mtstdbigwin, ctx);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to create derived window.");
		return ret;
	}

	if (memset(&search_win->wbord, 0, sizeof(struct prc_border_desc))
		== NULL)
	{
		eputs("Error: Failed to initialize window border.");
		return -1;
	}

	search_win->wpad.left = modlib_win->width + modlib_win->wpad.left;
	search_win->wpad.right = mtstdlogwin->width + mtstdlogwin->wpad.right;
	search_win->wpad.top = mtstdlogwin->wpad.top;
	search_win->wpad.bottom = 
		(((mtstdbigwin->height - mtstdlogwin->wpad.top) << 2) / 5 + 1);
	search_win->wpad.yes = TRUE;

	search_win->walign = PRC_ALIGN_NONE;

	ret = prc_create_derwin(search_win, mtstdbigwin, ctx);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to create derived window.");
		return -1;
	}

	if (memset(&modlist_win->wbord, 0, sizeof(struct prc_border_desc))
		== NULL)
	{
		eputs("Error: Failed to initialize window border.");
		return -1;
	}

	modlist_win->wpad.left = search_win->wpad.left;
	modlist_win->wpad.right = search_win->wpad.right;
	modlist_win->wpad.top = search_win->height + search_win->wpad.top;
	modlist_win->wpad.bottom = 1;
	modlist_win->wpad.yes = TRUE;

	modlist_win->walign = PRC_ALIGN_NONE;

	ret = prc_create_derwin(modlist_win, mtstdbigwin, ctx);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to create derived window.");
		return -1;
	}

	tui_layout6__.init = TRUE;

	return 0;
}

void m6_destroy_layout6(void)
{
	if (!tui_layout6__.init)
		return;

	prc_destroy_window(tui_layout6__.modlib_win, tui_layout6__.ctx);
	prc_destroy_window(tui_layout6__.modloader_win, tui_layout6__.ctx);
	prc_destroy_window(tui_layout6__.search_win, tui_layout6__.ctx);
	prc_destroy_window(tui_layout6__.modlist_win, tui_layout6__.ctx);
}

int m6_draw_layout6(void)
{
	struct prc_context *ctx = tui_layout6__.ctx;
	int ret = 0;

	struct prc_window *modlib_win = tui_layout6__.modlib_win;
	struct prc_window *modloader_win = tui_layout6__.modloader_win;
	struct prc_window *search_win = tui_layout6__.search_win;
	struct prc_window *modlist_win = tui_layout6__.modlist_win;
	
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

	ret = prc_draw_window_border(modlib_win);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to draw window border.");
		return ret;
	}

	ret = prc_draw_window_border(modloader_win);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to draw window border.");
		return ret;
	}

	ret = prc_draw_window_border(search_win);
	if (ret != FN_SUCCESS)
	{
		eputs("Error: Failed to draw window border.");
		return ret;
	}

	ret = prc_draw_window_border(modlist_win);
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

int menu6(struct tui_info *info)
{
	if (!tui_layout6__.init)
	{
		struct prc_context *ctx = &info->ctx;
		int ret = 0;

		struct prc_window *modlib_win = prc_get_freeaddr();
		if (modlib_win == NULL)
		{
			eputs("Error: No memory in window pool.");
			return -1;
		}

		struct prc_window *modloader_win = prc_get_freeaddr();
		if (modloader_win == NULL)
		{
			eputs("Error: No memory in window pool.");
			prc_destroy_window(modlib_win, ctx);
			return -1;
		}

		struct prc_window *search_win = prc_get_freeaddr();
		if (search_win == NULL)
		{
			eputs("Error: No memory in window pool.");
			prc_destroy_window(modlib_win, ctx);
			prc_destroy_window(modloader_win, ctx);
			return -1;
		}

		struct prc_window *modlist_win = prc_get_freeaddr();
		if (modlist_win == NULL)
		{
			eputs("Error: No memory in window pool.");
			prc_destroy_window(modlib_win, ctx);
			prc_destroy_window(modloader_win, ctx);
			prc_destroy_window(search_win, ctx);
			return -1;
		}

		tui_layout6__.modlib_win = modlib_win;
		tui_layout6__.modloader_win = modloader_win;
		tui_layout6__.search_win = search_win;
		tui_layout6__.modlist_win = modlist_win;
		tui_layout6__.ctx = &info->ctx;

		ret = m6_init_windows(info);
		if (ret != 0)
		{
			eputs("Failed to initialize layout.");
			return -1;
		}
	}

	if (m6_draw_layout6() != 0)
	{
		eputs("Failed to draw layout.");
		return -1;
	}

	struct prc_window *modlib_win = tui_layout6__.modlib_win;
	struct prc_window *modloader_win = tui_layout6__.modloader_win;
	struct prc_window *search_win = tui_layout6__.search_win;
	struct prc_window *modlist_win = tui_layout6__.modlist_win;
	// struct prc_context *ctx = tui_layout6__.ctx;

	wnoutrefresh(mtstdbigwin->win);
	wnoutrefresh(modlib_win->win);
	wnoutrefresh(modloader_win->win);
	wnoutrefresh(search_win->win);
	wnoutrefresh(modlist_win->win);
	doupdate();

	uint32_t c;
	while((c = getch()))
	{
		if (c == 'q' || c == 'Q')
			return 0;
	}

	return 0;
}
