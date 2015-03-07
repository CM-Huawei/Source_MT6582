
package com.mediatek.incallui.ext;

public interface IInCallUIPlugin {

    CallCardExtension getCallCardExtension();

    VTCallExtension getVTCallExtension();

    CallButtonExtension getCallButtonExtension();

    InCallUIExtension getInCallUIExtension();

    NotificationExtension getNotificationExtension();
}
