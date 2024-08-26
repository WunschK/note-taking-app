package com.example.mytestapp

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity


class AddNoteActivity : AppCompatActivity() {

    private lateinit var noteNameEditText: EditText
    private lateinit var drawingView: DrawingView
    private lateinit var saveButton: Button
    private lateinit var toggleEraserButton: Button
    private lateinit var clearCanvasButton: Button
    private var noteId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_note)

        // Initialize views
        noteNameEditText = findViewById(R.id.note_name)
        drawingView = findViewById(R.id.drawing_view)
        saveButton = findViewById(R.id.save_button)
        toggleEraserButton = findViewById(R.id.toggleEraser)
        clearCanvasButton = findViewById(R.id.clearCanvasButton)

        // Get the note ID from the intent
        noteId = intent.getStringExtra("NOTE_ID")

        // Load note if noteId is not null
        if (noteId != null) {
            loadNote()
        }

        // Set up the save button click listener
        saveButton.setOnClickListener {
            saveNote() // Save the note
            finish() // Close the activity
        }

        // Set up the toggle eraser button click listener
        toggleEraserButton.setOnClickListener {
            drawingView.toggleEraserMode()
            updateEraserButton()
        }

        updateEraserButton()

        clearCanvasButton.setOnClickListener {
            drawingView.clearDrawing()
        }
    }

    private fun updateEraserButton() {
        if (drawingView.isErasing) {
            toggleEraserButton.text = "Erase"
            toggleEraserButton.setBackgroundColor(getColor(android.R.color.holo_red_light))
        } else {
            toggleEraserButton.text = "Draw"
            toggleEraserButton.setBackgroundColor(getColor(android.R.color.holo_green_light))
        }
    }

    // Helper function to generate file path based on note ID
    private fun getFilePathForNoteId(noteId: String): String {
        // Return the file path where SVG files are saved
        return "${filesDir}/${noteId}.svg"
    }

    private fun loadNote() {
        val prefs = getSharedPreferences("MyNotesPrefs", MODE_PRIVATE)
        val noteName = prefs.getString("${noteId}_name", "")
        val svgFilePath = prefs.getString("${noteId}_svg", "")

        noteNameEditText.setText(noteName)


        if (svgFilePath != null && svgFilePath.isNotEmpty()) {
            drawingView.loadSVG(svgFilePath)
        }
    }

    private fun saveNote() {
        val noteName = noteNameEditText.text.toString()
        val filePath = getFilePathForNoteId(noteId!!)

        drawingView.saveDrawingAsSVG(filePath)

        val prefs = getSharedPreferences("MyNotesPrefs", MODE_PRIVATE)
        with(prefs.edit()) {
            putString("${noteId}_name", noteName)

            putString("${noteId}_svg", filePath)
            apply()
        }
        Log.d("AddNoteActivity", "Note saved: $noteId")
    }


    }
