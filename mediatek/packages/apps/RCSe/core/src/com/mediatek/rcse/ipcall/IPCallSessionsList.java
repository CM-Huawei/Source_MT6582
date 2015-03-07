package com.mediatek.rcse.ipcall;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView;

import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.R;
import com.orangelabs.rcs.service.api.client.ClientApiException;
import com.orangelabs.rcs.service.api.client.ClientApiListener;
import com.orangelabs.rcs.service.api.client.ImsEventListener;
import com.orangelabs.rcs.service.api.client.ipcall.IIPCallSession;
import com.orangelabs.rcs.service.api.client.ipcall.IPCallApi;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * List of current IP calls
 */
public class IPCallSessionsList extends Activity implements ClientApiListener, ImsEventListener {
	/**
	 * UI handler
	 */
	private Handler handler = new Handler();
	
	/**
	 * layout of activity
	 */
	private ListView sessionsList;
	
	/**
	 * refresh of sessions list when api is connected
	 */
	private Boolean refreshWhenApiConnected = false ;
	
	/**
	 * Logger
	 */
	private Logger logger = Logger.getLogger(IPCallSessionsList.class.getName());
	
	
	 /* *****************************************
     *                Activity
     ***************************************** */
    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (logger.isActivated()) {
			logger.info("onCreate()");
		}
		// Set layout
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		setContentView(R.layout.ipcall_sessions_list);

		// Set title
		setTitle(R.string.menu_ipcall_sessions);

		sessionsList = (ListView) findViewById(R.id.sessions_list);

		RcsSettings.createInstance(getApplicationContext());

