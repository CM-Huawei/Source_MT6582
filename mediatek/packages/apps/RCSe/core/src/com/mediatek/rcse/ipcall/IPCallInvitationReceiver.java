package com.mediatek.rcse.ipcall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * IP call invitation receiver
 */
public class IPCallInvitationReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		// Display invitation notification
		InitiateIPCall.addIPCallInvitationNotification(context, intent);
	}
}
