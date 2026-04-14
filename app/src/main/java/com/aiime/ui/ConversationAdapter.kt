package com.aiime.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aiime.R
import com.aiime.engine.ConversationMessage

/**
 * 对话气泡列表 Adapter
 *
 * 每条消息渲染为左/右气泡，点击 assistant 气泡可直接重新注入。
 */
class ConversationAdapter(
    private val onAssistantBubbleClick: (String) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.BubbleViewHolder>() {

    private val messages = mutableListOf<ConversationMessage>()

    fun submitMessages(list: List<ConversationMessage>) {
        messages.clear()
        messages.addAll(list)
        notifyDataSetChanged()
    }

    fun appendMessage(msg: ConversationMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BubbleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation_bubble, parent, false)
        return BubbleViewHolder(view)
    }

    override fun onBindViewHolder(holder: BubbleViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    inner class BubbleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvUser: TextView = view.findViewById(R.id.tv_user_bubble)
        private val tvAssistant: TextView = view.findViewById(R.id.tv_assistant_bubble)

        fun bind(msg: ConversationMessage) {
            if (msg.isUser) {
                tvUser.visibility = View.VISIBLE
                tvAssistant.visibility = View.GONE
                tvUser.text = msg.content
            } else {
                tvUser.visibility = View.GONE
                tvAssistant.visibility = View.VISIBLE
                tvAssistant.text = msg.content
                tvAssistant.setOnClickListener {
                    onAssistantBubbleClick(msg.content)
                }
            }
        }
    }
}
