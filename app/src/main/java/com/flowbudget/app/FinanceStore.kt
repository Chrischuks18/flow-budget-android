package com.flowbudget.app

import android.content.Context
import java.util.UUID

enum class TxType { INCOME, EXPENSE }
data class Transaction(val id:String=UUID.randomUUID().toString(), val type:TxType, val amount:Double, val category:String, val note:String, val timestamp:Long=System.currentTimeMillis(), val source:String="Manual")

class FinanceStore(context: Context) {
 private val prefs=context.getSharedPreferences("flow_budget",Context.MODE_PRIVATE)
 fun transactions():List<Transaction> = prefs.getStringSet("tx", emptySet()).orEmpty().mapNotNull(::decode).sortedByDescending{it.timestamp}
 fun add(t:Transaction){ val set=prefs.getStringSet("tx",emptySet()).orEmpty().toMutableSet(); set.add(encode(t)); prefs.edit().putStringSet("tx",set).apply() }
 fun delete(id:String){ prefs.edit().putStringSet("tx",transactions().filterNot{it.id==id}.map(::encode).toSet()).apply() }
 fun allowedSenders():Set<String> = prefs.getStringSet("senders", emptySet()).orEmpty()
 fun addSender(s:String){ val set=allowedSenders().toMutableSet(); set.add(s.trim().uppercase()); prefs.edit().putStringSet("senders",set).apply() }
 fun removeSender(s:String){ val set=allowedSenders().toMutableSet(); set.remove(s); prefs.edit().putStringSet("senders",set).apply() }
 fun budget():Double=prefs.getFloat("budget",200000f).toDouble()
 fun setBudget(v:Double){prefs.edit().putFloat("budget",v.toFloat()).apply()}
 fun premium():Boolean=prefs.getBoolean("premium",false)
 fun setPremium(v:Boolean){prefs.edit().putBoolean("premium",v).apply()}
 private fun encode(t:Transaction)=listOf(t.id,t.type.name,t.amount.toString(),clean(t.category),clean(t.note),t.timestamp.toString(),clean(t.source)).joinToString("¦")
 private fun decode(s:String):Transaction?=runCatching{val p=s.split("¦"); Transaction(p[0],TxType.valueOf(p[1]),p[2].toDouble(),p[3],p[4],p[5].toLong(),p[6])}.getOrNull()
 private fun clean(s:String)=s.replace("¦"," ")
}
