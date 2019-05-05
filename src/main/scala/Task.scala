import com.github.nscala_time.time.Imports._

case class Task(title: String, expire: DateTime, remind: DateTime) extends Serializable
