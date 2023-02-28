package com.example.cubegameapp.esp32ble


data class GameStatus(var gameStatus: String = "")
data class Esp32Data(val playStatus: String = "", val seite: String = "")
data class Repeat(var repeat: String = "R")
