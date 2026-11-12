package com.example.listaspesa

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.listaspesa.databinding.ActivityMainBinding
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import java.security.SecureRandom

data class ShoppingItem(val id: String, val name: String, val checked: Boolean)
private data class SharedList(val id: String, val name: String, val inviteCode: String)

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val credentialManager by lazy { CredentialManager.create(this) }
    private val prefs by lazy { getSharedPreferences("shopping_list", Context.MODE_PRIVATE) }
    private val lists = mutableListOf<SharedList>()
    private val items = mutableListOf<ShoppingItem>()
    private lateinit var adapter: ShoppingAdapter
    private var listListener: ListenerRegistration? = null
    private var itemListener: ListenerRegistration? = null
    private var selectedListId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        selectedListId = prefs.getString("selected_list_id", null)
        adapter = ShoppingAdapter(items, ::setChecked, ::deleteItem)
        binding.itemsList.layoutManager = LinearLayoutManager(this)
        binding.itemsList.adapter = adapter
        binding.signInButton.setOnClickListener { signIn() }
        binding.signOutButton.setOnClickListener { signOut() }
        binding.createListButton.setOnClickListener { createList() }
        binding.joinListButton.setOnClickListener { joinList() }
        binding.shareListButton.setOnClickListener { shareList() }
        binding.addButton.setOnClickListener { addItem() }
        binding.listSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val list = lists.getOrNull(position) ?: return
                if (selectedListId != list.id) {
                    selectedListId = list.id
                    prefs.edit().putString("selected_list_id", list.id).apply()
                    listenItems(list.id)
                }
            }
        }
        updateAccountUi(auth.currentUser)
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser != null) listenLists()
    }

    override fun onStop() {
        listListener?.remove()
        itemListener?.remove()
        listListener = null
        itemListener = null
        super.onStop()
    }

    private fun signIn() {
        val clientResource = resources.getIdentifier("default_web_client_id", "string", packageName)
        if (clientResource == 0) {
            showError("Configurazione Google non disponibile")
            return
        }
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(getString(clientResource))
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        lifecycleScope.launch {
            try {
                val response = credentialManager.getCredential(this@MainActivity, request)
                val credential = response.credential
                if (credential !is CustomCredential ||
                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    showError("Credenziale Google non valida")
                    return@launch
                }
                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                    .addOnSuccessListener {
                        updateAccountUi(auth.currentUser)
                        listenLists()
                    }
                    .addOnFailureListener { showError(it.localizedMessage ?: "Accesso non riuscito") }
            } catch (e: GetCredentialException) {
                showError(e.localizedMessage ?: "Accesso annullato")
            } catch (e: GoogleIdTokenParsingException) {
                showError("Token Google non valido")
            }
        }
    }

    private fun signOut() {
        listListener?.remove()
        itemListener?.remove()
        listListener = null
        itemListener = null
        auth.signOut()
        lists.clear()
        items.clear()
        selectedListId = null
        adapter.notifyDataSetChanged()
        updateAccountUi(null)
        lifecycleScope.launch {
            try { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
            catch (_: Exception) { /* Firebase session is already closed. */ }
        }
    }

    private fun updateAccountUi(user: FirebaseUser?) {
        val signedIn = user != null
        binding.accountStatus.text = user?.email ?: "Accedi con Google per condividere una lista"
        binding.signInButton.visibility = if (signedIn) View.GONE else View.VISIBLE
        binding.signOutButton.visibility = if (signedIn) View.VISIBLE else View.GONE
        binding.listsSection.visibility = if (signedIn) View.VISIBLE else View.GONE
        binding.itemInput.isEnabled = signedIn && selectedListId != null
        binding.addButton.isEnabled = signedIn && selectedListId != null
        updateSummary()
    }

    private fun listenLists() {
        val uid = auth.currentUser?.uid ?: return
        listListener?.remove()
        listListener = db.collection("lists").whereArrayContains("members", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { showError(error.localizedMessage ?: "Errore liste"); return@addSnapshotListener }
                lists.clear()
                snapshot?.documents?.forEach { doc ->
                    lists.add(SharedList(doc.id, doc.getString("name") ?: "Lista", doc.getString("inviteCode") ?: ""))
                }
                lists.sortBy { it.name.lowercase() }
                binding.listSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, lists.map { it.name })
                val index = lists.indexOfFirst { it.id == selectedListId }.let { if (it >= 0) it else 0 }
                if (lists.isNotEmpty()) {
                    binding.listSpinner.setSelection(index)
                    val list = lists[index]
                    if (itemListener == null || selectedListId != list.id) {
                        selectedListId = list.id
                        prefs.edit().putString("selected_list_id", list.id).apply()
                        listenItems(list.id)
                    }
                } else {
                    selectedListId = null
                    itemListener?.remove()
                    itemListener = null
                    items.clear()
                    adapter.notifyDataSetChanged()
                }
                updateAccountUi(auth.currentUser)
            }
    }

    private fun listenItems(listId: String) {
        itemListener?.remove()
        items.clear()
        adapter.notifyDataSetChanged()
        updateAccountUi(auth.currentUser)
        itemListener = db.collection("lists").document(listId).collection("items")
            .whereEqualTo("deleted", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { showError(error.localizedMessage ?: "Errore articoli"); return@addSnapshotListener }
                items.clear()
                snapshot?.documents?.forEach { doc ->
                    items.add(ShoppingItem(doc.id, doc.getString("name") ?: "", doc.getBoolean("checked") == true))
                }
                items.sortBy { it.name.lowercase() }
                adapter.notifyDataSetChanged()
                updateSummary()
            }
    }

    private fun createList() {
        val uid = auth.currentUser?.uid ?: return
        val name = binding.listNameInput.text.toString().trim()
        if (name.isEmpty()) { showError("Inserisci il nome della lista"); return }
        val codeBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val inviteCode = codeBytes.joinToString("") { "%02x".format(it) }
        val ref = db.collection("lists").document()
        ref.set(mapOf(
            "name" to name,
            "ownerId" to uid,
            "members" to listOf(uid),
            "inviteCode" to inviteCode,
            "createdAt" to FieldValue.serverTimestamp()
        )).addOnSuccessListener {
            selectedListId = ref.id
            prefs.edit().putString("selected_list_id", ref.id).apply()
            binding.listNameInput.text.clear()
            listenLists()
        }.addOnFailureListener { showError(it.localizedMessage ?: "Creazione non riuscita") }
    }

    private fun joinList() {
        val uid = auth.currentUser?.uid ?: return
        val parts = binding.inviteInput.text.toString().trim().split(':')
        if (parts.size != 2 || !parts[0].matches(Regex("[A-Za-z0-9]{20}")) ||
            !parts[1].matches(Regex("[0-9a-f]{32}"))) {
            showError("Codice invito non valido")
            return
        }
        val listId = parts[0]
        if (lists.any { it.id == listId }) {
            selectedListId = listId
            listenLists()
            return
        }
        db.collection("lists").document(listId).update(
            "members", FieldValue.arrayUnion(uid), "joinProof", parts[1]
        ).addOnSuccessListener {
            selectedListId = listId
            prefs.edit().putString("selected_list_id", listId).apply()
            binding.inviteInput.text.clear()
            listenLists()
        }.addOnFailureListener { showError("Invito non valido o lista non disponibile") }
    }

    private fun shareList() {
        val list = lists.firstOrNull { it.id == selectedListId } ?: return
        val code = "${list.id}:${list.inviteCode}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Entra nella lista ${list.name} di ListaSpesa con questo codice: $code")
        }
        startActivity(Intent.createChooser(intent, "Condividi codice invito"))
    }

    private fun addItem() {
        val uid = auth.currentUser?.uid ?: return
        val listId = selectedListId ?: return
        val name = binding.itemInput.text.toString().trim()
        if (name.isEmpty()) return
        db.collection("lists").document(listId).collection("items").document().set(mapOf(
            "name" to name,
            "checked" to false,
            "deleted" to false,
            "createdBy" to uid,
            "updatedBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )).addOnFailureListener { showError(it.localizedMessage ?: "Articolo non salvato") }
        binding.itemInput.text.clear()
    }

    private fun setChecked(item: ShoppingItem, checked: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val listId = selectedListId ?: return
        db.collection("lists").document(listId).collection("items").document(item.id)
            .update("checked", checked, "updatedBy", uid, "updatedAt", FieldValue.serverTimestamp())
            .addOnFailureListener { showError(it.localizedMessage ?: "Modifica non salvata") }
    }

    private fun deleteItem(item: ShoppingItem) {
        val uid = auth.currentUser?.uid ?: return
        val listId = selectedListId ?: return
        db.collection("lists").document(listId).collection("items").document(item.id)
            .update("deleted", true, "updatedBy", uid, "updatedAt", FieldValue.serverTimestamp())
            .addOnFailureListener { showError(it.localizedMessage ?: "Eliminazione non riuscita") }
    }

    private fun updateSummary() {
        binding.summary.text = if (auth.currentUser == null) "Accedi per vedere le liste" else if (selectedListId == null)
            "Crea una lista o inserisci un codice invito" else "${items.count { !it.checked }} da compra · ${items.size} totali"
    }

    private fun showError(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}

private class ShoppingAdapter(
    private val items: List<ShoppingItem>,
    private val onCheck: (ShoppingItem, Boolean) -> Unit,
    private val onDelete: (ShoppingItem) -> Unit
) : RecyclerView.Adapter<ShoppingViewHolder>() {
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int) = ShoppingViewHolder(
        android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_shopping, parent, false)
    )
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: ShoppingViewHolder, position: Int) = holder.bind(items[position], onCheck, onDelete)
}

private class ShoppingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    fun bind(item: ShoppingItem, onCheck: (ShoppingItem, Boolean) -> Unit, onDelete: (ShoppingItem) -> Unit) {
        val check = itemView.findViewById<android.widget.CheckBox>(R.id.checkBox)
        val name = itemView.findViewById<android.widget.TextView>(R.id.itemName)
        check.setOnCheckedChangeListener(null)
        check.isChecked = item.checked
        name.text = item.name
        check.setOnCheckedChangeListener { _, checked -> onCheck(item, checked) }
        itemView.findViewById<android.widget.ImageButton>(R.id.deleteButton).setOnClickListener { onDelete(item) }
    }
}
