package com.example.cubegameapp.model

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benasher44.uuid.uuidFrom
import com.example.cubegameapp.esp32ble.CUSTOM_SERVICE_UUID
import com.example.cubegameapp.esp32ble.Esp32Ble
import com.juul.kable.Filter
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.peripheral
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

object ScanState {
    const val NOT_SCANNING = 0
    const val SCANNING = 1
    const val FAILED = 2
}

object ConnectState {
    const val NO_DEVICE = 0
    const val DEVICE_SELECTED = 1
    const val CONNECTED = 2
    const val NOT_CONNECTED = 3
}

data class Device(val name: String, val address: String) {
    override fun toString(): String = name + ": " + address
}

class MainViewModel : ViewModel() {

    private val TAG = "MainViewModel"


    //---Device auswählen---
    private val _deviceList = MutableLiveData<MutableList<Device>>()
    val deviceList: LiveData<MutableList<Device>>
        get() = _deviceList


    fun getDeviceList(): List<Device>? {
        return _deviceList.value
    }


    private var deviceSelected = "CubeGame1: 24:6F:28:1A:71:76"
    fun getDeviceSelected(): String {
        return deviceSelected
    }


    fun setDeviceSelected(devicestring: String) {
        deviceSelected = devicestring
        _connectState.value = ConnectState.DEVICE_SELECTED
    }


    //---Zweck auswhählen---
    private val _selectedUse = MutableLiveData<String>()
    val selectedUse: LiveData<String>
        get() = _selectedUse


    fun getUseSelected(): String {
        return _selectedUse.value.toString()
    }

    fun setUseSelected(use: String) {
        _selectedUse.value = use
        Log.i(TAG,_selectedUse.value.toString())
    }


    init {
        _deviceList.value = mutableListOf()
        _selectedUse.value = ""
    }




    // Scanning
    // ------------------------------------------------------------------------------

    private lateinit var scanJob: Job

    private val scanner = Scanner {
        filters = listOf(
            Filter.Service(uuidFrom(CUSTOM_SERVICE_UUID))
        )
    }

    private var scanState = ScanState.NOT_SCANNING

    fun startScan() {
        Log.i(">>>>", "Start Scanning ...")
        if (scanState == ScanState.SCANNING) return // Scan already in progress.
        scanState = ScanState.SCANNING

        val SCAN_DURATION_MILLIS = TimeUnit.SECONDS.toMillis(10)
        scanJob = viewModelScope.launch {
            withTimeoutOrNull(SCAN_DURATION_MILLIS) {
                scanner
                    .advertisements
                    //.filter { advertisement -> advertisement.name?.startsWith("CubeGame1") == true }
                    .catch {cause -> scanState = ScanState.FAILED
                        Log.i(">>>> Scanning Failed", cause.message.toString())
                    }
                    .onCompletion { cause -> if (cause == null || cause is CancellationException)
                        scanState = ScanState.NOT_SCANNING
                    }
                    .collect { advertisement ->
                        val device = Device(name = advertisement.name.toString(),
                            address = advertisement.address.toString())
                        //deviceSelected = device.toString()
                        setDeviceSelected(device.toString())
                        //Log.i(">>deviceSelected>>:", deviceSelected)
                        /*if (_deviceList.value?.contains(device) == false) {
                            _deviceList.value?.add(device)
                            _deviceList.notifyObserver()
                        }*/
                        //Log.i(">>>>", _deviceList.value.toString())
                        //Log.i(">>>>>", deviceSelected)
                    }
            }
        }
    }

    fun stopScan() {
        scanState = ScanState.NOT_SCANNING
        scanJob.cancel()
    }

    // Connecting
    // --------------------------------------------------------------------------

    private lateinit var peripheral: Peripheral
    private lateinit var esp32: Esp32Ble

    private val _connectState = MutableLiveData<Int>(ConnectState.NO_DEVICE)
    val connectState: LiveData<Int>
        get() = _connectState

    fun connect() {
        if (_connectState.value == ConnectState.NO_DEVICE) return
        val macAddress = deviceSelected.substring(deviceSelected.length -17)
        //val macAddress = "24:6F:28:1A:71:76"
        Log.i("macAdress:", macAddress)
        peripheral = viewModelScope.peripheral(macAddress) {
            onServicesDiscovered {
                requestMtu(517)
            }
        }
        esp32 = Esp32Ble(peripheral)


        viewModelScope.launch {
            peripheral.state.collect { state ->

                Log.i(">>>> Connection State:", state.toString())
                when (state.toString()) {
                    "Connected" -> _connectState.value = ConnectState.CONNECTED
                    "Disconnected(null)" -> _connectState.value = ConnectState.NOT_CONNECTED
                    else -> _connectState.value = ConnectState.NOT_CONNECTED
                }
            }
        }

        viewModelScope.launch {
            esp32.connect()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            // Allow 5 seconds for graceful disconnect before forcefully closing `Peripheral`.
            withTimeoutOrNull(5_000L) {
                esp32.disconnect()
            }
        }
    }



    // Extension Function, um Änderung in den Einträgen von Listen
    // dem Observer anzeigen zu können
    fun <T> MutableLiveData<T>.notifyObserver() {
        this.value = this.value
    }


    // Communication
    // ____________________________________________________________________

/*
    var ledData = LedData()

    private lateinit var dataLoadJob: Job

    private var _esp32Data = MutableLiveData<Esp32Data>(Esp32Data())
    val esp32Data: LiveData<Esp32Data>
        get() = _esp32Data

    fun startDataLoadJob() {
        dataLoadJob = viewModelScope.launch {
            esp32.incomingMessages.collect { msg ->
                val jsonstring = String(msg)
                Log.i(">>>> msg in", jsonstring)
                _esp32Data.value = jsonParseEsp32Data(jsonstring)
            }
        }
    }

    fun cancelDataLoadJob() {
        dataLoadJob.cancel()
    }

    fun sendLedData() {
        viewModelScope.launch {
            try {
                esp32.sendMessage(jsonEncodeLedData(ledData))
            } catch (e: Exception) {
                Log.i(">>>>>", "Error sending ledData ${e.message}" + e.toString())
            }
        }
    }

    private fun jsonEncodeLedData(ledData: LedData): String {
        val obj = JSONObject()
        obj.put("LED", ledData.led)
        obj.put("LEDBlinken", ledData.ledBlinken)
        return obj.toString()
    }

    fun jsonParseEsp32Data(jsonString: String): Esp32Data {
        try {
            val obj = JSONObject(jsonString)
            return Esp32Data(
                ledstatus = obj.getString("ledstatus"),
                potiArray = obj.getJSONArray("potiArray")
            )
        } catch (e: Exception) {
            Log.i(">>>>", "Error decoding JSON ${e.message}")
            return Esp32Data()
        }
    }*/
}