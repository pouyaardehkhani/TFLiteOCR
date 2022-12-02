package com.example.tfliteocr

import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.tfliteocr.OCRModelExecutor.Companion.TAG
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var photo: Button
    private lateinit var detect: Button
    private lateinit var srcText: TextView
    private lateinit var gpu: Switch

    private var useGPU = false
    private var selectedImageName = "tensorflow.jpg"
    private var ocrModel: OCRModelExecutor? = null
    private val inferenceThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val mainScope = MainScope()
    private val mutex = Mutex()
    private lateinit var viewModel: MLExecutionViewModel

    // variable for our image bitmap.
    private lateinit var imageBitmap: Bitmap

    companion object {
        const val REQUEST_IMAGE_CAPTURE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView1)
        photo = findViewById(R.id.button1)
        detect = findViewById(R.id.button2)
        srcText = findViewById(R.id.srcText)
        gpu = findViewById(R.id.gpu)

        viewModel = ViewModelProvider.AndroidViewModelFactory(application).create(MLExecutionViewModel::class.java)
        viewModel.resultingBitmap.observe(
            this,
            Observer { resultImage ->
                if (resultImage != null) {
                    updateUIWithResults(resultImage)
                }
                enableControls(true)
            }
        )

        mainScope.async(inferenceThread) { createModelExecutor(useGPU) }

        gpu.setOnCheckedChangeListener { _, isChecked ->
            useGPU = isChecked
            mainScope.async(inferenceThread) { createModelExecutor(useGPU) }
        }

        photo.setOnClickListener {
            dispatchTakePictureIntent()
        }
        detect.setOnClickListener {
            enableControls(false)

            mainScope.async(inferenceThread) {
                mutex.withLock {
                    if (ocrModel != null) {
                        viewModel.onApplyModel(baseContext, imageBitmap, ocrModel, inferenceThread)
                    } else {
                        Log.d(
                            TAG,
                            "Skipping running OCR since the ocrModel has not been properly initialized ..."
                        )
                    }
                }
            }
        }

        enableControls(true)
    }

    private suspend fun createModelExecutor(useGPU: Boolean) {
        mutex.withLock {
            if (ocrModel != null) {
                ocrModel!!.close()
                ocrModel = null
            }
            try {
                ocrModel = OCRModelExecutor(this, useGPU)
            } catch (e: Exception) {
                Log.e(TAG, "Fail to create OCRModelExecutor: ${e.message}")
                srcText.text = e.message
            }
        }
    }

    private fun setImageView(imageView: ImageView, image: Bitmap) {
        Glide.with(baseContext).load(image).override(250, 250).fitCenter().into(imageView)
    }

    private fun updateUIWithResults(modelExecutionResult: ModelExecutionResult) {
        setImageView(imageView, modelExecutionResult.bitmapResult)
        srcText.text = modelExecutionResult.itemsFound.keys.toString()
        enableControls(true)
    }

    private fun enableControls(enable: Boolean) {
        detect.isEnabled = enable
    }

    private fun dispatchTakePictureIntent() {
        // in the method we are displaying an intent to capture our image.
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val chooser = Intent(Intent.ACTION_CHOOSER)

        chooser.putExtra(Intent.EXTRA_INTENT, galleryIntent)
        chooser.putExtra(Intent.EXTRA_TITLE, getString(R.string.chooseaction))
        val intentArray = arrayOf(cameraIntent)
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)

        // on below line we are calling a start activity
        // for result method to get the image captured.
        if (chooser.resolveActivity(packageManager) != null) {
            ActivityCompat.startActivityForResult(MainActivity@this, chooser, REQUEST_IMAGE_CAPTURE, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // calling on activity result method.
        if (resultCode == RESULT_OK) {
            if (data != null) {
                if (data.data != null) {
                    // this case will occur in case of picking image from the Gallery,
                    // but not when taking picture with a camera
                    try {
                        imageBitmap = MediaStore.Images.Media.getBitmap(
                            MainActivity@ this.contentResolver,
                            data.data
                        )

                        // do whatever you want with the Bitmap ....
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                } else {
                    // on below line we are getting
                    // data from our bundles. .
                    val extras = data!!.extras
                    imageBitmap = extras!!["data"] as Bitmap
                }
                // below line is to set the
                // image bitmap to our image.
                imageView!!.setImageBitmap(imageBitmap)
            }
        }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (resultCode == RESULT_OK) {
//            if (data.data != null) {
//                // this case will occur in case of picking image from the Gallery,
//                // but not when taking picture with a camera
//                try {
//                    val bitmap = MediaStore.Images.Media.getBitmap(
//                        MainActivity@this.contentResolver,
//                        data.data
//                    )
//
//                    // do whatever you want with the Bitmap ....
//                } catch (e: IOException) {
//                    e.printStackTrace()
//                }
//            } else {
//                // this case will occur when taking a picture with a camera
//                var bitmap: Bitmap? = null
//                val cursor: Cursor = MainActivity@this.contentResolver.query(
//                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(
//                        MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_ADDED,
//                        MediaStore.Images.ImageColumns.ORIENTATION
//                    ), MediaStore.Images.Media.DATE_ADDED,
//                    null, "date_added DESC"
//                )!!
//                if (cursor != null && cursor.moveToFirst()) {
//                    val uri: Uri =
//                        Uri.parse(cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA)))
//                    val photoPath: String = uri.toString()
//                    cursor.close()
//                    if (photoPath != null) {
//                        bitmap = BitmapFactory.decodeFile(photoPath)
//                    }
//                }
//                if (bitmap == null) {
//                    // for safety reasons you can
//                    // use thumbnail if not retrieved full sized image
//                    bitmap = data.extras!!["data"] as Bitmap?
//                }
//                // do whatever you want with the Bitmap ....
//            }
//            super.onActivityResult(requestCode, resultCode, data)
//        }
//    }
    }
}