package core

import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.cluster.Cluster
import akka.contrib.pattern.{ClusterSingletonManager, ClusterSingletonProxy}
import akka.pattern.ask
import com.typesafe.config.ConfigFactory
import core.stress.SimpleClusterListener.IsRemoved
import core.stress._
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration._
/**
 * Core is type containing the ``system: ActorSystem`` member. This enables us to use it in our
 * apps as well as in our tests.
 */
trait Core {

  implicit def system: ActorSystem

}

/**
 * This trait implements ``Core`` by starting the required ``ActorSystem`` and registering the
 * termination handler to stop the system when the JVM exits.
 */
trait BootedCore extends Core {
  this: App =>

  val log = LoggerFactory.getLogger(getClass)
  val nodePort: Int = Option(System.getProperty("nodePort")).getOrElse("2551").toInt

  val conf = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + nodePort)
    .withFallback(ConfigFactory.load())
    .withFallback(ConfigFactory.load("aws.conf"))

  /**
   * Construct the ActorSystem we will use in our application
   */
  implicit lazy val system = ActorSystem("ClusterSystem", conf)

  val listener = system.actorOf(Props(classOf[SimpleClusterListener]))

  /**
   * Ensure that the constructed ActorSystem is shut down when the JVM shuts down
   */
  sys.addShutdownHook({
    Cluster(system).leave(Cluster(system).selfAddress)
    blockUntilRemoved(10)
    system.shutdown()
  })

  implicit val timeout: akka.util.Timeout = 3 seconds
  import scala.concurrent.ExecutionContext.Implicits.global

  private def blockUntilRemoved(retriesLeft: Long): Unit = {
    if (retriesLeft > 0) {
      val futureResp = listener ? IsRemoved(Cluster(system).selfAddress)
      val dd = futureResp.map {
        resp => if (!resp.asInstanceOf[Boolean]) {
          Thread.sleep(1000)
          blockUntilRemoved(retriesLeft - 1)
        } else
        {
          log.info("Node removed itself from the cluster!")
        }
      }
      Await.result(dd, atMost = 3 seconds)
    }
  }

}

/**
 * This trait contains the actors that make up our application; it can be mixed in with
 * ``BootedCore`` for running code or ``TestKit`` for unit and integration tests.
 */
trait CoreActors {
  this: Core =>

  system.actorOf(
    ClusterSingletonManager.props(
      singletonProps = Props(classOf[JournaledActor]),
      singletonName = "writer",
      terminationMessage = PoisonPill,
      role = None), "singleton")

  val writer = system.actorOf(
    ClusterSingletonProxy.props(
      singletonPath = "/user/singleton/writer",
      role = None),
    name = "writerProxy")

  val reader = system.actorOf(Props[JournaledView])

  val reportCollector = system.actorOf(Props(new ReportCollector))
  val tester = system.actorOf(Props(new StressTester(writer, reader, reportCollector)))
}