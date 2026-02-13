package com.example.audiomailer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var secondsElapsed = 0
    private var handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    private var recorder: MediaRecorder? = null
    private lateinit var fileName: String
    private lateinit var btnRecord: Button
    private lateinit var btnStop: Button
    private lateinit var btnSend: Button
    private lateinit var listStudents: ListView
    private val studentsEmails = arrayListOf("juan@colegio.com", "ana@colegio.com","hugobarre122006@gmail.com")

    companion object {
        private const val REQUEST_PERMISSION = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(androidx.appcompat.R.style.Theme_AppCompat_DayNight_DarkActionBar)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnRecord = findViewById(R.id.btnRecord)
        btnStop = findViewById(R.id.btnStop)
        btnSend = findViewById(R.id.btnSend)
        listStudents = findViewById(R.id.listStudents)

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, studentsEmails)
        listStudents.adapter = adapter
        listStudents.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        // Define file path - saving to standard Music directory for better compatibility
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        fileName = File(storageDir, "audio_memo.3gp").absolutePath

        if (!checkPermissions()) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), // Note: WRITE_EXTERNAL_STORAGE is usually not needed for getExternalFilesDir
                REQUEST_PERMISSION
            )
        }

        btnRecord.setOnClickListener { startRecording() }
        btnStop.setOnClickListener { stopRecording() }
        btnSend.setOnClickListener { sendEmail() }
    }

    //Timer
    private fun startTimer() {
        secondsElapsed = 0
        runnable = object : Runnable {
            override fun run() {
                secondsElapsed++
                val minutes = secondsElapsed / 60
                val seconds = secondsElapsed % 60
                // Formats the string to look like 00:00
                findViewById<TextView>(R.id.timerTextView).text =
                    String.format("%02d:%02d", minutes, seconds)

                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(runnable, 1000)
    }

    private fun stopTimer() {
        handler.removeCallbacks(runnable)
        findViewById<TextView>(R.id.timerTextView).text = "00:00"
    }

    private fun checkPermissions(): Boolean {
        val micPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        return micPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun startRecording() {
        recorder = MediaRecorder().apply {
            startTimer()
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(fileName)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            try {
                prepare()
                start()
                Toast.makeText(this@MainActivity, "Recording...", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun stopRecording() {
        recorder?.apply {
            stop()
            stopTimer()
            release()
        }
        recorder = null
        Toast.makeText(this, "Stopped. Ready to send.", Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        // This prevents the timer from running in the background
        // and causing memory leaks if the user leaves the app.
        stopTimer()
    }

    private fun sendEmail() {
        val selectedEmails = ArrayList<String>()
        for (i in 0 until listStudents.count) {
            if (listStudents.isItemChecked(i)) selectedEmails.add(studentsEmails[i])
        }

        if (selectedEmails.isEmpty()) {
            Toast.makeText(this, "Select a student first", Toast.LENGTH_SHORT).show()
            return
        }

        val audioFile = File(fileName)
        if (!audioFile.exists()) {
            Toast.makeText(this, "Record something first!", Toast.LENGTH_SHORT).show()
            return
        }

        // Generate URI - Ensure this authority matches your Manifest!
        val fileUri: Uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            audioFile
        )

        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/3gp"
            putExtra(Intent.EXTRA_EMAIL, selectedEmails.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, "Audio Memo")
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(emailIntent, "Send email via..."))
    }
}