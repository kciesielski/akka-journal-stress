package core.stress

import akka.actor.{Actor, ActorLogging, ActorRef}
import scala.concurrent.duration._

class Updater(receiver: ActorRef) extends Actor with ActorLogging {

  var counter = 0L
  val Delay = 200.millis

  override def preStart() {
    super.preStart()
    self ! PerformUpdate
  }

  override def receive = {
    case PerformUpdate =>
      counter = counter + 1
      receiver ! UpdateStateCommand(counter)
      context.system.scheduler.scheduleOnce(Delay, self, PerformUpdate)
  }
}

case object PerformUpdate
