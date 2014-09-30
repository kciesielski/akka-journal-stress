package core.stress

import akka.actor.ActorLogging
import akka.persistence.PersistentActor
import org.joda.time.DateTime

class JournaledActor extends PersistentActor with ActorLogging {

  var state = InMemoryActorState.initial

  override def persistenceId = "journaled-actor"

  override def receiveRecover = {
    case newState: JournaledActorState => updateState(newState)
  }

  override def receiveCommand = {
    case UpdateStateCommand(number) => doUpdate(number)
    case ReadState => sender ! state
  }

  private def doUpdate(number: Long) {
    val newState = JournaledActorState(number, now())
    persistAsync(newState) { persistedState =>
      updateState(persistedState)
      sender ! StatePersisted(persistedState)
    }
  }

  private def now() = new DateTime()

  private def updateState(newState: JournaledActorState) {
    val diffMillis = calculateRecoveryTime(newState)
    this.state = InMemoryActorState(newState.number, diffMillis)
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