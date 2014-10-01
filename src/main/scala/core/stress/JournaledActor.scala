package core.stress

import akka.actor.ActorLogging
import akka.persistence.{Persistent, Processor}
import org.joda.time.DateTime

class JournaledActor extends Processor with ActorLogging {

  var state = JournaledActorState.initial()

  override def receive = {
    case Persistent(newState, seq) => updateState(newState)
    case ReadState => sender ! state
    case other => log.error(s"Unrecognized command: $other")
  }

  private def now() = new DateTime()

  private def updateState(newState: Any) {
    log.info("Updating state")
    this.state = newState.asInstanceOf[JournaledActorState]
    sender ! "persisted"
  }
}

case class JournaledActorState(number: Long, sendTime: DateTime)


object JournaledActorState {
  def initial() = JournaledActorState(0L, DateTime.now())
}
case class UpdateStateCommand(number: Long)

case object ReadState