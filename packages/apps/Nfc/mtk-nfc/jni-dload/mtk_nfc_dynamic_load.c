/*****************************************************************************
*  Copyright Statement:
*  --------------------
*  This software is protected by Copyright and the information contained
*  herein is confidential. The software may not be copied and the information
*  contained herein may not be used or disclosed except with the written
*  permission of MediaTek Inc. (C) 2012
*
*  BY OPENING THIS FILE, BUYER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
*  THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
*  RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO BUYER ON
*  AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
*  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
*  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
*  NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
*  SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
*  SUPPLIED WITH THE MEDIATEK SOFTWARE, AND BUYER AGREES TO LOOK ONLY TO SUCH
*  THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. MEDIATEK SHALL ALSO
*  NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE RELEASES MADE TO BUYER'S
*  SPECIFICATION OR TO CONFORM TO A PARTICULAR STANDARD OR OPEN FORUM.
*
*  BUYER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND CUMULATIVE
*  LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
*  AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
*  OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY BUYER TO
*  MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE. 
*
*  THE TRANSACTION CONTEMPLATED HEREUNDER SHALL BE CONS1D IN ACCORDANCE
*  WITH THE LAWS OF THE STATE OF CALIFORNIA, USA, EXCLUDING ITS CONFLICT OF
*  LAWS PRINCIPLES.  ANY DISPUTES, CONTROVERSIES OR CLAIMS ARISING THEREOF AND
*  RELATED THERETO SHALL BE SETTLED BY ARBITRATION IN SAN FRANCISCO, CA, UNDER
*  THE RULES OF THE INTERNATIONAL CHAMBER OF COMMERCE (ICC).
*
*****************************************************************************/

/*******************************************************************************
 * Filename:
 * ---------
 *  mtk_nfc_dynamic_load.c
 *
 * Project:
 * --------
 *
 * Description:
 * ------------
 *
 * Author:
 * -------
 *  LiangChi Huang, ext 25609, liangchi.huang@mediatek.com, 2012-12-17
 * 
 *******************************************************************************/
/***************************************************************************** 
 * Include
 *****************************************************************************/ 
#include <stdlib.h>
#include <pthread.h>
#include <unistd.h>  /* UNIX standard function definitions */
#include <fcntl.h>   /* File control definitions */
#include <errno.h>   /* Error number definitions */
#include <termios.h> /* POSIX terminal control definitions */
#include <signal.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <sys/ipc.h>
#include <sys/time.h>
#include <sys/timeb.h>
#include <sys/ioctl.h>
#include <sys/un.h>


#include <cutils/log.h> // For Debug
#include <utils/Log.h> // For Debug

#include "mtk_nfc_dynamic_load.h"

#define USE_SIGNAL_EVENT_TO_TIMER_CREATE

#define CLOCKID CLOCK_REALTIME
#define SIG SIGRTMIN

#define MTK_NFC_TIMER_INVALID_ID    0xFFFFFFFF

#define MSR3110_READ_BUF_SIZE_MAX   32
#define MSR3110_WRITE_RETRY_MAX     20
#define MSR3110_READ_RETRY_MAX      10

#undef LOG_TAG
#define LOG_TAG "NFC_DYNA_LOAD"
#define DEBUG_LOG


typedef void (*ppCallBck_t)(unsigned int TimerId, void *pContext);

typedef enum 
{
    MTK_NFC_TIMER_RESERVE_0 = 0x0,
    MTK_NFC_TIMER_RESERVE_1,
    MTK_NFC_TIMER_RESERVE_2,     
    MTK_NFC_TIMER_RESERVE_3,     
    MTK_NFC_TIMER_RESERVE_4,     
    MTK_NFC_TIMER_MAX_NUM   
} MTK_NFC_TIMER_E;

typedef struct
{
    int                is_used;    // 1 = used; 0 = unused
    timer_t             handle;     // system timer handle
    ppCallBck_t         timer_expiry_callback; // timeout callback
    void                *timer_expiry_context; // timeout callback context    
    int                is_stopped;    // 1 = stopped; 0 = running    
} nfc_timer_table_struct;

