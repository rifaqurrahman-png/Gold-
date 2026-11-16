package com.rifaqurrahman.goldpos

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Customer(val id: Long, val name: String, val phone: String, val gold: Double, val cash: Double)
data class Entry(val id: Long, val customer: String, val kind: String, val direction: String, val weight: Double, val amount: Double, val rate: Double, val note: String, val date: String)

class PosDb(context: Context) : SQLiteOpenHelper(context, "gold_pos.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE customers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,phone TEXT DEFAULT '',opening_gold REAL DEFAULT 0,opening_cash REAL DEFAULT 0)")
        db.execSQL("CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT,customer_id INTEGER NOT NULL,kind TEXT NOT NULL,direction TEXT NOT NULL,weight REAL DEFAULT 0,amount REAL DEFAULT 0,rate REAL DEFAULT 0,note TEXT DEFAULT '',created TEXT NOT NULL,deleted INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE rates(id INTEGER PRIMARY KEY AUTOINCREMENT,rate REAL NOT NULL,created TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun addCustomer(name: String, phone: String, gold: Double, cash: Double) {
        writableDatabase.insertOrThrow("customers", null, ContentValues().apply {
            put("name", name); put("phone", phone); put("opening_gold", gold); put("opening_cash", cash)
        })
    }
    fun customers(): List<Customer> {
        val c = readableDatabase.rawQuery("SELECT c.id,c.name,c.phone,c.opening_gold,c.opening_cash,COALESCE(SUM(CASE WHEN e.kind='Gold' AND e.direction='Received' THEN e.weight WHEN e.kind='Gold' THEN -e.weight ELSE 0 END),0),COALESCE(SUM(CASE WHEN e.kind='Cash' AND e.direction='Received' THEN e.amount WHEN e.kind='Cash' THEN -e.amount ELSE 0 END),0) FROM customers c LEFT JOIN entries e ON e.customer_id=c.id AND e.deleted=0 GROUP BY c.id ORDER BY c.name COLLATE NOCASE", null)
        val out = mutableListOf<Customer>()
        c.use { while (it.moveToNext()) out.add(Customer(it.getLong(0),it.getString(1),it.getString(2),it.getDouble(3)+it.getDouble(5),it.getDouble(4)+it.getDouble(6))) }
        return out
    }
    fun addEntry(customer: Long, kind: String, direction: String, weight: Double, amount: Double, rate: Double, note: String) {
        writableDatabase.insertOrThrow("entries", null, ContentValues().apply {
            put("customer_id",customer); put("kind",kind); put("direction",direction); put("weight",weight); put("amount",amount); put("rate",rate); put("note",note)
            put("created",SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(Date()))
        })
    }
    fun entries(customer: Long): List<Entry> {
        val c = readableDatabase.rawQuery("SELECT e.id,c.name,e.kind,e.direction,e.weight,e.amount,e.rate,e.note,e.created FROM entries e JOIN customers c ON c.id=e.customer_id WHERE e.customer_id=? AND e.deleted=0 ORDER BY e.id", arrayOf(customer.toString()))
        val out=mutableListOf<Entry>()
        c.use { while(it.moveToNext()) out.add(Entry(it.getLong(0),it.getString(1),it.getString(2),it.getString(3),it.getDouble(4),it.getDouble(5),it.getDouble(6),it.getString(7),it.getString(8))) }
        return out
    }
    fun setRate(rate: Double) {
        writableDatabase.insert("rates",null,ContentValues().apply { put("rate",rate); put("created",SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(Date())) })
    }
    fun rate(): Double {
        val c=readableDatabase.rawQuery("SELECT rate FROM rates ORDER BY id DESC LIMIT 1",null)
        c.use { return if(it.moveToFirst()) it.getDouble(0) else 0.0 }
    }
    fun allTotals(): Pair<Double,Double> {
        val cs=customers()
        return Pair(cs.sumOf{it.gold},cs.sumOf{it.cash})
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db=PosDb(this)
        val prefs=getSharedPreferences("goldpos",MODE_PRIVATE)
        setContent { GoldPosApp(db,prefs.getString("pin","") ?: "", { pin -> prefs.edit().putString("pin",pin).apply() }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldPosApp(db: PosDb, initialPin: String, savePin: (String)->Unit) {
    var pin by remember { mutableStateOf(initialPin) }
    var unlocked by remember { mutableStateOf(pin.isEmpty()) }
    var pinInput by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("Dashboard") }
    var refresh by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Customer?>(null) }
    var addCustomer by remember { mutableStateOf(false) }
    var addEntry by remember { mutableStateOf(false) }
    var rateDialog by remember { mutableStateOf(false) }
    var pinDialog by remember { mutableStateOf(false) }
    var rateText by remember { mutableStateOf(db.rate().takeIf{it>0}?.toString() ?: "") }
    val customers=db.customers()
    val totals=db.allTotals()
    val rate=db.rate()
    MaterialTheme(colorScheme=darkColorScheme(primary=androidx.compose.ui.graphics.Color(0xFFD4AF37),background=androidx.compose.ui.graphics.Color(0xFF101010),surface=androidx.compose.ui.graphics.Color(0xFF1B1B1B))) {
        if (!unlocked) {
            Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center) {
                Text("Gold POS",style=MaterialTheme.typography.headlineLarge)
                Text("Enter your PIN")
                OutlinedTextField(pinInput,{pinInput=it},label={Text("PIN")})
                Button(onClick={if(pinInput==pin) unlocked=true},modifier=Modifier.fillMaxWidth()){Text("Unlock")}
            }
        } else {
            Scaffold(topBar={TopAppBar(title={Text("Gold POS")})},bottomBar={
                NavigationBar { listOf("Dashboard","Customers","Settings").forEach { item ->
                    NavigationBarItem(selected=tab==item,onClick={tab=item;selected=null},icon={},label={Text(item)})
                } }
            },floatingActionButton={
                if(tab=="Customers" || (tab=="Dashboard" && customers.isNotEmpty())) FloatingActionButton(onClick={if(tab=="Customers") addCustomer=true else {selected=customers.firstOrNull();addEntry=true}}){Text("+")}
            }) { padding ->
                key(refresh,tab,selected?.id) {
                    when(tab) {
                        "Dashboard" -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            item { Text("Dashboard",style=MaterialTheme.typography.headlineMedium); Text("Offline customer ledger · PKR") }
                            item { SummaryCard("Gold net balance",String.format(Locale.US,"%.3f g",totals.first)) }
                            item { SummaryCard("Cash net balance",String.format(Locale.US,"PKR %.2f",totals.second)) }
                            item { SummaryCard("Gold rate per tola",String.format(Locale.US,"PKR %.2f",rate)) }
                            item { Text("Recent customers",style=MaterialTheme.typography.titleLarge) }
                            items(customers.take(8)) { c -> CustomerRow(c){selected=c;tab="Customers"} }
                        }
                        "Customers" -> if(selected==null) LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                            item { Text("Customers",style=MaterialTheme.typography.headlineMedium) }
                            items(customers) { c -> CustomerRow(c){selected=c} }
                            if(customers.isEmpty()) item { Text("No customers yet. Tap + to add your first customer.") }
                        } else CustomerAccount(selected!!,db,rate,padding,{addEntry=true},{selected=null;refresh++})
                        else -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            item { Text("Settings",style=MaterialTheme.typography.headlineMedium) }
                            item { Text("Current gold rate per tola: PKR $rate") }
                            item { Button(onClick={rateText=if(rate>0) rate.toString() else "";rateDialog=true}){Text("Update gold rate")} }
                            item { Button(onClick={pinDialog=true}){Text(if(pin.isEmpty()) "Set app PIN" else "Change app PIN")} }
                            item { Text("Weight standard: 1 tola = 12.15 grams") }
                            item { Text("Data is stored locally on this device. Keep regular backups (backup/restore feature is being added).",style=MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
    if(addCustomer) CustomerDialog({addCustomer=false}) { name,phone,gold,cash -> db.addCustomer(name,phone,gold,cash);addCustomer=false;refresh++ }
    if(addEntry) {
        val target=selected ?: customers.firstOrNull()
        if(target!=null) EntryDialog(target.name,rate,{addEntry=false}) { kind,dir,w,a,r,n -> db.addEntry(target.id,kind,dir,w,a,r,n);addEntry=false;refresh++ }
        else addEntry=false
    }
    if(rateDialog) AlertDialog(onDismissRequest={rateDialog=false},title={Text("Gold rate per tola (PKR)")},text={OutlinedTextField(rateText,{rateText=it},label={Text("Rate")})},confirmButton={TextButton(onClick={rateText.toDoubleOrNull()?.takeIf{it>0}?.let{db.setRate(it);refresh++};rateDialog=false}){Text("Save")}},dismissButton={TextButton(onClick={rateDialog=false}){Text("Cancel")}})
    if(pinDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest={pinDialog=false},title={Text("Set / change PIN")},text={OutlinedTextField(newPin,{newPin=it},label={Text("4–8 digit PIN")})},confirmButton={TextButton(onClick={if(newPin.length in 4..8 && newPin.all{it.isDigit()}) {pin=newPin;savePin(newPin)};pinDialog=false}){Text("Save")}},dismissButton={TextButton(onClick={pinDialog=false}){Text("Cancel")}})
    }
}

@Composable fun SummaryCard(label:String,value:String) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(label,style=MaterialTheme.typography.titleMedium);Text(value,style=MaterialTheme.typography.headlineSmall) } } }
@Composable fun CustomerRow(c:Customer,onClick:()->Unit) { Card(Modifier.fillMaxWidth().padding(vertical=5.dp).clickable{onClick()}) { Column(Modifier.padding(14.dp)) { Text(c.name,style=MaterialTheme.typography.titleMedium);Text("ID: ${c.id}  ·  ${c.phone}");Text("Gold: ${"%.3f".format(c.gold)} g   |   Cash: PKR ${"%.2f".format(c.cash)}") } } }

@Composable
fun CustomerAccount(c:Customer,db:PosDb,rate:Double,padding:PaddingValues,onAdd:()->Unit,onBack:()->Unit) {
    val entries=db.entries(c.id)
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item { Text("‹ Customers",Modifier.clickable{onBack()},color=MaterialTheme.colorScheme.primary);Text(c.name,style=MaterialTheme.typography.headlineMedium);Text("Customer ID: ${c.id}") }
        item { SummaryCard("Gold balance",String.format(Locale.US,"%.3f g",c.gold)) }
        item { SummaryCard("Cash balance",String.format(Locale.US,"PKR %.2f",c.cash)) }
        item { Button(onClick=onAdd,modifier=Modifier.fillMaxWidth()){Text("Add transaction")} }
        item { Text("Transaction history",style=MaterialTheme.typography.titleLarge) }
        items(entries) { e -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text("${e.kind} · ${e.direction}",style=MaterialTheme.typography.titleMedium)
            Text(if(e.kind=="Gold") "${e.weight} g · PKR ${"%.2f".format(e.weight*e.rate/12.15)}" else "PKR ${"%.2f".format(e.amount)}")
            Text("${e.date}  ·  Rate/toIa: ${e.rate}"); if(e.note.isNotBlank()) Text(e.note)
        } } }
        if(entries.isEmpty()) item { Text("No transactions recorded.") }
    }
}

