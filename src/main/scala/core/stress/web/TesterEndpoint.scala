package core.stress.web

import akka.actor.ActorRef
import akka.util.Timeout
import core.stress.{TesterReport, StartTest, GetFullReport}
import org.json4s.DefaultFormats
import org.json4s.Formats
import org.json4s.ext.JodaTimeSerializers
import spray.http.MediaTypes
import spray.httpx.Json4sJacksonSupport
import spray.routing.{Route, Directives}
import akka.pattern._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class TesterEndpoint(tester: ActorRef, reportCollector: ActorRef)(implicit executionContext: ExecutionContext)
  extends Directives with Json4sJacksonSupport {

  override implicit def json4sJacksonFormats: Formats = DefaultFormats ++ JodaTimeSerializers.all

  implicit val timeout: Timeout = 2.seconds

  def getJson(route: Route) = get {
    respondWithMediaType(MediaTypes.`application/json`) {
      route
    }
  }

  val route =
    path("testLoop") {
      post {
        handleWith {
          msg: StartTest => {
            tester ! msg
            "{}"
          }
        }
      }
    } ~
      path("report") {
        getJson {
          complete {
            implicit val marshaller = json4sMarshaller
            (reportCollector ? GetFullReport).mapTo[List[TesterReport]]
          }
        }
      }
}