static nfc_timer_table_struct nfc_timer_table[MTK_NFC_TIMER_MAX_NUM];

void mtk_nfc_sys_timer_stop( MTK_NFC_TIMER_E timer_slot ); //avoid build warning .

void nfc_timer_expiry_hdlr (int sig, siginfo_t *si, void *uc)
{
    int timer_slot;
    timer_t *tidp;
    ppCallBck_t cb_func;
    void *param;

    #ifdef DEBUG_LOG
    ALOGD("[TIMER]Caugh signal %d\n", sig);
    #endif

    tidp = si->si_value.sival_ptr;

    /* Look up timer_slot of this timeout, range = 0 ~ (MTK_NFC_TIMER_MAX_NUM-1) */
    for(timer_slot = 0; timer_slot < MTK_NFC_TIMER_MAX_NUM; timer_slot++)
    {
        if(nfc_timer_table[timer_slot].handle == *tidp)
        {
            break;
        }
    }
    
    if(timer_slot == MTK_NFC_TIMER_MAX_NUM)    //timer not found in table
    {
        #ifdef DEBUG_LOG
        //ALOGD("[TIMER]timer no found in the table : (handle: 0x%x)\r\n", nfc_timer_table[timer_slot].handle);
        ALOGD("[TIMER]timer no found in the table \r\n");
        #endif
        return;
    }
    
    //get the cb and param from gps timer pool
    cb_func = nfc_timer_table[timer_slot].timer_expiry_callback;
    param = nfc_timer_table[timer_slot].timer_expiry_context;
    
    //stop time (windows timer is periodic timer)
    mtk_nfc_sys_timer_stop(timer_slot);
    
    //execute cb
    (*cb_func)(timer_slot, param);
}



/***************************************************************************** 
 * Function
 *  mtk_nfc_sys_timer_init
 * DESCRIPTION
 *  Create a new timer
 * PARAMETERS
 *  NONE
 * RETURNS
 *  a valid timer ID or MTK_NFC_TIMER_INVALID_ID if an error occured
 *****************************************************************************/ 
int 
mtk_nfc_sys_timer_init (
    void
)
{
    int ret;

    struct sigaction sa;

    /* Establish handler for timer signal */ 
    #ifdef DEBUG_LOG
    ALOGD("Establishing handler for signal %d\n", SIG);
    #endif
    sa.sa_flags = SA_SIGINFO;
    sa.sa_sigaction = nfc_timer_expiry_hdlr;
    sigemptyset(&sa.sa_mask);
    
    ret = sigaction(SIG, &sa, NULL);
    if (ret == -1) {
        #ifdef DEBUG_LOG
        ALOGD("sigaction fail\r\n");
        #endif
    }
    
    return ret;
}

/***************************************************************************** 
 * Function
 *  mtk_nfc_sys_timer_create
 * DESCRIPTION
 *  Create a new timer
 * PARAMETERS
 *  NONE
 * RETURNS
 *  a valid timer ID or MTK_NFC_TIMER_INVALID_ID if an error occured
 *****************************************************************************/ 
