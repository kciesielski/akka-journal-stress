package core.stress

import akka.actor.{Address, ActorLogging, Actor}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import core.stress.SimpleClusterListener.IsRemoved

import scala.collection.mutable.ListBuffer

class SimpleClusterListener extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  // subscribe to cluster changes, re-subscribe when restart
  override def preStart(): Unit = {
    //#subscribe
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
    //#subscribe
  }
  override def postStop(): Unit = cluster.unsubscribe(self)

  var removedAddresses: ListBuffer[Address] = new ListBuffer[Address]()

  def receive = {
    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)
      removedAddresses = removedAddresses - member.address
    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}",
        member.address, previousStatus)
      removedAddresses = removedAddresses :+ member.address
    case IsRemoved(address) => sender ! removedAddresses.contains(address)
    case _: MemberEvent => // ignore
  }
}

object SimpleClusterListener {
  case class IsRemoved(address: Address)
}