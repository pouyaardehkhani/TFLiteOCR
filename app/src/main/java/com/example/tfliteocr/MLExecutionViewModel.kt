package com.example.tfliteocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "MLExecutionViewModel"

class MLExecutionViewModel : ViewModel() {

    private val _resultingBitmap = MutableLiveData<ModelExecutionResult>()

    val resultingBitmap: LiveData<ModelExecutionResult>
        get() = _resultingBitmap

    private val viewModelJob = Job()
    private val viewModelScope = CoroutineScope(viewModelJob)

    // the execution of the model has to be on the same thread where the interpreter
    // was created
    fun onApplyModel(
        context: Context,
        image: Bitmap,
        ocrModel: OCRModelExecutor?,
        inferenceThread: ExecutorCoroutineDispatcher
    ) {
        viewModelScope.launch(inferenceThread) {
            val contentImage = image
            try {
                val result = ocrModel?.execute(contentImage)
                _resultingBitmap.postValue(result)
            } catch (e: Exception) {
                Log.e(TAG, "Fail to execute OCRModelExecutor: ${e.message}")
                _resultingBitmap.postValue(null)
            }
        }
    }
}
