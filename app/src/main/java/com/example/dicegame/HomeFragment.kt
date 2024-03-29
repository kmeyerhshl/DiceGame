package com.example.dicegame

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.dicegame.databinding.FragmentHomeBinding
import com.example.dicegame.model.ConnectState
import com.example.dicegame.model.MainViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import splitties.toast.toast

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class HomeFragment : Fragment() {

    private val TAG = "Home"

    private var _binding: FragmentHomeBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    //Datenbank
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private lateinit var dbList: ArrayList<String>
    private lateinit var adapter: ArrayAdapter<String>

    //Variable für Verwendungszweck
    private var use: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Button zum Verbinden unsichtbar machen
        binding.btnConnect.isVisible = false
        //Scan nach Bluetooth Gerät starten und nach einem delay die Verbindung herstellen
        viewModel.startScan()
        val scope = MainScope()
        scope.launch {
            delay(1000)
            viewModel.connect()
        }

        //Liste der Verwendungszwecke aus Datenbank laden
        loadDbList()
        //ausgewählten Zweck im Exposed Dropdown Menu darstellen
        binding.autoCompleteTextView.onItemClickListener = AdapterView.OnItemClickListener { adapterView, view, i, l ->
            toast("${binding.autoCompleteTextView.text}")
            use = binding.autoCompleteTextView.text.toString()
            viewModel.setUseSelected(use)
        }

        //Button zum Öffnen des nächsten Fragments
        binding.btnStart.setOnClickListener {
            Log.i(TAG, "Button Start")
            //Fehlermeldung, wenn kein Zweck ausgewählt wurde
            if (use.isEmpty()) {
                toast("Bitte Zweck auswählen")
            } else {
                findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
            }
        }

        //Button zum Öffnen der Anleitung
        binding.fabAnleitung.setOnClickListener {
            showDialog()
        }

        //Button zum Herstellen der Bluetooth-Verbindung
        binding.btnConnect.setOnClickListener {
            Log.i(TAG, "Button Connect")
            viewModel.connect()
        }


        // Mittels Observer über Änderungen des connect status informieren
        // sobald keine Verbindung hergestellt werden kann, wird der Button zum manuellen Verbinden angezeigt
        viewModel.connectState.observe(viewLifecycleOwner) { state ->
            when (state) {
                ConnectState.CONNECTED -> {
                    binding.tvConnection.text = getString(R.string.connected)
                    binding.btnConnect.isVisible = false
                }
                ConnectState.NOT_CONNECTED -> {
                    binding.tvConnection.text = getString(R.string.disconnected)
                    binding.btnConnect.isVisible = true
                }
                ConnectState.NO_DEVICE -> {
                    binding.tvConnection.text = getString(R.string.no_selected_device)
                    binding.btnConnect.isVisible = true
                }
                ConnectState.DEVICE_SELECTED -> {
                    binding.tvConnection.text = getString(R.string.connecting)
                    binding.btnConnect.isVisible = false
                }
            }
        }
    }

    //Liste der Verwendungszwecke aus Datenbank laden
    private fun loadDbList() {
        db.collection("Verwendungszweck")
            .addSnapshotListener(EventListener { value, e ->
                if (e != null) {
                    return@EventListener
                }
                updateListOnChange(value!!)
            })
    }

    //aus der Datenbank abgerufene Verwendungszwecke zum Listview hinzufügen, Liste aktualisieren
    private fun updateListOnChange(value: QuerySnapshot) {
        dbList = ArrayList()
        for (documentSnapshot in value) {
            // Datenbankeintrag in Objektvariable speichern
            val highscore = documentSnapshot.toObject(Data::class.java)
            dbList.add(highscore.toString())
        }
        adapter = ArrayAdapter(requireContext(), R.layout.list_item, dbList)
        binding.autoCompleteTextView.setAdapter(adapter)
    }

    //Dialog für Anleitung
    private fun showDialog() {
        context?.let {
            MaterialAlertDialogBuilder(it)
                .setTitle(R.string.fabAnleitung)
                .setMessage(R.string.anleitung)
                .setPositiveButton(R.string.dialog_cancel) { dialog, which ->
                }
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}