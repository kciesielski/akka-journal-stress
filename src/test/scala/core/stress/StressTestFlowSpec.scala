package core.stress

import _root_.web.Web
import api.Api
import com.jayway.awaitility.scala.AwaitilitySupport
import core.{BootedCore, CoreActors}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization._
import org.scalatest.{FlatSpec, Matchers}
import spray.http.HttpEntity
import spray.http.MediaTypes._
import spray.routing.Directives
import spray.testkit.ScalatestRouteTest
import com.jayway.awaitility.Awaitility._

class StressTestFlowSpec extends FlatSpec with Matchers with Directives with ScalatestRouteTest with AwaitilitySupport {

  behavior of "Stress test"

  val app = new BootedCore with CoreActors with Api with Web

  implicit val formats = DefaultFormats

  it should "Match all trials for in-memory journal" in {
    // given
    val command = StartTest(10)

    // when
    Post("/testLoop", HttpEntity(`application/json`, write(command))) ~> app.testerRoute ~> check {
      status.intValue should equal(200)
      responseAs[String] should equal("\"{}\"")
    }
    // then
    var reports: List[TesterReport] = Nil

    await until {
      Get("/report") ~> app.testerRoute ~> check {
        status.intValue should equal(200)
        reports = read[List[TesterReport]](responseAs[String])
      }
      reports.length > 10
    }

    reports.foreach {
      report => report.number should equal(report.expected)
    }
  }
}
