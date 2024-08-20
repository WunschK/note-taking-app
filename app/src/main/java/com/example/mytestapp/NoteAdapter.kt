package com.example.mytestapp

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NoteAdapter(private val context: Context, private val notes: List<Pair<String, String>>) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val noteName: TextView = itemView.findViewById(R.id.note_name)
        val menuButton: ImageView = itemView.findViewById(R.id.menu_button)

        init {
            // Handle the note click to open it
            itemView.setOnClickListener {
                val noteId = notes[adapterPosition].first
                val intent = Intent(context, AddNoteActivity::class.java)
                intent.putExtra("NOTE_ID", noteId)
                context.startActivity(intent)
            }

            // Handle the menu button click
            menuButton.setOnClickListener { view ->
                showPopupMenu(view, adapterPosition)
            }
        }

        private fun showPopupMenu(view: View, position: Int) {
            val popupMenu = PopupMenu(context, view)
            MenuInflater(context).inflate(R.menu.note_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_open -> {
                        val noteId = notes[position].first
                        val intent = Intent(context, AddNoteActivity::class.java)
                        intent.putExtra("NOTE_ID", noteId)
                        context.startActivity(intent)
                        true
                    }
                    R.id.menu_delete -> {
                        deleteNote(position)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }

        private fun deleteNote(position: Int) {
            val noteId = notes[position].first
            // Remove the note from shared preferences
            val prefs = context.getSharedPreferences("MyNotesPrefs", Context.MODE_PRIVATE)
            with(prefs.edit()) {
                remove("${noteId}_name")
                remove("${noteId}_content")
                remove("${noteId}_svg")
                apply()
            }
            // Notify the adapter that the data has changed
            (context as MainActivity).updateNotes()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.note_item, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val (noteId, noteName) = notes[position]
        holder.noteName.text = noteName
    }

    override fun getItemCount(): Int {
        return notes.size
    }
}
