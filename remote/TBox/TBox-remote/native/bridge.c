/* Narrow OEM bridge. No CAN construction, no removal of factory preconditions.
 * Interposes only the remote-control callback registration and its correlated
 * outbound result. The original cloud path is forwarded unchanged otherwise.
 * Linux ARM64 ABI declarations avoid an Android libc dependency. */
typedef unsigned long size_t;
typedef unsigned int u32;
typedef unsigned long long u64;
extern int dprintf(int,const char*,...);
extern void *dlsym(void*,const char*);
extern int strcmp(const char*,const char*);
extern void *memcpy(void*,const void*,size_t);
extern int socket(int,int,int),bind(int,const void*,unsigned int),listen(int,int),accept(int,void*,void*),close(int),unlink(const char*),chmod(const char*,unsigned int);
extern long recv(int,void*,size_t,int),send(int,const void*,size_t,int);
extern int pthread_create(unsigned long*,void*,void*(*)(void*),void*),pthread_detach(unsigned long),pthread_mutex_lock(void*),pthread_mutex_unlock(void*),usleep(unsigned int);
extern void *malloc(size_t);extern void free(void*);
struct timespec {long sec,nsec;};extern int clock_gettime(int,struct timespec*);
extern unsigned int g_idle_add(int(*)(void*),void*);
extern void *g_variant_new_fixed_array(const void*,const void*,size_t,size_t),*g_variant_new_variant(void*),*g_variant_ref_sink(void*),*g_variant_get_variant(void*);
extern const void *g_variant_get_data(void*);extern size_t g_variant_get_size(void*);extern void g_variant_unref(void*);
static unsigned long mutex[8];
static void *proxy,*userdata;static int (*callback)(void*,int,void*,void*);
static int active=-1,started=0;static u32 serial=0;static u64 deadline=0;
static u64 nowms(void){struct timespec t;clock_gettime(7,&t);return (u64)t.sec*1000+t.nsec/1000000;}
static int readall(int fd,void*p,size_t n){size_t o=0;while(o<n){long r=recv(fd,(char*)p+o,n-o,0);if(r<=0)return 0;o+=r;}return 1;}
static int writeall(int fd,const void*p,size_t n){size_t o=0;while(o<n){long r=send(fd,(char*)p+o,n-o,0x4000);if(r<=0)return 0;o+=r;}return 1;}
static u32 field1(const unsigned char*p,size_t n){if(n<2||p[0]!=8)return 0;u32 v=0;for(unsigned i=1;i<n&&i<6;i++){v|=(p[i]&127)<<((i-1)*7);if(!(p[i]&128))return v;}return 0;}
struct request{u32 n;unsigned char data[4096];};
static int dispatch(void*arg){dprintf(2,"BRIDGE: dispatch callback\n");struct request*r=arg;pthread_mutex_lock(mutex);char c;long alive=active<0?0:recv(active,&c,1,0x42);/* PEEK|DONTWAIT: 0 means peer closed. */
 if(active>=0&&nowms()<deadline&&alive!=0){void*bytes=g_variant_new_fixed_array("y",r->data,r->n,1);void*v=g_variant_ref_sink(g_variant_new_variant(bytes));pthread_mutex_unlock(mutex);callback(proxy,1,v,userdata);g_variant_unref(v);free(r);return 0;}else if(active>=0){close(active);active=-1;serial=0;}pthread_mutex_unlock(mutex);free(r);return 0;}
static void*worker(void*unused){(void)unused;struct{unsigned short family;char path[108];}addr={1,"/run/TBox-remote-oem.sock"};int s=socket(1,1,0);if(s<0)return 0;unlink(addr.path);if(bind(s,&addr,sizeof(addr))||chmod(addr.path,0600)||listen(s,4)){close(s);return 0;}
 for(;;){int fd=accept(s,0,0);if(fd<0){usleep(100000);continue;}u32 h[2];if(!readall(fd,h,sizeof(h))||h[0]<2||h[0]>4095||h[1]==0||h[1]>10000){close(fd);continue;}struct request*r=malloc(sizeof(*r));if(!r){close(fd);continue;}r->n=h[0]+1;if(!readall(fd,r->data,h[0])){free(r);close(fd);continue;}r->data[h[0]]=0;u32 id=field1(r->data,h[0]);pthread_mutex_lock(mutex);if(active>=0||!id){pthread_mutex_unlock(mutex);free(r);close(fd);continue;}dprintf(2,"BRIDGE: accepted serial=%u bytes=%u\n",id,h[0]);active=fd;serial=id;deadline=nowms()+h[1];pthread_mutex_unlock(mutex);g_idle_add(dispatch,r);
 /* Do not release an in-flight operation just because the client timed out.
    The OEM result clears it; a stuck OEM command requires service recovery. */
 }
 return 0;}
unsigned long g_signal_connect_data(void*instance,const char*name,void*fn,void*data,void*destroy,int flags){typedef unsigned long(*F)(void*,const char*,void*,void*,void*,int);F real=(F)dlsym((void*)-1,"g_signal_connect_data");unsigned long id=real(instance,name,fn,data,destroy,flags);if(!strcmp(name,"dcvp-cmd-remote-ctrl")||!strcmp(name,"dcvp_cmd_remote_ctrl")){proxy=instance;callback=(int(*)(void*,int,void*,void*))fn;userdata=data;if(!started){started=1;unsigned long t;if(!pthread_create(&t,0,worker,0))pthread_detach(t);}}return id;}
int gdbus_app_cloudlink_call_dcvp_send_data_sync(void*p,int channel,long timestamp,int type,void*data,int*out,void*cancel,void**error){typedef int(*F)(void*,int,long,int,void*,int*,void*,void**);F real=(F)dlsym((void*)-1,"gdbus_app_cloudlink_call_dcvp_send_data_sync");
 dprintf(2,"BRIDGE: OEM output type=%x\n",type);if(type==0x101||type==0x102){void*v=g_variant_get_variant(data);const unsigned char*b=g_variant_get_data(v);size_t n=g_variant_get_size(v);u32 id=field1(b,n);pthread_mutex_lock(mutex);if(active>=0&&id==serial){if(type==0x102){u32 length=n;writeall(active,&length,4);writeall(active,b,n);close(active);active=-1;serial=0;}pthread_mutex_unlock(mutex);g_variant_unref(v);if(out)*out=0;if(error)*error=0;return 1;}pthread_mutex_unlock(mutex);g_variant_unref(v);}
 return real(p,channel,timestamp,type,data,out,cancel,error);}
