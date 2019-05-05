import com.bot4s.telegram.models.Message

import scala.collection.mutable
import scala.concurrent.Future

trait PerChatState[S] {
  import java.io._

  val file: String

  private val chatState: mutable.Map[Long, S] = {
    try {
      val input = new ObjectInputStream(new FileInputStream(file))
      input.readObject().asInstanceOf[mutable.Map[Long, S]]
    } catch {
      case _: FileNotFoundException =>
        println("Couldn't find that file. Create new file.")
        mutable.Map[Long, S]()
    }
  }

  private def save(): Unit ={
    val stream = new ObjectOutputStream(new FileOutputStream(file))
    try {
      stream.writeObject(chatState)
    } catch {
      case e: Exception =>
        val writer = new StringWriter
        e.printStackTrace(new PrintWriter(writer))
    } finally {
      stream.close()
    }
  }

  def setChatState(value: S)(implicit msg: Message): Unit = atomic {
    chatState.update(msg.chat.id, value)
    save()
  }

  def clearChatState(implicit msg: Message): Unit = atomic {
    chatState.remove(msg.chat.id)
    save()
  }

  private def atomic[T](f: => T): T = chatState.synchronized {
    f
  }

  def withChatState(f: Option[S] => Future[Unit])(implicit msg: Message) = {
    val result = f(getChatState)
    save()
    result
  }

  def getChatState(implicit msg: Message): Option[S] = atomic {
    chatState.get(msg.chat.id)
  }
}