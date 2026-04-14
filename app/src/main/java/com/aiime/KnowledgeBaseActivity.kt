package com.aiime

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiime.data.AppDatabase
import com.aiime.data.KnowledgeEntry
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KnowledgeBaseActivity : AppCompatActivity() {

    private lateinit var adapter: KnowledgeAdapter
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_knowledge_base)

        database = AppDatabase.getInstance(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val rv = findViewById<RecyclerView>(R.id.rv_entries)
        adapter = KnowledgeAdapter(
            onDelete = { entry -> deleteEntry(entry) }
        )
        rv.adapter = adapter
        rv.layoutManager = LinearLayoutManager(this)

        // 观察知识库变化
        lifecycleScope.launch {
            database.knowledgeDao().getAllEntries().collect { entries ->
                adapter.submitList(entries)
            }
        }

        findViewById<FloatingActionButton>(R.id.fab_add).setOnClickListener {
            showAddEntryDialog()
        }
    }

    private fun showAddEntryDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_entry, null)

        AlertDialog.Builder(this)
            .setTitle("添加知识条目")
            .setView(dialogView)
            .setPositiveButton("添加") { _, _ ->
                val title = dialogView.findViewById<TextInputEditText>(R.id.et_title)
                    .text?.toString()?.trim() ?: ""
                val content = dialogView.findViewById<TextInputEditText>(R.id.et_content)
                    .text?.toString()?.trim() ?: ""
                val tags = dialogView.findViewById<TextInputEditText>(R.id.et_tags)
                    .text?.toString()?.trim() ?: ""

                val chipGroup = dialogView.findViewById<ChipGroup>(R.id.chip_group_scene)
                val scene = when (chipGroup.checkedChipId) {
                    R.id.chip_work -> "work"
                    R.id.chip_chat -> "chat"
                    R.id.chip_email -> "email"
                    else -> "general"
                }

                if (title.isNotEmpty() && content.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        database.knowledgeDao().insert(
                            KnowledgeEntry(
                                title = title,
                                content = content,
                                tags = tags,
                                scene = scene
                            )
                        )
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteEntry(entry: KnowledgeEntry) {
        lifecycleScope.launch(Dispatchers.IO) {
            database.knowledgeDao().delete(entry)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

class KnowledgeAdapter(
    private val onDelete: (KnowledgeEntry) -> Unit
) : RecyclerView.Adapter<KnowledgeAdapter.ViewHolder>() {

    private var entries: List<KnowledgeEntry> = emptyList()

    fun submitList(list: List<KnowledgeEntry>) {
        entries = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_knowledge_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(entries[position])
    }

    override fun getItemCount() = entries.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tv_title)
        private val tvContent: TextView = view.findViewById(R.id.tv_content)
        private val tvTags: TextView = view.findViewById(R.id.tv_tags)
        private val tvSceneBadge: TextView = view.findViewById(R.id.tv_scene_badge)
        private val btnDelete: Button = view.findViewById(R.id.btn_delete)

        fun bind(entry: KnowledgeEntry) {
            tvTitle.text = entry.title
            tvContent.text = entry.content
            tvTags.text = if (entry.tags.isNotEmpty()) "# ${entry.tags}" else ""
            tvSceneBadge.text = when (entry.scene) {
                "work" -> "工作"
                "chat" -> "聊天"
                "email" -> "邮件"
                "social" -> "社交"
                else -> "通用"
            }
            btnDelete.setOnClickListener { onDelete(entry) }
        }
    }
}
