import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.io.StdIn._

object Main extends App {
  args.headOption match {
    case Some(head) =>
      val bot = new TaskManagerBot(head)
      val eol = bot.run()
      println("Press [ENTER] to shutdown the bot, it may take a few seconds...")
      readLine()
      bot.shutdown() // initiate shutdown
      // Wait for the bot end-of-life
      Await.result(eol, Duration.Inf)
    case None => println("Bot can't work without token.")
  }
}
