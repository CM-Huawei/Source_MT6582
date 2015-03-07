package com.mediatek.systemui.ext;

import android.view.View;
import android.view.ViewGroup;


/**
 * M: the interface for Plug-in definition of QuickSettings.
 */
public interface IQuickSettingsPlugin {

  /** Set QuickSettings interfaces. @{ */

    void customizeTileViews(ViewGroup parent);       
    void updateResources();
   

}
