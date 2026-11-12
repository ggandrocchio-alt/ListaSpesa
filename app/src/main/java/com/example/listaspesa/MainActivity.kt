package com.example.listaspesa

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.listaspesa.databinding.ActivityMainBinding

data class ShoppingItem(val name: String, var checked: Boolean = false)

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val items = mutableListOf<ShoppingItem>()
    private lateinit var adapter: ShoppingAdapter
    private val prefs by lazy { getSharedPreferences("shopping_list", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loadItems()
        adapter = ShoppingAdapter(items, ::saveItems) { item -> items.remove(item); adapter.notifyDataSetChanged(); saveItems(); updateSummary() }
        binding.itemsList.layoutManager = LinearLayoutManager(this)
        binding.itemsList.adapter = adapter
        binding.addButton.setOnClickListener {
            val name = binding.itemInput.text.toString().trim()
            if (name.isNotEmpty()) { items.add(ShoppingItem(name)); adapter.notifyItemInserted(items.lastIndex); binding.itemInput.text.clear(); saveItems(); updateSummary() }
        }
        updateSummary()
    }

    private fun loadItems() {
        prefs.getStringSet("items", emptySet()).orEmpty().forEach { value ->
            val parts = value.split("|", limit = 2); items.add(ShoppingItem(parts[0], parts.getOrNull(1) == "1"))
        }
    }
    private fun saveItems() { prefs.edit().putStringSet("items", items.map { "${it.name}|${if (it.checked) 1 else 0}" }.toSet()).apply() }
    private fun updateSummary() { binding.summary.text = "${items.count { !it.checked }} da compra · ${items.size} totali" }
}

private class ShoppingAdapter(private val items: MutableList<ShoppingItem>, private val onChange: () -> Unit, private val onDelete: (ShoppingItem) -> Unit) : RecyclerView.Adapter<ShoppingViewHolder>() {
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int) = ShoppingViewHolder(android.view.LayoutInflater.from(parent.context).inflate(com.example.listaspesa.R.layout.item_shopping, parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: ShoppingViewHolder, position: Int) { holder.bind(items[position], onChange, onDelete) }
}

private class ShoppingViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
    fun bind(item: ShoppingItem, onChange: () -> Unit, onDelete: (ShoppingItem) -> Unit) {
        val check = itemView.findViewById<android.widget.CheckBox>(com.example.listaspesa.R.id.checkBox)
        val name = itemView.findViewById<android.widget.TextView>(com.example.listaspesa.R.id.itemName)
        check.setOnCheckedChangeListener(null); check.isChecked = item.checked; name.text = item.name
        check.setOnCheckedChangeListener { _, checked -> item.checked = checked; onChange() }
        itemView.findViewById<android.widget.ImageButton>(com.example.listaspesa.R.id.deleteButton).setOnClickListener { onDelete(item) }
    }
}
