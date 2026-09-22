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
   type==TxType.INCOME&&has("interest credit","capitalized interest","dividend","investment return")->"Investment Income"
   has("commission","charge + vat","nip charge","sms alert charge","sms charge","stamp duty","witholding tax","withholding tax","maintenance fee","account maintenance","transfer fee","transaction fee","bank charge","vat mobile trf","fgn stamp")->"Bank Charges"
   has("mtn","airtel","glo","9mobile","airtime","data bundle","data purchase","mobile bills pymt")->"Airtime & Data"
   has("lab test","treatment","medication","hospital","clinic","pharmacy","chemist","medical","laboratory","health")->"Health & Medical"
   has("fuel","fueling","petrol","diesel","uber","bolt","indrive","taxi","transport","bus ticket","flight","air peace","arik","filling station","total energies","nnpc")->"Transport & Fuel"
   has("food","moimoi","pop corn","popcorn","groc","grocery","grocer","restaurant","eatery","cafe","coffee","chicken republic","kfc","dominos","pizza","supermarket","shoprite","provision")->"Food & Groceries"
   has("duplex printer","printer","toner","rj45","office tv","ipad","computer","laptop","router","headlamp")->"Office & Equipment"
   has("blocks","fillers","lectern","beautification","tanker paymt for water","building material","cement","paint","construction","renovation")->"Building & Maintenance"
   has("haircut","perfume","shoes","sports wear","sports canvas","boutique","clothing","fashion","salon")->"Personal Care & Clothing"
   has("mass booking","burial levy","chipins","church","parish","tithe","offering","donation","charity")->"Church & Giving"
   has("wedding","thanks for coming","thank you for coming","event","ceremony")->"Events & Hospitality"
   has("up keep","upkeep","family support")->"Family & Support"
   has("ikeja electric","ikedc","eko electric","ekedc","eedc","enugu electricity","nepa","electricity","prepaid meter","water bill","utility","dstv","gotv","startimes","internet bill","wifi")->"Bills & Utilities"
   has("netflix","spotify","youtube premium","apple com bill","google storage","prime video","showmax","subscription","renewal"," sub ")->"Subscriptions"
   has("school fees","tuition","university","college","academy","course","exam fee","waec","jamb")->"Education"
   has("hotel","guest house","booking com","airbnb","lodge")->"Travel & Accommodation"
   has("rent","landlord","house rent","accommodation rent")->"Rent & Housing"
   has("insurance","premium payment")->"Insurance"
   has("atm withdrawal","cash withdrawal","cash wd","withdrawal at atm")->"Cash Withdrawal"
   has("bet9ja","sportybet","betking","betway","gaming")->"Betting & Gaming"
   has("loan repayment","loan payment","credit repayment")->"Loan Repayment"
   has("investment","mutual fund","treasury bill","stock purchase")->"Savings & Investments"
   has("pos","web purchase","card purchase","merchant payment","purchase at","shopping","store")->"Shopping & POS"
   has("transfer","trf","nip","nibss","sent to","payment to","fund transfer","onb trf","mobile trf")->if(type==TxType.INCOME)"Transfers In" else "Transfers Out"
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
 private fun parseDate(s:String):Long{for(f in listOf("dd/MM/yyyy","dd-MM-yyyy","dd-MMM-yy","dd/MM/yy","dd-MM-yy","yyyy-MM-dd","dd MMM yyyy","MMM dd, yyyy"))runCatching{return SimpleDateFormat(f,Locale.US).parse(s)?.time?:System.currentTimeMillis()};return System.currentTimeMillis()}
 private fun split(line:String,sep:Char):List<String>{val out=mutableListOf<String>();val b=StringBuilder();var q=false;line.forEach{ch->when{ch=='"'->q=!q;ch==sep&&!q->{out+=b.toString();b.clear()};else->b.append(ch)}};out+=b.toString();return out}
 private fun queryName(c:Context,u:Uri):String{var n="statement.csv";c.contentResolver.query(u,null,null,null,null)?.use{cur->val i=cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(i>=0&&cur.moveToFirst())n=cur.getString(i)};return n}
 private fun importPdf(context:Context,uri:Uri,store:FinanceStore):Result{
  PDFBoxResourceLoader.init(context);val name=queryName(context,uri);val bytes=context.contentResolver.openInputStream(uri)?.readBytes()?:return Result(0,0,"Could not read PDF.")
  val text=PDDocument.load(bytes).use{doc->PDFTextStripper().apply{sortByPosition=true}.getText(doc)}
  val bank=detectBank(name+" "+text.take(4000));val account=detectAccount(text)
  val dateStart=Regex("""^\s*(\d{1,2}[/\-](?:\d{1,2}|[A-Za-z]{3})[/\-]\d{2,4})\b""",RegexOption.IGNORE_CASE)
  val moneyRx=Regex("""(?<!\d)(?:NGN|₦)?\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.\d{2})|[0-9]+\.\d{2})(?!\d)""",RegexOption.IGNORE_CASE)
  val lines=text.lines().map{it.trim()}.filter{it.isNotBlank()}
  val blocks=mutableListOf<Pair<String,StringBuilder>>();var current:StringBuilder?=null;var currentDate=""
  for(line in lines){val m=dateStart.find(line);if(m!=null){currentDate=m.groupValues[1];current=StringBuilder(line);blocks+=currentDate to current}else if(current!=null&&!isPdfFooter(line)){current.append(" ").append(line)}}
  val opening=findOpeningBalance(text,moneyRx);var previousBalance=opening;var imported=0;var skipped=0
  for((date,buf) in blocks){
   val raw=buf.toString().replace(Regex("\\s+")," ").trim()
   if(raw.contains("TOTALS",true)||raw.contains("TOTAL (CLEARED",true))continue
   val values=moneyRx.findAll(raw).mapNotNull{m->num(m.groupValues[1]).takeIf{it>=0}}.toList()
   if(values.isEmpty()){skipped++;continue}
   val balance=values.last()
   var type:TxType?=null;var amount=0.0
   if(previousBalance!=null){
    val delta=balance-previousBalance
    if(kotlin.math.abs(delta)>=0.005){type=if(delta>0)TxType.INCOME else TxType.EXPENSE;amount=kotlin.math.abs(delta)}
   }
   if(type==null||amount<=0){
    val lower=raw.lowercase()
    type=when{
     bank=="Fidelity Bank"&&Regex("""\b(pay in|credit)\b""").containsMatchIn(lower)->TxType.INCOME
     lower.contains("capitalized interest credit")||lower.contains("trf from")||lower.contains("credited")->TxType.INCOME
     else->TxType.EXPENSE
    }
    amount=values.dropLast(1).firstOrNull{it>0}?:0.0
   }
   previousBalance=balance
   if(amount<=0||raw.contains("opening balance",true)){continue}
   val narration=cleanPdfNarration(raw,date,moneyRx).ifBlank{"Statement transaction"}
   val t=Transaction(type=type,amount=amount,category=purpose(narration,type),note=narration,timestamp=parseDate(date),source="Statement • "+name,bank=bank,account=account)
   if(store.addIfNew(t))imported++ else skipped++
  }
  val layout=when(bank){"Access Bank"->"Access Debit/Credit";"Fidelity Bank"->"Fidelity Pay In/Pay Out";"Zenith Bank"->"Zenith Debit/Credit";else->"balance-aware"}
  return Result(imported,skipped,bank+" PDF analyzed with "+layout+" parsing: "+imported+" transactions imported. Narrations were retained for spending analysis.",bank,account)
 }
 private fun findOpeningBalance(text:String,moneyRx:Regex):Double?{
  val r=Regex("""(?i)opening\s+balance\s*[:\-]?\s*(?:NGN|₦)?\s*([0-9,]+\.\d{2})""").find(text)
  if(r!=null)return num(r.groupValues[1])
  val line=text.lines().firstOrNull{it.contains("Opening Balance",true)}?:return null
  return moneyRx.findAll(line).map{num(it.groupValues[1])}.lastOrNull()
 }
 private fun isPdfFooter(line:String):Boolean{val s=line.lowercase();return s.matches(Regex("""\d+\s+of\s+\d+"""))||s.contains("alertz verification")||s.contains("how to verify")}
 private fun cleanPdfNarration(raw:String,date:String,moneyRx:Regex):String{
  var s=raw.replaceFirst(date,"").trim()
  s=s.replace(Regex("""^\s*\d{1,2}[/\-](?:\d{1,2}|[A-Za-z]{3})[/\-]\d{2,4}\s*""",RegexOption.IGNORE_CASE),"")
  val matches=moneyRx.findAll(s).toList()
  if(matches.size>=2){val cut=matches[matches.size-2].range.first;s=s.substring(0,cut)}
  return s.replace(Regex("""(?i)\b(NIP Transfer|Online Banking|Others)\b""")," ").replace(Regex("\\s+")," ").trim(' ','-','|')
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