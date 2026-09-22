package com.flowbudget.app
import android.content.Context
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
object StatementImporter{
 data class Result(val imported:Int,val skipped:Int,val message:String,val bank:String="",val account:String="")
 fun importAny(context:Context,uri:Uri,store:FinanceStore):Result{val name=queryName(context,uri);return when(name.substringAfterLast(".","").lowercase()){ "pdf"->importPdf(context,uri,store); "xlsx"->importXlsx(context,uri,store); "csv","txt"->importCsv(context,uri,store); else->Result(0,0,"Unsupported statement format. Choose PDF, XLSX or CSV.") }}
 fun importCsv(context:Context,uri:Uri,store:FinanceStore):Result{
  val name=queryName(context,uri)
  val bank=detectBank(name)
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
  return Result(imported,skipped,"Imported "+imported+" statement records. "+skipped+" duplicate or unreadable rows were skipped.",bank)
 }
 fun purpose(text:String,type:TxType):String{
  val s=text.lowercase().replace(Regex("[^a-z0-9 ]")," ")
  fun has(vararg words:String)=words.any{s.contains(it)}
  return when{
   type==TxType.INCOME&&has("salary","payroll","wage","allowance","stipend")->"Salary & Allowance"
   type==TxType.INCOME&&has("refund","reversal","reversed","chargeback")->"Refunds & Reversals"
   type==TxType.INCOME&&has("interest","dividend","investment return")->"Investment Income"
   has("mtn","airtel","glo","9mobile","airtime","data bundle","data purchase")->"Airtime & Data"
   has("uber","bolt","indrive","taxi","transport","bus ticket","flight","air peace","arik","fuel","petrol","diesel","filling station","total energies","nnpc","mobil station")->"Transport & Fuel"
   has("restaurant","eatery","cafe","coffee","chicken republic","kfc","dominos","pizza","food","grocer","supermarket","shoprite","market","provision")->"Food & Groceries"
   has("ikeja electric","ikedc","eko electric","ekedc","eedc","enugu electricity","nepa","electricity","prepaid meter","water bill","utility","dstv","gotv","startimes","internet bill","wifi")->"Bills & Utilities"
   has("netflix","spotify","youtube premium","apple com bill","google storage","prime video","showmax","subscription","renewal")->"Subscriptions"
   has("hospital","clinic","pharmacy","chemist","medical","laboratory","lab test","health")->"Health & Medical"
   has("school fees","tuition","university","college","academy","course","exam fee","waec","jamb")->"Education"
   has("hotel","guest house","booking com","airbnb","lodge")->"Travel & Accommodation"
   has("church","parish","tithe","offering","donation","charity")->"Giving & Donations"
   has("rent","landlord","house rent","accommodation rent")->"Rent & Housing"
   has("insurance","premium payment")->"Insurance"
   has("atm withdrawal","cash withdrawal","cash wd","withdrawal at atm")->"Cash Withdrawal"
   has("stamp duty","sms alert charge","maintenance fee","account maintenance","transfer fee","transaction fee","bank charge","vat on fee","levy")->"Bank Charges"
   has("bet9ja","sportybet","betking","betway","gaming")->"Betting & Gaming"
   has("pos","web purchase","card purchase","merchant payment","purchase at","shopping","boutique","store")->"Shopping & POS"
   has("loan repayment","loan payment","credit repayment")->"Loan Repayment"
   has("investment","mutual fund","treasury bill","stock purchase")->"Savings & Investments"
   has("transfer","trf","nip","nibss","sent to","payment to","fund transfer")->if(type==TxType.INCOME)"Transfers In" else "Transfers Out"
   type==TxType.INCOME->"Other Income"
   else->"Other Expense"
  }
 }
 fun purposeDetail(text:String,category:String):String{
  val clean=text.replace(Regex("\\s+")," ").trim()
  val markers=listOf("uber","bolt","indrive","mtn","airtel","glo","netflix","spotify","dstv","gotv","shoprite","chicken republic","kfc","dominos","air peace","showmax","amazon","google","apple","pharmacy","hospital","hotel","school","university","rent","fuel","petrol","diesel")
  val found=markers.firstOrNull{clean.contains(it,true)}
  return if(found!=null) found.split(" ").joinToString(" "){it.replaceFirstChar(Char::uppercaseChar)} else clean.take(90).ifBlank{category}
 }
 private fun num(s:String)=s.replace(Regex("[^0-9.\\-]"),"").toDoubleOrNull()?:0.0
 private fun parseDate(s:String):Long{for(f in listOf("dd/MM/yyyy","dd-MM-yyyy","yyyy-MM-dd","dd MMM yyyy","MMM dd, yyyy"))runCatching{return SimpleDateFormat(f,Locale.US).parse(s)?.time?:System.currentTimeMillis()};return System.currentTimeMillis()}
 private fun split(line:String,sep:Char):List<String>{val out=mutableListOf<String>();val b=StringBuilder();var q=false;line.forEach{ch->when{ch=='"'->q=!q;ch==sep&&!q->{out+=b.toString();b.clear()};else->b.append(ch)}};out+=b.toString();return out}
 private fun queryName(c:Context,u:Uri):String{var n="statement.csv";c.contentResolver.query(u,null,null,null,null)?.use{cur->val i=cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(i>=0&&cur.moveToFirst())n=cur.getString(i)};return n}
 private fun importPdf(context:Context,uri:Uri,store:FinanceStore):Result{
  PDFBoxResourceLoader.init(context);val name=queryName(context,uri);val bytes=context.contentResolver.openInputStream(uri)?.readBytes()?:return Result(0,0,"Could not read PDF.")
  val text=PDDocument.load(bytes).use{PDFTextStripper().getText(it)};val bank=detectBank(name+" "+text.take(2500));val account=detectAccount(text)
  var imported=0;var skipped=0
  val dateRx=Regex("""\b(\d{1,2}[/\-]\d{1,2}[/\-]\d{2,4}|\d{1,2}\s+[A-Za-z]{3}\s+\d{4})\b""")
  val amountRx=Regex("""(?:NGN|₦|N)?\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.\d{2})|[0-9]+\.\d{2})""",RegexOption.IGNORE_CASE)
  text.lines().forEach{raw->val line=raw.trim();val dm=dateRx.find(line)?:return@forEach;val amounts=amountRx.findAll(line).mapNotNull{num(it.groupValues[1]).takeIf{x->x>0}}.toList();if(amounts.isEmpty())return@forEach
   val narration=line.replace(dm.value,"").replace(amountRx," ").replace(Regex("\\s+")," ").trim();val lower=line.lowercase();val type=when{Regex("\\b(cr|credit|credited|deposit)\\b").containsMatchIn(lower)->TxType.INCOME;Regex("\\b(dr|debit|debited|withdrawal|purchase|pos)\\b").containsMatchIn(lower)->TxType.EXPENSE;else->TxType.EXPENSE};val value=amounts.first()
   val t=Transaction(type=type,amount=value,category=purpose(narration,type),note=narration.ifBlank{"PDF statement transaction"},timestamp=parseDate(dm.value),source="Statement • "+name,bank=bank,account=account)
   if(store.addIfNew(t))imported++ else skipped++}
  return Result(imported,skipped,"PDF analyzed: "+imported+" transactions imported. Review the Activity page to verify automatically interpreted rows.",bank,account)
 }
 private fun importXlsx(context:Context,uri:Uri,store:FinanceStore):Result{
  val name=queryName(context,uri);val bytes=context.contentResolver.openInputStream(uri)?.readBytes()?:return Result(0,0,"Could not read Excel file.");val entries=mutableMapOf<String,ByteArray>();ZipInputStream(ByteArrayInputStream(bytes)).use{z->var e=z.nextEntry;while(e!=null){if(!e.isDirectory)entries[e.name]=z.readBytes();e=z.nextEntry}}
  val shared=entries["xl/sharedStrings.xml"]?.let{xmlStrings(it)}?:emptyList();val sheet=entries.entries.firstOrNull{it.key.startsWith("xl/worksheets/sheet")&&it.key.endsWith(".xml")}?.value?:return Result(0,0,"No worksheet was found in this XLSX file.")
  val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(sheet));val rows=doc.getElementsByTagName("row");val table=mutableListOf<List<String>>()
  for(i in 0 until rows.length){val cells=(rows.item(i) as org.w3c.dom.Element).getElementsByTagName("c");val row=mutableListOf<String>();for(j in 0 until cells.length){val ce=cells.item(j) as org.w3c.dom.Element;val ref=ce.getAttribute("r");val col=ref.takeWhile{it.isLetter()}.fold(0){a,ch->a*26+(ch.uppercaseChar()-'A'+1)}-1;while(row.size<=col)row.add("");val v=ce.getElementsByTagName("v");val raw=if(v.length>0)v.item(0).textContent else "";row[col]=if(ce.getAttribute("t")=="s")shared.getOrElse(raw.toIntOrNull()?:-1){raw}else raw};table+=row}
  return importTable(table,name,store)
 }
 private fun importTable(table:List<List<String>>,name:String,store:FinanceStore):Result{
  if(table.isEmpty()) return Result(0,0,"Excel statement is empty.")
  var headerIndex=table.indexOfFirst{row->row.any{cell->cell.lowercase().contains("date")} && row.any{cell->val v=cell.lowercase();v.contains("debit")||v.contains("credit")||v.contains("amount")}}
  if(headerIndex<0) headerIndex=0
  val headers=table[headerIndex].map{it.lowercase()}
  fun findIndex(vararg names:String):Int=headers.indexOfFirst{header->names.any{key->header.contains(key)}}
  val dateIndex=findIndex("date");val narrationIndex=findIndex("description","narration","details","remark","purpose");val debitIndex=findIndex("debit","withdrawal");val creditIndex=findIndex("credit","deposit");val amountIndex=findIndex("amount");val accountIndex=findIndex("account","acct");val bank=detectBank(name)
  var imported=0;var skipped=0
  for(row in table.drop(headerIndex+1)){
   fun cell(index:Int):String=if(index>=0&&index<row.size)row[index] else ""
   val debit=num(cell(debitIndex));val credit=num(cell(creditIndex));val amount=num(cell(amountIndex))
   val type=if(credit>0)TxType.INCOME else TxType.EXPENSE
   val value=when{credit>0->credit;debit>0->debit;else->kotlin.math.abs(amount)}
   if(value<=0){skipped++;continue}
   val note=cell(narrationIndex).ifBlank{"Excel statement transaction"}
   val transaction=Transaction(type=type,amount=value,category=purpose(note,type),note=note,timestamp=parseExcelDate(cell(dateIndex)),source="Statement • "+name,bank=bank,account=cell(accountIndex).ifBlank{"Statement"})
   if(store.addIfNew(transaction)) imported++ else skipped++
  }
  return Result(imported,skipped,"Excel analyzed: "+imported+" transactions imported.",bank)
 }
 private fun xmlStrings(bytes:ByteArray):List<String>{val d=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(bytes));val nodes=d.getElementsByTagName("si");return (0 until nodes.length).map{i->nodes.item(i).textContent}}
 private fun parseExcelDate(s:String):Long{val n=s.toDoubleOrNull();return if(n!=null&&n>20000)((n-25569.0)*86400000.0).toLong() else parseDate(s)}
 private fun detectAccount(text:String):String{val r=Regex("""(?i)(?:account|acct|a/c)\s*(?:no|number|#|:)?.{0,8}([0-9Xx*]{4,20})""").find(text);return r?.groupValues?.getOrNull(1)?.let{maskAccount(it)}?:"Statement"}
 private fun maskAccount(a:String):String{val d=a.filter{it.isDigit()};return if(d.length>=4)"••••"+d.takeLast(4) else a.takeLast(8)}
 private fun detectBank(text:String):String{val s=text.lowercase();val banks=listOf("Access Bank" to listOf("access bank","accessbank"),"GTBank / GTCO" to listOf("gtbank","gtco","guaranty trust"),"Zenith Bank" to listOf("zenith"),"FirstBank" to listOf("firstbank","first bank"),"UBA" to listOf("united bank for africa"," uba "),"Fidelity Bank" to listOf("fidelity"),"FCMB" to listOf("fcmb","first city monument"),"Stanbic IBTC" to listOf("stanbic"),"Sterling Bank" to listOf("sterling"),"Union Bank" to listOf("union bank"),"Wema Bank" to listOf("wema","alat"),"Kuda" to listOf("kuda"),"OPay" to listOf("opay"),"PalmPay" to listOf("palmpay"),"Moniepoint" to listOf("moniepoint"));return banks.firstOrNull{(_,k)->k.any{s.contains(it)}}?.first?:text.substringBefore(".").replace(Regex("(?i)(statement|account|transactions|download|csv|xlsx|pdf)"),"").trim().ifBlank{"Bank statement"}}

}