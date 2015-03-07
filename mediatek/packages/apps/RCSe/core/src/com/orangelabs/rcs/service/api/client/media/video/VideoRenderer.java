/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
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
 ******************************************************************************/

package com.orangelabs.rcs.service.api.client.media.video;

import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;

import android.graphics.Bitmap;
import android.os.RemoteException;
import android.os.SystemClock;

import com.orangelabs.rcs.core.ims.protocol.rtp.DummyPacketGenerator;
import com.orangelabs.rcs.core.ims.protocol.rtp.MediaRegistry;
import com.orangelabs.rcs.core.ims.protocol.rtp.VideoRtpReceiver;
import com.orangelabs.rcs.core.ims.protocol.rtp.codec.video.h264.decoder.NativeH264Decoder;
import com.orangelabs.rcs.core.ims.protocol.rtp.format.video.CameraOptions;
import com.orangelabs.rcs.core.ims.protocol.rtp.format.video.Orientation;
import com.orangelabs.rcs.core.ims.protocol.rtp.format.video.VideoFormat;
import com.orangelabs.rcs.core.ims.protocol.rtp.format.video.VideoOrientation;
import com.orangelabs.rcs.core.ims.protocol.rtp.media.MediaOutput;
import com.orangelabs.rcs.core.ims.protocol.rtp.media.MediaSample;
import com.orangelabs.rcs.core.ims.protocol.rtp.media.VideoSample;
import com.orangelabs.rcs.core.ims.protocol.rtp.stream.RtpInputStream;
import com.orangelabs.rcs.core.ims.protocol.rtp.stream.RtpStreamListener;
import com.orangelabs.rcs.platform.network.DatagramConnection;
import com.orangelabs.rcs.platform.network.NetworkFactory;
import com.orangelabs.rcs.service.api.client.media.IVideoEventListener;
import com.orangelabs.rcs.service.api.client.media.IVideoRenderer;
import com.orangelabs.rcs.service.api.client.media.MediaCodec;
import com.orangelabs.rcs.utils.CodecsUtils;
import com.orangelabs.rcs.utils.NetworkRessourceManager;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Video RTP renderer. Only the H264 QCIF format is supported.
 *
 * @author jexa7410
 */
public class VideoRenderer extends IVideoRenderer.Stub implements RtpStreamListener {

	/**
	 * List of supported video codecs
	 */
	private MediaCodec[] supportedMediaCodecs = null;

	/**
	 * Selected video codec
	 */
	private VideoCodec selectedVideoCodec = null;

	/**
	 * Video format
	 */
	private VideoFormat videoFormat;

	/**
	 * RtpInputStream shared with the renderer
	 */
	private RtpInputStream rendererRtpInputStream = null;

	/**
	 * Local RTP port
	 */
	private int localRtpPort;

	/**
	 * RTP receiver session
	 */
	private VideoRtpReceiver rtpReceiver = null;

	/**
	 * RTP dummy packet generator
	 */
	private DummyPacketGenerator rtpDummySender = null;

	/**
	 * RTP media output
	 */
	private MediaRtpOutput rtpOutput = null;

	/**
	 * Is player opened
	 */
	private boolean opened = false;

	/**
	 * Is player started
	 */
	private boolean started = false;

	/**
	 * Video start time
	 */
	private long videoStartTime = 0L;

	/**
	 * Video surface
	 */
	private VideoSurface surface = null;

	/**
	 * Media event listeners
	 */
	private Vector<IVideoEventListener> listeners = new Vector<IVideoEventListener>();

	/**
	 * Temporary connection to reserve the port
	 */
	private DatagramConnection temporaryConnection = null;

	/**
	 * Orientation header id.
	 */
	private int orientationHeaderId = -1;

	/**
	 * The logger
	 */
	private Logger logger = Logger.getLogger(this.getClass().getName());

