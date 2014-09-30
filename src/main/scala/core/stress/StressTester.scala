package core.stress

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.persistence.Persistent

class StressTester(journaledActor: ActorRef, reportCollector: ActorRef)
  extends Actor with ActorLogging {

  var loopCount = 1
  var maxLoops = 0
  var lastPersistReport: StatePersisted = _

  override def receive = idle

  def idle: Receive = {
    case StartTest(expectedMaxLoops) =>
      this.maxLoops = expectedMaxLoops
      doStart()
    case other => log.error(s"Tester is idle but received $other. Start tests first!")
  }

  def working: Receive = {
    case persistReport: StatePersisted => doRead(persistReport)
    case state: InMemoryActorState => doCompare(state)
    case StartTest => log.error(s"Cannot start tester, already in progress! ($loopCount / $maxLoops)")
  }

  private def doStart() {
    log.debug("Starting stress test")
    context.become(working)
    doWrite()
  }

  private def doWrite() {
    log.debug("Executing write")
    journaledActor ! Persistent(UpdateStateCommand(loopCount))
  }

  private def doRead(persistReport: StatePersisted) {
    log.debug("Asking the reader node to read state")
    this.lastPersistReport = persistReport
    journaledActor ! ReadState
  }

  private def tryRepeatRead() {
    log.debug("Retrying read due to non-matching state")
    journaledActor ! ReadState
  }

  private def doCompare(readState: InMemoryActorState) {
    log.info(s"Read state: ${readState.number}, recovered in ${readState.recoveryTimeInMs}, " +
      s"last persisted = ${lastPersistReport.state.number}")
    if (readState.number != lastPersistReport.state.number) {
      reportCollector ! reportFailed(readState)
      tryRepeatRead()
    }
    else {
      reportCollector ! TesterReport(readState.number, readState.number, readState.recoveryTimeInMs)
      repeatFlowOrEnd()
    }
  }

  def reportFailed(readState: InMemoryActorState): TesterReport =
    TesterReport(number = readState.number, expected = lastPersistReport.state.number, readState.recoveryTimeInMs)

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

case class TesterReport(number: Long, expected: Long, recoveryTimeMs: Long)

case class StartTest(maxLoops: Int)