unsigned int 
mtk_nfc_sys_timer_create (
    void
)
{
#ifdef USE_SIGNAL_EVENT_TO_TIMER_CREATE
    int ret;
    unsigned int timer_slot;
#if 0    
    sigset_t mask;
#endif
    struct sigevent se;

    /* Look for available time slot */
    for (timer_slot = 0; timer_slot < MTK_NFC_TIMER_MAX_NUM; timer_slot++) {
        if (nfc_timer_table[timer_slot].is_used == 0) {
            break;
        }
    }

    if (timer_slot == MTK_NFC_TIMER_MAX_NUM) {
        #ifdef DEBUG_LOG
        ALOGD("[TIMER]no timer slot could be used\r\n");
        #endif
        return MTK_NFC_TIMER_INVALID_ID;
    }

    /* Block timer signal temporarily */
#if 0    
    printf("Block signal %d\n", SIG);
    sigemptyset(&mask);
    sigaddset(&mask, SIG);
    if (sigprocmask(SIG_SETMASK, &mask, NULL) == -1) {
        printf("sigprocmask fail\r\n");
        return;    
    }
#endif    

    /* Create the timer */
    se.sigev_notify = SIGEV_SIGNAL;
    se.sigev_signo = SIG;    
    se.sigev_value.sival_ptr = &nfc_timer_table[timer_slot].handle;
    
    /* Create a POSIX per-process timer */
    if ((ret = timer_create(CLOCKID, &se, &(nfc_timer_table[timer_slot].handle))) == -1)
    {
        #ifdef DEBUG_LOG
        ALOGD("[TIMER]timer_create fail, ret:%d, errno:%d, %s\r\n", ret, errno, strerror(errno));
        #endif
        return MTK_NFC_TIMER_INVALID_ID;
    }
        
    nfc_timer_table[timer_slot].is_used = 1;
    #ifdef DEBUG_LOG
    ALOGD("[TIMER]create,time_slot,%d,handle,0x%x\r\n", timer_slot, nfc_timer_table[timer_slot].handle);
    #endif

    return timer_slot;
#else
    int ret;
    unsigned int timer_slot;
    struct sigevent se;

    se.sigev_notify = SIGEV_THREAD;
    se.sigev_notify_function = nfc_timer_expiry_hdlr;
    se.sigev_notify_attributes = NULL;

    /* Look for available time slot */
    for (timer_slot = 0; timer_slot < MTK_NFC_TIMER_MAX_NUM; timer_slot++)
    {
        if (nfc_timer_table[timer_slot].is_used == 0)
        {
            break;
        }
    }

    if (timer_slot == MTK_NFC_TIMER_MAX_NUM)
    {
        #ifdef DEBUG_LOG
        ALOGD("[TIMER]no timer slot could be used\r\n");
        #endif
        return MTK_NFC_TIMER_INVALID_ID;
    }

    se.sigev_value.sival_int = (int) timer_slot;

    /* Create a POSIX per-process timer */
    #ifdef DEBUG_LOG
    ALOGD("handle1:%x\r\n", nfc_timer_table[timer_slot].handle);
    #endif
    if ((ret = timer_create(CLOCK_REALTIME, &se, &(nfc_timer_table[timer_slot].handle))) == -1)
    {
        #ifdef DEBUG_LOG
        ALOGD("[TIMER]timer_create fail, ret:%d, errno:%d, %s\r\n", ret, errno, strerror(errno));
        ALOGD("handle2:%x\r\n", nfc_timer_table[timer_slot].handle);
        #endif
        return MTK_NFC_TIMER_INVALID_ID;
    }
        
    nfc_timer_table[timer_slot].is_used = 1;
    #ifdef DEBUG_LOG
    ALOGD("[TIMER]create,time_slot,%d\r\n", timer_slot);
    #endif
    
    return timer_slot;
#endif    
}

/***************************************************************************** 
 * Function
 *  mtk_nfc_sys_timer_start
 * DESCRIPTION
 *  Start a timer
 * PARAMETERS
 *  timer_slot  [IN] a valid timer slot
 *  period      [IN] expiration time in milliseconds
 *  timer_expiry[IN] callback to be called when timer expires
 *  arg         [IN] callback fucntion parameter
 * RETURNS
 *  NONE
 *****************************************************************************/ 
