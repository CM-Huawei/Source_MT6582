/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.nfc.handover;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.util.Log;

import com.android.nfc.LlcpException;
import com.android.nfc.NfcService;
import com.android.nfc.DeviceHost.LlcpServerSocket;
import com.android.nfc.DeviceHost.LlcpSocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/// M: @ { 
import com.mediatek.nfc.handover.MtkHandoverManager;
/// }

public final class HandoverServer {
    public static final String HANDOVER_SERVICE_NAME = "urn:nfc:sn:handover";
    public static final String TAG = "HandoverServer";
    public static final Boolean DBG = true;

    public static final int MIU = 128;

    final HandoverManager mHandoverManager;
    final int mSap;
    final Callback mCallback;

    ServerThread mServerThread = null;
    boolean mServerRunning = false;

    public interface Callback {
        void onHandoverRequestReceived();
    }

    public HandoverServer(int sap, HandoverManager manager, Callback callback) {
        mSap = sap;
        mHandoverManager = manager;
        mCallback = callback;
    }

    public synchronized void start() {
        if (mServerThread == null) {
            mServerThread = new ServerThread();
            mServerThread.start();
            mServerRunning = true;
        }
    }

    public synchronized void stop() {
        if (mServerThread != null) {
            mServerThread.shutdown();
            mServerThread = null;
            mServerRunning = false;
        }
    }

    private class ServerThread extends Thread {
        private boolean mThreadRunning = true;
        LlcpServerSocket mServerSocket;

        @Override
        public void run() {
            boolean threadRunning;
            synchronized (HandoverServer.this) {
                threadRunning = mThreadRunning;
            }

            while (threadRunning) {
                try {
                    synchronized (HandoverServer.this) {
                        mServerSocket = NfcService.getInstance().createLlcpServerSocket(mSap,
                                HANDOVER_SERVICE_NAME, MIU, 1, 1024);
                    }
                    if (mServerSocket == null) {
                        if (DBG) Log.d(TAG, "failed to create LLCP service socket");
                        return;
                    }
                    if (DBG) Log.d(TAG, "created LLCP service socket");
                    synchronized (HandoverServer.this) {
                        threadRunning = mThreadRunning;
                    }

                    while (threadRunning) {
                        LlcpServerSocket serverSocket;
                        synchronized (HandoverServer.this) {
                            serverSocket = mServerSocket;
                        }

                        if (serverSocket == null) {
                            if (DBG) Log.d(TAG, "Server socket shut down.");
                            return;
                        }
                        if (DBG) Log.d(TAG, "about to accept");
                        LlcpSocket communicationSocket = serverSocket.accept();
                        if (DBG) Log.d(TAG, "accept returned " + communicationSocket);
						
                        /// M: disable Beam during WFD @ {
                        if (communicationSocket != null) {
                            if (NfcService.getInstance().notifyUserIfWfdOngoing()) {
                                try {
                                    communicationSocket.close();
                                } catch (Exception e) {}
                            } else {
                            new ConnectionThread(communicationSocket).start();
                        }
                        }
                        /// }

                        synchronized (HandoverServer.this) {
                            threadRunning = mThreadRunning;
                        }
                    }
                    if (DBG) Log.d(TAG, "stop running");
                } catch (LlcpException e) {
                    Log.e(TAG, "llcp error", e);
                } catch (IOException e) {
                    Log.e(TAG, "IO error", e);
                } finally {
                    synchronized (HandoverServer.this) {
                        if (mServerSocket != null) {
                            if (DBG) Log.d(TAG, "about to close");
                            try {
                                mServerSocket.close();
                            } catch (IOException e) {
                                // ignore
                            }
                            mServerSocket = null;
                        }
                    }
                }

                synchronized (HandoverServer.this) {
                    threadRunning = mThreadRunning;
                }
            }
        }

        public void shutdown() {
            synchronized (HandoverServer.this) {
                mThreadRunning = false;
                if (mServerSocket != null) {
                    try {
                        mServerSocket.close();
                    } catch (IOException e) {
                        // ignore
                    }
                    mServerSocket = null;
                }
            }
        }
    }

    private class ConnectionThread extends Thread {
        private final LlcpSocket mSock;

        ConnectionThread(LlcpSocket socket) {
            super(TAG);
            mSock = socket;
        }

        @Override
        public void run() {
            if (DBG) Log.d(TAG, "starting connection thread");
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

            try {
                boolean running;
                synchronized (HandoverServer.this) {
                    running = mServerRunning;
                }

                byte[] partial = new byte[mSock.getLocalMiu()];

                NdefMessage handoverRequestMsg = null;
                while (running) {
                    int size = mSock.receive(partial);
                    if (size < 0) {
                        break;
                    }
                    byteStream.write(partial, 0, size);
                    // 1) Try to parse a handover request message from bytes received so far
                    try {
                        handoverRequestMsg = new NdefMessage(byteStream.toByteArray());
                    } catch (FormatException e) {
                        // Ignore, and try to fetch more bytes
                    }

                    if (handoverRequestMsg != null) {
                        // 2) convert to handover response
                        /// M: @ { 
                        Log.d(TAG, "====  HandoverServer ==== mSock.receive Get HrM, tryMtkHandoverRequest  then     call onHandoverRequestReceived");
                        NdefMessage resp = MtkHandoverManager.getInstance().tryMtkHandoverRequest(handoverRequestMsg);
                        /// }
                        if (resp == null) {
                            Log.e(TAG, "Failed to create handover response");
                            break;
                        }

                        // 3) send handover response
                        int offset = 0;
                        byte[] buffer = resp.toByteArray();
						/// M: @ {
                        int remoteMiu = Math.min(mSock.getRemoteMiu(), mSock.getLocalMiu());
                        Log.d(TAG, "remoteMiu: " + mSock.getRemoteMiu() + ", localMiu = " + mSock.getLocalMiu() + ", we're using " + remoteMiu);
						/// }
                        while (offset < buffer.length) {
                            int length = Math.min(buffer.length - offset, remoteMiu);
                            byte[] tmpBuffer = Arrays.copyOfRange(buffer, offset, offset+length);
                            mSock.send(tmpBuffer);
                            offset += length;
                        }
                        // We're done
                        mCallback.onHandoverRequestReceived();
                        // We can process another handover transfer
                        byteStream = new ByteArrayOutputStream();
                    }

                    synchronized (HandoverServer.this) {
                        running = mServerRunning;
                    }
                }

            } catch (IOException e) {
                if (DBG) Log.d(TAG, "IOException");
            } finally {
                try {
                    if (DBG) Log.d(TAG, "about to close");
                    mSock.close();
                } catch (IOException e) {
                    // ignore
                }
                try {
                    byteStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            if (DBG) Log.d(TAG, "finished connection thread");
        }
    }
}
