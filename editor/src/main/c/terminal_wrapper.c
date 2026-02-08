#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#if defined(__linux)
#include <pty.h>
#elif defined(__OpenBSD__) || defined(__NetBSD__) || defined(__APPLE__)
#include <util.h>
#elif defined(__FreeBSD__) || defined(__DragonFly__)
#include <libutil.h>
#endif

#include <vterm.h>

static int pack_rgb(const VTermColor *c) {
    if (!c) return 0;
    if (c->type == VTERM_COLOR_RGB) {
        return ((int)c->rgb.red << 16) | ((int)c->rgb.green << 8) | (int)c->rgb.blue;
    }
    /* Indexed colors are resolved by libvterm into RGB in many setups, but if not,
       fall back to black. */
    return 0;
}

enum {
    TERM_ATTR_BOLD = 1 << 0,
    TERM_ATTR_UNDERLINE = 1 << 1,
    TERM_ATTR_ITALIC = 1 << 2,
    TERM_ATTR_BLINK = 1 << 3,
    TERM_ATTR_REVERSE = 1 << 4,
    TERM_ATTR_STRIKE = 1 << 5,
    TERM_ATTR_DIM = 1 << 6,
};

static int unicode_to_utf8(uint32_t codepoint, char *out, size_t out_len) {
    if (!out || out_len == 0) return 0;
    if (codepoint == 0) {
        out[0] = '\0';
        return 0;
    }

    if (codepoint <= 0x7F) {
        if (out_len < 2) return 0;
        out[0] = (char)codepoint;
        out[1] = '\0';
        return 1;
    }
    if (codepoint <= 0x7FF) {
        if (out_len < 3) return 0;
        out[0] = (char)(0xC0 | ((codepoint >> 6) & 0x1F));
        out[1] = (char)(0x80 | (codepoint & 0x3F));
        out[2] = '\0';
        return 2;
    }
    if (codepoint <= 0xFFFF) {
        if (out_len < 4) return 0;
        out[0] = (char)(0xE0 | ((codepoint >> 12) & 0x0F));
        out[1] = (char)(0x80 | ((codepoint >> 6) & 0x3F));
        out[2] = (char)(0x80 | (codepoint & 0x3F));
        out[3] = '\0';
        return 3;
    }
    if (codepoint <= 0x10FFFF) {
        if (out_len < 5) return 0;
        out[0] = (char)(0xF0 | ((codepoint >> 18) & 0x07));
        out[1] = (char)(0x80 | ((codepoint >> 12) & 0x3F));
        out[2] = (char)(0x80 | ((codepoint >> 6) & 0x3F));
        out[3] = (char)(0x80 | (codepoint & 0x3F));
        out[4] = '\0';
        return 4;
    }

    out[0] = '\0';
    return 0;
}

static int set_nonblocking(int fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags < 0) return -1;
    if (fcntl(fd, F_SETFL, flags | O_NONBLOCK) < 0) return -1;
    return 0;
}

int term_openpty_spawn(const char *prog, char *const argv[], int *out_master_fd, int *out_child_pid) {
    if (!out_master_fd || !out_child_pid) {
        errno = EINVAL;
        return -1;
    }

    int master_fd = -1;
    int slave_fd = -1;

    if (openpty(&master_fd, &slave_fd, NULL, NULL, NULL) < 0) {
        return -1;
    }

    pid_t pid = fork();
    if (pid < 0) {
        close(master_fd);
        close(slave_fd);
        return -1;
    }

    if (pid == 0) {
        setsid();
        if (ioctl(slave_fd, TIOCSCTTY, NULL) < 0) {
            _exit(127);
        }

        dup2(slave_fd, 0);
        dup2(slave_fd, 1);
        dup2(slave_fd, 2);

        if (slave_fd > 2) close(slave_fd);
        close(master_fd);

        execvp(prog, argv);
        _exit(127);
    }

    close(slave_fd);
    set_nonblocking(master_fd);

    *out_master_fd = master_fd;
    *out_child_pid = (int)pid;
    return 0;
}

int term_resize(int master_fd, int rows, int cols, int xpixel, int ypixel) {
    struct winsize w;
    memset(&w, 0, sizeof(w));
    w.ws_row = (unsigned short)rows;
    w.ws_col = (unsigned short)cols;
    w.ws_xpixel = (unsigned short)xpixel;
    w.ws_ypixel = (unsigned short)ypixel;
    return ioctl(master_fd, TIOCSWINSZ, &w);
}

int term_kill(int pid, int sig) {
    return kill((pid_t)pid, sig);
}

int term_waitpid(int pid, int *out_status, int options) {
    int st = 0;
    pid_t r = waitpid((pid_t)pid, &st, options);
    if (r < 0) return -1;
    if (out_status) *out_status = st;
    return (int)r;
}

size_t term_vterm_screen_get_text(const VTermScreen *screen, char *str, size_t len,
                                 int start_row, int start_col, int end_row, int end_col) {
    VTermRect rect;
    rect.start_row = start_row;
    rect.start_col = start_col;
    rect.end_row = end_row;
    rect.end_col = end_col;
    return vterm_screen_get_text(screen, str, len, rect);
}

int term_vterm_screen_get_cell(
    const VTermScreen *screen,
    int row,
    int col,
    char *out_utf8,
    size_t out_utf8_len,
    int *out_fg_rgb,
    int *out_bg_rgb,
    int *out_attrs,
    int *out_width
) {
    if (!screen) return -1;

    VTermScreenCell cell;
    VTermPos pos;
    pos.row = row;
    pos.col = col;

    if (vterm_screen_get_cell(screen, pos, &cell) == 0) {
        if (out_fg_rgb) *out_fg_rgb = pack_rgb(&cell.fg);
        if (out_bg_rgb) *out_bg_rgb = pack_rgb(&cell.bg);

        if (out_attrs) {
            int a = 0;
            if (cell.attrs.bold) a |= TERM_ATTR_BOLD;
            if (cell.attrs.underline) a |= TERM_ATTR_UNDERLINE;
            if (cell.attrs.italic) a |= TERM_ATTR_ITALIC;
            if (cell.attrs.blink) a |= TERM_ATTR_BLINK;
            if (cell.attrs.reverse) a |= TERM_ATTR_REVERSE;
            if (cell.attrs.strike) a |= TERM_ATTR_STRIKE;
            *out_attrs = a;
        }

        if (out_width) *out_width = cell.width;

        if (out_utf8 && out_utf8_len > 0) {
            out_utf8[0] = '\0';
            if (cell.chars[0]) {
                unicode_to_utf8((uint32_t)cell.chars[0], out_utf8, out_utf8_len);
                out_utf8[out_utf8_len - 1] = '\0';
            }
        }

        return 0;
    }

    return -1;
}

int term_vterm_get_cursor(const VTerm *vt, int *out_row, int *out_col, int *out_visible) {
    if (!vt) return -1;
    VTermState *st = vterm_obtain_state((VTerm *)vt);
    if (!st) return -1;

    VTermPos pos;
    vterm_state_get_cursorpos(st, &pos);
    if (out_row) *out_row = pos.row;
    if (out_col) *out_col = pos.col;
    if (out_visible) *out_visible = 1;
    return 0;
}
