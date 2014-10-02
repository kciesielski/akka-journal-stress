package core.stress

import akka.actor.{Actor, ActorLogging, ActorRef}
import org.joda.time.DateTime

class StressTester(writer: ActorRef, reader: ActorRef, reportCollector: ActorRef)
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
    case state: ViewState => doCompare(state)
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
    writer ! newState
  }

  private def doRead() {
    log.debug("Spawning a tester child to start reading")
    reader ! ReadState
  }

  private def tryRepeatRead() {
//    log.debug("Retrying read due to non-matching state")
    Thread.sleep(10)
    reader ! ReadState
  }

  private def doCompare(readState: ViewState) {
//    log.info(s"Read state: ${readState.journaledState.number}, last persisted = ${lastPersistReport.number}")
    if (readState.journaledState.number != lastPersistReport.number) {
      reportCollector ! reportFailed(readState)
      tryRepeatRead()
    }
    else {
      reportCollector ! TesterReport(lastPersistReport.number, lastPersistReport.number,
        readState.journaledState.sendTime, readState.recoveryTimeMs)
      repeatFlowOrEnd()
    }
  }

  def reportFailed(readState: ViewState): TesterReport =
    TesterReport(number = readState.journaledState.number, expected = lastPersistReport.number,
      readState.journaledState.sendTime, 0L)

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

case class TesterReport(number: Long, expected: Long, msgCreationTime: DateTime, recoveryMs: Long)

case class StartTest(maxLoops: Int)