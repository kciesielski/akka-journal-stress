package core.stress

import akka.actor.{Actor, ActorLogging, ActorRef}

class StressTester(updater: ActorRef, secondaryReader: ActorRef, reportCollector: ActorRef)
  extends Actor with ActorLogging {

  var loopCount = 0
  var maxLoops = 0
  var lastPersistReport: StatePersisted = _

  override def receive = idle

  def idle: Receive = {
    case StartTest(expectedMaxLoops) =>
      this.maxLoops = expectedMaxLoops
      doStart()
    case other: _ => log.error(s"Tester is idle but received $other. Start tests first!")
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
    updater ! UpdateStateCommand(loopCount)
  }

  private def doRead(persistReport: StatePersisted) {
    log.debug("Asking the reader node to read state")
    this.lastPersistReport = persistReport
    secondaryReader ! ReadState
  }

  private def tryRepeatRead() {
    log.debug("Retrying read due to non-matching state")
    secondaryReader ! ReadState
  }

  private def doCompare(readState: InMemoryActorState) {
    log.info(s"Read state: ${readState.number}, recovered in ${readState.recoveryTimeInMs}, last persisted = ${lastPersistReport.state.number}")
    if (readState.number != lastPersistReport.state.number) {
      reportCollector ! ReportNonMatching(expected = lastPersistReport.state.number, found = readState.number)
      tryRepeatRead()
    }
    else {
      reportCollector ! ReportMatching(readState.number, readState.recoveryTimeInMs)
      repeatFlowOrEnd()
    }
  }

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

trait TesterReport

case class ReportMatching(number: Long, recoveryTimeMs: Long) extends TesterReport

case class ReportNonMatching(expected: Long, found: Long) extends TesterReport

case class StartTest(maxLoops: Int)