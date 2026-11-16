package com.rifaqurrahman.goldpos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GoldPosApp() }
    }
}

@Composable
private fun GoldPosApp() {
    var selected by remember { mutableStateOf("Dashboard") }
    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Gold POS") }) },
            bottomBar = {
                NavigationBar {
                    listOf("Dashboard", "Customers", "Settings").forEach { item ->
                        NavigationBarItem(
                            selected = selected == item,
                            onClick = { selected = item },
                            icon = {},
                            label = { Text(item) }
                        )
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(selected, style = MaterialTheme.typography.headlineMedium)
                    Text("Offline Gold and Cash customer ledger", style = MaterialTheme.typography.bodyMedium)
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Gold balance", style = MaterialTheme.typography.titleMedium)
                            Text("0.000 g", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Cash balance", style = MaterialTheme.typography.titleMedium)
                            Text("PKR 0.00", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
                item {
                    Text(
                        "Initial app shell only. Customer records, transaction storage, receipts, PIN, PDF statements and backup/restore are still being implemented.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
