package com.oppominer.stratum

import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

data class StratumJob(
    val jobId: String,
    val prevHash: String,
    val coinb1: String,
    val coinb2: String,
    val merkleBranch: List<String>,
    val version: String,
    val nbits: String,
    val ntime: String,
    val cleanJobs: Boolean,
    val midstate: IntArray,
    val tail: IntArray,
    val targetUpper: Long
)

class StratumClient(
    private val host: String,
    private val port: Int,
    private val btcAddress: String,
    private val workerName: String,
    private val onNewJob: (StratumJob) -> Unit
) {
    private var socket: Socket? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null
    private val messageId = AtomicInteger(1)
    private var extraNonce1 = ""
    private var extraNonce2Size = 4
    private var currentDifficulty = 1.0

    suspend fun connect() = withContext(Dispatchers.IO) {
        try {
            socket = Socket(host, port)
            writer = OutputStreamWriter(socket!!.getOutputStream())
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))

            // 1. mining.subscribe
            sendJsonRpc("mining.subscribe", JSONArray().put("oppo-miner/1.0"))

            // 2. Read messages in loop
            while (socket?.isConnected == true && !socket!!.isClosed) {
                val line = reader?.readLine() ?: break
                handleIncomingMessage(line)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Auto-reconnect after 5 seconds
            delay(5000)
            connect()
        }
    }

    private fun handleIncomingMessage(line: String) {
        val json = JSONObject(line)
        if (json.has("method")) {
            when (json.getString("method")) {
                "mining.notify" -> parseJobNotify(json.getJSONArray("params"))
                "mining.set_difficulty" -> {
                    currentDifficulty = json.getJSONArray("params").getDouble(0)
                }
            }
        } else if (json.has("result") && !json.isNull("result")) {
            // Subscribe response
            val result = json.optJSONArray("result")
            if (result != null && result.length() >= 2) {
                extraNonce1 = result.getString(1)
                extraNonce2Size = result.getInt(2)
                // Authorize worker
                val username = if (workerName.isNotEmpty()) "$btcAddress.$workerName" else btcAddress
                sendJsonRpc("mining.authorize", JSONArray().put(username).put("x"))
            }
        }
    }

    private fun parseJobNotify(params: JSONArray) {
        val jobId = params.getString(0)
        val prevHash = params.getString(1)
        val coinb1 = params.getString(2)
        val coinb2 = params.getString(3)
        val merkle = mutableListOf<String>()
        val merkleArray = params.getJSONArray(4)
        for (i in 0 until merkleArray.length()) {
            merkle.add(merkleArray.getString(i))
        }
        val version = params.getString(5)
        val nbits = params.getString(6)
        val ntime = params.getString(7)
        val clean = params.getBoolean(8)

        // Mock midstate and tail representation for JNI ARM64 kernel
        val dummyMidstate = IntArray(8) { 0x6a09e667 + it }
        val dummyTail = IntArray(4) { it * 100 }
        val targetUpper = (0x0000ffffL / (currentDifficulty.coerceAtLeast(1.0))).toLong()

        val job = StratumJob(jobId, prevHash, coinb1, coinb2, merkle, version, nbits, ntime, clean, dummyMidstate, dummyTail, targetUpper)
        onNewJob(job)
    }

    fun submitShare(jobId: String, extraNonce2: String, ntime: String, nonce: String) {
        val username = if (workerName.isNotEmpty()) "$btcAddress.$workerName" else btcAddress
        val params = JSONArray()
            .put(username)
            .put(jobId)
            .put(extraNonce2)
            .put(ntime)
            .put(nonce)
        sendJsonRpc("mining.submit", params)
    }

    private fun sendJsonRpc(method: String, params: JSONArray) {
        val obj = JSONObject()
        obj.put("id", messageId.getAndIncrement())
        obj.put("method", method)
        obj.put("params", params)
        val msg = obj.toString() + "\n"
        writer?.write(msg)
        writer?.flush()
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (e: Exception) {
            // ignore
        }
    }
}
