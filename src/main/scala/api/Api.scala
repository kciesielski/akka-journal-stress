package api

import core.stress.web.TesterEndpoint
import core.{CoreActors, Core}
import akka.actor.Props
import spray.routing.RouteConcatenation

/**
 * The REST API layer. It exposes the REST services, but does not provide any
 * web server interface.<br/>
 * Notice that it requires to be mixed in with ``core.CoreActors``, which provides access
 * to the top-level actors that make up the system.
 */
trait Api extends RouteConcatenation {
  this: CoreActors with Core =>

  private implicit val _ = system.dispatcher

  val testerRoute = new TesterEndpoint(tester, reportCollector).route
  val routes = testerRoute


  val rootService = system.actorOf(Props(new RoutedHttpService(routes)))
}
