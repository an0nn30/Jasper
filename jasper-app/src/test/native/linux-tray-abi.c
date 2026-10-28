#define _GNU_SOURCE
#include <stddef.h>
#include <stdio.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <xcb/xcb.h>
#include <xcb/xcbext.h>
#include <xcb/render.h>
#include <X11/Xauth.h>

#define SIZE(t) printf(#t "=%zu\n", sizeof(t))
#define OFFSET(t, f) printf(#t "." #f "=%zu\n", offsetof(t, f))
#define CONSTANT(c) printf(#c "=%ld\n", (long)(c))

int main(void) {
    SIZE(void *); SIZE(long); SIZE(xcb_window_t); SIZE(xcb_atom_t);
    SIZE(xcb_void_cookie_t); OFFSET(xcb_void_cookie_t, sequence);
    SIZE(xcb_generic_event_t); OFFSET(xcb_generic_event_t, response_type);
    OFFSET(xcb_generic_event_t, sequence); OFFSET(xcb_generic_event_t, full_sequence);
    SIZE(xcb_generic_error_t); OFFSET(xcb_generic_error_t, error_code);
    OFFSET(xcb_generic_error_t, sequence); OFFSET(xcb_generic_error_t, full_sequence);
    SIZE(xcb_client_message_event_t); OFFSET(xcb_client_message_event_t, format);
    OFFSET(xcb_client_message_event_t, window); OFFSET(xcb_client_message_event_t, type);
    OFFSET(xcb_client_message_event_t, data);
    SIZE(xcb_auth_info_t); OFFSET(xcb_auth_info_t, namelen); OFFSET(xcb_auth_info_t, name);
    OFFSET(xcb_auth_info_t, datalen); OFFSET(xcb_auth_info_t, data);
    SIZE(Xauth); OFFSET(Xauth, family); OFFSET(Xauth, address_length); OFFSET(Xauth, address);
    OFFSET(Xauth, number_length); OFFSET(Xauth, number); OFFSET(Xauth, name_length);
    OFFSET(Xauth, name); OFFSET(Xauth, data_length); OFFSET(Xauth, data);
    SIZE(struct pollfd); OFFSET(struct pollfd, fd); OFFSET(struct pollfd, events);
    OFFSET(struct pollfd, revents);
    SIZE(struct sockaddr_un); OFFSET(struct sockaddr_un, sun_family); OFFSET(struct sockaddr_un, sun_path);
    SIZE(struct stat); OFFSET(struct stat, st_dev); OFFSET(struct stat, st_ino);
    OFFSET(struct stat, st_mode); OFFSET(struct stat, st_size);
    OFFSET(struct stat, st_mtim.tv_sec); OFFSET(struct stat, st_mtim.tv_nsec);
    OFFSET(struct stat, st_ctim.tv_sec); OFFSET(struct stat, st_ctim.tv_nsec);
    SIZE(xcb_setup_t); OFFSET(xcb_setup_t, status); OFFSET(xcb_setup_t, length);
    OFFSET(xcb_setup_t, vendor_len); OFFSET(xcb_setup_t, roots_len);
    OFFSET(xcb_setup_t, pixmap_formats_len); OFFSET(xcb_setup_t, image_byte_order);
    SIZE(xcb_screen_t); OFFSET(xcb_screen_t, root); OFFSET(xcb_screen_t, root_visual);
    OFFSET(xcb_screen_t, root_depth); OFFSET(xcb_screen_t, allowed_depths_len);
    SIZE(xcb_depth_t); OFFSET(xcb_depth_t, depth); OFFSET(xcb_depth_t, visuals_len);
    SIZE(xcb_visualtype_t); OFFSET(xcb_visualtype_t, visual_id);
    OFFSET(xcb_visualtype_t, red_mask); OFFSET(xcb_visualtype_t, green_mask);
    OFFSET(xcb_visualtype_t, blue_mask);
    SIZE(xcb_format_t); OFFSET(xcb_format_t, depth); OFFSET(xcb_format_t, bits_per_pixel);
    OFFSET(xcb_format_t, scanline_pad);
    SIZE(xcb_generic_reply_t); OFFSET(xcb_generic_reply_t, response_type);
    OFFSET(xcb_generic_reply_t, sequence); OFFSET(xcb_generic_reply_t, length);
    OFFSET(xcb_intern_atom_reply_t, atom); OFFSET(xcb_get_selection_owner_reply_t, owner);
    CONSTANT(AF_UNIX); CONSTANT(SOCK_STREAM); CONSTANT(SOCK_NONBLOCK); CONSTANT(SOCK_CLOEXEC);
    CONSTANT(SHUT_RDWR); CONSTANT(SOL_SOCKET); CONSTANT(SO_ERROR);
    CONSTANT(EINPROGRESS); CONSTANT(EAGAIN); CONSTANT(EINTR);
    CONSTANT(POLLIN); CONSTANT(POLLOUT); CONSTANT(POLLERR); CONSTANT(POLLHUP);
    CONSTANT(O_NONBLOCK); CONSTANT(O_CLOEXEC); CONSTANT(O_NOFOLLOW); CONSTANT(F_DUPFD_CLOEXEC);
    CONSTANT(S_IFMT); CONSTANT(S_IFREG); CONSTANT(FamilyLocal); CONSTANT(FamilyWild);
    return 0;
}
