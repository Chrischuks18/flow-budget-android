package com.flowbudget.app
import android.content.Context
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.*
object StatementImporter{
 data class Result(val imported:Int,val skipped:Int,val message:String)
 fun importCsv(context:Context,uri:Uri,store:FinanceStore):Result{
  val name=queryName(context,uri)
  val bank=name.substringBefore(".").replace(Regex("(?i)(statement|account|transactions|csv|download)"),"").trim().ifBlank{"Bank statement"}
  val lines=context.contentResolver.openInputStream(uri)?.bufferedReader()?.readLines().orEmpty()
  if(lines.isEmpty())return Result(0,0,"The selected statement is empty.")
  val sep=if(lines.first().count{it==';'}>lines.first().count{it==','})';' else ','
  val headers=split(lines.first(),sep).map{it.trim().lowercase()}
  fun idx(vararg names:String)=headers.indexOfFirst{h->names.any{h.contains(it)}}
  val dateI=idx("date","transaction date","value date");val descI=idx("description","narration","details","remark","purpose","merchant");val debitI=idx("debit","withdrawal");val creditI=idx("credit","deposit");val amountI=idx("amount");val typeI=idx("type","transaction type");val accountI=idx("account","acct")
  var imported=0;var skipped=0
  lines.drop(1).forEach{line->runCatching{
   val p=split(line,sep);fun cell(i:Int)=if(i>=0&&i<p.size)p[i].trim() else ""
   val debit=num(cell(debitI));val credit=num(cell(creditI));val amount=num(cell(amountI));val typeText=cell(typeI).lowercase()
   val type=when{credit>0->TxType.INCOME;debit>0->TxType.EXPENSE;typeText.contains("credit")||typeText=="cr"->TxType.INCOME;typeText.contains("debit")||typeText=="dr"->TxType.EXPENSE;amount<0->TxType.EXPENSE;else->TxType.INCOME}
   val value=when{credit>0->credit;debit>0->debit;else->kotlin.math.abs(amount)}
   if(value<=0){skipped++;return@runCatching}
   val narration=cell(descI).ifBlank{"Statement transaction"};val account=cell(accountI).ifBlank{"Statement"}
   val t=Transaction(type=type,amount=value,category=purpose(narration,type),note=narration,timestamp=parseDate(cell(dateI)),source="Statement • "+name,bank=bank,account=account)
   if(store.addIfNew(t))imported++ else skipped++
  }.onFailure{skipped++}}
  return Result(imported,skipped,"Imported "+imported+" statement records. "+skipped+" duplicate or unreadable rows were skipped.")
 }
 fun purpose(text:String,type:TxType):String{val s=text.lowercase();return when{
  type==TxType.INCOME&&(listOf("salary","payroll","wage","allowance").any{s.contains(it)})->"Salary"
  type==TxType.INCOME&&(listOf("interest","dividend","refund","reversal").any{s.contains(it)})->"Income / Refund"
  listOf("airtime","data","mtn","airtel","glo","9mobile").any{s.contains(it)}->"Airtime & Data"
  listOf("uber","bolt","taxi","transport","fuel","petrol","filling station").any{s.contains(it)}->"Transport"
  listOf("restaurant","food","eatery","cafe","chicken","pizza","grocer","supermarket").any{s.contains(it)}->"Food & Groceries"
  listOf("electric","nepa","ekedc","ikedc","enugu disco","water","utility","dstv","gotv","startimes").any{s.contains(it)}->"Bills & Utilities"
  listOf("netflix","spotify","prime","subscription","renewal").any{s.contains(it)}->"Subscriptions"
  listOf("atm","cash withdrawal","withdrawal").any{s.contains(it)}->"Cash"
  listOf("fee","charge","levy","vat","stamp duty").any{s.contains(it)}->"Bank Charges"
  listOf("pos","purchase","merchant","shop","store").any{s.contains(it)}->"Shopping"
  listOf("transfer","trf","sent to","payment to").any{s.contains(it)}->if(type==TxType.INCOME)"Transfer In" else "Transfer Out"
  type==TxType.INCOME->"Other Income"
  else->"Other Expense"}}
 private fun num(s:String)=s.replace(Regex("[^0-9.\\-]"),"").toDoubleOrNull()?:0.0
 private fun parseDate(s:String):Long{for(f in listOf("dd/MM/yyyy","dd-MM-yyyy","yyyy-MM-dd","dd MMM yyyy","MMM dd, yyyy"))runCatching{return SimpleDateFormat(f,Locale.US).parse(s)?.time?:System.currentTimeMillis()};return System.currentTimeMillis()}
 private fun split(line:String,sep:Char):List<String>{val out=mutableListOf<String>();val b=StringBuilder();var q=false;line.forEach{ch->when{ch=='"'->q=!q;ch==sep&&!q->{out+=b.toString();b.clear()};else->b.append(ch)}};out+=b.toString();return out}
 private fun queryName(c:Context,u:Uri):String{var n="statement.csv";c.contentResolver.query(u,null,null,null,null)?.use{cur->val i=cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(i>=0&&cur.moveToFirst())n=cur.getString(i)};return n}
}