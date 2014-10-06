package core.stress

import akka.actor.ActorLogging
import akka.contrib.pattern.DistributedPubSubExtension
import akka.persistence.PersistentActor
import org.joda.time.DateTime
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
class JournaledActor extends PersistentActor with ActorLogging {

  var state = JournaledActorState.initial()
  val mediator = DistributedPubSubExtension(context.system).mediator
  var failFrequency = 50
  var failureCountdown = failFrequency

  import akka.contrib.pattern.DistributedPubSubMediator.Publish

  context.system.scheduler.schedule(2 seconds, 2 seconds, mediator, heartbeat())

  private def heartbeat() = Publish("topicName", WriterHeartbeat(state.number))

  override def receiveCommand = {
    case newState: JournaledActorState => updateState(newState)
    case SetFailFrequency(newFailFreq) =>
      this.failFrequency = newFailFreq
      resetFailCountdown()
    case other => log.error(s"Unrecognized command: $other")
  }

  private def updateState(newState: JournaledActorState) {
    log.info("Updating state")
    persistAsync(newState) {
      persistedState =>
        this.state = persistedState
        sender ! PersistConfirmation(newState.number)
        if (failureCountdown > 0) {
          mediator ! Publish("topicName", newState)
        }
        else
          resetFailCountdown()
        failureCountdown = failureCountdown - 1
    }
  }

  private def resetFailCountdown() {
    failureCountdown = failFrequency
  }

  override def receiveRecover = {
    case someState: JournaledActorState =>
      this.state = someState
      log.info(s"Recovered with state: $someState")
  }

  private def doBroadcastHeartbeat() {
    mediator ! Publish("topicName", WriterHeartbeat)

  }

  override def persistenceId = "journaled-actor"
}

case class JournaledActorState(number: Long, sendTime: DateTime)


object JournaledActorState {
  def initial() = JournaledActorState(0L, DateTime.now())
}
case class UpdateStateCommand(number: Long)

case class PersistConfirmation(number: Long)

case class SetFailFrequency(freq: Int)

case class WriterHeartbeat(lastSeq: Long)