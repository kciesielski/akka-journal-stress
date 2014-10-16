package core.stress

import akka.actor.ActorLogging
import akka.contrib.pattern.{DistributedPubSubMediator, DistributedPubSubExtension}
import akka.persistence.{SaveSnapshotSuccess, SaveSnapshotFailure, SnapshotOffer, PersistentActor}
import org.joda.time.DateTime
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
class JournaledActor extends PersistentActor with ActorLogging {

  var state = JournaledActorState.initial()
  val mediator = DistributedPubSubExtension(context.system).mediator
  var failFrequency = 50
  val SnapshotFrequency = 500
  var failureCountdown = failFrequency

  import DistributedPubSubMediator.Publish

  scheduleNextHeartbeat()

  private def scheduleNextHeartbeat() {
    context.system.scheduler.scheduleOnce(2 seconds, self, SendHeartbeat)
  }

  private def heartbeat() = Publish("topicName", WriterHeartbeat(state.number))

  override def receiveCommand = {
    case newState: JournaledActorState => updateState(newState)
    case SendHeartbeat =>
      mediator ! heartbeat()
      scheduleNextHeartbeat()
    case SaveSnapshotSuccess(metadata)         =>
      log.info("Snapshot persisted")
    case SaveSnapshotFailure(metadata, reason) =>
      log.error("Could not persist snapshot")
    case SetFailFrequency(newFailFreq) =>
      this.failFrequency = newFailFreq
      resetFailCountdown()
    case other => log.error(s"Unrecognized command: $other")
  }

  private def updateState(newState: JournaledActorState) {
    log.info("Updating state")
    persistAsync(newState) {
      persistedState =>
        snapshotIfNeeded(newState)
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

  def snapshotIfNeeded(newState: JournaledActorState) {
    if (newState.number % SnapshotFrequency == 0) {
      log.info("Persisting snapshot")
      saveSnapshot(newState)
    }
  }

  private def resetFailCountdown() {
    failureCountdown = failFrequency
  }

  override def receiveRecover = {
    case SnapshotOffer(_, stateSnapshot: JournaledActorState) =>
      log.info("Recovering from snapshot")
      this.state = stateSnapshot
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

case object SendHeartbeat