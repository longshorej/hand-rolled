import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RouteResult
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.{DebuggingDirectives, LogEntry}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

sealed trait AppMode

object AppMode {
  case object Always200 extends AppMode
}

object App {
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("http-server")
    implicit val executionContext: ExecutionContext = system.dispatcher

    val mode = args.lift(0) match {
      case Some("always-200") =>
        AppMode.Always200

      case other =>
        throw new IllegalArgumentException(s"Unknown Mode: $other")
    }

    val routeLogic = mode match {
      case AppMode.Always200 =>
        complete(StatusCodes.OK -> "http://whatever")
    }

    val routeLogger: HttpRequest => RouteResult => Option[LogEntry] =
      req => {
        case RouteResult.Complete(resp) =>
          Some(LogEntry(s"${req.method.name} ${req.uri}: ${resp.status}", Logging.InfoLevel))
        case _ =>
          None
      }

    val route = DebuggingDirectives.logRequestResult(routeLogger)(routeLogic)

    Http()
      .bindAndHandle(route, "localhost", 8000)
      .onComplete {
        case Success(binding) =>
          println(s"HTTP server running on ${binding.localAddress}")

        case Failure(reason) =>
          system.terminate()
      }
  }
}