void 
mtk_nfc_sys_timer_start (
    unsigned int      timer_slot, 
    unsigned int      period, 
    ppCallBck_t timer_expiry, 
    void        *arg
)
{
    struct itimerspec its;

    if (timer_slot >= MTK_NFC_TIMER_MAX_NUM)
    {
        #ifdef DEBUG_LOG
        ALOGD("[TIMER]timer_slot(%d) exceed max num of nfc timer\r\n", timer_slot);  
        #endif
        return;
    }

    if (timer_expiry == NULL)
    {
        #ifdef DEBUG_LOG
        ALOGD("[TIMER]timer_expiry_callback == NULL\r\n");    
        #endif
        return;    
    }

    if (nfc_timer_table[timer_slot].is_used == 0)
    {
        #ifdef DEBUG_LOG
        ALOGD("[TIMER]timer_slot(%d) didn't be created\r\n", timer_slot);
        #endif
        return;        
    }

    its.it_interval.tv_sec = 0;
    its.it_interval.tv_nsec = 0;
    its.it_value.tv_sec = period / 1000;
    its.it_value.tv_nsec = 1000000 * (period % 1000);
    if ((its.it_value.tv_sec == 0) && (its.it_value.tv_nsec == 0))
    {
        // this would inadvertently stop the timer (TODO: HIKI)
        its.it_value.tv_nsec = 1;
    }

    nfc_timer_table[timer_slot].timer_expiry_callback = timer_expiry;
    nfc_timer_table[timer_slot].timer_expiry_context = arg;
    nfc_timer_table[timer_slot].is_stopped = 0;   
    timer_settime(nfc_timer_table[timer_slot].handle, 0, &its, NULL);
    
    #ifdef DEBUG_LOG
    ALOGD("[TIMER]timer_slot(%d) start, handle(%d)\r\n", timer_slot, nfc_timer_table[timer_slot].handle);
    #endif
}

/***************************************************************************** 
 * Function
 *  mtk_nfc_sys_timer_stop
 * DESCRIPTION
 *  Start a timer
 * PARAMETERS
 *  timer_slot    [IN] a valid timer slot
 * RETURNS
 *  NONE
 *****************************************************************************/ 
void 
mtk_nfc_sys_timer_stop (
    MTK_NFC_TIMER_E timer_slot
)
{
    struct itimerspec its = {{0, 0}, {0, 0}};
    
    if (timer_slot >= MTK_NFC_TIMER_MAX_NUM)
    {
        #ifdef DEBUG_LOG
        ALOGD("[TIMER]timer_slot(%d) exceed max num of nfc timer\r\n", timer_slot);
        #endif
        return;
    }

    if (nfc_timer_table[timer_slot].is_used == 0)
    {
        #ifdef DEBUG_LOG
        ALOGD("[TIMER]timer_slot(%d) already be deleted\r\n", timer_slot);
        #endif
        return;        
    }

    if (nfc_timer_table[timer_slot].is_stopped == 1)
    {
        #ifdef DEBUG_LOG
        ALOGD("[TIMER]timer_slot(%d) already be stopped\r\n", timer_slot);
        #endif
        return;
    }
    
    nfc_timer_table[timer_slot].is_stopped = 1;
    timer_settime(nfc_timer_table[timer_slot].handle, 0, &its, NULL);
    
    #ifdef DEBUG_LOG
    ALOGD("[TIMER]timer_slot(%d) stop, handle(%d)\r\n", timer_slot, nfc_timer_table[timer_slot].handle);
    #endif
}

/***************************************************************************** 
 * Function
 *  mtk_nfc_sys_timer_delete
 * DESCRIPTION
 *  Delete a timer
 * PARAMETERS
 *  timer_slot    [IN] a valid timer slot
 * RETURNS
 *  NONE
 *****************************************************************************/ 
void
mtk_nfc_sys_timer_delete (
    MTK_NFC_TIMER_E timer_slot
)
{
    if (timer_slot >= MTK_NFC_TIMER_MAX_NUM)
    {
        #ifdef DEBUG_LOG
        ALOGD("[TIMER]exceed max num of nfc timer,%d\r\n", timer_slot);
        #endif
        return;
    }

    if (nfc_timer_table[timer_slot].is_used == 0)
    {
        #ifdef DEBUG_LOG
        ALOGD("[TIMER]timer_slot(%d) already be deleted\r\n", timer_slot);        
        #endif
        return;
    }
    
    timer_delete(nfc_timer_table[timer_slot].handle);
    nfc_timer_table[timer_slot].handle = 0;
    nfc_timer_table[timer_slot].timer_expiry_callback = NULL;
    nfc_timer_table[timer_slot].timer_expiry_context = NULL;
    nfc_timer_table[timer_slot].is_used = 0; // clear used flag
    #ifdef DEBUG_LOG
    ALOGD("[TIMER]timer_slot(%d) delete\r\n", timer_slot);    
    #endif
}

