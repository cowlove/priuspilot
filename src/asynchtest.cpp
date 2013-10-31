#include <sys/types.h>
#include <sys/ipc.h>
#include <sys/shm.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdlib.h>
#include <aio.h>

#include <linux/types.h>
#include <linux/videodev2.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <malloc.h>
#include <string.h>

#define PAGE_SIZE 4096
#define PAGE_ROUND(n) ((n + PAGE_SIZE - 1) & (~(PAGE_SIZE - 1)))


struct aiocb *cbs;
int ncb = 1000, nextcb = 0;
int aio_dropped_frames = 0;
int aio_count;

void asynch_write(int fd, const void *buf, int len) { 
	if (cbs == NULL)  {
		cbs = (struct aiocb *)calloc(ncb, sizeof(*cbs));
		for(int n = 0; n < ncb; n++) { 
			struct aiocb *cb = &cbs[n];
			cb->aio_buf = malloc(len + PAGE_SIZE);
			cb->aio_buf = (void *)PAGE_ROUND((unsigned long)cb->aio_buf);
		}
	}
	if (aio_count++ % 100 == 0) {
		int unfinished = 0;
		for(int n = 0; n < ncb; n++) { 
			struct aiocb *cb = &cbs[n];
			if(cb->aio_fildes != 0 && aio_error(cb) == EINPROGRESS)
				unfinished++;
			
		}
		fprintf(stderr, "%d aio_writes in progress\n", unfinished);
	}
	struct aiocb *cb = &cbs[nextcb];
	if (cb->aio_fildes != 0 && aio_error(cb) != 0) {
		fprintf(stderr, "asynch_write(%d) number %d dropped %d frames, aio_error %d %s\n", 
		len, aio_count, aio_dropped_frames++, aio_error(cb), sys_errlist[aio_error(cb)]);
		return;
	}

	void *b = (void *)cb->aio_buf;
	bzero(cb, sizeof(*cb));
	cb->aio_buf = (void *)b;
    memcpy((void *)cb->aio_buf, buf, len);
	cb->aio_fildes = fd;
	cb->aio_nbytes = len;
	cb->aio_offset = 0;
	cb->aio_sigevent.sigev_notify = SIGEV_NONE;
 
	if (aio_write(cb) == -1) {
		fprintf(stderr, "asynch_write error %d frames\n", aio_dropped_frames++);
		return;
	}
	nextcb = (nextcb + 1) % ncb;
}

main(int argc, char **argv) {
	int size = 200 * 1024;
	void *buf = malloc(size);
	int fd = open(argv[1], O_WRONLY | O_CREAT |O_DIRECT | O_TRUNC | O_APPEND, 0644);
	
	for(int n = 0; n < 100000; n++) { 
		usleep(16000);
		asynch_write(fd, (void *)buf, size);
	}
	close(fd);
}
