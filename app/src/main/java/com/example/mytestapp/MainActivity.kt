package com.example.mytestapp

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var fab: FloatingActionButton
    private lateinit var notesRecyclerView: RecyclerView
    private lateinit var noteAdapter: NoteAdapter
    private var notes: List<Pair<String, String>> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        fab = findViewById(R.id.fab)
        fab.setOnClickListener {
            val intent = Intent(this, AddNoteActivity::class.java)
            intent.putExtra("NOTE_ID", generateNewNoteId())
            startActivity(intent)
        }

        notesRecyclerView = findViewById(R.id.notes_recycler_view)
        notesRecyclerView.layoutManager = LinearLayoutManager(this)

        updateNotes()
    }

    fun updateNotes() {
        loadNotes()
        noteAdapter = NoteAdapter(this, notes)
        notesRecyclerView.adapter = noteAdapter
    }

    private fun loadNotes() {
        val sharedPreferences = getSharedPreferences("MyNotesPrefs", MODE_PRIVATE)
        val loadedNotes = mutableListOf<Pair<String, String>>()
        sharedPreferences.all.forEach { (key, value) ->
            if (key.endsWith("_name")) {
                val noteId = key.removeSuffix("_name")
                val noteName = value as String
                loadedNotes.add(Pair(noteId, noteName))
            }
        }
        notes = loadedNotes
    }

    private fun generateNewNoteId(): String {
        return System.currentTimeMillis().toString()
    }

    override fun onResume() {
        super.onResume()
        updateNotes()
    }
}
