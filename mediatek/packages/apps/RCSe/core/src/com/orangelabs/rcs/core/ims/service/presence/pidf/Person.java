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

package com.orangelabs.rcs.core.ims.service.presence.pidf;

public class Person {
	private String id = null;
	private Note note = null;
	private OverridingWillingness willingness = null;
	private StatusIcon statusIcon = null;
	private String homePage = null;
	private long timestamp = -1;

	public Person(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
	
	public Note getNote() {
		return note;
	}

	public void setNote(Note note) {
		this.note = note;
	}	

	public OverridingWillingness getOverridingWillingness() {
		return willingness;
	}

	public void setOverridingWillingness(OverridingWillingness status) {
		this.willingness = status;
	}

	public StatusIcon getStatusIcon(){
		return statusIcon;
	}
	
	public void setStatusIcon(StatusIcon statusIcon){
		this.statusIcon = statusIcon;
	}

	public String getHomePage(){
		return homePage;
	}
	
	public void setHomePage(String homePage){
		this.homePage = homePage;
	}
	
	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long ts) {
		this.timestamp = ts;
	}
}
