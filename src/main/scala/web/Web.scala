package web

import core.{CoreActors, Core}
import api.Api
import akka.io.IO
import spray.can.Http

/**
 * Provides the web server (spray-can) for the REST api in ``Api``, using the actor system
 * defined in ``Core``.
 *
 * You may sometimes wish to construct separate ``ActorSystem`` for the web server machinery.
 * However, for this simple application, we shall use the same ``ActorSystem`` for the
 * entire application.
 *
 * Benefits of separate ``ActorSystem`` include the ability to use completely different
 * configuration, especially when it comes to the threading model.
 */
trait Web {
  this: App with Api with CoreActors with Core =>

  val restPort: Int = Option(System.getProperty("restPort")).getOrElse("8080").toInt

  IO(Http)(system) ! Http.Bind(rootService, "0.0.0.0", port = restPort)

}
