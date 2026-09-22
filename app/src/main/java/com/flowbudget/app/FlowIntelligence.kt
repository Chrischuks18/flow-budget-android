package com.flowbudget.app
import java.util.*
import kotlin.math.*
data class SavingsGoal(val id:String=UUID.randomUUID().toString(),val name:String,val target:Double,val saved:Double=0.0)
data class SubscriptionHit(val name:String,val amount:Double,val occurrences:Int,val lastSeen:Long)
data class FlowHealth(val score:Int,val savingsRate:Int,val budgetUse:Int,val streak:Int)
object FlowIntelligence{
 fun categories(tx:List<Transaction>)=tx.filter{it.type==TxType.EXPENSE}.groupBy{it.category}.mapValues{it.value.sumOf{x->x.amount}}.toList().sortedByDescending{it.second}
 fun subscriptions(tx:List<Transaction>):List<SubscriptionHit>{val ex=tx.filter{it.type==TxType.EXPENSE};return ex.groupBy{(it.note.lowercase().replace(Regex("[0-9,.:]")," ").trim().take(28))+"|"+round(it.amount)}.mapNotNull{(_,v)->if(v.size>=2)SubscriptionHit(v.first().category+" • "+v.first().note.take(24),v.map{it.amount}.average(),v.size,v.maxOf{it.timestamp})else null}.sortedByDescending{it.occurrences}}
 fun streak(tx:List<Transaction>,daily:Double):Int{if(tx.isEmpty()||daily<=0)return 0;val fmt=java.text.SimpleDateFormat("yyyyMMdd",Locale.US);val days=tx.filter{it.type==TxType.EXPENSE}.groupBy{fmt.format(Date(it.timestamp))}.mapValues{it.value.sumOf{x->x.amount}};var s=0;val c=Calendar.getInstance();repeat(60){if((days[fmt.format(c.time)]?:0.0)<=daily)s++ else return s;c.add(Calendar.DAY_OF_YEAR,-1)};return s}
 fun health(tx:List<Transaction>,budget:Double):FlowHealth{val inc=tx.filter{it.type==TxType.INCOME}.sumOf{it.amount};val exp=tx.filter{it.type==TxType.EXPENSE}.sumOf{it.amount};val savings=if(inc>0)(((inc-exp)/inc)*100).roundToInt().coerceIn(0,100) else 0;val use=if(budget>0)((exp/budget)*100).roundToInt() else 100;val st=streak(tx,budget/30);val score=(45+savings/3+(100-use.coerceAtMost(100))/3+st.coerceAtMost(20)).coerceIn(1,100);return FlowHealth(score,savings,use,st)}
 fun runway(tx:List<Transaction>,future:List<FutureExpense>):String{val bal=tx.sumOf{if(it.type==TxType.INCOME)it.amount else -it.amount};val recent=tx.filter{it.type==TxType.EXPENSE&&it.timestamp>System.currentTimeMillis()-30L*86400000}.sumOf{it.amount}/30.0;val upcoming=future.filter{it.dueAt>System.currentTimeMillis()}.minByOrNull{it.dueAt}?:return "Add an upcoming bill to calculate your bill runway.";if(recent<=0)return "More spending history is needed for a runway forecast.";val spare=bal-upcoming.amount;val days=floor(spare/recent).toInt();return if(spare<0)"Your current recorded balance is already below the amount planned for "+upcoming.title+"." else "At your recent daily spending pace, the balance reserved for "+upcoming.title+" could be reached in about "+days.coerceAtLeast(0)+" days."}
 fun roundups(tx:List<Transaction>)=tx.filter{it.type==TxType.EXPENSE}.sumOf{ceil(it.amount)-it.amount}
}
