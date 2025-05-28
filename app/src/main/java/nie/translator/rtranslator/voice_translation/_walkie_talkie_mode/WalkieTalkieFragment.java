/*
 * Copyright 2016 Luca Martino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyFile of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nie.translator.rtranslator.voice_translation._walkie_talkie_mode;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.Toolbar;
import android.widget.Button;
import android.os.SystemClock;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.settings.SettingsActivity;
import nie.translator.rtranslator.utils.CustomLocale;
import nie.translator.rtranslator.utils.ErrorCodes;
import nie.translator.rtranslator.utils.Tools;
import nie.translator.rtranslator.utils.gui.AnimatedTextView;
import nie.translator.rtranslator.utils.gui.ButtonMic;
import nie.translator.rtranslator.utils.gui.ButtonSound;
import nie.translator.rtranslator.utils.gui.DeactivableButton;
import nie.translator.rtranslator.utils.gui.LanguageListAdapter;
import nie.translator.rtranslator.utils.gui.messages.GuiMessage;
import nie.translator.rtranslator.utils.gui.messages.MessagesAdapter;
import nie.translator.rtranslator.utils.services_communication.ServiceCommunicator;
import nie.translator.rtranslator.utils.services_communication.ServiceCommunicatorListener;
import nie.translator.rtranslator.voice_translation.VoiceTranslationFragment;
import nie.translator.rtranslator.voice_translation.VoiceTranslationService;


public class WalkieTalkieFragment extends VoiceTranslationFragment {
    public static final int INITIALIZE = 0;
    public static final long LONG_PRESS_THRESHOLD_MS = 700;
    private boolean isMicAutomatic = true;
    private boolean isAudioPathBTMode = false; //can switch audio path from bt to local according to button state
    private boolean isAudioPathBT = false;
    private ConstraintLayout container;
    protected ButtonMic micBtn;
    private ButtonMic leftMicBtn;
    private ButtonMic rightMicBtn;
    private AnimatedTextView leftMicLanguage;
    private AnimatedTextView rightMicLanguage;
    private ConstraintLayout constraintLayout;
    private AppCompatImageButton exitButton;
    private ConstraintLayout firstLanguageSelector;
    private ConstraintLayout secondLanguageSelector;
    private AppCompatImageButton settingsButton;
    private ButtonSound sound;
    private long lastPressedLeftMic = -1;
    private long lastPressedRightMic = -1;
    //connection
    protected WalkieTalkieService.WalkieTalkieServiceCommunicator walkieTalkieServiceCommunicator;
    protected VoiceTranslationService.VoiceTranslationServiceCallback walkieTalkieServiceCallback;

    //languageListDialog
    private LanguageListAdapter listView;
    private ListView listViewGui;
    private ProgressBar progressBar;
    private ImageButton reloadButton;
    private String selectedLanguageCode;
    private AlertDialog dialog;
    private Handler mHandler = new Handler();


    private void performButtonPressDown(AppCompatImageButton button) {
        MotionEvent downEvent = MotionEvent.obtain(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 0, 0, 0);
        button.dispatchTouchEvent(downEvent); // 按下
    }
    private void performButtonPressUp(AppCompatImageButton button) {
        MotionEvent upEvent = MotionEvent.obtain(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 0, 0, 0);
        button.dispatchTouchEvent(upEvent); // 弹起
    }

    public WalkieTalkieFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        walkieTalkieServiceCommunicator = new WalkieTalkieService.WalkieTalkieServiceCommunicator(0);
        walkieTalkieServiceCallback = new WalkieTalkieServiceCallback();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_walkie_talkie, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        constraintLayout = view.findViewById(R.id.container);
        container = view.findViewById(R.id.walkie_talkie_main_container);
        firstLanguageSelector = view.findViewById(R.id.firstLanguageSelector);
        secondLanguageSelector = view.findViewById(R.id.secondLanguageSelector);
        exitButton = view.findViewById(R.id.exitButton);
        sound = view.findViewById(R.id.soundButton);
        micBtn = view.findViewById(R.id.buttonMic);
        micBtn.initialize(this, view.findViewById(R.id.leftLine), view.findViewById(R.id.centerLine), view.findViewById(R.id.rightLine));
        leftMicBtn = view.findViewById(R.id.buttonMicLeft);
        leftMicBtn.initialize(null, view.findViewById(R.id.leftLineL), view.findViewById(R.id.centerLineL), view.findViewById(R.id.rightLineL));
        rightMicBtn = view.findViewById(R.id.buttonMicRight);
        rightMicBtn.initialize(null, view.findViewById(R.id.leftLineR), view.findViewById(R.id.centerLineR), view.findViewById(R.id.rightLineR));
        leftMicLanguage = view.findViewById(R.id.textButton1);
        rightMicLanguage = view.findViewById(R.id.textButton2);
        settingsButton = view.findViewById(R.id.settingsButton);
        description.setText(R.string.description_walkie_talkie);
        deactivateInputs(DeactivableButton.DEACTIVATED);
        //container.setVisibility(View.INVISIBLE);  //we make the UI invisible until the restore of the attributes from the service (to avoid instant changes of the UI).
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final View.OnClickListener deactivatedClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Toast.makeText(activity, getResources().getString(R.string.error_wait_initialization), Toast.LENGTH_SHORT).show();
            }
        };
        final View.OnClickListener micMissingClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(activity, R.string.error_missing_mic_permissions, Toast.LENGTH_SHORT).show();
            }
        };
        Toolbar toolbar = activity.findViewById(R.id.toolbarWalkieTalkie);
        activity.setActionBar(toolbar);
        // we give the constraint layout the information on the system measures (status bar etc.), which has the fragmentContainer,
        // because they are not passed to it if started with a Transaction and therefore it overlaps the status bar because it fitsSystemWindows does not work
        WindowInsets windowInsets = activity.getFragmentContainer().getRootWindowInsets();
        if (windowInsets != null) {
            constraintLayout.dispatchApplyWindowInsets(windowInsets.replaceSystemWindowInsets(windowInsets.getSystemWindowInsetLeft(),windowInsets.getSystemWindowInsetTop(),windowInsets.getSystemWindowInsetRight(),0));
        }

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(activity, SettingsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });

        sound.setOnClickListenerForDeactivated(deactivatedClickListener);
        sound.setOnClickListenerForTTSError(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(global, R.string.error_tts_toast, Toast.LENGTH_SHORT).show();
            }
        });
        sound.setOnClickListenerForActivated(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (sound.isMute()) {
                    startSound();
                } else {
                    stopSound();
                }
            }
        });

        micBtn.setOnClickListenerForActivated(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (micBtn.getState() == ButtonMic.STATE_NORMAL) {
                    if(isMicAutomatic) {
                        if (micBtn.isMute()) {
                            startMicrophone(true);
                        } else {
                            stopMicrophone(true);
                        }
                    }else{
                        switchMicMode(true);
                    }
                }
            }
        });
        micBtn.setOnClickListenerForDeactivatedForMissingMicPermission(micMissingClickListener);
        micBtn.setOnClickListenerForDeactivated(deactivatedClickListener);

        leftMicBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:  // PRESSED
                        if (leftMicBtn.getActivationStatus() == DeactivableButton.ACTIVATED && leftMicBtn.getState() == ButtonMic.STATE_NORMAL) {
                            if(isMicAutomatic) {
                                switchMicMode(false);
                            }
                            if(!leftMicBtn.isListening()){
                                walkieTalkieServiceCommunicator.startRecognizingFirstLanguage();
                                //leftMicBtn.onVoiceStarted();
                            }else{
                                //leftMicBtn.onVoiceEnded();
                                walkieTalkieServiceCommunicator.stopRecognizingFirstLanguage();
                            }
                            lastPressedLeftMic = System.currentTimeMillis();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:  // RELEASED
                        if(leftMicBtn.getActivationStatus() == DeactivableButton.ACTIVATED){
                            if(leftMicBtn.getState() == ButtonMic.STATE_NORMAL && lastPressedLeftMic != -1){
                                if(System.currentTimeMillis() - lastPressedLeftMic <= LONG_PRESS_THRESHOLD_MS){  //short click release

                                }else{   //long click release
                                    if(leftMicBtn.isListening()){
                                        //leftMicBtn.onVoiceEnded();
                                        walkieTalkieServiceCommunicator.stopRecognizingFirstLanguage();
                                    }
                                }
                            }
                        }else{
                            leftMicBtn.performClick();
                        }
                        lastPressedLeftMic = -1;
                        return true;
                }
                return false;
            }
        });
        leftMicBtn.setOnClickListenerForDeactivatedForMissingMicPermission(micMissingClickListener);
        leftMicBtn.setOnClickListenerForDeactivated(deactivatedClickListener);

        //only support leftMicBtn/
        rightMicBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:  // PRESSED

                        //// simluate
                        if(!rightMicBtn.isListening()){
                            if(isAudioPathBTMode) {
                                performButtonPressUp(leftMicBtn);
                                switchAudioPath(false);
                            }
                        }

                        if (rightMicBtn.getActivationStatus() == DeactivableButton.ACTIVATED && rightMicBtn.getState() == ButtonMic.STATE_NORMAL) {
                            if(isMicAutomatic) {
                                switchMicMode(false);
                            }
                            if(!rightMicBtn.isListening()){
                                //rightMicBtn.onVoiceStarted();
                                walkieTalkieServiceCommunicator.startRecognizingSecondLanguage();
                            }else{
                                walkieTalkieServiceCommunicator.stopRecognizingSecondLanguage();
                            }
                            lastPressedRightMic = System.currentTimeMillis();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:  // RELEASED
                        if(rightMicBtn.getActivationStatus() == DeactivableButton.ACTIVATED){
                            if(rightMicBtn.getState() == ButtonMic.STATE_NORMAL && lastPressedRightMic != -1){
                                if(System.currentTimeMillis() - lastPressedRightMic <= LONG_PRESS_THRESHOLD_MS){  //short click release

                                }else{   //long click release
                                    if(rightMicBtn.isListening()){
                                        walkieTalkieServiceCommunicator.stopRecognizingSecondLanguage();
                                    }
                                }

                                //// simluate
                                if(isAudioPathBTMode) {
                                    performButtonPressDown(leftMicBtn);
                                    switchAudioPath(true);
                                }
                            }
                        }else{
                            rightMicBtn.performClick();
                        }
                        lastPressedRightMic = -1;
                        return true;
                }
                return false;
            }
        });
        rightMicBtn.setOnClickListenerForDeactivatedForMissingMicPermission(micMissingClickListener);
        rightMicBtn.setOnClickListenerForDeactivated(deactivatedClickListener);

        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("button", "exitButton pressed");
                activity.onBackPressed();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getArguments() != null) {
            if (getArguments().getBoolean("firstStart", false)) {
                getArguments().remove("firstStart");
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        connectToService();
                    }
                }, 300);
            } else {
                connectToService();
            }
        } else {
            connectToService();
        }
    }

    @Override
    protected void connectToService() {
        activity.connectToWalkieTalkieService(walkieTalkieServiceCallback, new ServiceCommunicatorListener() {
            @Override
            public void onServiceCommunicator(ServiceCommunicator serviceCommunicator) {
                walkieTalkieServiceCommunicator = (WalkieTalkieService.WalkieTalkieServiceCommunicator) serviceCommunicator;
                restoreAttributesFromService();
                // listener setting for the two language selectors
                firstLanguageSelector.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showLanguageListDialog(1);
                    }
                });
                secondLanguageSelector.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showLanguageListDialog(2);
                    }
                });

                // setting of the selected languages
                walkieTalkieServiceCommunicator.getFirstLanguage(new WalkieTalkieService.LanguageListener() {
                    @Override
                    public void onLanguage(CustomLocale language) {
                        setFirstLanguage(language);
                    }
                });
                walkieTalkieServiceCommunicator.getSecondLanguage(new WalkieTalkieService.LanguageListener() {
                    @Override
                    public void onLanguage(CustomLocale language) {
                        setSecondLanguage(language);
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                WalkieTalkieFragment.super.onFailureConnectingWithService(reasons, value);
            }
        });
    }

    @Override
    public void restoreAttributesFromService() {
        walkieTalkieServiceCommunicator.getAttributes(new VoiceTranslationService.AttributesListener() {
            @Override
            public void onSuccess(ArrayList<GuiMessage> messages, boolean isMicMute, boolean isAudioMute, boolean isTTSError, final boolean isEditTextOpen, boolean isBluetoothHeadsetConnected, boolean isMicAutomatic, boolean isMicActivated, int listeningMic) {
                // initialization with service values
                //container.setVisibility(View.VISIBLE);
                mAdapter = new MessagesAdapter(messages, new MessagesAdapter.Callback() {
                    @Override
                    public void onFirstItemAdded() {
                        description.setVisibility(View.GONE);
                        mRecyclerView.setVisibility(View.VISIBLE);
                    }
                });
                mRecyclerView.setAdapter(mAdapter);
                // restore micBtn and sound status
                if(isMicAutomatic) {
                    micBtn.setMute(isMicMute, false);
                    leftMicBtn.setMute(true, false);
                    rightMicBtn.setMute(true, false);
                    if(isMicActivated) {
                        if (listeningMic == VoiceTranslationService.AUTO_LANGUAGE) {
                            micBtn.onVoiceStarted(false);
                        } else {
                            micBtn.onVoiceEnded(false);
                        }
                    }else{
                        micBtn.onVoiceEnded(false);
                    }
                    leftMicBtn.onVoiceEnded(false);
                    rightMicBtn.onVoiceEnded(false);
                }else{
                    WalkieTalkieFragment.this.isMicAutomatic = false;
                    micBtn.setMute(true, false);
                    leftMicBtn.setMute(false, false);
                    rightMicBtn.setMute(false, false);
                    if(isMicActivated) {
                        if (listeningMic == VoiceTranslationService.FIRST_LANGUAGE) {
                            leftMicBtn.onVoiceStarted(false);
                        } else {
                            leftMicBtn.onVoiceEnded(false);
                        }
                        if (listeningMic == VoiceTranslationService.SECOND_LANGUAGE) {
                            rightMicBtn.onVoiceStarted(false);
                        } else {
                            rightMicBtn.onVoiceEnded(false);
                        }
                    }else{
                        leftMicBtn.onVoiceEnded(false);
                        rightMicBtn.onVoiceEnded(false);
                    }
                    micBtn.onVoiceEnded(false);
                }

                sound.setMute(isAudioMute);
                if(isTTSError){
                    sound.deactivate(DeactivableButton.DEACTIVATED_FOR_TTS_ERROR);
                }

                if(isMicActivated){
                    if (!micBtn.isMute()) {
                        activateInputs(true);
                    } else {
                        activateInputs(false);
                    }
                }else{
                    deactivateInputs(DeactivableButton.DEACTIVATED);
                }
            }
        });
    }


    @Override
    public void startMicrophone(boolean changeAspect) {
        if (changeAspect) {
            micBtn.setMute(false);
        }
        walkieTalkieServiceCommunicator.startMic();
    }

    @Override
    public void stopMicrophone(boolean changeAspect) {
        if (changeAspect) {
            micBtn.setMute(true);
        }
        walkieTalkieServiceCommunicator.stopMic(changeAspect);
    }

    protected void startSound() {
        sound.setMute(false);
        walkieTalkieServiceCommunicator.startSound();
    }

    protected void stopSound() {
        sound.setMute(true);
        walkieTalkieServiceCommunicator.stopSound();
    }

    @Override
    protected void deactivateInputs(int cause) {
        micBtn.deactivate(cause);
        leftMicBtn.deactivate(cause);
        rightMicBtn.deactivate(cause);
        if (cause == DeactivableButton.DEACTIVATED) {
            sound.deactivate(DeactivableButton.DEACTIVATED);
        } else {
            sound.activate(false);  // to activate the button sound which otherwise remains deactivated and when clicked it shows the message "wait for initialisation"
        }
    }

    @Override
    protected void activateInputs(boolean start) {
        Log.d("mic", "activatedInputs");
        micBtn.activate(start);
        leftMicBtn.activate(false);
        rightMicBtn.activate(false);
        sound.activate(false);
    }

    private void switchMicMode(boolean automatic){
        if(isMicAutomatic != automatic){
            //walkieTalkieServiceCallback.onVoiceEnded();
            isMicAutomatic = automatic;
            if(!isMicAutomatic){  //we switched from automatic to manual
                micBtn.setMute(true);
                leftMicBtn.setMute(false);
                rightMicBtn.setMute(false);
                walkieTalkieServiceCommunicator.startManualRecognition();
            }else{
                walkieTalkieServiceCommunicator.stopManualRecognition();
                micBtn.setMute(false);
                leftMicBtn.setMute(true);
                rightMicBtn.setMute(true);
            }
        }
    }


    private void switchAudioPath(boolean bt){
        if(isAudioPathBT != bt){
            //walkieTalkieServiceCallback.onVoiceEnded();
            isAudioPathBT = bt;
            if(!isAudioPathBT){  //we switched from automatic to manual
                //micBtn.setMute(true);
                leftMicBtn.setMute(false); //change to bt
                //rightMicBtn.setMute(false);
                walkieTalkieServiceCommunicator.switchAudioPathToBT();
            }else{
                walkieTalkieServiceCommunicator.switchAudioPathToLocal();
                //micBtn.setMute(false);
                leftMicBtn.setMute(true);
                //rightMicBtn.setMute(true);
            }
        }
    }


    private void showLanguageListDialog(final int languageNumber) {
        //when the dialog is shown at the beginning the loading is shown, then once the list of languages​is obtained (within the showList)
        //the loading is replaced with the list of languages
        String title = "";
        switch (languageNumber) {
            case 1: {
                title = global.getResources().getString(R.string.dialog_select_first_language);
                break;
            }
            case 2: {
                title = global.getResources().getString(R.string.dialog_select_second_language);
                break;
            }
        }

        final View editDialogLayout = activity.getLayoutInflater().inflate(R.layout.dialog_languages, null);

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setCancelable(true);
        builder.setTitle(title);

        dialog = builder.create();
        dialog.setView(editDialogLayout, 0, Tools.convertDpToPixels(activity, 16), 0, 0);
        dialog.show();

        listViewGui = editDialogLayout.findViewById(R.id.list_view_dialog);
        progressBar = editDialogLayout.findViewById(R.id.progressBar3);
        reloadButton = editDialogLayout.findViewById(R.id.reloadButton);

        Global.GetLocaleListener listener = new Global.GetLocaleListener() {
            @Override
            public void onSuccess(final CustomLocale result) {
                reloadButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showList(languageNumber, result);
                    }
                });
                showList(languageNumber, result);
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                onFailureShowingList(reasons, value);
            }
        };

        switch (languageNumber) {
            case 1: {
                global.getFirstLanguage(false, listener);
                break;
            }
            case 2: {
                global.getSecondLanguage(false, listener);
                break;
            }
        }

    }

    private void showList(final int languageNumber, final CustomLocale selectedLanguage) {
        reloadButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);

        global.getLanguages(true, true, new Global.GetLocalesListListener() {
            @Override
            public void onSuccess(final ArrayList<CustomLocale> languages) {
                progressBar.setVisibility(View.GONE);
                listViewGui.setVisibility(View.VISIBLE);

                listView = new LanguageListAdapter(activity, languages, selectedLanguage);
                listViewGui.setAdapter(listView);
                listViewGui.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                        if (languages.contains((CustomLocale) listView.getItem(position))) {
                            switch (languageNumber) {
                                case 1: {
                                    setFirstLanguage((CustomLocale) listView.getItem(position));
                                    break;
                                }
                                case 2: {
                                    setSecondLanguage((CustomLocale) listView.getItem(position));
                                    break;
                                }
                            }
                        }
                        dialog.dismiss();
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                onFailureShowingList(reasons, value);
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        mHandler.removeCallbacksAndMessages(null);
        firstLanguageSelector.setOnClickListener(null);
        secondLanguageSelector.setOnClickListener(null);
        activity.disconnectFromWalkieTalkieService(walkieTalkieServiceCommunicator);
    }

    private void setFirstLanguage(CustomLocale language) {
        // new language setting in the WalkieTalkieService
        walkieTalkieServiceCommunicator.changeFirstLanguage(language);
        // save firstLanguage selected
        global.setFirstLanguage(language);
        // change language displayed
        global.getTTSLanguages(true, new Global.GetLocalesListListener() {
            @Override
            public void onSuccess(ArrayList<CustomLocale> ttsLanguages) {
                ((AnimatedTextView) firstLanguageSelector.findViewById(R.id.firstLanguageName)).setText(language.getDisplayNameWithoutTTS(), true);
                leftMicLanguage.setText(language.getDisplayNameWithoutTTS(), true);
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                //never called in this case
            }
        });
    }

    private void setSecondLanguage(CustomLocale language) {
        // new language setting in the WalkieTalkieService
        walkieTalkieServiceCommunicator.changeSecondLanguage(language);
        // save secondLanguage selected
        global.setSecondLanguage(language);
        // change language displayed
        global.getTTSLanguages(true, new Global.GetLocalesListListener() {
            @Override
            public void onSuccess(ArrayList<CustomLocale> ttsLanguages) {
                ((AnimatedTextView) secondLanguageSelector.findViewById(R.id.secondLanguageName)).setText(language.getDisplayNameWithoutTTS(), true);
                rightMicLanguage.setText(language.getDisplayNameWithoutTTS(), true);
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                //never called in this case
            }
        });
    }

    private void onFailureShowingList(int[] reasons, long value) {
        progressBar.setVisibility(View.GONE);
        reloadButton.setVisibility(View.VISIBLE);
        for (int aReason : reasons) {
            switch (aReason) {
                case ErrorCodes.MISSED_ARGUMENT:
                case ErrorCodes.SAFETY_NET_EXCEPTION:
                case ErrorCodes.MISSED_CONNECTION:
                    Toast.makeText(activity, getResources().getString(R.string.error_internet_lack_loading_languages), Toast.LENGTH_LONG).show();
                    break;
                default:
                    activity.onError(aReason, value);
                    break;
            }
        }
    }

    /**
     * Handles user acceptance (or denial) of our permission request.
     */
    @CallSuper
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != VoiceTranslationService.REQUEST_CODE_REQUIRED_PERMISSIONS) {
            return;
        }

        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(activity, R.string.error_missing_mic_permissions, Toast.LENGTH_LONG).show();
                deactivateInputs(DeactivableButton.DEACTIVATED_FOR_MISSING_MIC_PERMISSION);
                return;
            }
        }

        // possible activation of the mic
        if (!micBtn.isMute() && micBtn.getActivationStatus() == DeactivableButton.ACTIVATED) {
            startMicrophone(false);
        }
    }



    public class WalkieTalkieServiceCallback extends VoiceTranslationService.VoiceTranslationServiceCallback {
        @Override
        public void onVoiceStarted(int mode) {
            super.onVoiceStarted(mode);
            if(mode == VoiceTranslationService.AUTO_LANGUAGE && !micBtn.isMute()) {
                micBtn.onVoiceStarted(true);
                Log.e("onVoiceStart", "onVoiceStart center");
            }else if(mode == VoiceTranslationService.FIRST_LANGUAGE && !leftMicBtn.isMute()) {
                leftMicBtn.onVoiceStarted(true);
                Log.e("onVoiceStart", "onVoiceStart left");
            }else if(mode == VoiceTranslationService.SECOND_LANGUAGE && !rightMicBtn.isMute()) {
                rightMicBtn.onVoiceStarted(true);
                Log.e("onVoiceStart", "onVoiceStart right");
            }
        }

        @Override
        public void onVoiceEnded() {
            super.onVoiceEnded();
            micBtn.onVoiceEnded(true);
            leftMicBtn.onVoiceEnded(true);
            rightMicBtn.onVoiceEnded(true);
        }

        @Override
        public void onVolumeLevel(float volumeLevel) {
            super.onVolumeLevel(volumeLevel);
            if(micBtn.isListening()) {
                micBtn.updateVolumeLevel(volumeLevel);
            }else if(leftMicBtn.isListening()){
                leftMicBtn.updateVolumeLevel(volumeLevel);
            }else if(rightMicBtn.isListening()){
                rightMicBtn.updateVolumeLevel(volumeLevel);
            }
        }

        @Override
        public void onMicActivated() {
            super.onMicActivated();
            Log.d("mic", "onMicActivated");
            if(!micBtn.isActivated()) {
                micBtn.activate(false);
            }
            if(!leftMicBtn.isActivated()) {
                leftMicBtn.activate(false);
            }
            if(!rightMicBtn.isActivated()) {
                rightMicBtn.activate(false);
            }
        }

        @Override
        public void onMicDeactivated() {
            super.onMicDeactivated();
            if (micBtn.getState() == ButtonMic.STATE_NORMAL && micBtn.isActivated()) {
                micBtn.deactivate(DeactivableButton.DEACTIVATED);
            }
            if (leftMicBtn.getState() == ButtonMic.STATE_NORMAL && leftMicBtn.isActivated()) {
                leftMicBtn.deactivate(DeactivableButton.DEACTIVATED);
            }
            if (rightMicBtn.getState() == ButtonMic.STATE_NORMAL && rightMicBtn.isActivated()) {
                rightMicBtn.deactivate(DeactivableButton.DEACTIVATED);
            }
        }

        @Override
        public void onMessage(GuiMessage message) {
            super.onMessage(message);
            if (message != null) {
                int messageIndex = mAdapter.getMessageIndex(message.getMessageID());
                if(messageIndex != -1) {
                    if((!mRecyclerView.isAnimating() && !mRecyclerView.getLayoutManager().isSmoothScrolling()) || message.isFinal()) {
                        if(message.isFinal()){
                            if(mRecyclerView.getItemAnimator() != null) {
                                mRecyclerView.getItemAnimator().endAnimations();
                            }
                        }
                        mAdapter.setMessage(messageIndex, message);
                    }
                }else{
                    if(mRecyclerView.getItemAnimator() != null) {
                        mRecyclerView.getItemAnimator().endAnimations();
                    }
                    mAdapter.addMessage(message);
                    //we do an eventual automatic scroll (only if we are at the bottom of the recyclerview)
                    if(((LinearLayoutManager) mRecyclerView.getLayoutManager()).findLastVisibleItemPosition() == mAdapter.getItemCount()-2){
                        mRecyclerView.smoothScrollToPosition(mAdapter.getItemCount()-1);
                    }
                }
            }
        }

        @Override
        public void onError(int[] reasons, long value) {
            for (int aReason : reasons) {
                switch (aReason) {
                    case ErrorCodes.SAFETY_NET_EXCEPTION:
                    case ErrorCodes.MISSED_CONNECTION:
                        activity.showInternetLackDialog(R.string.error_internet_lack_services, null);
                        break;
                    case ErrorCodes.MISSING_GOOGLE_TTS:
                        sound.deactivate(DeactivableButton.DEACTIVATED_FOR_TTS_ERROR);
                        //activity.showMissingGoogleTTSDialog();
                        break;
                    case ErrorCodes.GOOGLE_TTS_ERROR:
                        sound.deactivate(DeactivableButton.DEACTIVATED_FOR_TTS_ERROR);
                        //activity.showGoogleTTSErrorDialog();
                        break;
                    case VoiceTranslationService.MISSING_MIC_PERMISSION: {
                        if(getContext() != null) {
                            requestPermissions(VoiceTranslationService.REQUIRED_PERMISSIONS, VoiceTranslationService.REQUEST_CODE_REQUIRED_PERMISSIONS);
                        }
                        break;
                    }
                    default: {
                        activity.onError(aReason, value);
                        break;
                    }
                }
            }
        }
    }
}