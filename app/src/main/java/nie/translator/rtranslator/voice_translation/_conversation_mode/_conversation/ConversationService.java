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

package nie.translator.rtranslator.voice_translation._conversation_mode._conversation;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;

import java.util.ArrayList;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.utils.BluetoothHeadsetUtils;
import nie.translator.rtranslator.utils.CustomLocale;
import nie.translator.rtranslator.utils.Tools;
import nie.translator.rtranslator.utils.gui.messages.GuiMessage;
import nie.translator.rtranslator.utils.gui.peers.GuiPeer;
import nie.translator.rtranslator.voice_translation.VoiceTranslationService;
import nie.translator.rtranslator.voice_translation._conversation_mode.communication.ConversationBluetoothCommunicator;

import nie.translator.rtranslator.bluetooth.Message;
import nie.translator.rtranslator.bluetooth.Peer;
import nie.translator.rtranslator.neural_networks.NeuralNetworkApiText;
import nie.translator.rtranslator.neural_networks.translation.Translator;
import nie.translator.rtranslator.neural_networks.voice.VadRecognizer;
import nie.translator.rtranslator.neural_networks.voice.RecognizerListener;
import nie.translator.rtranslator.neural_networks.voice.Recorder;


public class ConversationService extends VoiceTranslationService {
    //properties
    public static final int SPEECH_BEAM_SIZE = 4;
    public static final int TRANSLATOR_BEAM_SIZE = 1;

    //commands
    public static final int CHANGE_LANGUAGE = 15;

    //others
    private String textRecognized = "";
    private Translator translator;
    private String myPeerName;
    private VadRecognizer speechRecognizer;
    private RecognizerListener speechRecognizerCallback;
    //private BluetoothHelper mBluetoothHelper;
    private Global global;
    private ConversationBluetoothCommunicator.Callback communicationCallback;
    private static Handler mHandler = new Handler();
    private Handler mainHandler;


