package core.stress

import akka.actor.{Actor, ActorLogging, ActorRef}

class StressTestReaderWorker(number: Long, view: ActorRef, reportCollector: ActorRef) extends Actor with ActorLogging {

  override def receive = {
    case "start" => doStart()
    case viewStateResponse: ViewStateResponse => processViewState(viewStateResponse)
  }

  private def doStart() {
    view ! ReadState(number)
  }

  private def processViewState(response: ViewStateResponse) {
    response match {
      case missing: MissingState =>
        reportCollector ! reportFailed(missing)
        tryRepeatRead()
      case state: ViewState =>
        reportCollector ! TesterReport(number, number, Some(state.journaledState.sendTime), Some(state.recoveryTimeMs))
        context.parent ! FinishedReading(number)
    }
  }

  private def tryRepeatRead() {
    log.debug("Retrying read due to non-matching state")
    Thread.sleep(10)
    view ! ReadState(number)
  }

  private def reportFailed(state: MissingState): TesterReport = TesterReport(state.lastNumber, number, state.sendTimeOpt,
    None)

}

case class FinishedReading(number: Long)