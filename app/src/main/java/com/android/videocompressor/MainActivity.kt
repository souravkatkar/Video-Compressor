package com.android.videocompressor

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.provider.MediaStore
import android.text.format.DateUtils
import android.view.View
import android.widget.Toast
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import android.view.Menu;
import android.widget.MediaController;
import android.widget.VideoView
import java.util.logging.Level
import java.util.logging.Level.parse

class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_SELECT_VIDEO=0
    }

    private lateinit var path: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        videoView1.visibility= View.GONE
        progressBar.visibility= View.GONE
        progress.visibility = View.VISIBLE

        button.setOnClickListener {
            videoView1.visibility= View.GONE
            progressBar.visibility = View.VISIBLE
            progress.visibility = View.VISIBLE
            pickvideo()
        }
    }

    private fun pickvideo() {
        val intent = Intent()
        intent.apply {
            type = "video/*"
            action = Intent.ACTION_PICK
        }
        startActivityForResult(Intent.createChooser(intent, "Select video"), REQUEST_SELECT_VIDEO)
    }

    @SuppressLint("SetTextI18n")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(resultCode == Activity.RESULT_OK){
            if(requestCode == REQUEST_SELECT_VIDEO ){
                if(data!=null && data.data !=null){
                    val uri =data.data
                    Toast.makeText(applicationContext,"Successfully selected Video " + data.toString(),Toast.LENGTH_LONG).show()
                    uri?.let {

                        //GlideApp.with(applicationContext).load(uri).into(videoImage)
                        GlobalScope.launch {

                            val job = async { getMediaPath(applicationContext, uri) }
                            path = job.await()

                           val desFile = saveVideoFile(path)


                            desFile?.let {
                                var time = 0L
                                VideoCompressor.start(
                                    path,
                                    desFile.path,
                                    object : CompressionListener {
                                        override fun onProgress(percent: Float) {
                                            // Update UI with progress value
                                            runOnUiThread {
                                                // update a text view
                                                progress.text = "${percent.toLong()}%"
                                                // update a progress bar
                                                progressBar.progress = percent.toInt()
                                            }
                                        }

                                        override fun onStart() {
                                            // Compression start
                                            time = System.currentTimeMillis()

                                        }

                                        override fun onSuccess() {
                                            // On Compression success

                                            progressBar.visibility = View.GONE
                                            time = System.currentTimeMillis() - time
                                            progress.text=(time/1000).toString() + "seconds"
                                            val uri : Uri = Uri.fromFile(desFile)
                                            preparevideo(uri)






                                        }
                                        override fun onFailure(failureMessage: String) {
                                            // On Failure
                                            Toast.makeText(applicationContext,"Not compressed because of low bitrate",Toast.LENGTH_LONG).show()
                                            val uri : Uri = Uri.fromFile(desFile)
                                            preparevideo(uri)

                                        }

                                        override fun onCancelled() {
                                            // On Cancelled
                                        }

                                    }, VideoQuality.MEDIUM, isMinBitRateEnabled = true, keepOriginalResolution = false)
                            }
                        }

                    }
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun preparevideo(uri: Uri) {
        progressBar.visibility = View.GONE
        progress.visibility = View.GONE
        videoView1.visibility = View.VISIBLE
        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView1)
        videoView1.setMediaController(mediaController)
        videoView1.setVideoURI(uri)

    }


    @Suppress("DEPRECATION")
    private fun saveVideoFile(filePath: String?): File? {
        filePath?.let {
            val videoFile = File(filePath)
            val videoFileName = "${System.currentTimeMillis()}_${videoFile.name}"
            val folderName = Environment.DIRECTORY_MOVIES
            if (Build.VERSION.SDK_INT >= 29) {

                val values = ContentValues().apply {

                    put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        videoFileName
                    )
                    put(MediaStore.Images.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Images.Media.RELATIVE_PATH, folderName)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val collection =
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                val fileUri = applicationContext.contentResolver.insert(collection, values)

                fileUri?.let {
                    application.contentResolver.openFileDescriptor(fileUri, "w").use { descriptor ->
                        descriptor?.let {
                            FileOutputStream(descriptor.fileDescriptor).use { out ->
                                FileInputStream(videoFile).use { inputStream ->
                                    val buf = ByteArray(4096)
                                    while (true) {
                                        val sz = inputStream.read(buf)
                                        if (sz <= 0) break
                                        out.write(buf, 0, sz)
                                    }
                                }
                            }
                        }
                    }

                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    applicationContext.contentResolver.update(fileUri, values, null, null)

                    return File(getMediaPath(applicationContext, fileUri))
                }
            } else {

                val downloadsPath =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)

                val desFile = File(downloadsPath, videoFileName)

                if (desFile.exists())
                    desFile.delete()

                try {
                    desFile.createNewFile()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                return desFile

            }
        }
        return null
    }


}