int handle;
char* DevNode_mt6605 = "/dev/mt6605";
char* DevNode_msr3110 = "/dev/msr3110";
volatile int  mtkNfcQueryChipIdTimeout;


static void 
mtkNfcQueryChipIdTimeoutCb (
    uint_t timer_slot,
    void *pContext    
)
{
    mtkNfcQueryChipIdTimeout = 0;
    close(handle);
}

MTK_NFC_CHIP_TYPE_E mtk_nfc_get_chip_type (void)
{
    MTK_NFC_CHIP_TYPE_E eChipType = MTK_NFC_CHIP_TYPE_UNKNOW;
    int result;
    char data[32];// ="0x55";
    int len =0;
    unsigned int TimerID;
    
    
    //Init Timer
    // Initialize timer handler
    result = mtk_nfc_sys_timer_init();
    if (result < 0) 
    {
      #ifdef DEBUG_LOG
      ALOGD("mtk_nfc_sys_timer_init fail, error code %d\n", result);
      #endif
      return (result);
    }
    //{
    //int i;
    //for (i=0;i<100;i++){ usleep(1000);}
    //}  
    
    //ALOGD("MT6605_LC_TEST_ADD_DELAY");
    /* TRY MT6605*/
    //////////////////////////////////////////////////////////////////
    // (1) open device node
    handle = open(DevNode_mt6605, O_RDWR | O_NOCTTY);    
    if (handle < 0)
    {
        #ifdef DEBUG_LOG
        ALOGD("OpenDeviceFail,eChipType,%d",eChipType); 
        #endif		
        return 	eChipType;	
    }
    // (3) mask IRQ by IOCTL
    ioctl(handle,0xFE00,0);
    // (4) re-registration IRQ handle by IOCTL
    ioctl(handle,0xFE01,0);
    
    //
    ioctl(handle,0x01, ((0x00 << 8) | (0x01)));
    ioctl(handle,0x01, ((0x01 << 8) | (0x00)));
    {
    int i;
    for (i=0;i<10;i++){ usleep(1000);}
    }   
    
    // 
    ioctl(handle,0x01, ((0x00 << 8) | (0x00)));
    ioctl(handle,0x01, ((0x01 << 8) | (0x00)));
    {
    int i;
    for (i=0;i<10;i++){ usleep(1000);}
    }
    ioctl(handle,0x01, ((0x01 << 8) | (0x01)));
    //    
    {
    int i;
    for (i=0;i<100;i++){ usleep(1000);}
    }   
    //ALOGD("MT6605_LC_TEST_O");
    // (5) Write 0x55    
    data[0] = 0x55;
    len = 1;
    if (write(handle, &data[0], len) < 0)
    {
        #ifdef DEBUG_LOG
        ALOGD("MT6605_Write_Err"); 
        #endif		
        ioctl(handle,0x01, ((0x00 << 8) | (0x01)));
        ioctl(handle,0x01, ((0x01 << 8) | (0x00)));
        close(handle); 
        return 	eChipType;	
    }
    // (6) Read 0x55
    // Set Timeout   
    mtkNfcQueryChipIdTimeout = 1; 
    TimerID = mtk_nfc_sys_timer_create();
    // Seart Timer and set time-out is 3 sec
    mtk_nfc_sys_timer_start(TimerID, 3000, &mtkNfcQueryChipIdTimeoutCb, NULL);
    len = 32;
    if (read(handle, &data[0], len) < 0)
    {
        #ifdef DEBUG_LOG
        ALOGD("MT6605_Read_Err"); 
        #endif		
        ioctl(handle,0x01, ((0x00 << 8) | (0x01)));
        ioctl(handle,0x01, ((0x01 << 8) | (0x00)));
        close(handle); 
        return 	eChipType;	
    }
    
    
    ioctl(handle,0x01, ((0x00 << 8) | (0x01)));
    ioctl(handle,0x01, ((0x01 << 8) | (0x00)));
    
    if (mtkNfcQueryChipIdTimeout == 1)
    {
        mtk_nfc_sys_timer_stop(TimerID);
    
        if ((data[0] == 0xA5) /*&& (data[1] == 0x05) && (data[2] == 0x00) && (data[3] == 0x01)*/)
        {
            eChipType = MTK_NFC_CHIP_TYPE_MT6605;
        }
    }  
        
    ioctl(handle,0x01, ((0x00 << 8) | (0x01)));
    ioctl(handle,0x01, ((0x01 << 8) | (0x00)));
        close(handle);
    
    mtk_nfc_sys_timer_delete(TimerID);
    //////////////////////////////////////////////////////////////////
    /* TRY MT6605 END*/    
    
    #ifdef DEBUG_LOG
    ALOGD("eChipType,%d",eChipType);
    #endif
    
    return 	eChipType;	
}



