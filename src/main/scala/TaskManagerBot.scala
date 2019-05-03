import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import cats.instances.future._
import cats.syntax.functor._
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.api.declarative.Commands
import com.bot4s.telegram.clients.FutureSttpClient
import com.bot4s.telegram.future.Polling
import com.bot4s.telegram.future.TelegramBot
import slogging.{LogLevel, LoggerConfig, PrintLoggerFactory}
import com.softwaremill.sttp.okhttp.OkHttpFutureBackend
import com.github.nscala_time.time.Imports._
import com.github.nscala_time.time.Imports.DateTime.now

import scala.collection.mutable
import scala.concurrent.Future

class TaskManagerBot(val token: String) extends TelegramBot
  with Polling
  with PerChatState[mutable.Set[String]]
  with Commands[Future] {

  LoggerConfig.factory = PrintLoggerFactory()
  LoggerConfig.level = LogLevel.TRACE

  implicit val backend = OkHttpFutureBackend()
  override val client: RequestHandler[Future] = new FutureSttpClient(token)

  val sc = Executors.newScheduledThreadPool(10)
  val emptyRunnable = () => ()

  onCommand("init" | "start"){ implicit msg =>
    withChatState{
      case None =>
        setChatState(mutable.Set.empty[String])
        reply("TaskManager Bot initialized").void
      case Some(_) =>
        reply("TaskManager Bot already initialized. For reinitialization please use /reinit").void
    }
  }

  onCommand("reinit" | "clean"){ implicit msg =>
    setChatState(mutable.Set.empty[String])
    reply("TaskManager Bot reinitialized").void
  }

  onCommand("add" | "edit"){ implicit msg =>
    withArgs {
      case message if message.length >= 3 => withChatState {
        case Some(tasks) =>
          try {
            val date = DateTime.parse(message.head)
            val remainder = DateTime.parse(message(1))
            val task = message.drop(2).mkString(" ")

            tasks += task

            val remainderDuration = Duration.millis((now to remainder).toDurationMillis).toScalaDuration

            sc.schedule(() =>
              if (tasks contains task) reply {
                s"""
                   |Напоминание о задаче!
                   |$task
                """.stripMargin}.void, remainderDuration.length, remainderDuration.unit)

            val estimateDuration = Duration.millis((now to date).toDurationMillis).toScalaDuration

            sc.schedule(() =>
              if (tasks contains task) {
                def loop: Unit = sc.schedule(() => if (tasks contains task) {
                  reply {
                    s"""
                       |Задача просрочена!
                       |$task
                    """.stripMargin
                  }.void
                  loop
                } else emptyRunnable, 1L, TimeUnit.HOURS)

                loop
                emptyRunnable
              }, estimateDuration.length, estimateDuration.unit)


            reply(
              s"""
                 |Задача: $task
                 |Дата завершения задачи: $date
                 |Напоминание установлено на: $remainder
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
          val task = message.mkString(" ")
          tasks -= message.mkString(" ")
          reply(s"Deleted: $task").void
        case None => reply("ERROR! Chat state -- broken. Please /init bot or /reinit. \nFor advanced help contact to administrator.").void
      }
      case _ => reply("Invalid argumentヽ(ಠ_ಠ)ノ").void
    }
  }

  onCommand("list"){ implicit msg =>
    withChatState {
      case Some(tasks) => reply(s"Список задач:\n ${tasks.mkString(";\n")}").void
      case None => reply("ERROR! Chat state -- broken. Please /init bot or /reinit. \nFor advanced help contact to administrator.").void
    }
  }

  onCommand("help"){ implicit msg =>
    reply("Я сосал, меня ебали, так вот ботов мы писали.").void
  }
}
