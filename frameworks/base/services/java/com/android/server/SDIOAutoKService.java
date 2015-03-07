package com.android.server;

import android.content.Context;
import android.os.Binder;
import android.os.UEventObserver;
import android.util.Slog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;

import java.lang.Character;

public final class SDIOAutoKService extends Binder {
    private static final String TAG = SDIOAutoKService.class.getSimpleName();
    private final Context mContext;
    
    public SDIOAutoKService(Context context) {
        File fAutoK = new File("proc/lte_autok");
                            
        Slog.d(TAG, ">>>>>>> SDIOAutoK Start Observing <<<<<<<");
        mContext = context;
        mSDIOAutoKObserver.startObserving("FROM=");
        
        if(fAutoK.exists()) {
            try {
                FileOutputStream fout = new FileOutputStream("proc/lte_autok");
                BufferedOutputStream bos = new BufferedOutputStream(fout);
                
                byte[] procParams = "system_server".getBytes("UTF-8");
                String str = "";
                int i;
                for(i = 0; i < procParams.length; i++)
                {
                    str = str + Byte.toString(procParams[i]) + " ";
                }
                Slog.d(TAG, "system_server procParams.length: " + String.valueOf(procParams.length));
                Slog.d(TAG, "system_server procParam: " + str);
                
                bos.write(procParams, 0, procParams.length);
                
                bos.flush();
                bos.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    public static byte[] hexStringToByteArray_reverse(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[(len - i - 2) / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    
    private final UEventObserver mSDIOAutoKObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            Slog.d(TAG, ">>>>>>> SDIOAutoK UEVENT: " + event.toString() + " <<<<<<<");
            String from = event.get("FROM");
            byte[] autokParams = new byte[256];
            byte[] procParams = new byte[512];
            int paramsOffset = 0;
            int autokLen = 0;
            File fAutoK = new File("data/autok");
            
            if ("sdio_autok".equals(from)) {
                
                if(fAutoK.exists()) {
                    return;
                }
                
                try {
                    FileInputStream fin = new FileInputStream("proc/autok");
                    BufferedInputStream bis = new BufferedInputStream(fin);
                    FileOutputStream fout = new FileOutputStream("data/autok");
                    BufferedOutputStream bos = new BufferedOutputStream(fout);
                    int i;
                    
                    while((autokLen = bis.read(autokParams)) != -1) { 
                        String str = "";
                        for(i = 0; i < autokLen; i++)
                        {
                            str = str + Byte.toString(autokParams[i]);
                        }
                        
                        Slog.d(TAG, "read from proc (Str): " + str + " \n length: " + String.valueOf(autokLen));
                        bos.write(autokParams, 0, autokLen);
                    } 
                    
                    bos.flush();
                    bos.close();
                    bis.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else if ("lte_drv".equals(from)) {
                byte[] stage = new byte[1];
                String paramsStr = "";
                int i;
                
                stage[0] = 0;
                
                String sdiofunc = event.get("SDIOFUNC");
                
                byte[] sdiofunc_addr = hexStringToByteArray_reverse(sdiofunc.substring(2));
                System.arraycopy(sdiofunc_addr, 0, procParams, paramsOffset, sdiofunc_addr.length);
                paramsOffset = paramsOffset + sdiofunc_addr.length;
                
                if(fAutoK.exists()) {
                    Slog.d(TAG, "/data/autok exists, do stage 2 auto-K");
                    
                    stage[0] = 2;
                    System.arraycopy(stage, 0, procParams, paramsOffset, stage.length);
                    paramsOffset = paramsOffset + stage.length;
                    
                    try {
                        FileInputStream fin = new FileInputStream(fAutoK);
                        BufferedInputStream bis = new BufferedInputStream(fin);
                        
                        while((autokLen = bis.read(autokParams)) != -1) {
                            String str = "";
                            
                            System.arraycopy(autokParams, 0, procParams, paramsOffset, autokLen);
                            paramsOffset = paramsOffset + autokLen;
                            
                            for(i = 0; i < autokLen; i++)
                            {
                                str = str + Byte.toString(autokParams[i]);
                            }
                            paramsStr = paramsStr + str;
                        }
                        Slog.d(TAG, "/data/autok content:");
                        Slog.d(TAG, " " + paramsStr);
                        
                        bis.close();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    stage[0] = 1;
                    System.arraycopy(stage, 0, procParams, paramsOffset, stage.length);
                    paramsOffset = paramsOffset + stage.length;
                }
                
                Slog.d(TAG, "length of params write to proc:" + String.valueOf(paramsOffset));
                
                try {
                    FileOutputStream fout = new FileOutputStream("proc/autok");
                    BufferedOutputStream bos = new BufferedOutputStream(fout);
                    
                    bos.write(procParams, 0, paramsOffset);
                    
                    bos.flush();
                    bos.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else if ("autok_done".equals(from)) {
                try {
                    FileOutputStream fout = new FileOutputStream("proc/lte_autok");
                    BufferedOutputStream bos = new BufferedOutputStream(fout);
                    
                    byte[] lteprocParams = "autok_done".getBytes("UTF-8");
                    String str = "";
                    int i;
                    for(i = 0; i < lteprocParams.length; i++)
                    {
                        str = str + Byte.toString(lteprocParams[i]) + " ";
                    }
                    Slog.d(TAG, "autok_done procParams.length: " + String.valueOf(lteprocParams.length));
                    Slog.d(TAG, "autok_done procParam: " + str);
                    
                    bos.write(lteprocParams, 0, lteprocParams.length);
                    
                    bos.flush();
                    bos.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };
}