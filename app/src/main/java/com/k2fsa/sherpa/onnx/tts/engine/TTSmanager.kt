//@file:OptIn(ExperimentalMaterial3Api::class)

package com.k2fsa.sherpa.onnx.tts.engine

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
// import androidx.activity.ComponentActivity
// import androidx.activity.compose.setContent
// import androidx.activity.viewModels
 
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import android.content.Context

const val TAG = "sherpa-onnx-tts-engine"

class TTSmanager(val context: Context) {
 

    private lateinit var track: AudioTrack

    private var stopped: Boolean = false

    private var samplesChannel = Channel<FloatArray>()
    private lateinit var preferenceHelper: PreferenceHelper
    private lateinit var langDB: LangDB
    private var volume: Float = 1.0f

    fun onPause() {
        //super.onPause()
        samplesChannel.close()
    }

    fun reloadLanguage() {
        //Reset speed in case it has been changed by TtsService
        val db = LangDB.getInstance(this.context)
        val languages = db.allInstalledLanguages
        val language = languages.first { it.lang == TtsEngine.lang }
        TtsEngine.speed = language.speed
        //super.onResume()
    }

    fun init() {
        //super.onCreate(savedInstanceState)
        preferenceHelper = PreferenceHelper(this.context)
        langDB = LangDB.getInstance(this.context)
        volume = preferenceHelper.getVolume()
        Migrate.renameModelFolder(this.context)   //Rename model folder if "old" structure
        if (!preferenceHelper.getCurrentLanguage().equals("")) {
            TtsEngine.createTts(this.context, preferenceHelper.getCurrentLanguage()!!)
            initAudioTrack()
            //setupDisplay(langDB, preferenceHelper)    //show ui,but here no need
 
        } else {
            val intent = Intent(this.context, ManageLanguagesActivity::class.java)
            context.startActivity(intent)
            //finish()
        }
    }


    fun getSpeed() : Float {
        return TtsEngine.speed
    }

    fun setSpeed(it: Float) {
        TtsEngine.speed = it

        langDB.updateLang(
            TtsEngine.lang,
            TtsEngine.speakerId,
            TtsEngine.speed
        )
                                
    }



    private var sampleText = getSampleText(TtsEngine.lang ?: "")
    private val numLanguages = langDB.allInstalledLanguages.size
    private val languages = langDB.allInstalledLanguages
    private var selectedLang = languages.indexOfFirst { it.lang == preferenceHelper.getCurrentLanguage()!! }
    private val numSpeakers = TtsEngine.tts!!.numSpeakers()
    private var selectedSpeaker = 0
    //private var displayVol by remember { mutableStateOf(preferenceHelper.getVolume()) }
    fun getLanguage() : Int {
        return selectedLang
    }
    fun setLanguage(langId: Int) {
        selectedLang = langId
        preferenceHelper.setCurrentLanguage(
            languages[langId].lang
        )

    }

    fun getSpeakerId() : Int {
        return selectedSpeaker
    }
    fun setSpeakerId(speakerId: Int) {
        selectedSpeaker = speakerId
        TtsEngine.speakerId = speakerId
        langDB.updateLang(
            TtsEngine.lang,
            TtsEngine.speakerId,
            TtsEngine.speed
        )

    }

    fun deleteLanguage() {
        deleteLang(preferenceHelper.getCurrentLanguage())
    }


    fun getVolume(): Float {
        return preferenceHelper.getVolume()
    }
    fun setVolume(volume: Float) {
        preferenceHelper.setVolume(volume)
    }


    fun speak(sampleText:String) {

        if (sampleText.isBlank() || sampleText.isEmpty()) {
            //
        } else {
            stopped = false

            track.pause()
            track.flush()
            track.play()

            samplesChannel = Channel<FloatArray>()

            CoroutineScope(Dispatchers.IO).launch {
                for (samples in samplesChannel) {
                    for (i in samples.indices) {
                        samples[i] *= volume
                    }
                    track.write(
                        samples,
                        0,
                        samples.size,
                        AudioTrack.WRITE_BLOCKING
                    )
                }
            }

            CoroutineScope(Dispatchers.Default).launch {
                TtsEngine.tts!!.generateWithCallback(
                    text = sampleText,
                    sid = TtsEngine.speakerId,
                    speed = TtsEngine.speed,
                    callback = ::callback,
                )
            }.start()
        }

    }


    fun stop() {
        stopped = true
        track.pause()
        track.flush()


    }



    private fun deleteLang(currentLanguage: String?) {
        TtsEngine.tts = null //reset TtsEngine to make sure a new voice is loaded at next start
        val country: String
        val languages = langDB.allInstalledLanguages
        val language = languages.first { it.lang == currentLanguage }
        country = language.country

        val subdirectoryName = currentLanguage + country
        val subdirectory = File(context.getExternalFilesDir(null), subdirectoryName)

        if (subdirectory.exists() && subdirectory.isDirectory) {
            val files = subdirectory.listFiles()

            files?.forEach { file ->
                if (file.isFile) {
                    file.delete()
                }
            }

            subdirectory.delete()
            langDB.removeLang(currentLanguage)
            if (langDB.allInstalledLanguages.isEmpty()) preferenceHelper.setCurrentLanguage("")
            else preferenceHelper.setCurrentLanguage(langDB.allInstalledLanguages[0].lang)
        }
        //restart()
    }

    fun onDestroy() {
        if (this::track.isInitialized) track.release()
        //super.onDestroy()
    }

    // this function is called from C++
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun callback(samples: FloatArray): Int {
        if (!stopped) {
            val samplesCopy = samples.copyOf()
            CoroutineScope(Dispatchers.IO).launch {
                if (!samplesChannel.isClosedForSend) samplesChannel.send(samplesCopy)
            }
            return 1
        } else {
            track.stop()
            Log.i(TAG, " return 0")
            return 0
        }
    }

    private fun initAudioTrack() {
        val sampleRate = TtsEngine.tts!!.sampleRate()
        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        Log.i(TAG, "sampleRate: $sampleRate, buffLength: $bufLength")

        val attr = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()

        track = AudioTrack(
            attr, format, bufLength, AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.play()
    }
}