/***************************************************************************** 
 * Function
 *  msr_nfc_interface_send
 * DESCRIPTION
 *  send msr3110 command
 * PARAMETERS
 *  DevNodeHandle  [IN] device node handle
 *  SendBuf            [IN] command
 *  SendLen            [IN] command length
 * RETURNS
 *  The function returns the number of bytes write.  on error it returns -1
 *****************************************************************************/ 
static int 
msr_nfc_interface_send (
    int DevNodeHandle,
    unsigned char *SendBuf,
    int SendLen
)
{
    int result = 0;
    int loopIdx = 0;
    for( loopIdx =0; loopIdx < MSR3110_WRITE_RETRY_MAX; loopIdx++)
    {
        result = write( DevNodeHandle, SendBuf, SendLen);  
        if( result == SendLen )
        {
            #ifdef DEBUG_LOG
            ALOGD( "SEND SUCCESS: read len. \n");
            #endif
            break;
        }
        if( result < 0)
        {
            #ifdef DEBUG_LOG
            ALOGD( "SEND CONTINUE. loopIdx: %d, result: %d.\n", loopIdx, result);
            #endif
            usleep(500);
            continue;       
        }
        #ifdef DEBUG_LOG
        ALOGD( "SEND ERROR: read len. loopIdx: %d, result: %d.\n", loopIdx, result);
        #endif
        result = -1;
        goto end;
    }
end:
    return result;  

}


/***************************************************************************** 
 * Function
 *  msr_nfc_interface_recv
 * DESCRIPTION
 *  receive msr3110 response
 * PARAMETERS
 *  DevNodeHandle   [IN] device node handle
 *  RecvBuf              [IN]  resopnse
 *  RecvLen              [IN] response length
 * RETURNS
 *  The function returns the number of bytes read.  on error it returns -1
 *****************************************************************************/ 
