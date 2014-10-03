package core.stress

import akka.actor._
import org.joda.time.DateTime

class StressTester(writer: ActorRef, reader: ActorRef, reportCollector: ActorRef)
  extends Actor with ActorLogging {

  var currentNumber = 0
  var maxLoops = 0
  var workingChildren: Map[Long, ActorRef] = Map.empty
  var finishedChildrenCount = 0

  override def receive = idle

  def idle: Receive = {
    case StartTest(expectedMaxLoops) =>
      this.maxLoops = expectedMaxLoops
      doStart()
    case other => log.error(s"Tester is idle but received $other. Start tests first!")
  }

  def working: Receive = {
    case PersistConfirmation(persistedNumber) => startReading(persistedNumber)
    case FinishedReading(readNumber) => finishWorker(readNumber)
    case StartTest => log.error(s"Cannot start tester, already in progress! ($currentNumber / $maxLoops)")
  }

  private def doStart() {
    log.debug("Starting stress test")
    context.become(working)
    doWrite()
  }

  private def doWrite() {
    log.debug("Executing write")
    if (currentNumber < maxLoops) {
      currentNumber = currentNumber + 1
      val newState = JournaledActorState(currentNumber, DateTime.now())
      writer ! newState
    }
    else log.info(s"Written all $currentNumber states, waiting for reads to finish.")
  }

  private def finishWorker(number: Long) {
    log.info(s"Tester notified that child $number finished reading")
    sender ! PoisonPill
    workingChildren = workingChildren - number
    finishedChildrenCount = finishedChildrenCount + 1
    if (finishedChildrenCount == maxLoops) {
      log.info("Reached loop count, finishing")
      context become idle
    }
  }
  private def startReading(number: Long) {
    log.debug("Spawning a tester child to start reading")
    val newChild = context.actorOf(Props(new StressTestReaderWorker(number, reader, reportCollector)))
    workingChildren = workingChildren + (number -> newChild)
    newChild ! "start"
    doWrite()
  }

}

case class TesterReport(number: Long, expected: Long, msgCreationTimeOpt: Option[DateTime], recoveryMsOpt: Option[Long])

case class StartTest(maxLoops: Int)