package core.stress

import akka.actor.Actor

import scala.collection.mutable.ListBuffer

class ReportCollector extends Actor {

  var data = new ListBuffer[TesterReport]()

  override def receive = {
    case GetFullReport => sender ! data
    case report: TesterReport => data.append(report)
  }
}

case object GetFullReport
