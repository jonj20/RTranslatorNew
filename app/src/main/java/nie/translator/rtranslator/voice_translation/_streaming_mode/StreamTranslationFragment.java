package nie.translator.rtranslator.voice_translation._streaming_mode;

import static android.content.Context.CLIPBOARD_SERVICE;

import android.animation.Animator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.bluetooth.Message;
import nie.translator.rtranslator.neural_networks.translation.Translator;
import nie.translator.rtranslator.neural_networks.tts.TTS;
import nie.translator.rtranslator.settings.SettingsActivity;
import nie.translator.rtranslator.utils.CustomLocale;
import nie.translator.rtranslator.utils.ErrorCodes;
import nie.translator.rtranslator.utils.Tools;
import nie.translator.rtranslator.utils.gui.AnimatedTextView;
import nie.translator.rtranslator.utils.gui.ButtonMic;
import nie.translator.rtranslator.utils.gui.ButtonSound;
import nie.translator.rtranslator.utils.gui.DeactivableButton;
import nie.translator.rtranslator.utils.gui.GuiTools;
import nie.translator.rtranslator.utils.gui.LanguageListAdapter;
import nie.translator.rtranslator.utils.gui.MicrophoneComunicable;
import nie.translator.rtranslator.utils.gui.animations.CustomAnimator;
import nie.translator.rtranslator.utils.gui.messages.GuiMessage;
import nie.translator.rtranslator.utils.gui.messages.MessagesAdapter;
import nie.translator.rtranslator.utils.services_communication.ServiceCommunicator;
import nie.translator.rtranslator.utils.services_communication.ServiceCommunicatorListener;
import nie.translator.rtranslator.voice_translation.VoiceTranslationFragment;
import nie.translator.rtranslator.voice_translation.VoiceTranslationService;
import nie.translator.rtranslator.voice_translation.VoiceTranslationActivity;

public class StreamTranslationFragment  extends Fragment implements MicrophoneComunicable {
    public static final int DEFAULT_BEAM_SIZE = 1;
    public static final int MAX_BEAM_SIZE = 6;
    public static final long LONG_PRESS_THRESHOLD_MS = 700;
    private boolean isMicAutomatic = true;
    private VoiceTranslationActivity activity;
    private Global global;
    private Translator.TranslateListener translateListener;
    private TextWatcher inputTextListener;
    private TextWatcher outputTextListener;
    private TTS tts;
    private UtteranceProgressListener ttsListener;

    //TranslatorFragment's GUI
    private MaterialButton translateButton;
    private ButtonMic leftMicBtn;
    //private FloatingActionButton streamTranslationButton;


    private EditText inputText;
    private EditText outputText;
	private AppCompatImageButton exitButton;
    private CardView firstLanguageSelector;
    private CardView secondLanguageSelector;
    private AppCompatImageButton invertLanguagesButton;
    //private View lineSeparator;
    private ConstraintLayout toolbarContainer;
    private TextView title;
    private AppCompatImageButton settingsButton;
    private ButtonSound sound;
    private AppCompatImageButton settingsButtonReduced;
    private AppCompatImageButton backButton;
    private FloatingActionButton copyInputButton;
    private FloatingActionButton copyOutputButton;
    private FloatingActionButton cancelInputButton;
    private FloatingActionButton cancelOutputButton;
    private FloatingActionButton ttsInputButton;
    private FloatingActionButton ttsOutputButton;
    private ConstraintLayout outputContainer;
    private CustomAnimator animator = new CustomAnimator();
    private Animator colorAnimator = null;
    private int activatedColor = R.color.primary;
    private int deactivatedColor = R.color.gray;
    private boolean isKeyboardShowing = false;
    private boolean isScreenReduced = false;
    private boolean isInputEmpty = true;
    private boolean isOutputEmpty = true;

    private static final int REDUCED_GUI_THRESHOLD_DP = 550;

    private long lastPressedLeftMic = -1;

    //connection
    protected StreamTranslationService.StreamTranslationServiceCommunicator streamTranslationServiceCommunicator;
    protected VoiceTranslationService.VoiceTranslationServiceCallback streamTranslationServiceCallback;


    //languageListDialog
    private LanguageListAdapter listView;
    private ListView listViewGui;
    private ProgressBar progressBar;
    private ImageButton reloadButton;
    private AlertDialog dialog;
    private Handler mHandler = new Handler();