	/**
	 * Constructor
	 */
	public VideoRenderer() {
		// Set the local RTP port
		localRtpPort = NetworkRessourceManager.generateLocalRtpPort();
		reservePort(localRtpPort);

		// Init codecs
		supportedMediaCodecs = CodecsUtils.getRendererCodecList();

		// Set the default media codec
		if (supportedMediaCodecs.length > 0) {
			setVideoCodec(supportedMediaCodecs[0]);
		}
	}

	/**
	 * Constructor with a list of video codecs
	 *
	 * @param codecs Ordered list of codecs (preferred codec in first)
	 */
	public VideoRenderer(MediaCodec[] codecs) {
		// Set the local RTP port
		localRtpPort = NetworkRessourceManager.generateLocalRtpPort();
		reservePort(localRtpPort);

		// Init codecs
		supportedMediaCodecs = codecs;

		// Set the default media codec
		if (supportedMediaCodecs.length > 0) {
			setVideoCodec(supportedMediaCodecs[0]);
		}
	}

	/**
	 * Set the surface to render video
	 *
	 * @param surface Video surface
	 */
	public void setVideoSurface(VideoSurface surface) {
		this.surface = surface;
	}

	/**
	 * Return the video start time
	 *
	 * @return Milliseconds
	 */
	public long getVideoStartTime() {
		return videoStartTime;
	}

	/**
	 * Returns the local RTP port
	 *
	 * @return Port
	 */
	public int getLocalRtpPort() {
		return localRtpPort;
	}

	/**
	 * Returns the local RTP stream (set after the open)
	 *
	 * @return RtpInputStream
	 */
	public RtpInputStream getRtpInputStream() {
		return rendererRtpInputStream;
	}

	/**
	 * Reserve a port
	 *
	 * @param port Port to reserve
	 */
	private void reservePort(int port) {
		if (temporaryConnection == null) {
			try {
				temporaryConnection = NetworkFactory.getFactory().createDatagramConnection();
				temporaryConnection.open(port);
			} catch (IOException e) {
				temporaryConnection = null;
			}
		}
	}

	/**
	 * Release the reserved port.
	 */
	private void releasePort() {
		if (temporaryConnection != null) {
			try {
				temporaryConnection.close();
			} catch (IOException e) {
				temporaryConnection = null;
			}
		}
	}

	/**
	 * Is player opened
	 *
	 * @return Boolean
	 */
	public boolean isOpened() {
		return opened;
	}

	/**
	 * Is player started
	 *
	 * @return Boolean
	 */
	public boolean isStarted() {
		return started;
	}

	/**
	 * Open the renderer
	 *
	 * @param remoteHost Remote host
	 * @param remotePort Remote port
	 */
	public void open(String remoteHost, int remotePort) {
		if (opened) {
			// Already opened
			return;
		}

		// Check video codec
		if (selectedVideoCodec == null) {
			notifyPlayerEventError("Video Codec not selected");
			return;
		}

		try {
			// Init the video decoder
			int result = NativeH264Decoder.InitDecoder();
			if (result != 0) {
				notifyPlayerEventError("Decoder init failed with error code " + result);
				return;
			}
		} catch (UnsatisfiedLinkError e) {
			notifyPlayerEventError(e.getMessage());
			return;
		}

		try {
			// Init the RTP layer
			releasePort();
			rtpReceiver = new VideoRtpReceiver(localRtpPort);
			rtpDummySender = new DummyPacketGenerator();
			rtpOutput = new MediaRtpOutput();
			rtpOutput.open();
			rtpReceiver.prepareSession(remoteHost, remotePort, orientationHeaderId, rtpOutput, videoFormat, this);
			rendererRtpInputStream = rtpReceiver.getInputStream();
			rtpDummySender.prepareSession(remoteHost, remotePort, rtpReceiver.getInputStream());
			rtpDummySender.startSession();
		} catch (Exception e) {
			notifyPlayerEventError(e.getMessage());
			return;
		}

		// Player is opened
		opened = true;
		notifyPlayerEventOpened();
	}

