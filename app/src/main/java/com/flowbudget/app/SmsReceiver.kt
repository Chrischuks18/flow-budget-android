package com.flowbudget.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver: BroadcastReceiver() {
 override fun onReceive(context:Context,intent:Intent) {
  if(intent.action!=Telephony.Sms.Intents.SMS_RECEIVED_ACTION)return
  val store=FinanceStore(context); val allowed=store.allowedSenders()
  Telephony.Sms.Intents.getMessagesFromIntent(intent).groupBy{it.originatingAddress.orEmpty()}.forEach{(sender,parts)->
   val normalized=sender.trim().uppercase()
   if(allowed.none{normalized==it || normalized.contains(it)}) return@forEach
   val body=parts.joinToString(""){it.messageBody.orEmpty()}
   BankSmsParser.parse(body,sender)?.let(store::add)
  }
 }
}
object BankSmsParser {
 private val amount=Regex("""(?i)(?:NGN|₦|N)\s*([0-9][0-9,]*(?:\.\d{1,2})?)""")
 fun parse(body:String,sender:String):Transaction? {
  val lower=body.lowercase()
  val type=when {
   Regex("""\b(debit|debited|dr)\b""",RegexOption.IGNORE_CASE).containsMatchIn(body)->TxType.EXPENSE
   Regex("""\b(credit|credited|cr)\b""",RegexOption.IGNORE_CASE).containsMatchIn(body)->TxType.INCOME
   else->return null
  }
  val value=amount.find(body)?.groupValues?.get(1)?.replace(",","")?.toDoubleOrNull()?:return null
  val category=if(type==TxType.INCOME)"Bank Credit" else when {"airtime" in lower || "data" in lower -> "Airtime & Data"; "pos" in lower -> "Shopping"; "transfer" in lower -> "Transfer"; else -> "Bank Debit"}
  return Transaction(type=type,amount=value,category=category,note="Imported from bank alert",source="SMS • "+sender)
 }
}
