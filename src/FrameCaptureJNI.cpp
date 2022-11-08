#include <jni.h>
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


#include "FrameCaptureJNI.h"

#define PAGE_SIZE 4096
#define PAGE_ROUND(n) ((n + PAGE_SIZE - 1) & (~(PAGE_SIZE - 1)))
/*
 * Class:     FrameCaptureJNI
 * Method:    grabFrame
 * Signature: (Ljava/lang/String;II)[I
 */

// must run with LD_PRELOAD="/usr/lib/libv4l/v4l1compat.so" 

// compile w/ 
// g++ FrameCaptureJNI.c CannyEdgeDetector.cpp -I /usr/lib/jvm/java-6-openjdk/include/ -O3  -o  libframecapturejni.so -lm -shared



// create java headers with javac FrameCaptureJNI.java, javah FrameCaptureJNI



int openvid(const char *filename, int w, int h);
extern "C" int *canny(int, int, unsigned char *);
int get_brightness_adj(unsigned char *image, long size, int *brightness);


//static unsigned char *obuf = NULL, *line = NULL;
struct buffer {
        void *                  start;
        size_t                  length;
};

struct config {
	int fd;
	char filename[256];
	int resWidth;
	int resHeight;
	int windX;
	int windY;
	int windWidth;
	int windHeight;
	int flip;
	bool reopenCaptureFile;
	char captureFile[256];
	int captureFd;
	int captureFileCount; 
	int captureFileSize; 
	int nightMode;
	long timestamp;
	long currentCapFileSize;
	bool live;
	int currentCapFileCount;
	int count; // frame count
	struct buffer *buffers;
	long frameTimestamp;
	long lastFrameTimestamp;
	unsigned char *obuf;
	int totalFrames;
	int droppedFrames;
	int lastDroppedFrames;
	int lastMs;
	int maxFrameMs; /* max ms per frame before switching to night mode */
	int rawRecordSkip; /* skip rate for dumping/recording raw frame data to disk */
	//struct aiocbp aio;
	struct {
		long timestamp;
		long steer;
	} logData; 
} *configs[10];


void v4l2mmap_open(config *);
void v4l2mmap_close(config *);
int v4l2mmap_poll(config *);
void v4l2mmap_wait_frame(config *, unsigned char *);



config *getCameraObject(int camIndex) {
	if (configs[camIndex] == NULL) {
		configs[camIndex] = (config *)malloc(sizeof(config));
		bzero(configs[camIndex], sizeof(config));
	}
	return configs[camIndex];
}

void setLogData(int id, long steer, long timestamp) { 
	config *c = getCameraObject(id);
	c->logData.steer = steer;
	c->logData.timestamp = timestamp; 

}

JNIEXPORT void JNICALL Java_FrameCaptureJNI_configure
  (JNIEnv *env, jobject o, jint cameraIndex, jstring fname, jint resWidth, jint resHeight, jint windX, jint windY, 
	jint windWidth, jint windHeight, jboolean flip, jstring capfname, jint capsize, 
	jint capcount, jint maxms, jint recordSkip) {

		config *conf = getCameraObject(cameraIndex);
		bzero(conf, sizeof(*conf));
		jboolean iscopy;
		const char *fn = (env)->GetStringUTFChars(fname, &iscopy);
		strcpy(conf->filename, fn);
		(env)->ReleaseStringUTFChars(fname, fn);
		
		fn = (env)->GetStringUTFChars(capfname, &iscopy);
		strcpy(conf->captureFile, fn);
		(env)->ReleaseStringUTFChars(capfname, fn);

		conf->resWidth = resWidth;
		conf->resHeight = resHeight;
		conf->windX = windX;
		conf->windY = windY;
		conf->windWidth= windWidth;
		conf->windHeight= windHeight;
		conf->flip = flip;
		conf->captureFileCount = capcount;
		conf->captureFileSize = capsize;
		conf->reopenCaptureFile = false;
		conf->maxFrameMs = maxms;
		conf->rawRecordSkip = recordSkip;
		
		conf->captureFd = -1;
		conf->fd = -1;
		conf->obuf = NULL;
  }
  
  

JNIEXPORT void JNICALL Java_FrameCaptureJNI_discardFrame
  (JNIEnv *, jobject, jint cameraIndex, jstring)  { 
	config *conf = getCameraObject(cameraIndex);
    int n = read(conf->fd, conf->obuf, conf->resWidth * conf->resHeight * 3);
}

