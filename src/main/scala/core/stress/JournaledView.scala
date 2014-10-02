package core.stress

import akka.actor.ActorLogging
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.{Subscribe, SubscribeAck}
import akka.persistence.PersistentView
import org.joda.time.DateTime
class JournaledView extends PersistentView with ActorLogging {

  override def viewId = "journaled-view"

  override def persistenceId = "journaled-actor"

  var state: ViewState = ViewState(JournaledActorState.initial(), 0L)
  val mediator = DistributedPubSubExtension(context.system).mediator
  mediator ! Subscribe("topicName", self)

  override def receive = {
    case newState: JournaledActorState => processNewState(newState)
    case ReadState => sender ! state
    case SubscribeAck(Subscribe("topicName", None, `self`)) => log.info("Subscribed to events")
  }

  private def processNewState(newState: JournaledActorState) {
    if (this.state.journaledState.number + 1 == newState.number) {
      log.info("Updating view with new state")
      val diffMs = DateTime.now().getMillis - newState.sendTime.getMillis
      this.state = ViewState(newState, diffMs)
    }
    else if (this.state.journaledState.number + 1 < newState.number) {
      log.info("Restarting actor due to incosistent state.")
      context.stop(self)
    }
    else {
      log.info("Skipping state " + newState.number)
    }
  }

  override def autoUpdate = false
}

case object ReadState

case class ViewState(journaledState: JournaledActorState, recoveryTimeMs: Long)