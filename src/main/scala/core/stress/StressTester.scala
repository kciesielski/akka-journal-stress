package core.stress

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.persistence.Persistent
import org.joda.time.DateTime

class StressTester(journaledActor: ActorRef, reportCollector: ActorRef)
  extends Actor with ActorLogging {

  var loopCount = 1
  var maxLoops = 0
  var lastPersistReport: JournaledActorState = _

  override def receive = idle

  def idle: Receive = {
    case StartTest(expectedMaxLoops) =>
      this.maxLoops = expectedMaxLoops
      doStart()
    case other => log.error(s"Tester is idle but received $other. Start tests first!")
  }

  def working: Receive = {
    case "persisted" => doRead()
    case state: JournaledActorState => doCompare(state)
    case StartTest => log.error(s"Cannot start tester, already in progress! ($loopCount / $maxLoops)")
  }

  private def doStart() {
    log.debug("Starting stress test")
    context.become(working)
    doWrite()
  }

  private def doWrite() {
    log.debug("Executing write")
    val newState = JournaledActorState(loopCount, DateTime.now())
    this.lastPersistReport = newState
    journaledActor ! Persistent(newState)
  }

  private def doRead() {
    log.debug("Asking the reader node to read state")
    journaledActor ! ReadState
  }

  private def tryRepeatRead() {
    log.debug("Retrying read due to non-matching state")
    journaledActor ! ReadState
  }

  private def doCompare(readState: JournaledActorState) {
    log.info(s"Read state: ${readState.number}, last persisted = ${lastPersistReport.number}")
    if (readState.number != lastPersistReport.number) {
      reportCollector ! reportFailed(readState)
      tryRepeatRead()
    }
    else {
      reportCollector ! TesterReport(readState.number, readState.number, readState.sendTime)
      repeatFlowOrEnd()
    }
  }

  def reportFailed(readState: JournaledActorState): TesterReport =
    TesterReport(number = readState.number, expected = lastPersistReport.number, readState.sendTime)

  private def repeatFlowOrEnd() {
    if (loopCount == maxLoops) {
      log.info("Reached loop count, finishing")
      context.become(idle)
    } else {
      loopCount = loopCount + 1
      doWrite()
    }
  }
}

case class TesterReport(number: Long, expected: Long, msgCreationTime: DateTime)

case class StartTest(maxLoops: Int)