    //animations
    private int textActionButtonHeight;
    private int textActionButtonBottomMargin;
    private int actionButtonHeight;
    private int translateButtonHeight;
    private int actionButtonTopMargin;
    private int actionButtonBottomMargin;
    @Nullable
    private Animator animationKeyboardButton;
    @Nullable
    private Animator animationKeyboardTop;
    @Nullable
    private Animator animationInput;
    @Nullable
    private Animator animationOutput;

    //adddd
    String lastInputText = "";
    String lastOutputText = "";


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        streamTranslationServiceCommunicator = new StreamTranslationService.StreamTranslationServiceCommunicator(0);
        streamTranslationServiceCallback = new StreamTranslationServiceCallback();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_stream_translation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        firstLanguageSelector = view.findViewById(R.id.firstLanguageSelectorContainer);
        secondLanguageSelector = view.findViewById(R.id.secondLanguageSelectorContainer);
        invertLanguagesButton = view.findViewById(R.id.invertLanguages);
        translateButton = view.findViewById(R.id.buttonTranslate);
        exitButton = view.findViewById(R.id.exitButton);
        sound = view.findViewById(R.id.soundButton);
        leftMicBtn = view.findViewById(R.id.buttonMicLeft);
        leftMicBtn.initialize(null, view.findViewById(R.id.leftLineL), view.findViewById(R.id.centerLineL), view.findViewById(R.id.rightLineL));
        inputText = view.findViewById(R.id.multiAutoCompleteTextView);
        outputText = view.findViewById(R.id.multiAutoCompleteTextView2);
        //lineSeparator = view.findViewById(R.id.lineSeparator);
        toolbarContainer = view.findViewById(R.id.toolbarTranslatorContainer);
        title = view.findViewById(R.id.title2);
        settingsButton = view.findViewById(R.id.settingsButton);
        settingsButtonReduced = view.findViewById(R.id.settingsButton2);
        backButton = view.findViewById(R.id.backButton);
        copyInputButton = view.findViewById(R.id.copyButtonInput);
        copyOutputButton = view.findViewById(R.id.copyButtonOutput);
        cancelInputButton = view.findViewById(R.id.cancelButtonInput);
        cancelOutputButton = view.findViewById(R.id.cancelButtonOutput);
        ttsInputButton = view.findViewById(R.id.ttsButtonInput);
        ttsOutputButton = view.findViewById(R.id.ttsButtonOutput);
        outputContainer = view.findViewById(R.id.outputContainer);
        //we set the initial tag for the tts buttons
        ttsInputButton.setTag(R.drawable.sound_icon);
        ttsOutputButton.setTag(R.drawable.sound_icon);
    }

    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity = (VoiceTranslationActivity) requireActivity();
        global = (Global) activity.getApplication();
        Toolbar toolbar = activity.findViewById(R.id.toolbarTranslator);
        activity.setActionBar(toolbar);
        //inputText.setImeOptions(EditorInfo.IME_ACTION_GO);
        //inputText.setRawInputType(InputType.TYPE_CLASS_TEXT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getActivity().getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }


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



        // setting of the selected languages
        global.getFirstTextLanguage(true, new Global.GetLocaleListener() {
            @Override
            public void onSuccess(CustomLocale result) {
                setFirstLanguage(result);
            }
            @Override
            public void onFailure(int[] reasons, long value) {

            }
        });
        global.getSecondTextLanguage(true, new Global.GetLocaleListener() {
            @Override
            public void onSuccess(CustomLocale result) {
                setSecondLanguage(result);
            }
            @Override
            public void onFailure(int[] reasons, long value) {

            }
        });


        leftMicBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:  // PRESSED
                        Log.d("mic", "leftMicBtn onTouch ACTION_DOWN");
                        if (leftMicBtn.getActivationStatus() == DeactivableButton.ACTIVATED && leftMicBtn.getState() == ButtonMic.STATE_NORMAL) {
                            if(isMicAutomatic) {
                                switchMicMode(false);
                            }
                            if(!leftMicBtn.isListening()){
                                streamTranslationServiceCommunicator.startRecognizingFirstLanguage();
                                //leftMicBtn.onVoiceStarted();
                            }else{
                                //leftMicBtn.onVoiceEnded();
                                streamTranslationServiceCommunicator.stopRecognizingFirstLanguage();
                            }
                            lastPressedLeftMic = System.currentTimeMillis();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:  // RELEASED
                        Log.d("mic", "leftMicBtn onTouch ACTION_UP");
                        if(leftMicBtn.getActivationStatus() == DeactivableButton.ACTIVATED){
                            if(leftMicBtn.getState() == ButtonMic.STATE_NORMAL && lastPressedLeftMic != -1){
                                if(System.currentTimeMillis() - lastPressedLeftMic <= LONG_PRESS_THRESHOLD_MS){  //short click release

                                }else{   //long click release
                                    if(leftMicBtn.isListening()){
                                        //leftMicBtn.onVoiceEnded();
                                        streamTranslationServiceCommunicator.stopRecognizingFirstLanguage();
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


        translateListener = new Translator.TranslateListener() {
            @Override
            public void onTranslatedText(String text, long resultID, boolean isFinal, CustomLocale languageOfText) {
                outputText.setText(text);
                if(isFinal){
                    activateTranslationButton();
                }
            }

            @Override
            public void onFailure(int[] reasons, long value) {

            }
        };
        translateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String text = inputText.getText().toString();

                /*if(text.length() <= 0){   //test code
                    text = "Also unlike 2014, there aren’t nearly as many loopholes. You can’t just buy a 150-watt incandescent or a three-way bulb, the ban covers any normal bulb that generates less than 45 lumens per watt, which pretty much rules out both incandescent and halogen tech in their entirety.";
                    inputText.setText(text);
                }*/

                if(!text.isEmpty()) {
                    String finalText = text;
                    global.getFirstAndSecondTextLanguages(true, new Global.GetTwoLocaleListener() {
                        @Override
                        public void onSuccess(CustomLocale firstLanguage, CustomLocale secondLanguage) {
                            //we deactivate translate button
                            deactivateTranslationButton();
                            //we start the translation
                            global.getTranslator().translate(finalText, firstLanguage, secondLanguage, global.getBeamSize(), true);
                        }

                        @Override
                        public void onFailure(int[] reasons, long value) {

                        }
                    });
                }
            }
        });
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(activity, SettingsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });
        settingsButtonReduced.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(activity, SettingsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isKeyboardShowing) {
                    activity.onBackPressed();
                }else {
                    View view = activity.getCurrentFocus();
                    if (view != null) {
                        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    }
                }
            }
        });
        copyInputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = inputText.getText().toString();
                if(!text.isEmpty()){
                    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("text", text);
                    clipboard.setPrimaryClip(clip);
                }
            }
        });
        copyOutputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = outputText.getText().toString();
                if(!text.isEmpty()){
                    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("text", text);
                    clipboard.setPrimaryClip(clip);
                }
            }
        });
        cancelInputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                inputText.setText("");
            }
        });
        cancelOutputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                outputText.setText("");
                global.getTranslator().resetLastOutput();
            }
        });
		
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


        //we hide the keyboard
        if(getView() != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
        }

        inputTextListener = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if(global.getTranslator() != null){
                    ///////////////todo

                    global.getTranslator().setLastInputText(new GuiMessage(new Message(global, s.toString()), true, true));
                }
                if(isInputEmpty != s.toString().isEmpty()){  //the input editText transitioned from empty to not empty or vice versa
                    isInputEmpty = s.toString().isEmpty();
                    if(animationInput != null){
                        animationInput.cancel();
                    }
                    if(!s.toString().isEmpty()) {
                        animationInput = animator.animateInputAppearance(activity, ttsInputButton, copyInputButton, cancelInputButton, new CustomAnimator.Listener() {
                            @Override
                            public void onAnimationEnd() {
                                super.onAnimationEnd();
                                animationInput = null;
                                translateButton.performClick(); //simluate
                            }
                        });
                    }else{
                        animationInput = animator.animateInputDisappearance(activity, ttsInputButton, copyInputButton, cancelInputButton, new CustomAnimator.Listener() {
                            @Override
                            public void onAnimationEnd() {
                                super.onAnimationEnd();
                                animationInput = null;
                                translateButton.performClick(); //simluate
                            }
                        });
                    }
                }
            }
        };
        inputText.addTextChangedListener(inputTextListener);
        inputText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //we start the compress animations
                /*if (!isKeyboardShowing) {
                    isKeyboardShowing = true;
                    onKeyboardVisibilityChanged(true);
                }*/
            }
        });

        outputTextListener = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if(isOutputEmpty != s.toString().isEmpty()){  //the output editText transitioned from empty to not empty or vice versa
                    isOutputEmpty = s.toString().isEmpty();
                    if(animationOutput != null){
                        animationOutput.cancel();
                    }
                    if(!s.toString().isEmpty()) {
                         Log.d("button", "setVisibility VISIBLE");
                        outputContainer.setVisibility(View.VISIBLE);
       
                        // animationOutput = animator.animateOutputAppearance(activity, outputContainer, lineSeparator, new CustomAnimator.Listener() {
                        //     @Override
                        //     public void onAnimationEnd() {
                        //         super.onAnimationEnd();
                        //         animationOutput = null;
                        //     }
                        // });
                    }else{
                        Log.d("button", "setVisibility GONE");
                        outputContainer.setVisibility(View.GONE);
         
                        // animationOutput = animator.animateOutputDisappearance(activity, outputContainer, lineSeparator, new CustomAnimator.Listener() {
                        //     @Override
                        //     public void onAnimationEnd() {
                        //         super.onAnimationEnd();
                        //         animationOutput = null;
                        //     }
                        // });
                    }
                }
            }
        };
        outputText.addTextChangedListener(outputTextListener);

        //we restore the last input and output text
        if(lastInputText != null){
            inputText.setText(lastInputText);
        }

        if(lastOutputText != null){
           outputText.setText(lastOutputText);
        }
        //we attach the translate listener
        global.getTranslator().addCallback(translateListener);
        //we attach the click listener for the language selectors
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
        invertLanguagesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                global.getFirstAndSecondTextLanguages(true, new Global.GetTwoLocaleListener() {
                    @Override
                    public void onSuccess(CustomLocale language1, CustomLocale language2) {
                        animator.animateSwitchLanguages(activity, firstLanguageSelector, secondLanguageSelector, invertLanguagesButton, new CustomAnimator.Listener() {
                            @Override
                            public void onAnimationEnd() {
                                super.onAnimationEnd();
                                setFirstLanguage(language2);
                                setSecondLanguage(language1);
                            }
                        });
                    }

                    @Override
                    public void onFailure(int[] reasons, long value) {

                    }
                });
            }
        });
        //we restore the translation button state based on the translation status
        if(global.getTranslator().isTranslating()){
            deactivateTranslationButton();
        }else{
            activateTranslationButton();
        }
        //we set some buttons to not clickable (it is done here as well as in the xml because android set clickable to true when we set an onClickListener)
        backButton.setClickable(false);
        settingsButtonReduced.setClickable(false);

        //we set the listener for the keyboard opening


        // tts initialization
        ttsListener = new UtteranceProgressListener() {
            @Override
            public void onStart(String s) {
            }

            @Override
            public void onDone(String s) {
                if(((int) ttsInputButton.getTag()) == R.drawable.stop_icon){
                    ttsInputButton.setImageResource(R.drawable.sound_icon);
                    ttsInputButton.setTag(R.drawable.sound_icon);
                }
                if(((int) ttsOutputButton.getTag()) == R.drawable.stop_icon){
                    ttsOutputButton.setImageResource(R.drawable.sound_icon);
                    ttsOutputButton.setTag(R.drawable.sound_icon);
                }
            }

            @Override
            public void onError(String s) {
            }
        };
        initializeTTS();

        ttsInputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(((int) ttsInputButton.getTag()) == R.drawable.stop_icon){
                    tts.stop();
                    ttsListener.onDone("");  //we call this to make eventual visual updates to the tts buttons (stop() doesn't call onDone automatically)
                }else {
                    global.getFirstTextLanguage(true, new Global.GetLocaleListener() {
                        @Override
                        public void onSuccess(CustomLocale firstLanguage) {
                            global.getTTSLanguages(true, new Global.GetLocalesListListener() {
                                @Override
                                public void onSuccess(ArrayList<CustomLocale> ttsLanguages) {
                                    if (CustomLocale.containsLanguage(ttsLanguages, firstLanguage)) { // check if the language can be speak
                                        tts.stop();
                                        ttsListener.onDone("");  //we call this to make eventual visual updates to the tts buttons (stop() doesn't call onDone automatically)
                                        speak(inputText.getText().toString(), firstLanguage);
                                        ttsInputButton.setImageResource(R.drawable.stop_icon);
                                        ttsInputButton.setTag(R.drawable.stop_icon);
                                    }
                                }

                                @Override
                                public void onFailure(int[] reasons, long value) {
                                    //we do nothing
                                }
                            });
                        }

                        @Override
                        public void onFailure(int[] reasons, long value) {
                            //we do nothing
                        }
                    });
                }
            }
        });

        ttsOutputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(((int) ttsOutputButton.getTag()) == R.drawable.stop_icon){
                    tts.stop();
                    ttsListener.onDone("");  //we call this to make eventual visual updates to the tts buttons (stop() doesn't call onDone automatically)
                }else {
                    global.getSecondTextLanguage(true, new Global.GetLocaleListener() {
                        @Override
                        public void onSuccess(CustomLocale secondLanguage) {
                            global.getTTSLanguages(true, new Global.GetLocalesListListener() {
                                @Override
                                public void onSuccess(ArrayList<CustomLocale> ttsLanguages) {
                                    if (CustomLocale.containsLanguage(ttsLanguages, secondLanguage)) { // check if the language can be speak
                                        tts.stop();
                                        ttsListener.onDone("");  //we call this to make eventual visual updates to the tts buttons (stop() doesn't call onDone automatically)
                                        speak(outputText.getText().toString(), secondLanguage);
                                        ttsOutputButton.setImageResource(R.drawable.stop_icon);
                                        ttsOutputButton.setTag(R.drawable.stop_icon);
                                    }
                                }

                                @Override
                                public void onFailure(int[] reasons, long value) {
                                    //we do nothing
                                }
                            });
                        }

                        @Override
                        public void onFailure(int[] reasons, long value) {
                            //we do nothing
                        }
                    });
                }
            }
        });
    }

    private void onKeyboardVisibilityChanged(boolean opened) {
        if(!isScreenReduced) {
            changeGuiCompression(opened, true);
        }
    }

    private void onScreenSizeChanged(boolean reduced){
        if(!reduced && isKeyboardShowing){
            changeGuiCompression(true, true);
        } else {
            changeGuiCompression(reduced, false);
        }
    }

    private void changeGuiCompression(boolean compress, boolean hideActionButtons){
        if(activity != null) {
            if (animationKeyboardButton != null) {
                animationKeyboardButton.cancel();
            }
            if(animationKeyboardTop != null){
                animationKeyboardTop.cancel();
            }
            if (compress) {

                animationKeyboardTop = animator.animateCompressActionBar(activity, toolbarContainer, title, settingsButton, settingsButtonReduced, backButton, new CustomAnimator.Listener() {
                    @Override
                    public void onAnimationEnd() {
                        super.onAnimationEnd();
                        animationKeyboardTop = null;
                    }
                });
            } else {

                animationKeyboardTop = animator.animateEnlargeActionBar(activity, toolbarContainer, title, settingsButton, settingsButtonReduced, backButton, new CustomAnimator.Listener() {
                    @Override
                    public void onAnimationEnd() {
                        super.onAnimationEnd();
                        animationKeyboardTop = null;
                    }
                });
            }
        }
    }

    private void initializeTTS() {
        tts = new TTS(activity, new TTS.InitListener() {  // tts initialization (to be improved, automatic package installation)
            @Override
            public void onInit() {
                if(tts != null) {
                    tts.setOnUtteranceProgressListener(ttsListener);
                }
            }

            @Override
            public void onError(int reason) {
                tts = null;
                //notifyError(new int[]{reason}, -1);
            }
        });
    }

    public void speak(String text, CustomLocale language) {
        if (tts != null && tts.isActive()) {
            if (tts.getVoice() != null && language.equals(new CustomLocale(tts.getVoice().getLocale()))) {
                tts.speak(text, TextToSpeech.QUEUE_ADD, null, "c01");
            } else {
                tts.setLanguage(language, activity);
                tts.speak(text, TextToSpeech.QUEUE_ADD, null, "c01");
            }
        }
    }

    private void activateTranslationButton(){
        if(colorAnimator != null){
            colorAnimator.cancel();
        }
        if(!translateButton.isActivated()) {
            colorAnimator = animator.createAnimatorColor(translateButton, GuiTools.getColorStateList(activity, deactivatedColor).getDefaultColor(), GuiTools.getColorStateList(activity, activatedColor).getDefaultColor(), activity.getResources().getInteger(R.integer.durationShort));
            colorAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(@NonNull Animator animation) {
                }

                @Override
                public void onAnimationEnd(@NonNull Animator animation) {
                    translateButton.setActivated(true);
                    colorAnimator = null;
                }

                @Override
                public void onAnimationCancel(@NonNull Animator animation) {
                }

                @Override
                public void onAnimationRepeat(@NonNull Animator animation) {
                }
            });
            colorAnimator.start();
        }else{
            translateButton.setBackgroundColor(GuiTools.getColorStateList(activity, activatedColor).getDefaultColor());
        }
    }

    private void deactivateTranslationButton(){
        if(colorAnimator != null){
            colorAnimator.cancel();
        }
        if(translateButton.isActivated()) {
            translateButton.setActivated(false);
            colorAnimator = animator.createAnimatorColor(translateButton, GuiTools.getColorStateList(activity, activatedColor).getDefaultColor(), GuiTools.getColorStateList(activity, deactivatedColor).getDefaultColor(), activity.getResources().getInteger(R.integer.durationShort));
            colorAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(@NonNull Animator animation) {
                }

                @Override
                public void onAnimationEnd(@NonNull Animator animation) {
                    colorAnimator = null;
                }

                @Override
                public void onAnimationCancel(@NonNull Animator animation) {
                }

                @Override
                public void onAnimationRepeat(@NonNull Animator animation) {
                }
            });
            colorAnimator.start();
        }else{
            translateButton.setBackgroundColor(GuiTools.getColorStateList(activity, deactivatedColor).getDefaultColor());
        }
    }


 


  //  @Override
    protected void connectToService() {
        activity.connectToStreamTranslationService(streamTranslationServiceCallback, new ServiceCommunicatorListener() {
            @Override
            public void onServiceCommunicator(ServiceCommunicator serviceCommunicator) {
                streamTranslationServiceCommunicator = (StreamTranslationService.StreamTranslationServiceCommunicator) serviceCommunicator;
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
                streamTranslationServiceCommunicator.getFirstLanguage(new StreamTranslationService.LanguageListener() {
                    @Override
                    public void onLanguage(CustomLocale language) {
                        setFirstLanguage(language);
                    }
                });
                streamTranslationServiceCommunicator.getSecondLanguage(new StreamTranslationService.LanguageListener() {
                    @Override
                    public void onLanguage(CustomLocale language) {
                        setSecondLanguage(language);
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                onFailureConnectingWithService(reasons, value);
            }
        });
    }



    protected void onFailureConnectingWithService(int[] reasons, long value) {
        for (int aReason : reasons) {
            switch (aReason) {
                case ErrorCodes.MISSED_ARGUMENT:
                case ErrorCodes.SAFETY_NET_EXCEPTION:
                case ErrorCodes.MISSED_CONNECTION:
                    //creation of the dialog.
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setMessage(R.string.error_internet_lack_accessing);
                    builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            activity.exitFromVoiceTranslation();
                        }
                    });
                    builder.setPositiveButton(R.string.retry, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            connectToService();
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.show();
                    break;
                case ErrorCodes.MISSING_GOOGLE_TTS:
                    activity.showMissingGoogleTTSDialog(null);
                    break;
                case ErrorCodes.GOOGLE_TTS_ERROR:
                    activity.showGoogleTTSErrorDialog(new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            connectToService();
                        }
                    });
                    break;
                default:
                    activity.onError(aReason, value);
                    break;
            }
        }
    }

   // @Override
    public void restoreAttributesFromService() {
        streamTranslationServiceCommunicator.getAttributes(new VoiceTranslationService.AttributesListener() {
            @Override
            public void onSuccess(ArrayList<GuiMessage> messages, boolean isMicMute, boolean isAudioMute, boolean isTTSError, final boolean isEditTextOpen, boolean isBluetoothHeadsetConnected, boolean isMicAutomatic, boolean isMicActivated, int listeningMic) {
                // initialization with service values
                //container.setVisibility(View.VISIBLE);
//                mAdapter = new MessagesAdapter(messages, new MessagesAdapter.Callback() {
//                    @Override
//                    public void onFirstItemAdded() {
//                  ///      description.setVisibility(View.GONE);
//                        //mRecyclerView.setVisibility(View.VISIBLE);
//                    }
//                });
                //mRecyclerView.setAdapter(mAdapter);
            
                    Log.d("stream", "===>onSuccess: messages size : "+ messages.size());
                
                    leftMicBtn.setMute(false, false);
           
                    if(isMicActivated) {
                        if (listeningMic == VoiceTranslationService.FIRST_LANGUAGE) {
                            leftMicBtn.onVoiceStarted(false);
                        } else {
                            leftMicBtn.onVoiceEnded(false);
                        }
      
                    }else{
                        leftMicBtn.onVoiceEnded(false);
            
                    }
       
                

                sound.setMute(isAudioMute);
                if(isTTSError){
                    sound.deactivate(DeactivableButton.DEACTIVATED_FOR_TTS_ERROR);
                }

                if(isMicActivated){
                   
                        activateInputs(true); //check
                 
                      //  activateInputs(false);
                  
                }else{
                    deactivateInputs(DeactivableButton.DEACTIVATED);
                }
            }
        });
    }


    @Override
    public void startMicrophone(boolean changeAspect) {
        if (changeAspect) {
        
        }
        streamTranslationServiceCommunicator.startMic();
    }

    @Override
    public void stopMicrophone(boolean changeAspect) {
        if (changeAspect) {
        
        }
        streamTranslationServiceCommunicator.stopMic(changeAspect);
    }

    protected void startSound() {
        sound.setMute(false);
        streamTranslationServiceCommunicator.startSound();
    }

    protected void stopSound() {
        sound.setMute(true);
        streamTranslationServiceCommunicator.stopSound();
    }

  //  @Override
    protected void deactivateInputs(int cause) {
    
        leftMicBtn.deactivate(cause);
 
        if (cause == DeactivableButton.DEACTIVATED) {
            sound.deactivate(DeactivableButton.DEACTIVATED);
        } else {
            sound.activate(false);  // to activate the button sound which otherwise remains deactivated and when clicked it shows the message "wait for initialisation"
        }
    }

  //  @Override
    protected void activateInputs(boolean start) {
        Log.d("mic", "activatedInputs");
      
        leftMicBtn.activate(false);

        sound.activate(false);
    }

    private void switchMicMode(boolean automatic){
        if(isMicAutomatic != automatic){
            //walkieTalkieServiceCallback.onVoiceEnded();
            isMicAutomatic = automatic;
            if(!isMicAutomatic){  //we switched from automatic to manual
              
                leftMicBtn.setMute(false);
               
                streamTranslationServiceCommunicator.startManualRecognition();
            }else{
                streamTranslationServiceCommunicator.stopManualRecognition();
              
                leftMicBtn.setMute(true);
              
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
                global.getFirstTextLanguage(false, listener);
                break;
            }
            case 2: {
                global.getSecondTextLanguage(false, listener);
                break;
            }
        }
    }

    private void showList(final int languageNumber, final CustomLocale selectedLanguage) {
        reloadButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);

        global.getTranslatorLanguages(true, new Global.GetLocalesListListener() {
            @Override
            public void onSuccess(final ArrayList<CustomLocale> languages) {
                progressBar.setVisibility(View.GONE);
                listViewGui.setVisibility(View.VISIBLE);

                listView = new LanguageListAdapter(activity, true, languages, selectedLanguage);
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
        invertLanguagesButton.setOnClickListener(null);
        inputText.removeTextChangedListener(inputTextListener);
        outputText.removeTextChangedListener(outputTextListener);
        inputText.clearFocus();
        outputText.clearFocus();
        //we detach the translate listener
        global.getTranslator().removeCallback(translateListener);
		
        activity.disconnectFromStreamTranslationService(streamTranslationServiceCommunicator);
    }

    private void setFirstLanguage(CustomLocale language) {
        // new language setting in the StreamTranslationService
        streamTranslationServiceCommunicator.changeFirstLanguage(language);
        // save firstLanguage selected
        global.setFirstTextLanguage(language);
        // change language displayed
        global.getTTSLanguages(true, new Global.GetLocalesListListener() {
            @Override
            public void onSuccess(ArrayList<CustomLocale> ttsLanguages) {
                ((AnimatedTextView) firstLanguageSelector.findViewById(R.id.firstLanguageName)).setText(language.getDisplayNameWithoutTTS(), true);
                //leftMicLanguage.setText(language.getDisplayNameWithoutTTS(), true); //
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                //never called in this case
            }
        });
    }

    private void setSecondLanguage(CustomLocale language) {
        // new language setting in the StreamTranslationService
        streamTranslationServiceCommunicator.changeSecondLanguage(language);
        // save secondLanguage selected
        global.setSecondLanguage(language);
        // change language displayed
        global.getTTSLanguages(true, new Global.GetLocalesListListener() {
            @Override
            public void onSuccess(ArrayList<CustomLocale> ttsLanguages) {
                ((AnimatedTextView) secondLanguageSelector.findViewById(R.id.secondLanguageName)).setText(language.getDisplayNameWithoutTTS(), true);
                ///rightMicLanguage.setText(language.getDisplayNameWithoutTTS(), true); //TODO 
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

    public int getTextActionButtonHeight() {
        return textActionButtonHeight;
    }

    public int getTextActionButtonBottomMargin() {
        return textActionButtonBottomMargin;
    }

    public int getActionButtonHeight() {
        return actionButtonHeight;
    }

    public int getTranslateButtonHeight(){
        return translateButtonHeight;
    }

    public int getActionButtonTopMargin() {
        return actionButtonTopMargin;
    }

    public int getActionButtonBottomMargin() {
        return actionButtonBottomMargin;
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
            startMicrophone(false);   //TODO //check need

    }



    public class StreamTranslationServiceCallback extends VoiceTranslationService.VoiceTranslationServiceCallback {
        @Override
        public void onVoiceStarted(int mode) {
            super.onVoiceStarted(mode);

            //voice
            if(mode == VoiceTranslationService.FIRST_LANGUAGE && !leftMicBtn.isMute()) {
                leftMicBtn.onVoiceStarted(true);
                Log.e("onVoiceStart", "onVoiceStart left");
            }

        }

        @Override
        public void onVoiceEnded() {
            super.onVoiceEnded();

            leftMicBtn.onVoiceEnded(true);

        }

        @Override
        public void onVolumeLevel(float volumeLevel) {
            super.onVolumeLevel(volumeLevel);
            
            if(leftMicBtn.isListening()){
                leftMicBtn.updateVolumeLevel(volumeLevel);
            }
        }

        @Override
        public void onMicActivated() {
            super.onMicActivated();
            Log.d("mic", "onMicActivated");

            if(!leftMicBtn.isActivated()) {
                leftMicBtn.activate(false);
            }
        }

        @Override
        public void onMicDeactivated() {
            super.onMicDeactivated();

            if (leftMicBtn.getState() == ButtonMic.STATE_NORMAL && leftMicBtn.isActivated()) {
                leftMicBtn.deactivate(DeactivableButton.DEACTIVATED);
            }
        }

        @Override
        public void onMessage(GuiMessage message) {
            super.onMessage(message);
            if (message != null) {
                //int messageIndex = mAdapter.getMessageIndex(message.getMessageID());


                Log.d("stream", "===>onMessage:: "+ message.getMessage().getText() + "/"+ message.getMessageID() + "/"+ message.isFinal());
  
                if(message.getMessageID() == -1) {// asr 

                    lastInputText += message.getMessage().getText();
                    if(lastInputText != null){
                        Log.d("stream", "input text ");
                        inputText.setText(lastInputText);
                    }
                } else {
                    String temp = "";
                    if(message.isFinal()) {
                        lastOutputText += message.getMessage().getText();
                    } else {
                        temp = message.getMessage().getText();
                    }

                    Log.d("stream", "output text ");

                    if(lastOutputText != null){
                        Log.d("stream", "output text 2");
                        outputText.setText(lastOutputText + temp);
                    }
                }

                //if(messageIndex != -1) {
                    //TODO
                    // if((!mRecyclerView.isAnimating() && !mRecyclerView.getLayoutManager().isSmoothScrolling()) || message.isFinal()) {
                    //     if(message.isFinal()){
                    //         if(mRecyclerView.getItemAnimator() != null) {
                    //             mRecyclerView.getItemAnimator().endAnimations();
                    //         }
                    //     }
                    //     mAdapter.setMessage(messageIndex, message);
                    // }
                //}else{
                    // if(mRecyclerView.getItemAnimator() != null) {
                    //     mRecyclerView.getItemAnimator().endAnimations();
                    // }
                    // mAdapter.addMessage(message);
                    // //we do an eventual automatic scroll (only if we are at the bottom of the recyclerview)
                    // if(((LinearLayoutManager) mRecyclerView.getLayoutManager()).findLastVisibleItemPosition() == mAdapter.getItemCount()-2){
                    //     mRecyclerView.smoothScrollToPosition(mAdapter.getItemCount()-1);
                    // }
                //}
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
