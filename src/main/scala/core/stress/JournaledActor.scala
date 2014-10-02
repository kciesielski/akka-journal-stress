package core.stress

import akka.actor.ActorLogging
import akka.contrib.pattern.{DistributedPubSubMediator, DistributedPubSubExtension}
import akka.persistence.{PersistentActor, Persistent, Processor}
import org.joda.time.DateTime

class JournaledActor extends PersistentActor with ActorLogging {

  var state = JournaledActorState.initial()
  val mediator = DistributedPubSubExtension(context.system).mediator
  var failureCountdown = 50

  import DistributedPubSubMediator.Publish

  override def receiveCommand = {
    case newState: JournaledActorState => updateState(newState)
    case other => log.error(s"Unrecognized command: $other")
  }

  private def now() = new DateTime()

  private def updateState(newState: JournaledActorState) {
    log.info("Updating state")
    persistAsync(newState) {
      persistedState =>
        this.state = persistedState
        sender ! "persisted"
        if (failureCountdown > 0) {
          mediator ! Publish("topicName", newState)
        }
        else {
          failureCountdown = 50
        }
        failureCountdown = failureCountdown - 1
    }
  }

  override def receiveRecover = {
    case someState: JournaledActorState =>
      this.state = someState
      log.info(s"Recovered with state: $someState")
  }

  override def persistenceId = "journaled-actor"
}

case class JournaledActorState(number: Long, sendTime: DateTime)


object JournaledActorState {
  def initial() = JournaledActorState(0L, DateTime.now())
}
case class UpdateStateCommand(number: Long)