	/**
	 * Close the renderer
	 */
	public void close() {
		if (!opened) {
			// Already closed
			return;
		}

		// Close the RTP layer
		rtpOutput.close();
		rtpReceiver.stopSession();
		rtpDummySender.stopSession();

		try {
			// Close the video decoder
			NativeH264Decoder.DeinitDecoder();
		} catch (UnsatisfiedLinkError e) {
			if (logger.isActivated()) {
				logger.error("Can't close correctly the video decoder", e);
			}
		}

		// Player is closed
		opened = false;
		notifyPlayerEventClosed();
	}

	/**
	 * Start the player
	 */
	public void start() {
		if (!opened) {
			// Player not opened
			return;
		}

		if (started) {
			// Already started
			return;
		}

		// Start RTP layer
		rtpReceiver.startSession();

		// Renderer is started
		videoStartTime = SystemClock.uptimeMillis();
		started = true;
		notifyPlayerEventStarted();
	}

	/**
	 * Stop the renderer
	 */
	public void stop() {
		if (!started) {
			return;
		}

		// Stop RTP layer
		if (rtpReceiver != null) {
			rtpReceiver.stopSession();
		}
		if (rtpDummySender != null) {
			rtpDummySender.stopSession();
		}
		if (rtpOutput != null) {
			rtpOutput.close();
		}

		// Force black screen
		surface.clearImage();

		// Renderer is stopped
		started = false;
		videoStartTime = 0L;
		notifyPlayerEventStopped();
	}

	/**
	 * Add a media event listener
	 *
	 * @param listener Media event listener
	 */
	public void addListener(IVideoEventListener listener) {
		listeners.addElement(listener);
	}

	/**
	 * Remove all media event listeners
	 */
	public void removeAllListeners() {
		listeners.removeAllElements();
	}

	/**
	 * Get supported video codecs
	 *
	 * @return media Codecs list
	 */
	public MediaCodec[] getSupportedVideoCodecs() {
		return supportedMediaCodecs;
	}

	/**
	 * Get video codec
	 *
	 * @return Video codec
	 */
	public MediaCodec getVideoCodec() {
		if (selectedVideoCodec == null)
			return null;
		else
			return selectedVideoCodec.getMediaCodec();
	}

	/**
	 * Set video codec
	 *
	 * @param mediaCodec Media codec
	 */
	public void setVideoCodec(MediaCodec mediaCodec) {
		if (VideoCodec.checkVideoCodec(supportedMediaCodecs, new VideoCodec(mediaCodec))) {
			selectedVideoCodec = new VideoCodec(mediaCodec);
			videoFormat = (VideoFormat) MediaRegistry.generateFormat(mediaCodec.getCodecName());
		} else {
			notifyPlayerEventError("Codec not supported");
		}
	}

	/**
	 * Notify RTP aborted
	 */
	public void rtpStreamAborted() {
		notifyPlayerEventError("RTP session aborted");
	}

	/**
	 * Set extension header orientation id
	 *
	 * @param headerId extension header orientation id
	 */
	public void setOrientationHeaderId(int headerId) {
		this.orientationHeaderId = headerId;
	}

	/**
	 * Notify player event started
	 */
	private void notifyPlayerEventStarted() {
		if (logger.isActivated()) {
			logger.debug("Player is started");
		}
		Iterator<IVideoEventListener> ite = listeners.iterator();
		while (ite.hasNext()) {
			try {
				((IVideoEventListener)ite.next()).mediaStarted();
			} catch (RemoteException e) {
				if (logger.isActivated()) {
					logger.error("Can't notify listener", e);
				}
			}
		}
	}

	/**
	 * Notify player event resized
	 */
	private void notifyPlayerEventResized(int width, int height) {
		if (logger.isActivated()) {
			logger.debug("The media size has changed");
		}
		Iterator<IVideoEventListener> ite = listeners.iterator();
		while (ite.hasNext()) {
			try {
				((IVideoEventListener)ite.next()).mediaResized(width, height);
			} catch (RemoteException e) {
				if (logger.isActivated()) {
					logger.error("Can't notify listener", e);
				}
			}
		}
	}