		if (IPCallSessionsData.getInstance().isCallApiConnected) {
			// remove "old" listeners and set "new" listeners
			IPCallSessionsData.getInstance().callApi.removeAllApiEventListeners();
			IPCallSessionsData.getInstance().callApi
					.removeImsEventListener(IPCallSessionsData.getInstance().imsEventListener);
			IPCallSessionsData.getInstance().callApi.addApiEventListener(this);
			IPCallSessionsData.getInstance().callApiListener = this;
			IPCallSessionsData.getInstance().callApi.addImsEventListener(this);
			IPCallSessionsData.getInstance().imsEventListener = this;
		}
		else { // connect API - get sessions when connected		
			refreshWhenApiConnected = true;

			// instantiate callApi if null
			if (IPCallSessionsData.getInstance().callApi == null) { 
				IPCallSessionsData.getInstance().callApi = new IPCallApi(getApplicationContext());
			} 
			else { // callApi already exists remove "old" listeners
				IPCallSessionsData.getInstance().callApi.removeAllApiEventListeners();
				IPCallSessionsData.getInstance().callApi
						.removeImsEventListener(IPCallSessionsData.getInstance().imsEventListener);
			}
			
			//set "new" listeners
			IPCallSessionsData.getInstance().callApi.addApiEventListener(this);
			IPCallSessionsData.getInstance().callApiListener = this;
			IPCallSessionsData.getInstance().callApi.addImsEventListener(this);
			IPCallSessionsData.getInstance().imsEventListener = this;
			
			//connect api
			IPCallSessionsData.getInstance().callApi.connectApi();
		}
	
	}

   
    @Override
    public void onResume() {
    	super.onResume();
    	
    	if (logger.isActivated()) {
			logger.info("onResume()");
		}
    	
    	if (IPCallSessionsData.getInstance().isCallApiConnected) {
    		refreshSessionsList();
    	}	
    	else {
    		refreshWhenApiConnected = true;
    	} 	
    }
    
    
    @Override
    public void onDestroy() {
    	super.onDestroy();  	
    	if (logger.isActivated()) {
			logger.info("onDestroy()");
		}
    }

    
    public void refreshSessionsList(){   	
    	
    	Thread thread = new Thread() {
			public void run() {
				List<String> sessionsLabel = new ArrayList();
				
				try {
					IPCallSessionsData.getInstance().sessions = (ArrayList<IBinder>) IPCallSessionsData.getInstance().callApi
							.getSessions();
					if (logger.isActivated()) {
						logger.info("sessions list initialized");
					}
				} catch (ClientApiException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 

				handler.post(new Runnable() {
					public void run() {
				
						

						if (IPCallSessionsData.getInstance().sessions.isEmpty()) {
							Utils.showMessage(IPCallSessionsList.this,
									getString(R.string.label_list_empty));
						} else {
							sessionsList.setAdapter(new SessionsListAdapter(
									IPCallSessionsList.this,
									R.layout.ipcall_sessions_list_item,
									IPCallSessionsData.getInstance().sessions){
								
							});
							sessionsList
									.setOnItemClickListener(sessionsListListener);
						}
					}
				});

			}
		};
		thread.start();
    }

    
	public class SessionsListAdapter extends ArrayAdapter<IBinder> {

		Context context;
		int layoutResourceId;
		ArrayList<IBinder> sessionsList;

		// we use the constructor allowing to provide a List of objects for the
		// data
		// to be binded.
		public SessionsListAdapter(Context context, int layoutResourceId,
				ArrayList<IBinder> objects) {
			super(context, layoutResourceId, objects);
			this.context = context;
			this.layoutResourceId = layoutResourceId;
			this.sessionsList = (ArrayList<IBinder>) objects;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			View row = convertView;
			ViewHolder holder = null;

			if (row == null) {
				LayoutInflater inflater = ((Activity) context)
						.getLayoutInflater();
				row = inflater.inflate(layoutResourceId, parent, false);

				holder = new ViewHolder();
				holder.label = (TextView) row
						.findViewById(R.id.ipcall_sessions_list_item_label);

				row.setTag(holder);
			} else {
				holder = (ViewHolder) row.getTag();
			}

			// get session and session data
			IIPCallSession session = IIPCallSession.Stub.asInterface(sessionsList.get(position));			
			try {
				if (logger.isActivated()) {
					logger.info("session.getSessionDirection() ="+session.getSessionDirection());
				}
				String labelText;
				int direction = session.getSessionDirection() ;
				String contact = session.getRemoteContact() ;
				int beginIdx = contact.indexOf("tel");
				int endIdx = contact.indexOf(">", beginIdx);
				if (endIdx == -1) {endIdx = contact.length();}
				String remoteContact = contact.substring(beginIdx, endIdx);
				
				if (direction== IPCallSessionsData.TYPE_OUTGOING_IPCALL){
					labelText = "Outgoing call to:";
				}
				else {labelText = "Incoming call from:";}				
				labelText += remoteContact;
				holder.label.setText(labelText);			
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return row;
		}
     
        /**
         *
         * Inner holder class for a single row view in the ListView
         *
         */
        class ViewHolder {
            TextView label;

        }     
    }
    

	@Override
	public void handleApiConnected() {
		if (logger.isActivated()) {
			logger.debug("API, connected");
		}
	
		IPCallSessionsData.getInstance().isCallApiConnected = true;

		 if (refreshWhenApiConnected){
			 refreshSessionsList();
			 refreshWhenApiConnected = false;}

	}

	@Override
	public void handleApiDisabled() {
		if (logger.isActivated()) {
			logger.debug("API, disabled");
		}
		IPCallSessionsData.getInstance().isCallApiConnected = false;

		String msg = IPCallSessionsList.this.getString(R.string.label_api_disabled);
		
		// Api disabled
				Intent intent = new Intent(IPCallSessionsList.this.getApplicationContext(), IPCallView.class);
				intent.setAction("ExitActivity");
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				intent.putExtra("messages", msg);
				getApplicationContext().startActivity(intent);
	}

	@Override
	public void handleApiDisconnected() {
		if (logger.isActivated()) {
			logger.debug("API, disconnected");
		}
		IPCallSessionsData.getInstance().isCallApiConnected = false;
		
		String msg = IPCallSessionsList.this.getString(R.string.label_api_disconnected);

		// Service has been disconnected
		Intent intent = new Intent(IPCallSessionsList.this.getApplicationContext(), IPCallView.class);
		intent.setAction("ExitActivity");
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra("messages", msg);
		getApplicationContext().startActivity(intent);
	}
	
	@Override
	public void handleImsConnected() {
		if (logger.isActivated()) {
			logger.debug("IMS, connected");
		}
		// nothing to do
	}

	@Override
	public void handleImsDisconnected(int arg0) {
		if (logger.isActivated()) {
			logger.debug("IMS, disconnected");
		}
		
		String msg = IPCallSessionsList.this.getString(R.string.label_ims_disconnected);
		
		// IMS has been disconnected
		Intent intent = new Intent(IPCallSessionsList.this.getApplicationContext(), IPCallView.class);
		intent.setAction("ExitActivity");
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra("messages", msg);
		getApplicationContext().startActivity(intent);
	}
	
	private OnItemClickListener sessionsListListener = new OnItemClickListener() {
	    @Override
	    public void onItemClick(AdapterView<?> parent, View view, int position,
	            long id) {
	    	if (logger.isActivated()) {logger.debug("onItemClick");	}
	        
	    	// get session
	    	IBinder  iBinder  = (IBinder) parent.getItemAtPosition(position);
	    	IIPCallSession session = IIPCallSession.Stub.asInterface(iBinder);
	        
	        try {	        		        	
	        	//launch IPCallSessionActivity with recover action on sessionId
				getApplicationContext().startActivity(setIntentRecoverSession(session.getSessionID()));
				IPCallSessionsList.this.finish();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block 
				e.printStackTrace();
			}
	    }
	};
	
	private Intent setIntentRecoverSession(String id) {
		// Initiate Intent to launch outgoing IP call
		Intent intent = new Intent(getApplicationContext(), IPCallView.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("sessionId", id);
        intent.setAction("recover");
        
        return intent;
	}
	
}