static int 
msr_nfc_interface_recv (
    int DevNodeHandle,
    unsigned char *RecvBuf,
    int RecvBufLen
)
{
    int result = 0;
    int loopIdx = 0;
  
    for( loopIdx = 0; loopIdx < MSR3110_READ_RETRY_MAX; loopIdx++) 
    {
        result = read( DevNodeHandle, RecvBuf, 2);
        if( result == 2)
        {
            #ifdef DEBUG_LOG
            ALOGD( "RECV SUCCESS: read len. \n");
            #endif
            break;
        }
  
        if( result <= 0) 
        {
            #ifdef DEBUG_LOG
            ALOGD( "RECV CONTINUE. loopIdx: %d, result: %d.\n", loopIdx, result);
            #endif
            usleep(500);
            continue;       
        }
        #ifdef DEBUG_LOG
        ALOGD( "RECV ERROR: read len. loopIdx: %d, result: %d.\n", loopIdx, result);
        #endif
        result = -1;
        goto end;
    }
    
    if( result < 0) 
    {
        #ifdef DEBUG_LOG
        ALOGD( "RECV ERROR: read len, out of retry. result: %d.\n", result);
        #endif
        result = -1;
        goto end;
    }
      
    if( RecvBuf[ 0] != 0x02)
    {
        #ifdef DEBUG_LOG
        ALOGD( "RECV ERROR: RecvBuf[ 0]: %02X != 0x02.\n", RecvBuf[ 0]);
        #endif
        result = -1;
        goto end;       
    }

    #ifdef DEBUG_LOG
    ALOGD( "RecvBuf, len: %d. \n", RecvBuf[1]);
    #endif
    
    if( RecvBuf[1] <= 0)
    {
        #ifdef DEBUG_LOG
        ALOGD( "RECV ERROR: len <= 0. \n");
        #endif
        result = -1;
        goto end;
    }
  
    for( loopIdx = 0; loopIdx < MSR3110_READ_RETRY_MAX; loopIdx++) 
    {
        result = read( DevNodeHandle, &RecvBuf[2], RecvBuf[1] + 2);
        if( result == ( RecvBuf[1] + 2))
        {
            #ifdef DEBUG_LOG
            ALOGD( "RECV SUCCESS: read data. \n");
            #endif
            break;
        }
  
        if( result < 0) 
        {
            #ifdef DEBUG_LOG
            ALOGD( "RECV CONTINUE. loopIdx: %d, result: %d.\n", loopIdx, result);
            #endif
            usleep(500);
            continue;       
        }
        #ifdef DEBUG_LOG
        ALOGD( "RECV ERROR: read data. loopIdx: %d, result: %d.\n", loopIdx, result);
        #endif
        result = -1;
        goto end;
    }
    if( result < 0) 
    {
        #ifdef DEBUG_LOG
        ALOGD( "RECV ERROR: read len out of retry. result: %d.\n", result);
        #endif
        result = -1;
        goto end;
    }
    result = RecvBuf[1] + 4;
      
end: 
    return result;

}

int verify_checksum(unsigned char *buf, unsigned short length )
{
    int i = 0;
    unsigned short sum1 = 0;
    unsigned short sum2 = 0;

    for( i=0; i< length; i++ ) {
        sum1 += *( buf + i );
    }

    sum2 = *(buf + length + 1 );
    sum2 <<= 8;
    sum2 += *(buf + length);

    if (sum1 == sum2) return 1;
    
    return 0;
}

