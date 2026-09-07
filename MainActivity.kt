package com.oppominer.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oppominer.service.MiningForegroundService

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MinerDashboard(
                        onStartMining = { threads, temp, pool, port, btc, worker ->
                            val intent = Intent(this, MiningForegroundService::class.java).apply {
                                action = MiningForegroundService.ACTION_START
                                putExtra("threads", threads)
                                putExtra("maxTemp", temp)
                                putExtra("poolHost", pool)
                                putExtra("poolPort", port)
                                putExtra("btcAddress", btc)
                                putExtra("workerName", worker)
                            }
                            startForegroundService(intent)
                        },
                        onStopMining = {
                            val intent = Intent(this, MiningForegroundService::class.java).apply {
                                action = MiningForegroundService.ACTION_STOP
                            }
                            startService(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MinerDashboard(
    onStartMining: (Int, Float, String, Int, String, String) -> Unit,
    onStopMining: () -> Unit
) {
    var isMining by remember { mutableStateOf(false) }
    var threads by remember { mutableStateOf(6f) }
    var maxTemp by remember { mutableStateOf(43f) }
    var poolHost by remember { mutableStateOf("solo.ckpool.org") }
    var poolPort by remember { mutableStateOf("3333") }
    var btcAddress by remember { mutableStateOf("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh") }
    var workerName by remember { mutableStateOf("oppo_a5") }

    Column(modifier = Modifier.padding(20.dp)) {
        Text("OPPO A5 Bitcoin Miner", style = MaterialTheme.typography.headlineMedium)
        Text("ARM64 C++ NDK + Stratum V1", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = btcAddress,
            onValueChange = { btcAddress = it },
            label = { Text("BTC Payout Address (No Private Key Required)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text("Active CPU Mining Threads: ${threads.toInt()} of 8 cores (Recommended: 6)")
        Slider(
            value = threads,
            onValueChange = { threads = it },
            valueRange = 1f..8f,
            steps = 6
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text("Thermal Cutoff: ${maxTemp.toInt()}°C")
        Slider(
            value = maxTemp,
            onValueChange = { maxTemp = it },
            valueRange = 38f..48f
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (!isMining) {
                    onStartMining(threads.toInt(), maxTemp, poolHost, poolPort.toIntOrNull() ?: 3333, btcAddress, workerName)
                    isMining = true
                } else {
                    onStopMining()
                    isMining = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isMining) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (isMining) "STOP MINING" else "START MINING (6 THREADS)")
        }
    }
}
