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

    private var audioUri: Uri? = null // Variable global

    private var secondsElapsed = 0
    private var handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    private var recorder: MediaRecorder? = null
    private lateinit var fileName: String
    private lateinit var btnRecord: Button
    private lateinit var btnStop: Button
    private lateinit var btnSend: Button
    private lateinit var listStudents: ListView

    private lateinit var adapter: ArrayAdapter<String>
    private val allEmails = mutableListOf<String>()

    companion object {
        private const val REQUEST_PERMISSION = 200
    }

    // Lista txt
    private val FILE_NAME = "emails_list.txt"

    // Guarda un correo nuevo en una línea nueva del archivo
    private fun saveEmailToFile(email: String) {
        try {
            val fileOutputStream = openFileOutput(FILE_NAME, MODE_APPEND)
            fileOutputStream.write((email + "\n").toByteArray())
            fileOutputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Lee el archivo y devuelve una lista de correos
    private fun readEmailsFromFile(): List<String> {
        val emails = mutableListOf<String>()
        try {
            val fileInputStream = openFileInput(FILE_NAME)
            fileInputStream.bufferedReader().useLines { lines ->
                lines.forEach { emails.add(it) }
            }
        } catch (e: Exception) {
            // Si el archivo no existe aún, devolvemos lista vacía
        }
        return emails
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(androidx.appcompat.R.style.Theme_AppCompat_DayNight_DarkActionBar)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnRecord = findViewById(R.id.btnRecord)
        btnStop = findViewById(R.id.btnStop)
        btnSend = findViewById(R.id.btnSend)
        listStudents = findViewById(R.id.listStudents)

        val editEmail = findViewById<android.widget.EditText>(R.id.editEmail)
        val btnAddEmail = findViewById<Button>(R.id.btnAddEmail)

        allEmails.addAll(readEmailsFromFile())

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, allEmails)
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

        btnAddEmail.setOnClickListener {
            val email = editEmail.text.toString().trim()
            if (email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                saveEmailToFile(email) // Guarda en el TXT
                allEmails.add(email)   // Añade a la lista en memoria
                adapter.notifyDataSetChanged() // Refresca la lista visual
                editEmail.text.clear()
                Toast.makeText(this, "Correo añadido", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Email no válido", Toast.LENGTH_SHORT).show()
            }
        }

        btnRecord.setOnClickListener { startRecording() }
        btnStop.setOnClickListener { stopRecording() }
        btnSend.setOnClickListener {
            audioUri?.let { uri ->
                sendEmail(uri)
            } ?: run {
                Toast.makeText(this, "Primero graba un audio", Toast.LENGTH_SHORT).show()
            }
        }
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
            try {
                stop()
                release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        recorder = null
        stopTimer()

        // IMPORTANTE: Usamos 'fileName' que es donde grabamos el audio real
        val audioFile = File(fileName)
        if (audioFile.exists()) {
            audioUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                audioFile
            )
            Toast.makeText(this, "Grabación guardada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        // This prevents the timer from running in the background
        // and causing memory leaks if the user leaves the app.
        stopTimer()
    }

    private fun sendEmail(fileUri: Uri) {
        val selectedEmails = mutableListOf<String>()
        val checkedPositions = listStudents.checkedItemPositions

        for (i in 0 until allEmails.size) {
            if (checkedPositions.get(i)) {
                selectedEmails.add(allEmails[i])
            }
        }

        if (selectedEmails.isEmpty()) {
            Toast.makeText(this, "Selecciona al menos un destinatario de la lista", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, selectedEmails.toTypedArray()) // Enviamos solo los seleccionados
            putExtra(Intent.EXTRA_SUBJECT, "Audio Grabado")
            putExtra(Intent.EXTRA_TEXT, "Adjunto envío el audio.")
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(fileUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Enviar correo con..."))
    }
}