@Composable
fun CustomerDialog(close:()->Unit,save:(String,String,Double,Double)->Unit) {
    var name by remember{mutableStateOf("")};var phone by remember{mutableStateOf("")};var gold by remember{mutableStateOf("0")};var cash by remember{mutableStateOf("0")}
    AlertDialog(onDismissRequest=close,title={Text("Add customer")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(name,{name=it},label={Text("Customer name *")});OutlinedTextField(phone,{phone=it},label={Text("Mobile (optional)")})
        OutlinedTextField(gold,{gold=it},label={Text("Opening gold (grams)")});OutlinedTextField(cash,{cash=it},label={Text("Opening cash (PKR)")})
    }},confirmButton={TextButton(onClick={if(name.isNotBlank()) save(name.trim(),phone,gold.toDoubleOrNull()?:0.0,cash.toDoubleOrNull()?:0.0)}){Text("Save")}},dismissButton={TextButton(onClick=close){Text("Cancel")}})
}

@Composable
fun EntryDialog(customer:String,currentRate:Double,close:()->Unit,save:(String,String,Double,Double,Double,String)->Unit) {
    var kind by remember{mutableStateOf("Gold")};var direction by remember{mutableStateOf("Received")};var weight by remember{mutableStateOf("")};var amount by remember{mutableStateOf("")};var rate by remember{mutableStateOf(currentRate.toString())};var note by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=close,title={Text("Transaction · $customer")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row { listOf("Gold","Cash").forEach { FilterChip(selected=kind==it,onClick={kind=it},label={Text(it)},modifier=Modifier.padding(end=6.dp)) } }
        Row { listOf("Received","Given").forEach { FilterChip(selected=direction==it,onClick={direction=it},label={Text(it)},modifier=Modifier.padding(end=6.dp)) } }
        if(kind=="Gold") { OutlinedTextField(weight,{weight=it},label={Text("Weight (grams)")});OutlinedTextField(rate,{rate=it},label={Text("Rate per tola (PKR)")});Text("Amount: PKR ${"%.2f".format((weight.toDoubleOrNull()?:0.0)*(rate.toDoubleOrNull()?:0.0)/12.15)}") }
        else OutlinedTextField(amount,{amount=it},label={Text("Amount (PKR)")})
        OutlinedTextField(note,{note=it},label={Text("Notes")})
    }},confirmButton={TextButton(onClick={val w=weight.toDoubleOrNull()?:0.0;val a=amount.toDoubleOrNull()?:0.0;val r=rate.toDoubleOrNull()?:0.0;if((kind=="Gold"&&w>0&&r>0)||(kind=="Cash"&&a>0)) save(kind,direction,w,a,r,note)}){Text("Save")}},dismissButton={TextButton(onClick=close){Text("Cancel")}})
}
