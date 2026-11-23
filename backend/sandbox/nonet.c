/*
 * nonet: 인터넷 소켓을 만들 수 없게 막은 뒤 프로그램을 실행한다.
 * 사용: nonet -- <program> [args...]
 *
 * 사용자 제출 코드가 외부 서버, 같은 네트워크의 DB, 클라우드 메타데이터 주소(169.254.x.x)에
 * 접속하지 못하게 seccomp 필터로 socket()을 AF_UNIX 외에는 EACCES로 거부한다.
 * socket()을 거치지 않고 소켓을 만들 수 있는 io_uring도 막는다.
 * 필터는 exec 이후와 자식 프로세스에도 그대로 유지되고 해제할 수 없다.
 * 추가 권한(CAP_SYS_ADMIN, CAP_NET_ADMIN)이 필요 없어 도커 기본 권한·AWS Fargate에서도 동작한다.
 * ProcessUtils가 setpriv(권한 없는 계정 전환) 다음 단계로 실행한다.
 */
#include <errno.h>
#include <linux/audit.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <sys/syscall.h>
#include <unistd.h>

#if defined(__x86_64__)
#define NONET_ARCH AUDIT_ARCH_X86_64
#elif defined(__aarch64__)
#define NONET_ARCH AUDIT_ARCH_AARCH64
#else
#error "nonet: x86_64, aarch64만 지원한다"
#endif

#define DENY(err) BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | ((err) & SECCOMP_RET_DATA))
#define ALLOW BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW)
#define KILL BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_KILL_PROCESS)

int main(int argc, char **argv) {
    if (argc < 3 || strcmp(argv[1], "--") != 0) {
        fprintf(stderr, "usage: nonet -- program [args...]\n");
        return 2;
    }

    struct sock_filter filter[] = {
        /* 다른 아키텍처의 시스템 콜 표(예: x86_64에서 int 0x80)로 필터를 우회하지 못하게 한다 */
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, arch)),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, NONET_ARCH, 1, 0),
        KILL,
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr)),
#if defined(__x86_64__)
        /* x32 ABI 시스템 콜 번호(0x40000000 이상)로 우회하는 것도 막는다 */
        BPF_JUMP(BPF_JMP | BPF_JGE | BPF_K, 0x40000000, 0, 1),
        KILL,
#endif
#ifdef __NR_io_uring_setup
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_io_uring_setup, 0, 1),
        DENY(EPERM),
#endif
        /* socket(domain, ...): domain이 AF_UNIX면 허용, 그 외(AF_INET, AF_INET6, AF_NETLINK 등)는 거부 */
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_socket, 0, 3),
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, args[0])),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, AF_UNIX, 1, 0),
        DENY(EACCES),
        ALLOW,
    };
    struct sock_fprog prog = {
        .len = (unsigned short)(sizeof(filter) / sizeof(filter[0])),
        .filter = filter,
    };

    /* 권한 없는 프로세스가 seccomp 필터를 걸려면 no_new_privs가 먼저 켜져 있어야 한다 */
    if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0) {
        perror("nonet: PR_SET_NO_NEW_PRIVS");
        return 126;
    }
    if (prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &prog) != 0) {
        perror("nonet: PR_SET_SECCOMP");
        return 126;
    }
    execvp(argv[2], &argv[2]);
    perror("nonet: exec");
    return 127;
}