    @Override
    public void onCreate() {
        super.onCreate();
        global = (Global) getApplication();
        mainHandler = new Handler(Looper.getMainLooper());
        //startBluetoothSco
        //mBluetoothHelper = new BluetoothHelper(this);
        mVoiceCallback = new Recorder.Callback() {
            @Override
            public void onVoiceStart() {
                if (speechRecognizer != null) {
                    super.onVoiceStart();
                    Log.e("recorder","onVoiceStart");
                    //we notify the client
                    ConversationService.super.notifyVoiceStart();
                }
            }

            @Override
            public void onVoice(@NonNull float[] data, int size) {
                if (speechRecognizer != null) {
                    super.onVoice(data,size);
                    global.getLanguage(true, new Global.GetLocaleListener() {
                        @Override
                        public void onSuccess(CustomLocale result) {
                            int sampleRate = getVoiceRecorderSampleRate();
                            if (sampleRate != 0) {
                                speechRecognizer.recognize(data, SPEECH_BEAM_SIZE, result.getCode());
                            }
                        }

                        @Override
                        public void onFailure(int[] reasons, long value) {
                            ConversationService.super.notifyError(reasons, value);
                        }
                    });
                }
            }
            
            @Override
            public void onVoiceStream(@NonNull float[] data, int size) {
                super.onVoiceStream(data,size);
            }

            @Override
            public void onVoiceEnd() {
                if (speechRecognizer != null) {
                    super.onVoiceEnd();
                    Log.e("recorder","onVoiceEnd");
                    // if the textRecognizer is not empty then it means that we have a result that has not been correctly recognized as final
                    if (!textRecognized.equals("")) {
                        textRecognized = "";
                    }
                    // the client is notified
                    ConversationService.super.notifyVoiceEnd();
                }
            }

            @Override
            public void onVolumeLevel(float volumeLevel) {
                super.onVolumeLevel(volumeLevel);
                // we notify the client
                ConversationService.super.notifyVolumeLevel(volumeLevel);
            }
        };
        clientHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(final android.os.Message message) {
                int command = message.getData().getInt("command", -1);
                final String text = message.getData().getString("text");
                if (command != -1) {
                    if (!ConversationService.super.executeCommand(command, message.getData())) {
                        switch (command) {
                            case RECEIVE_TEXT:
                                global.getLanguage(true,new Global.GetLocaleListener() {
                                    @Override
                                    public void onSuccess(CustomLocale language) {
                                        if (text != null) {
                                            GuiMessage guiMessage = new GuiMessage(new Message(global, text), global.getTranslator().incrementCurrentResultID(), true, true);
                                            // send the message
                                            sendMessage(new ConversationMessage(new NeuralNetworkApiText(text, language)));

                                            notifyMessage(guiMessage);
                                            // we save every new message in the exchanged messages so that the fragment can restore them
                                            addOrUpdateMessage(guiMessage);
                                        }
                                    }

                                    @Override
                                    public void onFailure(int[] reasons, long value) {
                                        ConversationService.super.notifyError(reasons, value);
                                    }
                                });
                                break;
                        }
                    }
                }
                return false;
            }
        });
        communicationCallback = new ConversationBluetoothCommunicator.Callback() {
            @Override
            public void onMessageReceived(final Message message) {
                super.onMessageReceived(message);
                global.getLanguage(false,new Global.GetLocaleListener() {
                    @Override
                    public void onSuccess(CustomLocale result) {
                        String completeText = message.getText();
                        int languageCodeSize = Integer.valueOf(completeText.substring(completeText.length() - 1));
                        String text = completeText.substring(0, completeText.length() - (languageCodeSize + 1));
                        String languageCode = completeText.substring(completeText.length() - (languageCodeSize + 1), completeText.length() - 1);

                        ConversationMessage conversationMessage = new ConversationMessage(message.getSender(), new NeuralNetworkApiText(text, CustomLocale.getInstance(languageCode)));
                        translator.translateMessage(conversationMessage, result, TRANSLATOR_BEAM_SIZE, new Translator.TranslateMessageListener() {
                            @Override
                            public void onTranslatedMessage(ConversationMessage conversationMessage, long messageID, boolean isFinal) {
                                global.getTTSLanguages(true, new Global.GetLocalesListListener() {
                                    @Override
                                    public void onSuccess(ArrayList<CustomLocale> ttsLanguages) {
                                        if(isFinal && CustomLocale.containsLanguage(ttsLanguages, conversationMessage.getPayload().getLanguage())) { // check if the language can be speak
                                            speak(conversationMessage.getPayload().getText(), conversationMessage.getPayload().getLanguage());
                                        }
                                        message.setText(conversationMessage.getPayload().getText());   // updating the text with the new translated text (and without the language code)
                                        GuiMessage guiMessage = new GuiMessage(message, messageID, false, isFinal);
                                        notifyMessage(guiMessage);
                                        // we save every new message in the exchanged messages so that the fragment can restore them
                                        addOrUpdateMessage(guiMessage);
                                    }

                                    @Override
                                    public void onFailure(int[] reasons, long value) {
                                        //never called in this case
                                    }
                                });
                            }

                            @Override
                            public void onFailure(int[] reasons, long value) {
                                ConversationService.super.notifyError(reasons, value);
                            }
                        });
                    }

                    @Override
                    public void onFailure(int[] reasons, long value) {
                        ConversationService.super.notifyError(reasons, value);
                    }
                });
            }

            @Override
            public void onDisconnected(GuiPeer peer, int peersLeft) {
                super.onDisconnected(peer, peersLeft);
                if (peersLeft == 0) {
                    stopSelf();
                }
            }
        };
        if(global.getBluetoothCommunicator() != null) {
            global.getBluetoothCommunicator().addCallback(communicationCallback);
        }

        // speech recognition and translation initialization
        translator = global.getTranslator();
        speechRecognizer = global.getSpeechRecognizer();
        speechRecognizerCallback = new VoiceTranslationServiceRecognizerListener() {
            @Override
            public void onSpeechRecognizedResult(String text, String languageCode, double confidenceScore, boolean isFinal) {
                if (text != null && languageCode != null && !text.equals("") && !isMetaText(text)) {
                    CustomLocale language = CustomLocale.getInstance(languageCode);
                    GuiMessage guiMessage = new GuiMessage(new Message(global, text), global.getTranslator().incrementCurrentResultID(), true, isFinal);
                    if (isFinal) {
                        textRecognized = "";  // to ensure that we continue to listen since in this case the result is automatically extracted
                        // send the message
                        sendMessage(new ConversationMessage(new NeuralNetworkApiText(text, language)));

                        notifyMessage(guiMessage);
                        // we save every new message in the exchanged messages so that the fragment can restore them
                        addOrUpdateMessage(guiMessage);
                    } else {
                        notifyMessage(guiMessage);
                        textRecognized = text;  // if it equals something then when calling voiceEnd we stop recognition
                    }
                }
            }

            @Override
            public void onError(int[] reasons, long value) {
                ConversationService.super.notifyError(reasons, value);
            }
        };

        speechRecognizer.addCallback(speechRecognizerCallback);
        //mBluetoothHelper.start();

        //voice recorder initialization
        initializeVoiceRecorder();
    }

    private void sendMessage(ConversationMessage conversationMessage) {
        if(global.getBluetoothCommunicator() != null) {
            String languageCode = conversationMessage.getPayload().getLanguage().getCode();
            global.getBluetoothCommunicator().sendMessage(new Message(global, conversationMessage.getPayload().getText() + languageCode + languageCode.length()));
        }
    }

    public void initializeVoiceRecorder() {
        if (Tools.hasPermissions(this, REQUIRED_PERMISSIONS)) {
            //voice recorder initialization
            super.mVoiceRecorder = new Recorder((Global) getApplication(), true, mVoiceCallback, new BluetoothHeadsetCallback());
        }
    }

    public String getMyPeerName() {
        return myPeerName;
    }

    public void setMyPeerName(String myPeerName) {
        this.myPeerName = myPeerName;
    }

    @Override
    protected boolean shouldDeactivateMicDuringTTS() {
        return !isBluetoothHeadsetConnected();
    }

    @Override
    protected boolean isBluetoothHeadsetConnected() {
        if(mVoiceRecorder != null) {
            return mVoiceRecorder.isOnHeadsetSco();
        }else {
            return false;
        }
    }

    @Override
    public void onDestroy() {
        // Stop SpeechRecognizer
        //speechRecognizer.destroy();
        speechRecognizer.removeCallback(speechRecognizerCallback);
        speechRecognizer = null;
        //stop Bluetooth helper
        //mBluetoothHelper.stop();
        if(global.getBluetoothCommunicator() != null) {
            global.getBluetoothCommunicator().removeCallback(communicationCallback);
        }
        super.onDestroy();
    }

    private class BluetoothHelper extends BluetoothHeadsetUtils {
        private BluetoothHelper(Context context) {
            super(context);
        }

        @Override
        public void onHeadsetConnected() {
        }

        @Override
        public void onScoAudioConnected() {
            Bundle bundle = new Bundle();
            bundle.putInt("callback", ON_CONNECTED_BLUETOOTH_HEADSET);
            notifyToClient(bundle);
        }

        @Override
        public void onScoAudioDisconnected() {
            Bundle bundle = new Bundle();
            bundle.putInt("callback", ON_DISCONNECTED_BLUETOOTH_HEADSET);
            notifyToClient(bundle);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stop();
                    start();
                }
            }, 1000);
        }

        @Override
        public void onHeadsetDisconnected() {
        }
    }

    public class BluetoothHeadsetCallback {

        public void onHeadsetConnected() {
        }

        public void onScoAudioConnected() {
            Bundle bundle = new Bundle();
            bundle.putInt("callback", ON_CONNECTED_BLUETOOTH_HEADSET);
            notifyToClient(bundle);
        }

        public void onScoAudioDisconnected() {
            Bundle bundle = new Bundle();
            bundle.putInt("callback", ON_DISCONNECTED_BLUETOOTH_HEADSET);
            notifyToClient(bundle);
        }

        public void onHeadsetDisconnected() {
        }
    }

    public static class ConversationServiceCommunicator extends VoiceTranslationServiceCommunicator {
        public ConversationServiceCommunicator(int id) {
            super(id);
            super.serviceHandler = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(android.os.Message msg) {
                    msg.getData().setClassLoader(Peer.class.getClassLoader());
                    int callbackMessage = msg.getData().getInt("callback", -1);
                    Bundle data = msg.getData();
                    executeCallback(callbackMessage, data);
                    return true;
                }
            });
        }
    }
}