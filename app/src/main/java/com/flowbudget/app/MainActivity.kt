package com.flowbudget.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MaterialTheme{FlowBudgetApp(FinanceStore(this))}}}
}
@Composable fun FlowBudgetApp(store:FinanceStore){
 var tab by remember{mutableIntStateOf(0)}; var refresh by remember{mutableIntStateOf(0)}; refresh
 val tx=store.transactions(); val income=tx.filter{it.type==TxType.INCOME}.sumOf{it.amount}; val expense=tx.filter{it.type==TxType.EXPENSE}.sumOf{it.amount}
 Scaffold(bottomBar={NavigationBar{listOf("Home" to Icons.Default.Home,"Transactions" to Icons.Default.ReceiptLong,"Banks" to Icons.Default.AccountBalance,"Budget" to Icons.Default.PieChart,"Premium" to Icons.Default.Star).forEachIndexed{i,p->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(p.second,null)},label={Text(p.first)})}}}){pad->
  Box(Modifier.padding(pad).fillMaxSize()){when(tab){0->Dashboard(income,expense,store.budget(),tx);1->Transactions(store,tx){refresh++};2->Banks(store){refresh++};3->Budget(store,expense){refresh++};else->Premium(store){refresh++}}}
 }
}
fun money(v:Double)=NumberFormat.getCurrencyInstance(Locale("en","NG")).format(v)
@Composable fun Header(title:String,subtitle:String){Column(Modifier.padding(bottom=20.dp)){Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable fun Dashboard(income:Double,expense:Double,budget:Double,tx:List<Transaction>){LazyColumn(Modifier.fillMaxSize().padding(20.dp)){item{Header("Flow Budget","Know where your money goes.");ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp)){Text("Available balance");Text(money(income-expense),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);Spacer(Modifier.height(16.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Income\n"+money(income));Text("Expenses\n"+money(expense))}}};Spacer(Modifier.height(16.dp));Text("Monthly budget",fontWeight=FontWeight.Bold);LinearProgressIndicator(progress={if(budget<=0)0f else (expense/budget).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().padding(vertical=8.dp));Text(money(expense)+" of "+money(budget));Spacer(Modifier.height(20.dp));Text("Recent activity",fontWeight=FontWeight.Bold)};items(tx.take(5)){TxRow(it)}}}
@Composable fun TxRow(t:Transaction){ListItem(headlineContent={Text(t.category,fontWeight=FontWeight.SemiBold)},supportingContent={Text(t.note+" • "+t.source)},trailingContent={Text((if(t.type==TxType.INCOME)"+ " else "- ")+money(t.amount))})}
@Composable fun Transactions(store:FinanceStore,tx:List<Transaction>,changed:()->Unit){var show by remember{mutableStateOf(false)};Column(Modifier.fillMaxSize().padding(20.dp)){Header("Transactions","Manual entries and bank alerts");Button(onClick={show=true}){Icon(Icons.Default.Add,null);Text(" Add transaction")};Spacer(Modifier.height(8.dp));LazyColumn{items(tx){t->TxRow(t);HorizontalDivider()}}};if(show)AddDialog(onDismiss={show=false}){store.add(it);show=false;changed()}}
@Composable fun AddDialog(onDismiss:()->Unit,onAdd:(Transaction)->Unit){var amount by remember{mutableStateOf("")};var note by remember{mutableStateOf("")};var income by remember{mutableStateOf(false)};AlertDialog(onDismissRequest=onDismiss,title={Text("New transaction")},text={Column{OutlinedTextField(amount,{amount=it},label={Text("Amount (₦)")});OutlinedTextField(note,{note=it},label={Text("Note")});Row(verticalAlignment=Alignment.CenterVertically){Switch(income,{income=it});Text(" Income")}}},confirmButton={Button(onClick={amount.toDoubleOrNull()?.let{onAdd(Transaction(type=if(income)TxType.INCOME else TxType.EXPENSE,amount=it,category=if(income)"Income" else "Other",note=note))}}){Text("Save")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})}
@Composable fun Banks(store:FinanceStore,changed:()->Unit){var sender by remember{mutableStateOf("")};val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){};Column(Modifier.fillMaxSize().padding(20.dp)){Header("Bank SMS","Only process senders you approve");Button(onClick={permission.launch(arrayOf(Manifest.permission.READ_SMS,Manifest.permission.RECEIVE_SMS))}){Text("Grant SMS permission")};Spacer(Modifier.height(16.dp));OutlinedTextField(sender,{sender=it},label={Text("Bank sender ID or number")},supportingText={Text("Enter the exact sender shown in your SMS app")});Button(onClick={if(sender.isNotBlank()){store.addSender(sender);sender="";changed()}}){Text("Allow sender")};Spacer(Modifier.height(20.dp));Text("Allowed senders",fontWeight=FontWeight.Bold);store.allowedSenders().forEach{s->ListItem(headlineContent={Text(s)},trailingContent={IconButton(onClick={store.removeSender(s);changed()}){Icon(Icons.Default.Delete,null)}})}}}
@Composable fun Budget(store:FinanceStore,expense:Double,changed:()->Unit){var value by remember{mutableStateOf(store.budget().toLong().toString())};Column(Modifier.padding(20.dp)){Header("Budget","Set a monthly spending target");OutlinedTextField(value,{value=it},label={Text("Monthly budget (₦)")});Button(onClick={value.toDoubleOrNull()?.let{store.setBudget(it);changed()}}){Text("Save budget")};Spacer(Modifier.height(24.dp));Text("Spent: "+money(expense));Text("Remaining: "+money((store.budget()-expense).coerceAtLeast(0.0)))}}
@Composable fun Premium(store:FinanceStore,changed:()->Unit){val premium=store.premium();Column(Modifier.padding(20.dp)){Header(if(premium)"Flow Premium" else "Upgrade to Premium","More control, deeper insights.");Text("Basic",fontWeight=FontWeight.Bold);Text("• Manual transactions\n• Bank SMS import\n• Monthly budget\n• Basic dashboard");Spacer(Modifier.height(20.dp));Text("Premium",fontWeight=FontWeight.Bold);Text("• Multiple accounts\n• Unlimited budgets & goals\n• Advanced analytics\n• Recurring transactions\n• PDF/CSV reports\n• Cloud-sync readiness");Spacer(Modifier.height(20.dp));Button(onClick={store.setPremium(!premium);changed()}){Text(if(premium)"Switch to Basic (test)" else "Try Premium (test mode)")};Text("Subscription billing will be connected before production release.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=8.dp))}}
