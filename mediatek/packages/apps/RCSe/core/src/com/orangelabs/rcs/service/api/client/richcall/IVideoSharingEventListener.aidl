package com.orangelabs.rcs.service.api.client.richcall;

/**
 * Video sharing event listener
 */
interface IVideoSharingEventListener {

	// Session is started
	void handleSessionStarted();

	// Session has been aborted
	void handleSessionAborted(in int reason);
    
	// Session has been terminated by remote
	void handleSessionTerminatedByRemote();

	// Content sharing error
	void handleSharingError(in int error);

    // Video stream has been resized
    void handleVideoResized(in int width, in int height);
}
