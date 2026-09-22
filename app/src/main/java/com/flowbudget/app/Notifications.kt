package com.flowbudget.app
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
object FlowNotifications{
 const val CHANNEL="flow_budget_reminders"
 fun ensure(context:Context){if(Build.VERSION.SDK_INT>=26){val c=NotificationChannel(CHANNEL,"Budget reminders",NotificationManager.IMPORTANCE_DEFAULT).apply{description="Future spending and budget reminders"};(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(c)}}
 fun schedule(context:Context,f:FutureExpense){if(!f.reminder)return;ensure(context);val intent=Intent(context,ReminderReceiver::class.java).putExtra("title",f.title).putExtra("amount",f.amount);val pi=PendingIntent.getBroadcast(context,f.id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val alarm=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager;val whenAt=(f.dueAt-24*60*60*1000).coerceAtLeast(System.currentTimeMillis()+5000);alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,whenAt,pi)}
 fun show(context:Context,title:String,body:String,id:Int){ensure(context);if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;val n=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true).build();NotificationManagerCompat.from(context).notify(id,n)}
}
class ReminderReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){val title=intent.getStringExtra("title")?:"Planned expense";val amount=intent.getDoubleExtra("amount",0.0);FlowNotifications.show(context,"Upcoming: "+title,money(amount)+" is planned for tomorrow.",title.hashCode())}}
