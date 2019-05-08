import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

import cats.instances.future._
import cats.syntax.functor._
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.api.declarative.Commands
import com.bot4s.telegram.clients.FutureSttpClient
import com.bot4s.telegram.future.Polling
import com.bot4s.telegram.future.TelegramBot
import com.bot4s.telegram.models.Message
import slogging.{LogLevel, LoggerConfig, PrintLoggerFactory}
import com.softwaremill.sttp.okhttp.OkHttpFutureBackend
import com.github.nscala_time.time.Imports._
import com.github.nscala_time.time.Imports.DateTime.now
import org.joda.time.Minutes._

import scala.collection.mutable
import scala.concurrent.Future

class TaskManagerBot(val token: String, val file: String) extends TelegramBot
  with Polling
  with PerChatState[mutable.Set[Task]]
  with Commands[Future] {

  LoggerConfig.factory = PrintLoggerFactory()
  LoggerConfig.level = LogLevel.TRACE

  implicit val backend = OkHttpFutureBackend()
  override val client: RequestHandler[Future] = new FutureSttpClient(token)
  private var scheduler: ScheduledFuture[_] = null

  def initScheduler(implicit msg: Message) = withChatState {
    case Some(tasks) =>
      if(scheduler != null) scheduler.cancel(true)
      scheduler = Executors.newScheduledThreadPool(10).scheduleAtFixedRate(() => {
        for (task <- tasks) {
          if(now > task.expire && now.getHourOfDay > 7 && now.getHourOfDay < 24 && now.getMinuteOfHour < 10) reply {
            s"""
               |Задача просрочена!
               |${task.title}
               |Должна была быть выполнена до ${task.expire}
            """.stripMargin}.void
          if(task.remind <= now && now <= task.remind + minutes(10)) reply {
            s"""
               |Напоминание о задаче!
               |${task.title}
               |Должна быть выполнена до ${task.expire}
            """.stripMargin}.void
        }
      }, 0L, 10L, TimeUnit.MINUTES)
      monad.pure(())
    case _ => reply("Tasks doesn't found.").void
  }

  onMessage { implicit msg =>
    if (scheduler == null || scheduler.isCancelled) {
      initScheduler
      reply("Scheduler restored").void
    } else monad.pure(())
  }

  onCommand("init" | "start"){ implicit msg =>
    withChatState {
      case None =>
        setChatState(mutable.Set.empty[Task])
        initScheduler
        reply("TaskManager Bot initialized").void
      case Some(_) =>
        initScheduler
        reply("TaskManager Bot already initialized. For reinitialization please use /reinit").void
    }
  }

  onCommand("reinit" | "clean"){ implicit msg =>
    setChatState(mutable.Set.empty[Task])
    scheduler.cancel(true)
    initScheduler
    reply("TaskManager Bot reinitialized").void
  }

  onCommand("add" | "edit"){ implicit msg =>
    withArgs {
      case message if message.length >= 3 => withChatState {
        case Some(tasks) => try {
          val task = Task(
            title = message.drop(2).mkString(" "),
            remind = DateTime.parse(message(1)),
            expire = DateTime.parse(message.head))

          tasks += task

          reply(
            s"""
               |Задача: ${task.title}
               |Дата завершения задачи: ${task.expire}
               |Напоминание установлено на: ${task.remind}
            """.stripMargin).void
        } catch {
          case e: Exception => reply(s"Failed because: ${e.getMessage}").void
        }
        case None => reply("ERROR! Chat state -- broken. Please /init bot or /reinit. \nFor advanced help contact to administrator.").void
      }
      case _ => reply("Invalid argumentヽ(ಠ_ಠ)ノ").void
    }
  }

  onCommand("delete" | "complete"){ implicit msg =>
    withArgs{
      case message => withChatState {
        case Some(tasks) =>
          val title = message.mkString(" ")
          tasks.filter(_.title == title).headOption match {
            case Some(task) =>
              tasks.remove(task)
              reply(s"Deleted: $title").void
            case None =>
              reply("Task not exists").void
          }
        case None => reply("ERROR! Chat state -- broken. Please /init bot or /reinit. \nFor advanced help contact to administrator.").void
      }
      case _ => reply("Invalid argumentヽ(ಠ_ಠ)ノ").void
    }
  }

  onCommand("list"){ implicit msg =>
    withChatState {
      case Some(tasks) => reply(s"Список задач: \n${tasks.map(_.title).mkString(";\n")}").void
      case None => reply("ERROR! Chat state -- broken. Please /init bot or /reinit. \nFor advanced help contact to administrator.").void
    }
  }

  onCommand("help"){ implicit msg =>
    reply("Я сосал, меня ебали, так вот ботов мы писали.").void
  }
}
