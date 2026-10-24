/* Experimental pipe-to-POSIX-PTY relay. Compile with Cygwin gcc -pthread.
 * stdin: type:u8, length:u32be, payload. Type 1: input (<=4096 bytes).
 * Type 2: rows:u16be, columns:u16be. stdout: unmodified PTY output.
 * The Windows console is deliberately never read by this process. */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <pthread.h>
#ifdef __APPLE__
#include <util.h>
#else
#include <pty.h>
#endif
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <unistd.h>

static int master_fd, completion[2];
static int write_all(int fd, const void *data, size_t length) {
    const char *p = data;
    while (length) {
        ssize_t n = write(fd, p, length);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) return -1;
        p += n; length -= (size_t)n;
    }
    return 0;
}
static void *copy_output(void *ignored) {
    (void)ignored;
    char buffer[8192];
    int failed = 0;
    for (;;) {
        ssize_t n = read(master_fd, buffer, sizeof buffer);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) break;
        if (write_all(STDOUT_FILENO, buffer, (size_t)n)) { failed = 1; break; }
    }
    (void)write_all(completion[1], failed ? "f" : "e", 1);
    return NULL;
}
static int stop_child(pid_t child) {
    int status = 1 << 8;
    kill(-child, SIGHUP);
    for (int i = 0; i < 100; i++) {
        pid_t result = waitpid(child, &status, WNOHANG);
        if (result == child) return status;
        if (result < 0 && errno != EINTR) return 1 << 8;
        usleep(10000);
    }
    kill(-child, SIGKILL);
    while (waitpid(child, &status, 0) < 0 && errno == EINTR) {}
    return status;
}
int main(int argc, char **argv) {
    if (argc < 4) { fputs("relay rows columns program [args...]\n", stderr); return 64; }
    struct winsize size = { .ws_row = (unsigned short)atoi(argv[1]), .ws_col = (unsigned short)atoi(argv[2]) };
    if (!size.ws_row || !size.ws_col || pipe(completion)) return 65;
    signal(SIGPIPE, SIG_IGN);
    pid_t child = forkpty(&master_fd, NULL, NULL, &size);
    if (child < 0) { perror("forkpty"); return 66; }
    if (child == 0) {
        close(completion[0]); close(completion[1]);
        signal(SIGPIPE, SIG_DFL);
        execvp(argv[3], argv + 3);
        perror("execvp"); _exit(127);
    }
    pthread_t output;
    if (pthread_create(&output, NULL, copy_output, NULL)) { stop_child(child); return 67; }
    int ended = 0, status = 0, failure = 0;
    unsigned char header[5], payload[4096];
    size_t header_at = 0, payload_at = 0;
    uint32_t length = 0;
    int flags = fcntl(STDIN_FILENO, F_GETFL);
    if (flags < 0 || fcntl(STDIN_FILENO, F_SETFL, flags | O_NONBLOCK) < 0) failure = 74;
    while (!failure) {
        struct pollfd fds[] = {{ STDIN_FILENO, POLLIN, 0 }, { completion[0], POLLIN, 0 }};
        int ready = poll(fds, 2, -1);
        if (ready < 0 && errno == EINTR) continue;
        if (ready < 0) { failure = 74; break; }
        if (fds[1].revents) {
            char reason = 'f';
            (void)read(completion[0], &reason, 1);
            ended = reason == 'e';
            if (!ended) failure = 74;
            else if (header_at) failure = 65;
            break;
        }
        if (!fds[0].revents) continue;
        unsigned char *target = header_at < sizeof header ? header + header_at : payload + payload_at;
        size_t remaining = header_at < sizeof header ? sizeof header - header_at : length - payload_at;
        ssize_t n = read(STDIN_FILENO, target, remaining);
        if (n < 0 && (errno == EINTR || errno == EAGAIN)) continue;
        if (n <= 0) { failure = n < 0 ? 74 : header_at ? 65 : 0; break; }
        if (header_at < sizeof header) {
            header_at += (size_t)n;
            if (header_at < sizeof header) continue;
            length = ((uint32_t)header[1] << 24) | ((uint32_t)header[2] << 16)
                | ((uint32_t)header[3] << 8) | header[4];
            if (length > sizeof payload || (header[0] != 1 && header[0] != 2)
                    || (header[0] == 2 && length != 4)) { failure = 65; break; }
            if (length) continue;
        } else {
            payload_at += (size_t)n;
            if (payload_at < length) continue;
        }
        if (header[0] == 1) {
            if (write_all(master_fd, payload, length)) failure = 74;
        } else {
            size.ws_row = ((unsigned short)payload[0] << 8) | payload[1];
            size.ws_col = ((unsigned short)payload[2] << 8) | payload[3];
            if (!size.ws_row || !size.ws_col) failure = 65;
            else if (ioctl(master_fd, TIOCSWINSZ, &size)) failure = 74;
        }
        header_at = payload_at = 0;
    }
    if (ended) {
        while (waitpid(child, &status, 0) < 0 && errno == EINTR) {}
    } else status = stop_child(child);
    close(master_fd);
    pthread_join(output, NULL);
    close(completion[0]); close(completion[1]);
    if (failure) return failure;
    return WIFEXITED(status) ? WEXITSTATUS(status) : WIFSIGNALED(status) ? 128 + WTERMSIG(status) : 1;
}
