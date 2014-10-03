package core

import akka.actor.{PoisonPill, Props, ActorSystem}
import akka.contrib.pattern.{ClusterSingletonProxy, ClusterSingletonManager}
import com.typesafe.config.ConfigFactory
import core.stress.{JournaledView, StressTester, ReportCollector, JournaledActor}

/**
 * Core is type containing the ``system: ActorSystem`` member. This enables us to use it in our
 * apps as well as in our tests.
 */
trait Core {

  def restPort: Int
  implicit def system: ActorSystem

}

/**
 * This trait implements ``Core`` by starting the required ``ActorSystem`` and registering the
 * termination handler to stop the system when the JVM exits.
 */
trait BootedCore extends Core {
  this: App =>

  val nodePortString: scala.Predef.String = List.fromArray(args, 0, args.length).toList.headOption.getOrElse(throw new IllegalArgumentException())
  val a: scala.Predef.String = "1234"
  val b = a.toInt
  val nodePort = nodePortString.toInt
  val restPortArg = args.view.toList.tail.headOption.getOrElse("8080").toInt

  def restPort = restPortArg

  val conf = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + nodePort)
    .withFallback(ConfigFactory.load())

  /**
   * Construct the ActorSystem we will use in our application
   */
  implicit lazy val system = ActorSystem("ClusterSystem", conf)

  /**
   * Ensure that the constructed ActorSystem is shut down when the JVM shuts down
   */
  sys.addShutdownHook(system.shutdown())

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
      role = None))

  val writer = system.actorOf(
    ClusterSingletonProxy.props(
      singletonPath = "/user/singleton/writer",
      role = None),
    name = "writerProxy")

  val reader = system.actorOf(Props[JournaledView])

  val reportCollector = system.actorOf(Props(new ReportCollector))
  val tester = system.actorOf(Props(new StressTester(writer, reader, reportCollector)))
}