MTK_NFC_CHIP_TYPE_E msr_nfc_get_chip_type(void)
{
    MTK_NFC_CHIP_TYPE_E eChipType = MTK_NFC_CHIP_TYPE_UNKNOW;
    //unsigned char cmd_get_version[] = { 0x05, 0x01, 0x00, 0x06, 0x00};
    //unsigned char response_buffer[ MSR3110_READ_BUF_SIZE_MAX] = {0};
    
    int result = 0;
    int pinVal = 0;
    int loopIdx = 0;
    //return eChipType; // test
    /* TRY MSR3110*/
    //////////////////////////////////////////////////////////////////
    //(1) open device node
    handle = open(DevNode_msr3110, O_RDWR | O_NOCTTY);    
    if (handle < 0)
    {
        #ifdef DEBUG_LOG
        ALOGD("OpenDeviceFail,eChipType,%d",eChipType); 
        #endif      
        return 	eChipType;	
    }

#if 0
    //(1.1) init msr3110
    pinVal = 1;
    ioctl( handle, MSR3110_IOCTL_SET_VEN, ( int)&pinVal);
    //pinVal = 0;
    //ioctl( handle, MSR3110_IOCTL_SET_RST, ( int)&pinVal);    
    usleep( 300000);    
#endif
    
    //(2) get chip version

    for ( loopIdx = 0; loopIdx < 3; loopIdx++) {

    result = ioctl( handle, MSR3110_IOCTL_CHIP_DETECT, (int)&pinVal);
	
    #ifdef DEBUG_LOG
    ALOGD("MSR3110_IOCTL_CHIP_DETECT = %d", result);
    #endif
    
    if (result == 1) {
            break;	
        } else {
            #ifdef DEBUG_LOG
            ALOGD("result != 1, retry : %d", loopIdx);
            #endif
        }
    }

    if (result == 1) {
        eChipType = MTK_NFC_CHIP_TYPE_MSR3110;

        #ifdef DEBUG_LOG
        ALOGD( "%s: Mount MSR3110 Driver", __FUNCTION__);
        #endif
        ioctl( handle, MSR3110_IOCTL_DRIVER_INIT, 0);

    } else {
        //return eChipType;
        eChipType = MTK_NFC_CHIP_TYPE_UNKNOW;
    } 

#if 0    
    //(2) get  version sart
    //(2.1) write command
    result = msr_nfc_interface_send( handle, cmd_get_version,  sizeof(cmd_get_version));
    if( result < 0) {
        #ifdef DEBUG_LOG
        ALOGD( "%s: Fail : send command (%d)", __FUNCTION__, result);
        #endif
        return eChipType;
    }   
    usleep( 50000);

    //(2.2) receive response
    result = msr_nfc_interface_recv( handle, response_buffer, MSR3110_READ_BUF_SIZE_MAX);
    if( result < 0) {
        #ifdef DEBUG_LOG
        ALOGD( "%s: Fail : recv command (%d)", __FUNCTION__, result); 
        #endif
        return eChipType;       
    }   
 
    //(2.3) set eChipType
    //(2.3.1) verify checksum
    if (verify_checksum(response_buffer, response_buffer[1] + 2) == 0)
    {
        #ifdef DEBUG_LOG
        ALOGD( "%s: Fail : checksum error", __FUNCTION__);
        #endif    
        return eChipType;
    }

    //(2.3.2) verify return code and length
    if (response_buffer[0] == 0x02 && response_buffer[1] == 0x0B)
    {
        eChipType = MTK_NFC_CHIP_TYPE_MSR3110;
        #ifdef DEBUG_LOG
        ALOGD( "%s: Mount MSR3110 Driver", __FUNCTION__);
        #endif
        ioctl( handle, MSR3110_IOCTL_IRQ_REG, 0);
    }
#endif

	//can not power off for SWP Init
#if 0    
    //(3) power off MSR3110    
    pinVal = 0;
    ioctl( handle, MSR3110_IOCTL_SET_VEN, ( int)&pinVal);
    usleep( 200000);
#endif


	close( handle);
    //////////////////////////////////////////////////////////////////
    /* TRY MSR3110 END*/ 
    #ifdef DEBUG_LOG
    ALOGD("eChipType,%d",eChipType);
    #endif
    
    return eChipType;

}

int query_nfc_chip(void)
{
    int version = 0;
    handle = open(DevNode_mt6605, O_RDWR | O_NOCTTY);
    version = ioctl(handle,0xFEFE, 0x00);    
    close(handle); 
    
    #ifdef DEBUG_LOG
    ALOGD("[Nfc_queryVersion]query_nfc_chip,version,%d",version);
    #endif  
    
    return version;	
}

void update_nfc_chip(int type)
{
    handle = open(DevNode_mt6605, O_RDWR | O_NOCTTY);
    ioctl(handle,0xFEFD, type);        
    close(handle);   
    
    #ifdef DEBUG_LOG
    ALOGD("[Nfc_queryVersion]update_nfc_chip,type,%d",type);
    #endif
}



int NativeDynamicLoad_queryVersion(void)
{ 
    int version;
    int read_length;

    version = query_nfc_chip();
    ALOGD("[NFC_queryVersion],version,%d",version);
    if( version != 0x01 && version != 0x02 )
    {      
       version = msr_nfc_get_chip_type();
    if (version != 0x01) {
        version = mtk_nfc_get_chip_type();
    }
       
       if((version == 0x1) || (version == 0x02))
       {
          update_nfc_chip(version);
       }
    }
    return version;
}
