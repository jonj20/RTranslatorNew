package com.k2fsa.sherpa.onnx.tts.engine.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import nie.translator.rtranslator.R;


public final class ActivityManageLanguagesBinding { //implements ViewBinding {
    @NonNull
    public final Button buttonStart;
    
    @NonNull
    public final Button buttonTestVoices;
    
    @NonNull
    public final TextView coquiHeader;
    
    @NonNull
    public final ListView coquiModelList;
    
    @NonNull
    public final TextView downloadSize;
    
    @NonNull
    public final TextView piperHeader;
    
    @NonNull
    public final ListView piperModelList;
    
    @NonNull
    private final View rootView;

    private ActivityManageLanguagesBinding(@NonNull View rootView, 
                                           @NonNull Button buttonStart,
                                           @NonNull Button buttonTestVoices,
                                           @NonNull TextView coquiHeader,
                                           @NonNull ListView coquiModelList,
                                           @NonNull TextView downloadSize,
                                           @NonNull TextView piperHeader,
                                           @NonNull ListView piperModelList) {
        this.rootView = rootView;
        this.buttonStart = buttonStart;
        this.buttonTestVoices = buttonTestVoices;
        this.coquiHeader = coquiHeader;
        this.coquiModelList = coquiModelList;
        this.downloadSize = downloadSize;
        this.piperHeader = piperHeader;
        this.piperModelList = piperModelList;
    }

    @NonNull
    public static ActivityManageLanguagesBinding inflate(@NonNull LayoutInflater inflater) {
        return inflate(inflater, null, false);
    }

    @NonNull
    public static ActivityManageLanguagesBinding inflate(@NonNull LayoutInflater inflater, 
                                                         ViewGroup parent, 
                                                         boolean attachToParent) {
        View root = inflater.inflate(R.layout.activity_manage_languages, parent, attachToParent);
        return bind(root);
    }

    @NonNull
    public static ActivityManageLanguagesBinding bind(@NonNull View rootView) {
        Button buttonStart = rootView.findViewById(R.id.button_start);
        Button buttonTestVoices = rootView.findViewById(R.id.button_test_voices);
        TextView coquiHeader = rootView.findViewById(R.id.coqui_header);
        ListView coquiModelList = rootView.findViewById(R.id.coqui_model_list);
        TextView downloadSize = rootView.findViewById(R.id.download_size);
        TextView piperHeader = rootView.findViewById(R.id.piper_header);
        ListView piperModelList = rootView.findViewById(R.id.piper_model_list);

        return new ActivityManageLanguagesBinding(rootView, buttonStart, buttonTestVoices, 
                                                  coquiHeader, coquiModelList, downloadSize, 
                                                  piperHeader, piperModelList);
    }

    @NonNull
    //@Override
    public View getRoot() {
        return rootView;
    }
}