JNIEXPORT void JNICALL Java_FrameCaptureJNI_renameCurrentCaptureFile
  (JNIEnv *, jobject, jint cameraIndex, jstring) {
		config *conf = getCameraObject(cameraIndex);
	  
}

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
		if (unfinished != 0 || aio_dropped_frames != 0) 
			fprintf(stderr, "%d aio_writes in progress, %d dropped\n", unfinished,
			aio_dropped_frames);
	}
	struct aiocb *cb = &cbs[nextcb];
	if (cb->aio_fildes != 0 && aio_error(cb) != 0) {
		//fprintf(stderr, "asynch_write(%d) number %d dropped %d frames, aio_error %d %s\n", 
		//len, aio_count, aio_dropped_frames++, aio_error(cb), sys_errlist[aio_error(cb)]);
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



extern "C" void process_image(config *conf, const unsigned char *in, unsigned char *out,
	struct timeval *tv) {
	if (tv != NULL) 
		conf->frameTimestamp = (long)tv->tv_usec / 1000 + (long)tv->tv_sec * 1000;
	if (conf->flip) { 
			printf("TODO- flip for yuyv\n");
			exit(-1);
	} else { 
			memcpy(out, in, conf->windHeight * conf->windWidth * 2);
	}
		
	if (conf->captureFd > 0 && (conf->rawRecordSkip < 1 || (conf->count % conf->rawRecordSkip) == 0)
		) {
			// TODO - nonblocking AIO
			/*
			int r = write(conf->captureFd, &conf->frameTimestamp, 8);
			conf->currentCapFileSize += r;
			r = write(conf->captureFd, in, conf->windHeight * conf->windWidth * 2);
			conf->currentCapFileSize += r;
			//fsync(conf->captureFd);
			*/

			memcpy((void *)in, (void *)&conf->frameTimestamp, 8); // stash the timestamp in the first bytes of the image
			memcpy((void *)(in + 8), (void *)&conf->logData, sizeof(conf->logData)); // and the logged steering data 

			asynch_write(conf->captureFd, in, PAGE_ROUND(conf->windHeight * conf->windWidth * 2));
			//if (write(conf->captureFd, in, AGE_ROUND(conf->windHeight * conf->windWidth * 2))
			//	perror("write()");
			
	}


}

int get_ctl(int fd, int id) { 
		struct v4l2_control ctl;
		memset(&ctl, 0, sizeof(ctl));

		ctl.id = id;
		if (ioctl(fd, VIDIOC_G_CTRL, &ctl) < 0) 
			perror("VIDIOC_G_CTRL");
		return ctl.value;
	}

void set_ctl(int fd, int id, int val) { 
		struct v4l2_control ctl;
		memset(&ctl, 0, sizeof(ctl));
		//printf("set ctl %d to %d\n", id, val);
		ctl.id = id;
		if (ioctl(fd, VIDIOC_G_CTRL, &ctl) < 0) 
			perror("VIDIOC_G_CTRL");

		ctl.id = id;
		ctl.value = val;
		if (ioctl(fd, VIDIOC_S_CTRL, &ctl) < 0) {
			perror("VIDIOC_G_CTRL");
		}
}


int get_ctl_max(int fd, int id) { 
			struct v4l2_queryctrl query;
			memset(&query, 0, sizeof(query));
			query.id = id;
			if (ioctl(fd, VIDIOC_QUERYCTRL, &query) < 0) 
					perror("VIDIOC_QUERYCTRL:");
			return query.maximum;
}			


int get_ctl_default(int fd, int id) { 
			struct v4l2_queryctrl query;
			memset(&query, 0, sizeof(query));
			query.id = id;
			if (ioctl(fd, VIDIOC_QUERYCTRL, &query) < 0) 
					perror("VIDIOC_QUERYCTRL:");
			return query.default_value;
}			


int get_ctl_min(int fd, int id) { 
			struct v4l2_queryctrl query;
			memset(&query, 0, sizeof(query));
			query.id = id;
			if (ioctl(fd, VIDIOC_QUERYCTRL, &query) < 0) 
					perror("VIDIOC_QUERYCTRL:");
			return query.minimum;
}			
void set_ctl_to_max(int fd, int id) { 
	set_ctl(fd, id, get_ctl_max(fd, id) - 1);
	set_ctl(fd, id, get_ctl_max(fd, id));
}

void set_ctl_to_default(int fd, int id) { 
	set_ctl(fd, id, get_ctl_default(fd, id));
}

	
void enum_v4l2_controls(int fd) { 
			struct v4l2_control ctl;
			struct v4l2_queryctrl query;
		
			memset(&ctl, 0, sizeof(ctl));
			memset(&query, 0, sizeof(query));

			printf("Avalaible V4L2 controls:\n");
			for(query.id = V4L2_CID_BASE; query.id <= V4L2_CID_LASTP1; query.id++) { 
				if (ioctl(fd, VIDIOC_QUERYCTRL, &query) >= 0) { 
					printf("\t%25s\t %d-%d +/-%d, default %d  (%d)\n", 
						query.name, query.minimum, query.maximum, query.step,
						query.default_value, query.id);				
				}
			}
			for(query.id = V4L2_CID_CAMERA_CLASS_BASE; query.id <= V4L2_CID_CAMERA_CLASS_BASE 
			+ 100; query.id++) { 
				if (ioctl(fd, VIDIOC_QUERYCTRL, &query) >= 0) { 
					printf("\t%25s\t %d-%d +/-%d, default %d\n", 
						query.name, query.minimum, query.maximum, query.step,
						query.default_value);				
				}
			}

	
}


void max_brightness(int fd, bool max) { 
			if (max) { 
				set_ctl(fd, V4L2_CID_EXPOSURE_AUTO, V4L2_EXPOSURE_APERTURE_PRIORITY);
				set_ctl(fd, V4L2_CID_EXPOSURE_AUTO, V4L2_EXPOSURE_MANUAL);
				set_ctl(fd, V4L2_CID_EXPOSURE_ABSOLUTE, 156);
				
				set_ctl(fd, V4L2_CID_EXPOSURE_AUTO, V4L2_EXPOSURE_APERTURE_PRIORITY);
				set_ctl(fd, V4L2_CID_EXPOSURE_AUTO, V4L2_EXPOSURE_MANUAL);
				//set_ctl_to_max(V4L2_CID_BRIGHTNESS);
				//set_ctl_to_max(V4L2_CID_CONTRAST);
				set_ctl_to_max(fd, V4L2_CID_SHARPNESS);
				//set_ctl_to_max(V4L2_CID_SATURATION);
				//set_ctl_to_max(V4L2_CID_BRIGHTNESS);
				set_ctl(fd, V4L2_CID_BRIGHTNESS, 100);
			} else { 
				set_ctl(fd, V4L2_CID_EXPOSURE_AUTO, V4L2_EXPOSURE_APERTURE_PRIORITY);
				set_ctl_to_default(fd, V4L2_CID_BRIGHTNESS);
				set_ctl_to_max(fd, V4L2_CID_SHARPNESS);
				//set_ctl(V4L2_CID_EXPOSURE_AUTO, V4L2_EXPOSURE_APERTURE_PRIORITY);
				//set_ctl(V4L2_CID_EXPOSURE_ABSOLUTE, 156);
				//set_ctl_to_max(V4L2_CID_SHARPNESS);
					//set_ctl(V4L2_CID_EXPOSURE_AUTO, V4L2_EXPOSURE_APERTURE_PRIORITY);
				//set_ctl_to_max(V4L2_CID_SHARPNESS);
			}
}

JNIEXPORT jint JNICALL Java_FrameCaptureJNI_grabFrame
  (JNIEnv *env, jobject obj, jint cameraIndex, jobject ib) { 
	caddr_t m; 
    jboolean iscopy;
	int newbright;
    struct stat finfo;
	int n;
  
	config *conf = getCameraObject(cameraIndex);
	int w = conf->resWidth;
	int h = conf->resHeight;

    unsigned char *buf = (unsigned char *)(env)->GetDirectBufferAddress(ib);

	if ((conf->captureFileSize != 0 && conf->currentCapFileSize > conf->captureFileSize &&
		conf->captureFd >= 0) || conf->reopenCaptureFile) {
			if (conf->captureFd >= 0) 
				close(conf->captureFd);
			conf->currentCapFileCount++;
			if (conf->captureFileCount > 0) 
				conf->currentCapFileCount %= conf->captureFileCount;
			conf->captureFd = -1;
			conf->reopenCaptureFile = false;
	}
	if (conf->captureFd < 0 && strlen(conf->captureFile)  > 0) {
		char fname[256];
		conf->currentCapFileSize = 0;
		sprintf(fname, conf->captureFile, conf->currentCapFileCount); 
		conf->captureFd = open(fname, O_WRONLY | O_CREAT | O_DIRECT | O_TRUNC | O_APPEND, 0644);
	}
		
    if (conf->fd < 0) {
		v4l2mmap_open(conf);
		//openvid(conf->filename, conf->resWidth, conf->resHeight);
		if (conf->fd > 0) {
			conf->live = 1;
			enum_v4l2_controls(conf->fd);
		}
	}
	if (conf->fd < 0) {
		conf->live = 0;
		printf("opening normal file %s\n", conf->filename);
		if (strcmp(conf->filename, "stdin") == 0) {
				conf->fd = 0; 
		} else
			    conf->fd = open(conf->filename, O_RDONLY);
	}
	//buf = malloc(win.width * win.height * sizeof(int));

	long long t;
	
	static long long l = 0;
	
	if (!conf->live) {
		if (0) {
			// Uncomment this to throttle the file playback to 30fps
			// TODO make a command line arg.
			static long long last = 0;
			struct timeval t;
			gettimeofday(&t, NULL);
			long long ms = t.tv_sec * 1000 + t.tv_usec / 1000;
			int waittime = 40 - (ms - last);
			if (waittime > 0)
				usleep(waittime * 1000);
			last = ms;
		}
		
		if (conf->obuf == NULL) 
			conf->obuf = (unsigned char *)malloc(PAGE_ROUND(h * w * 2));
		int needed = PAGE_ROUND(w * h * 2);
		unsigned char *p = conf->obuf;
		do { 
			n = read(conf->fd, p, needed);
			needed -= n;
			p += n;
		} while(needed > 0 && n > 0);
		memcpy((void *)&conf->frameTimestamp, (void *)conf->obuf, 8);
		process_image(conf, conf->obuf, buf, 0);
	} else {
		do {
			v4l2mmap_wait_frame(conf, buf);
		} while(v4l2mmap_poll(conf) != 0);
		n = w * h * 2;
	}
	
	


	//rgb32toRgb24((int *)b2, (unsigned char *)buf, win.height * win.width);
	
    if (n <= 0) {
		close(conf->fd);
		conf->fd = -1;
	}

    
    //rgb24toHsl24(buf, obuf, w * h);
    //bcopy(obuf, buf, w * h * 3);
    
    int fms = conf->frameTimestamp - conf->lastFrameTimestamp;


	conf->count++;

	// HACK - set day/night exposure settings about 1 second into operation
	if (conf->live) {
		if (conf->nightMode) { 
			// Bad hack - call max_brightness to put camera  
			if (conf->count == 20) {
				max_brightness(conf->fd, true);
			}
			if (conf->count < 255) 
				set_ctl(conf->fd, V4L2_CID_BRIGHTNESS, get_ctl(conf->fd, V4L2_CID_BRIGHTNESS) + 1);
			else if (conf->count % 5) { 
				int newbright;
				get_brightness_adj(buf, conf->windHeight * conf->windWidth * 2, &newbright);
				int val = get_ctl(conf->fd, V4L2_CID_BRIGHTNESS);
				
				int step = 1;
				if (newbright > step)  
					set_ctl(conf->fd, V4L2_CID_BRIGHTNESS, val + step);
				else if (newbright < -step) 
					set_ctl(conf->fd, V4L2_CID_BRIGHTNESS, val - step);
				val = get_ctl(conf->fd, V4L2_CID_BRIGHTNESS);
				if (val < 65) { 
					printf("SWITCHING TO DAY MODE, brightness was %d\n", val);
					conf->nightMode = false;
					conf->count = conf->lastMs = 0;
				}		
			}
		} else {
			if (conf->count == 20) {
				max_brightness(conf->fd, false);
			}
			// check to see if we need to switch to night mode
			static const int modeCheckInterval = 2; // seconds
			static const int expectedFPS = 30;
			if (!conf->nightMode && conf->count > 30 && 
				conf->count % (expectedFPS * modeCheckInterval) == 0) {
				if (conf->droppedFrames == conf->lastDroppedFrames) {
					if (conf->lastMs != 0) {
						int avgms = (conf->frameTimestamp - conf->lastMs) / (expectedFPS * modeCheckInterval);
						if (conf->maxFrameMs != 0 && avgms > conf->maxFrameMs) { 
							printf("SWITCHING TO NIGHT MODE, frame ms was %d\n", avgms);
							// reset conf->count to 0 so frail mode-switching 
							// code can run. 
							conf->nightMode = true;
							conf->count = 0;
							conf->lastMs = 0;
						}
					}
				}
				conf->lastDroppedFrames = conf->droppedFrames;
				conf->lastMs = conf->frameTimestamp;
			}
		}
	}
	
    

    //int *cbuf = canny(w, h, buf); 

    //rgb32toRgb24(cbuf, buf, w * h);

    //fprintf(stderr, "Return %d\n", n);
    return n; 
}


JNIEXPORT jlong JNICALL Java_FrameCaptureJNI_getFrameTimestamp
  (JNIEnv *, jobject, jint cameraIndex) {
	  config *conf = getCameraObject(cameraIndex);
	  return conf->frameTimestamp;
} 
	  

#define TARGET_LUMINANCE 125
int get_brightness_adj(unsigned char *image, long size, int *brightness) {
  long i, tot = 0;
  for (i=0; i<size; i += 2)
	  tot += image[i];
  *brightness = (TARGET_LUMINANCE - tot/(size / 2));
  return !((tot/(size)) >= 126 && (tot/(size)) <= 130);
}


/*
int openvid(const char *filename, int w, int h) { 
	struct video_window win;
	struct video_capability cap;
	struct video_picture vpic;  
	unsigned char *buffer, *src;
	int r = 0, g = 0, b = 0;
	unsigned int i;
	int n;

	  fd = open(filename, O_RDONLY);

	  if (fd < 0) {
			if (!alreadyComplained)
			perror(filename);
			alreadyComplained = 1;
			return fd;
	  }
	  alreadyComplained = 0;

	  if (ioctl(fd, VIDIOCGCAP, &cap) < 0) {
			perror("VIDIOGCAP");
			fprintf(stderr, "( not a video4linux device?)\n");
			close(fd);
			return fd = -1;
	  }

	  if (ioctl(fd, VIDIOCGWIN, &win) < 0) {
			perror("VIDIOCGWIN");
			close(fd);
			return fd = -1;
	  }

	  win.height = h;
	  win.width = w;
	  if (ioctl(fd, VIDIOCSWIN, &win) < 0) {
			perror("VIDIOCSWIN");
			close(fd);
			return fd = -1;
	  }



	  if (cap.type & VID_TYPE_MONOCHROME) {
			  fprintf(stderr, "Unable to find a supported COLOR format.\n");
			  close(fd);
				  return fd = -1;
	  } 

		if (ioctl(fd, VIDIOCGPICT, &vpic) < 0) {
				perror("VIDIOCGPICT");
				close(fd);
				return fd = -1;
		}

	  //vpic.palette=VIDEO_PALETTE_RGB24;
	  vpic.palette=VIDEO_PALETTE_YUYV;
	  vpic.depth = 16;
	  if(ioctl(fd, VIDIOCSPICT, &vpic) < 0) {
			  fprintf(stderr, "palette %d depth %d didn't work\n", vpic.palette, 				vpic.depth);
			close(fd);
			return fd = -1;
	  }

	  fprintf(stderr, "Video device '%s' opened at %dx%d\n", filename, w, h);  
	  return fd;
}
*/
/*
 * Class:     FrameCaptureJNI
 * Method:    findTemplate
 * Signature: ([I)D
 */
JNIEXPORT jdouble JNICALL Java_FrameCaptureJNI_findTemplate
  (JNIEnv *, jobject, jintArray) {
	  jdouble r = 0;
	  return r;
  }

/*
 * Class:     FrameCaptureJNI
 * Method:    setTemplate
 * Signature: (IIII)V
 */
JNIEXPORT void JNICALL Java_FrameCaptureJNI_setTemplate
  (JNIEnv *, jobject, jint, jint, jint, jint) { 
	  
  }


JNIEXPORT void JNICALL Java_FrameCaptureJNI_setCaptureFile
  (JNIEnv *env, jobject, jint cameraIndex, jstring f) { 
    jboolean iscopy;
	const char *fname = (env)->GetStringUTFChars(f, &iscopy);
	config *conf = getCameraObject(cameraIndex);
	conf->reopenCaptureFile = true;
	strcpy(conf->captureFile, fname);
	(env)->ReleaseStringUTFChars(f, fname);
  }
  
  
JNIEXPORT void JNICALL Java_FrameCaptureJNI_close
  (JNIEnv *, jobject, jint cameraIndex) {
	config *conf = getCameraObject(cameraIndex);
	if (conf->fd > 0) 
		close(conf->fd);
	conf->fd = -1;
}


/*
 *  V4L2 video capture example
 *
 *  This program can be used and distributed without restrictions.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

#include <getopt.h>             /* getopt_long() */

#include <fcntl.h>              /* low-level i/o */
#include <unistd.h>
#include <errno.h>
#include <malloc.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/time.h>
#include <sys/mman.h>
#include <sys/ioctl.h>

#include <asm/types.h>          /* for videodev2.h */

#include <linux/videodev2.h>

#define CLEAR(x) memset (&(x), 0, sizeof (x))



static unsigned int     n_buffers       = 0;

static void stop_capturing();
static void
errno_exit                      (const char *           s)
{
        fprintf (stderr, "%s error %d, %s\n",
                 s, errno, strerror (errno));
		//close(conf->fd);
		printf("closed!");
        exit (EXIT_FAILURE);
}

static int
xioctl                          (int                    fd,
                                 int                    request,
                                 void *                 arg)
{
        int r;

        do r = ioctl (fd, request, arg);
        while (-1 == r && EINTR == errno);

        return r;
}

void process_image(const unsigned char *, unsigned char *, struct timeval *);
/*
static void
process_image                   (const void *           p)
{
        fputc ('.', stdout);
        fflush (stdout);
}
*/


int
read_frame                      (config *conf, unsigned char *arg)
{
        struct v4l2_buffer buf;
        unsigned int i;

		CLEAR (buf);
		buf.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
		buf.memory = V4L2_MEMORY_MMAP;
		int errors = 0;

		while(-1 == xioctl (conf->fd, VIDIOC_DQBUF, &buf)) {
				perror("VIDIOC_DQBUF");
				switch (errno) {
				case EAGAIN:
						return 0;

				case EIO:
						/* Could ignore EIO, see spec. */

						/* fall through */

				default:
					perror("VIDIOC_DQBUF()");
					usleep(50000);
					if (errors++ > 30) {
						errno_exit("VIDIOC_DQBUF()");
					}
					//v4l2mmap_close(conf);
					//v4l2mmap_open(conf);
				}
		}

		assert (buf.index < n_buffers);

		conf->totalFrames++;
		if (v4l2mmap_poll(conf) != 0) {
			// ugly: others waiting, just discard this one 
			//printf("v4l2.c: %d frames dropped out of %d\n", conf->droppedFrames++, conf->totalFrames);
		} else { 
			process_image(conf, (const unsigned char *)conf->buffers[buf.index].start, 
				arg, &buf.timestamp);
		}
		
		if (-1 == xioctl (conf->fd, VIDIOC_QBUF, &buf)) {
				fprintf(stderr, "VIDIOC_QBUF failed for device '%s'\n", conf->filename);
				errno_exit ("VIDIOC_QBUF");
		}
        return 1;
}

int
v4l2mmap_poll                        (config *conf)
{
		fd_set fds;
		struct timeval tv;

		FD_ZERO (&fds);
		FD_SET (conf->fd, &fds);

		/* Timeout. */
		tv.tv_sec = 0;
		tv.tv_usec = 0;

		return select (conf->fd + 1, &fds, NULL, NULL, &tv);
}					


void
v4l2mmap_wait_frame                        (config *conf, unsigned char *b)
{
        unsigned int count;

        count = 1;

        while (count-- > 0) {
                for (;;) {
                        fd_set fds;
                        struct timeval tv;
                        int r =1;

                        FD_ZERO (&fds);
                        FD_SET (conf->fd, &fds);

                        /* Timeout. */
                        tv.tv_sec = 5;
                        tv.tv_usec = 0;

                        r = select (conf->fd + 1, &fds, NULL, NULL, &tv);
						//printf("select returned %d\n", r);
						
                        if (-1 == r) {
                                if (EINTR == errno)
                                        continue;

                                errno_exit ("select");
                        }

                        if (0 == r) {
                                fprintf (stderr, "select timeout\n");
                                //exit (EXIT_FAILURE);
                        }

                        if (read_frame (conf, b))
                                break;
        
                        /* EAGAIN - continue select loop. */
                }
        }
}

void
stop_capturing                  (config *conf)
{
        enum v4l2_buf_type type;
		type = V4L2_BUF_TYPE_VIDEO_CAPTURE;

		if (-1 == xioctl (conf->fd, VIDIOC_STREAMOFF, &type))
				perror ("VIDIOC_STREAMOFF");

}

static void
start_capturing                 (config *conf)
{
        unsigned int i;
        enum v4l2_buf_type type;

		for (i = 0; i < n_buffers; ++i) {
                struct v4l2_buffer buf;

				CLEAR (buf);

				buf.type        = V4L2_BUF_TYPE_VIDEO_CAPTURE;
				buf.memory      = V4L2_MEMORY_MMAP;
				buf.index       = i;

				if (-1 == xioctl (conf->fd, VIDIOC_QBUF, &buf)) {
					fprintf(stderr, "VIDIOC_QBUF failed for device '%s'\n", conf->filename);
					//errno_exit ("VIDIOC_QBUF");
				}	
		}
		type = V4L2_BUF_TYPE_VIDEO_CAPTURE;

		if (-1 == xioctl (conf->fd, VIDIOC_STREAMON, &type))
				perror ("VIDIOC_STREAMON");
}

static void
uninit_device                   (config *conf)
{
        unsigned int i;

		for (i = 0; i < n_buffers; ++i)
			if (-1 == munmap (conf->buffers[i].start, conf->buffers[i].length))
					perror ("munmap");
        //free (conf->buffers);
		conf->buffers = NULL;
}

static void
init_read                       (config *conf, unsigned int           buffer_size)
{
        conf->buffers = (buffer *)calloc (1, sizeof (*conf->buffers));
        if (!conf->buffers) {
                fprintf (stderr, "Out of memory\n");
                exit (EXIT_FAILURE);
        }

        conf->buffers[0].length = buffer_size;
        conf->buffers[0].start = malloc (buffer_size);

        if (!conf->buffers[0].start) {
                fprintf (stderr, "Out of memory\n");
                exit (EXIT_FAILURE);
        }
}

static void
init_mmap                       (config *conf)
{
        struct v4l2_requestbuffers req;

        CLEAR (req);

        req.count               = 4;
        req.type                = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        req.memory              = V4L2_MEMORY_MMAP;

        if (-1 == xioctl (conf->fd, VIDIOC_REQBUFS, &req)) {
                if (EINVAL == errno) {
                        fprintf (stderr, "%s does not support "
                                 "memory mapping\n", conf->filename);
                        exit (EXIT_FAILURE);
                } else {
                        errno_exit ("VIDIOC_REQBUFS");
                }
        }

        if (req.count < 2) {
                fprintf (stderr, "Insufficient buffer memory on %s\n",
                         conf->filename);
                exit (EXIT_FAILURE);
        }

        conf->buffers = (buffer *)calloc (req.count, sizeof (*conf->buffers));

        if (!conf->buffers) {
                fprintf (stderr, "Out of memory\n");
                exit (EXIT_FAILURE);
        }

        for (n_buffers = 0; n_buffers < req.count; ++n_buffers) {
                struct v4l2_buffer buf;

                CLEAR (buf);

                buf.type        = V4L2_BUF_TYPE_VIDEO_CAPTURE;
                buf.memory      = V4L2_MEMORY_MMAP;
                buf.index       = n_buffers;

                void *p  = memalign (getpagesize(), 1024 * 1024 * 16);



                if (-1 == xioctl (conf->fd, VIDIOC_QUERYBUF, &buf))
                        errno_exit ("VIDIOC_QUERYBUF");

                conf->buffers[n_buffers].length = buf.length;
                conf->buffers[n_buffers].start =
                        mmap (p,
                              buf.length,
                              PROT_READ | PROT_WRITE /* required */,
                              MAP_SHARED /* recommended */,
                              conf->fd, buf.m.offset);

                if (MAP_FAILED == conf->buffers[n_buffers].start)
                        errno_exit ("mmap");
                printf("buf length p=0x%x, start=0x%x %d byte\n", 
                (int)p, (int)conf->buffers[n_buffers].start, buf.length);
        }
        //printf ("init_mmap() done\n");
}

static void
init_device                     (config *conf)
{
        struct v4l2_capability cap;
        struct v4l2_cropcap cropcap;
        struct v4l2_crop crop;
        struct v4l2_format fmt;
        struct v4l2_fmtdesc desc;
        
        unsigned int i, min;

        if (-1 == xioctl (conf->fd, VIDIOC_QUERYCAP, &cap)) {
                if (EINVAL == errno) {
                        fprintf (stderr, "%s is no V4L2 device\n",
                                 conf->filename);
                        return;
                } else {
                        perror ("VIDIOC_QUERYCAP");
                        return;
                }
        }

        if (!(cap.capabilities & V4L2_CAP_VIDEO_CAPTURE)) {
                fprintf (stderr, "%s is no video capture device\n",
                         conf->filename);
                exit (EXIT_FAILURE);
        }

		if (!(cap.capabilities & V4L2_CAP_STREAMING)) {
				fprintf (stderr, "%s does not support streaming i/o\n",
						 conf->filename);
				exit (EXIT_FAILURE);
		}


        /* Select video input, video standard and tune here. */


        CLEAR (cropcap);

        cropcap.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;

        if (0 == xioctl (conf->fd, VIDIOC_CROPCAP, &cropcap)) {
                crop.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
                crop.c = cropcap.defrect; /* reset to default */
				
				
                if (-1 == xioctl (conf->fd, VIDIOC_S_CROP, &crop)) {
						perror("VIDIOC_S_CROP");
                        switch (errno) {
                        case EINVAL:
                                /* Cropping not supported. */
                                break;
                        default:
                                /* Errors ignored. */
                                break;
                        }
                }
        } else {        
				perror("VIDIOC_S_CROPCAP");
        }

		printf("supported video modes:\n");
		for(i = 0;; i++) { 
			CLEAR(desc);
			desc.index = i;
			desc.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
			if (-1 == xioctl (conf->fd, VIDIOC_ENUM_FMT, &desc)) { 
				//perror("VIDIOC_ENUM_FMT");
				break;
			}
			printf("index %d '%s' pixelformat 0x%x\n",
				desc.index, desc.description, desc.pixelformat);
		}
			

        CLEAR (fmt);

        fmt.type                = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        fmt.fmt.pix.width       = conf->resWidth; 
        fmt.fmt.pix.height      = conf->resHeight;
        fmt.fmt.pix.pixelformat = V4L2_PIX_FMT_YUYV;
        fmt.fmt.pix.field       = V4L2_FIELD_ANY;

		printf ("opening video at %dx%d\n", conf->resWidth, conf->resHeight);
		if (-1 == xioctl (conf->fd, VIDIOC_S_FMT, &fmt)) 
			perror("VIDIOC_S_FMT");

        /* Note VIDIOC_S_FMT may change width and height. */

        /* Buggy driver paranoia. */
        min = fmt.fmt.pix.width * 2;
        if (fmt.fmt.pix.bytesperline < min)
                fmt.fmt.pix.bytesperline = min;
        min = fmt.fmt.pix.bytesperline * fmt.fmt.pix.height;
        if (fmt.fmt.pix.sizeimage < min)
                fmt.fmt.pix.sizeimage = min;

        init_mmap (conf);
}


static void
close_device                    (config *conf)
{
        if (-1 == close (conf->fd))
                errno_exit ("close");

        conf->fd = -1;
}

static void
open_device                     (config *conf)
{
        struct stat st; 

        if (-1 == stat (conf->filename, &st)) {
                fprintf (stderr, "Cannot identify '%s': %d, %s\n",
                         conf->filename, errno, strerror (errno));
                return;
        }

        if (!S_ISCHR (st.st_mode)) {
                fprintf (stderr, "%s is no device\n", conf->filename);
                return;
        }

        conf->fd = open (conf->filename, O_RDWR /* required */ | O_NONBLOCK, 0);

        if (-1 == conf->fd) {
                fprintf (stderr, "Cannot open '%s': %d, %s\n",
                         conf->filename, errno, strerror (errno));
                exit (EXIT_FAILURE);
        }
}


void v4l2mmap_open(config *conf) { 
	open_device (conf);
	init_device (conf);
	start_capturing (conf);
}
   
void v4l2mmap_close(config *conf) { 
	printf("v4l2_mmap_close()");
	stop_capturing (conf);
	printf("stop cap done");
	uninit_device (conf);
	close_device (conf);
}	 


/*
main() {
	int w = 320; 
	int h = 240; 
	openvid("/dev/video0", w, h);
	char *b2 = (char *)malloc(w * h * 4);
    int n = read(fd, b2, w * h * 4);
    fprintf(stderr, "Got %d\n", n);

}
*/
