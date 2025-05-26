package com.k2fsa.sherpa.onnx

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.k2fsa.sherpa.onnx.getOfflineModelConfig
import com.k2fsa.sherpa.onnx.getVadModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

import android.content.Context


private const val TAG = "sherpa-onnx"
private const val REQUEST_RECORD_AUDIO_PERMISSION = 200

class VadOfflineRecog(context: Context) {

    private val application = context.applicationContext

    private lateinit var vad: Vad

    //private var audioRecord: AudioRecord? = null
    //private var recordingThread: Thread? = null
    //private val audioSource = MediaRecorder.AudioSource.MIC
    private val sampleRateInHz = 16000
    //private val channelConfig = AudioFormat.CHANNEL_IN_MONO

    // Note: We don't use AudioFormat.ENCODING_PCM_FLOAT
    // since the AudioRecord.read(float[]) needs API level >= 23
    // but we are targeting API level >= 21
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    //private val permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    // Non-streaming ASR
    private lateinit var offlineRecognizer: OfflineRecognizer

    private var idx: Int = 0
    private var lastText: String = ""

    @Volatile
    private var isRecording: Boolean = false

    init {
        Log.i(TAG, "Start to initialize model")
        initVadModel()
        Log.i(TAG, "Finished initializing model")

        Log.i(TAG, "Start to initialize non-streaimng recognizer")
        initOfflineRecognizer()
        Log.i(TAG, "Finished initializing non-streaming recognizer")

    }
    

    private  fun initVadModel() {
        val type = 0
        Log.i(TAG, "Select VAD model type ${type}")
        val config = getVadModelConfig(type)

        vad = Vad(
            assetManager = application.assets,
            config = config!!,
        )
    }




    private fun initOfflineRecognizer() {
        // Please change getOfflineModelConfig() to add new models
        // See https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html
        // for a list of available models
        val asrModelType = 15
        val asrRuleFsts: String?
        asrRuleFsts = null
        Log.i(TAG, "Select model type ${asrModelType} for ASR")

        val config = OfflineRecognizerConfig(
            featConfig = getFeatureConfig(sampleRate = sampleRateInHz, featureDim = 80),
            modelConfig = getOfflineModelConfig(type = asrModelType)!!,
        )
        if (asrRuleFsts != null) {
            config.ruleFsts = asrRuleFsts;
        }

        offlineRecognizer = OfflineRecognizer(
            assetManager = application.assets,
            config = config,
        )
    }

    private fun runSecondPass(samples: FloatArray): String {
        val stream = offlineRecognizer.createStream()
        stream.acceptWaveform(samples, sampleRateInHz)
        offlineRecognizer.decode(stream)
        val result = offlineRecognizer.getResult(stream)
        stream.release()
        return result.text
    }


    public fun processSamples(samples: FloatArray, callback:Callback) {
        //val bufferSize = 512 // in samples
        //var readIndex = 0
        Log.i(TAG, "processSamples : data[0]" + samples[0])
        //while(readIndex < samples.size) {

        //    val len = if((samples.size - readIndex) > bufferSize)  bufferSize else samples.size - readIndex
        //    Log.i(TAG, "sliceArray len:" + len + " readIndex:" + readIndex)
        //    var sliceBuffer = samples.sliceArray(readIndex until readIndex + len)
        //    readIndex += len

            //vad.acceptWaveform(sliceBuffer)
            Log.i(TAG, "processSamples " + samples.size)
            //while(!vad.empty()) {
                //var segment = vad.front()
                //    Log.i(TAG, "runSecondPass" + samples.size)
                    val text = runSecondPass(samples)
                    Log.i(TAG, "runSecondPass text: " + text)
                    if (text.isNotBlank()) {
                        callback.notify(text)
                    }

            //    vad.pop();
            //}
        //}

    }

    public interface Callback {
        fun notify(text:String)
    }

}
