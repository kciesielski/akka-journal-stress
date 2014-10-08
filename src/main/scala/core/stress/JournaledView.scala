package core.stress

import akka.actor.ActorLogging
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.{Subscribe, SubscribeAck}
import akka.persistence.{PersistentView, SnapshotOffer}
import org.joda.time.DateTime
class JournaledView extends PersistentView with ActorLogging {

  // Yes - we want it that way.
  override def viewId = persistenceId

  override def persistenceId = "journaled-actor"

  var states: Map[Long, ViewState] = Map.empty
  var lastProcessedNumber = 0L

  val mediator = DistributedPubSubExtension(context.system).mediator
  mediator ! Subscribe("topicName", self)

  override def receive = {
    case newState: JournaledActorState => processNewState(newState)
    case SnapshotOffer(_, stateSnapshot: JournaledActorState) =>
      log.info("Recovering view from snapshot")
      this.lastProcessedNumber = stateSnapshot.number - 1 // dirty way to pretend we have all previous states...
      processNewState(stateSnapshot)
    case ReadState(expectedNumber) => checkState(expectedNumber)
    case WriterHeartbeat(seqNo) => doCheckHeartbeat(seqNo)
    case SubscribeAck(Subscribe("topicName", None, `self`)) => log.info("Subscribed to events")
  }

  private def checkState(expectedNumber: Long) {
    val lastStateOpt = states.get(lastProcessedNumber)
    val expectedStateOpt = states.get(expectedNumber)
    val lastStateSendTimeOpt = lastStateOpt.map(_.journaledState.sendTime)
    val response = expectedStateOpt.getOrElse(MissingState(lastProcessedNumber, lastStateSendTimeOpt))
    sender ! response
  }

  private def doCheckHeartbeat(writerSeqNo: Long) {
    if (writerSeqNo != lastProcessedNumber) {
      throw new IllegalStateException("Restarting actor due to incosistent state from the heartbeat " +
        s"($writerSeqNo != $lastProcessedNumber)")
    }
  }

  private def processNewState(newState: JournaledActorState) {
    if (lastProcessedNumber + 1 == newState.number) {
      log.info("Updating view with new state")
      val diffMs = DateTime.now().getMillis - newState.sendTime.getMillis
      this.states = this.states + (newState.number -> ViewState(newState, diffMs))
      lastProcessedNumber = newState.number
    }
    else if (lastProcessedNumber + 1 < newState.number) {
      log.info("Restarting actor due to incosistent state.")
      throw new IllegalStateException("restarting actor")
    }
    else {
      log.info("Skipping state " + newState.number)
    }
  }

  override def autoUpdate = false
}

case class ReadState(expectedNumber: Long)

trait ViewStateResponse

case class ViewState(journaledState: JournaledActorState, recoveryTimeMs: Long) extends ViewStateResponse

case class MissingState(lastNumber: Long, sendTimeOpt: Option[DateTime]) extends ViewStateResponse
