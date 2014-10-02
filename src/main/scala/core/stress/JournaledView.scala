package core.stress

import akka.actor.ActorLogging
import akka.persistence.PersistentView
import org.joda.time.DateTime

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
class JournaledView extends PersistentView with ActorLogging {

  override def viewId = "journaled-view"

  override def persistenceId = "journaled-actor"

  var state: ViewState = ViewState(JournaledActorState.initial(), 0L)

  override def receive = {
    case newState: JournaledActorState if isPersistent =>
      log.info("Updating view with new state")
      val diffMs = DateTime.now().getMillis - newState.sendTime.getMillis
      this.state = ViewState(newState, diffMs)
    case ReadState => sender ! state
  }

  //override def autoUpdateInterval: FiniteDuration = 100 milliseconds
}

case object ReadState

case class ViewState(journaledState: JournaledActorState, recoveryTimeMs: Long)