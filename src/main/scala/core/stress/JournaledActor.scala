package core.stress

import akka.actor.ActorLogging
import akka.persistence.{Persistent, Processor}
import org.joda.time.DateTime

class JournaledActor extends Processor with ActorLogging {

  var state = InMemoryActorState.initial

  override def receive = {
    case Persistent(newState, seq) => updateState(newState)
    case ReadState => sender ! state
    case other => log.error(s"Unrecognized command: $other")
  }

  private def now() = new DateTime()

  private def updateState(newState: Any) {
    log.info("Updating state")
//    val diffMillis = calculateRecoveryTime(newState)
    this.state = InMemoryActorState(newState.asInstanceOf[UpdateStateCommand].number, 0L) // TODO...
    sender ! StatePersisted(JournaledActorState(state.number, new DateTime()))
  }

  private def calculateRecoveryTime(newState: JournaledActorState) = {
    now().getMillis - newState.persistenceTime.getMillis
  }
}

case class JournaledActorState(number: Long, persistenceTime: DateTime)

case class InMemoryActorState(number: Long, recoveryTimeInMs: Long)

object InMemoryActorState {
  def initial = InMemoryActorState(0L, 0L)
}

case class UpdateStateCommand(number: Long)

case class StatePersisted(state: JournaledActorState)

case object ReadState