	/**
	 * Notify player event stopped
	 */
	private void notifyPlayerEventStopped() {
		if (logger.isActivated()) {
			logger.debug("Player is stopped");
		}
		Iterator<IVideoEventListener> ite = listeners.iterator();
		while (ite.hasNext()) {
			try {
				((IVideoEventListener)ite.next()).mediaStopped();
			} catch (RemoteException e) {
				if (logger.isActivated()) {
					logger.error("Can't notify listener", e);
				}
			}
		}
	}

	/**
	 * Notify player event opened
	 */
	private void notifyPlayerEventOpened() {
		if (logger.isActivated()) {
			logger.debug("Player is opened");
		}
		Iterator<IVideoEventListener> ite = listeners.iterator();
		while (ite.hasNext()) {
			try {
				((IVideoEventListener)ite.next()).mediaOpened();
			} catch (RemoteException e) {
				if (logger.isActivated()) {
					logger.error("Can't notify listener", e);
				}
			}
		}
	}

	/**
	 * Notify player event closed
	 */
	private void notifyPlayerEventClosed() {
		if (logger.isActivated()) {
			logger.debug("Player is closed");
		}
		Iterator<IVideoEventListener> ite = listeners.iterator();
		while (ite.hasNext()) {
			try {
				((IVideoEventListener)ite.next()).mediaClosed();
			} catch (RemoteException e) {
				if (logger.isActivated()) {
					logger.error("Can't notify listener", e);
				}
			}
		}
	}

	/**
	 * Notify player event error
	 */
	private void notifyPlayerEventError(String error) {
		if (logger.isActivated()) {
			logger.debug("Renderer error: " + error);
		}

		Iterator<IVideoEventListener> ite = listeners.iterator();
		while (ite.hasNext()) {
			try {
				((IVideoEventListener)ite.next()).mediaError(error);
			} catch (RemoteException e) {
				if (logger.isActivated()) {
					logger.error("Can't notify listener", e);
				}
			}
		}
	}

	/**
	 * Media RTP output
	 */
	private class MediaRtpOutput implements MediaOutput {
		/**
		 * Bitmap frame
		 */
		private Bitmap rgbFrame = null;

		/**
		 * Video orientation
		 */
		private VideoOrientation videoOrientation = new VideoOrientation(CameraOptions.BACK, Orientation.NONE);

		/**
		 * Frame dimensions
		 * Just 2 - width and height
		 */
		private int decodedFrameDimensions[] = new int[2];

		/**
		 * Constructor
		 */
		public MediaRtpOutput() {
			// Init rgbFrame with a default size
			rgbFrame = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
		}

		/**
		 * Open the renderer
		 */
		public void open() {
			// Nothing to do
		}

		/**
		 * Close the renderer
		 */
		public void close() {
		}

		/**
		 * Write a media sample
		 *
		 * @param sample Sample
		 */
		public void writeSample(MediaSample sample) {
			rtpDummySender.incomingStarted();

			// Init orientation
			VideoOrientation orientation = ((VideoSample)sample).getVideoOrientation();
			if (orientation != null) {
				this.videoOrientation = orientation;
			}

			int[] decodedFrame = NativeH264Decoder.DecodeAndConvert(sample.getData(), videoOrientation.getOrientation().getValue(), decodedFrameDimensions);

			if (NativeH264Decoder.getLastDecodeStatus() == 0) {
				if ((surface != null) && (decodedFrame.length > 0)) {
					// Init rgbFrame with the decoder dimensions
					if ((rgbFrame.getWidth() != decodedFrameDimensions[0]) || (rgbFrame.getHeight() != decodedFrameDimensions[1])) {
						rgbFrame = Bitmap.createBitmap(decodedFrameDimensions[0], decodedFrameDimensions[1], Bitmap.Config.RGB_565);
						notifyPlayerEventResized(decodedFrameDimensions[0], decodedFrameDimensions[1]);
					}

					// Set data in image
					rgbFrame.setPixels(decodedFrame, 0, decodedFrameDimensions[0], 0, 0,
							decodedFrameDimensions[0], decodedFrameDimensions[1]);
					surface.setImage(rgbFrame);
				}
			}
		}

	}
}

