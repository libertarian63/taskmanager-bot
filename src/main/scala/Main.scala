import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.io.StdIn._

object Main extends App {
  args match {
    case Array(token, file) =>
      val bot = new TaskManagerBot(token, file)
      val eol = bot.run()
      println("Press [ENTER] to shutdown the bot, it may take a few seconds...")
      readLine()
      bot.shutdown() // initiate shutdown
      // Wait for the bot end-of-life
      Await.result(eol, Duration.Inf)
      println("Bot stopped.")
    case _ => println("Bot can't work without token or file.")
  }
}
