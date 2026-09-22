package com.flowbudget.app
import android.content.Context
import java.util.UUID
enum class TxType{INCOME,EXPENSE}
data class Transaction(val id:String=UUID.randomUUID().toString(),val type:TxType,val amount:Double,val category:String,val note:String,val timestamp:Long=System.currentTimeMillis(),val source:String="Manual",val bank:String="Manual",val account:String="General")
data class FutureExpense(val id:String=UUID.randomUUID().toString(),val title:String,val amount:Double,val dueAt:Long,val account:String="All accounts",val reminder:Boolean=true)
class FinanceStore(context:Context){
 private val prefs=context.getSharedPreferences("flow_budget",Context.MODE_PRIVATE)
 fun transactions():List<Transaction> = prefs.getStringSet("tx",emptySet()).orEmpty().mapNotNull(::decode).sortedByDescending{it.timestamp}
 fun add(t:Transaction){val s=prefs.getStringSet("tx",emptySet()).orEmpty().toMutableSet();s.add(encode(t));prefs.edit().putStringSet("tx",s).apply()}
 fun addIfNew(t:Transaction):Boolean{val key=t.type.toString()+"|"+t.amount+"|"+(t.timestamp/60000)+"|"+t.source;if(transactions().any{it.type.toString()+"|"+it.amount+"|"+(it.timestamp/60000)+"|"+it.source==key})return false;add(t);return true}
 fun allowedSenders():Set<String> = prefs.getStringSet("senders",emptySet()).orEmpty()
 fun addSender(s:String){val x=allowedSenders().toMutableSet();x.add(s.trim().uppercase());prefs.edit().putStringSet("senders",x).apply()}
 fun removeSender(s:String){val x=allowedSenders().toMutableSet();x.remove(s);prefs.edit().putStringSet("senders",x).apply()}
 fun budget():Double=prefs.getFloat("budget",200000f).toDouble()
 fun setBudget(v:Double){prefs.edit().putFloat("budget",v.toFloat()).apply()}
 fun premium():Boolean=prefs.getBoolean("premium",false)
 fun setPremium(v:Boolean){prefs.edit().putBoolean("premium",v).apply()}
 fun futureExpenses():List<FutureExpense> = prefs.getStringSet("future",emptySet()).orEmpty().mapNotNull(::decodeFuture).sortedBy{it.dueAt}
 fun addFuture(f:FutureExpense){val s=prefs.getStringSet("future",emptySet()).orEmpty().toMutableSet();s.add(listOf(f.id,clean(f.title),f.amount.toString(),f.dueAt.toString(),clean(f.account),f.reminder.toString()).joinToString("¦"));prefs.edit().putStringSet("future",s).apply()}
 fun removeFuture(id:String){val s=prefs.getStringSet("future",emptySet()).orEmpty().filterNot{it.startsWith(id+"¦")}.toSet();prefs.edit().putStringSet("future",s).apply()}
 private fun decodeFuture(s:String):FutureExpense?=runCatching{val p=s.split("¦");FutureExpense(p[0],p[1],p[2].toDouble(),p[3].toLong(),p[4],p[5].toBoolean())}.getOrNull()
 private fun encode(t:Transaction)=listOf(t.id,t.type.name,t.amount.toString(),clean(t.category),clean(t.note),t.timestamp.toString(),clean(t.source),clean(t.bank),clean(t.account)).joinToString("¦")
 private fun decode(s:String):Transaction?=runCatching{val p=s.split("¦");Transaction(p[0],TxType.valueOf(p[1]),p[2].toDouble(),p[3],p[4],p[5].toLong(),p[6],p.getOrElse(7){bankFromSource(p[6])},p.getOrElse(8){"General"})}.getOrNull()
 private fun bankFromSource(s:String)=s.substringAfter("SMS • ","Manual")
 private fun clean(s:String)=s.replace("¦"," ")
}
