/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package akka.cluster.ddata.typed.javadsl

import java.util.function.{ Function ⇒ JFunction }
import akka.cluster.{ ddata ⇒ dd }
import akka.cluster.ddata.typed.scaladsl
import akka.cluster.ddata.Key
import akka.cluster.ddata.ReplicatedData
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.cluster.ddata.typed.internal.ReplicatorBehavior
import akka.cluster.ddata.typed.scaladsl

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.Duration
import java.util.Optional
import java.util.function.{ Function ⇒ JFunction }

import akka.actor.{ DeadLetterSuppression, NoSerializationVerificationNeeded }
import akka.actor.typed.{ ActorRef, Behavior }
import akka.annotation.{ DoNotInherit, InternalApi }
import akka.cluster.ddata.typed.internal.ReplicatorBehavior
import akka.cluster.ddata.typed.scaladsl
import akka.cluster.{ ddata ⇒ dd }

import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.util.control.NoStackTrace

/**
 * @see [[akka.cluster.ddata.Replicator]].
 */
object Replicator {
  import dd.Replicator.DefaultMajorityMinCap

  /**
   * The `Behavior` for the `Replicator` actor.
   */
  def behavior(settings: dd.ReplicatorSettings): Behavior[Command] =
    ReplicatorBehavior.behavior(settings, underlyingReplicator = None).narrow[Command]

  /**
   * The `Behavior` for the `Replicator` actor.
   * It will use the given underlying [[akka.cluster.ddata.Replicator]]
   */
  def behavior(settings: dd.ReplicatorSettings, underlyingReplicator: akka.actor.ActorRef): Behavior[Command] =
    ReplicatorBehavior.behavior(settings, Some(underlyingReplicator)).narrow[Command]

  @DoNotInherit trait Command extends akka.cluster.ddata.typed.scaladsl.Replicator.Command

  sealed trait ReadConsistency {
    def timeout: FiniteDuration

    /** INTERNAL API */
    @InternalApi private[akka] def toUntyped: dd.Replicator.ReadConsistency
  }
  case object ReadLocal extends ReadConsistency {
    override def timeout: FiniteDuration = Duration.Zero

    /** INTERNAL API */
    @InternalApi private[akka] override def toUntyped = dd.Replicator.ReadLocal
  }
  final case class ReadFrom(n: Int, timeout: FiniteDuration) extends ReadConsistency {
    require(n >= 2, "ReadFrom n must be >= 2, use ReadLocal for n=1")

    /** INTERNAL API */
    @InternalApi private[akka] override def toUntyped = dd.Replicator.ReadFrom(n, timeout)
  }
  final case class ReadMajority(timeout: FiniteDuration, minCap: Int = DefaultMajorityMinCap) extends ReadConsistency {
    def this(timeout: FiniteDuration) = this(timeout, DefaultMajorityMinCap)

    /** INTERNAL API */
    @InternalApi private[akka] override def toUntyped = dd.Replicator.ReadMajority(timeout, minCap)
  }
  final case class ReadAll(timeout: FiniteDuration) extends ReadConsistency {
    /** INTERNAL API */
    @InternalApi private[akka] override def toUntyped = dd.Replicator.ReadAll(timeout)
  }

  sealed trait WriteConsistency {
    def timeout: FiniteDuration

    /** INTERNAL API */
    @InternalApi private[akka] def toUntyped: dd.Replicator.WriteConsistency
  }
  case object WriteLocal extends WriteConsistency {
    override def timeout: FiniteDuration = Duration.Zero

    /** INTERNAL API */
    @InternalApi private[akka] override def toUntyped = dd.Replicator.WriteLocal
  }
  final case class WriteTo(n: Int, timeout: FiniteDuration) extends WriteConsistency {
    require(n >= 2, "WriteTo n must be >= 2, use WriteLocal for n=1")

    /** INTERNAL API */
    @InternalApi private[akka] override def toUntyped = dd.Replicator.WriteTo(n, timeout)
  }
  final case class WriteMajority(timeout: FiniteDuration, minCap: Int = DefaultMajorityMinCap) extends WriteConsistency {
    def this(timeout: FiniteDuration) = this(timeout, DefaultMajorityMinCap)

    /** INTERNAL API */
    @InternalApi private[akka] override def toUntyped = dd.Replicator.WriteMajority(timeout, minCap)
  }
  final case class WriteAll(timeout: FiniteDuration) extends WriteConsistency {
    /** INTERNAL API */
    @InternalApi private[akka] override def toUntyped = dd.Replicator.WriteAll(timeout)
  }

  /**
   * The `ReadLocal` instance
   */
  def readLocal: ReadConsistency = ReadLocal

  /**
   * The `WriteLocal` instance
   */
  def writeLocal: WriteConsistency = WriteLocal

  /**
   * Send this message to the local `Replicator` to retrieve a data value for the
   * given `key`. The `Replicator` will reply with one of the [[GetResponse]] messages.
   *
   * The optional `request` context is included in the reply messages. This is a convenient
   * way to pass contextual information (e.g. original sender) without having to use `ask`
   * or maintain local correlation data structures.
   */
  final case class Get[A <: ReplicatedData](key: Key[A], consistency: ReadConsistency, replyTo: ActorRef[GetResponse[A]], request: Optional[Any])
    extends Command {

    def this(key: Key[A], consistency: ReadConsistency, replyTo: ActorRef[GetResponse[A]]) =
      this(key, consistency, replyTo, Optional.empty[Any])
  }

  @DoNotInherit sealed abstract class GetResponse[A <: ReplicatedData] extends NoSerializationVerificationNeeded {
    def key: Key[A]
    def request: Optional[Any]
    def getRequest: Optional[Any] = request
  }
  /**
   * Reply from `Get`. The data value is retrieved with [[#get]] using the typed key.
   */
  final case class GetSuccess[A <: ReplicatedData](key: Key[A], request: Optional[Any])(data: A)
    extends GetResponse[A] {

    /**
     * The data value, with correct type.
     */
    def get[T <: ReplicatedData](key: Key[T]): T = {
      require(key == this.key, "wrong key used, must use contained key")
      data.asInstanceOf[T]
    }

    /**
     * The data value. Use [[#get]] to get the fully typed value.
     */
    def dataValue: A = data
  }
  final case class NotFound[A <: ReplicatedData](key: Key[A], request: Optional[Any])
    extends GetResponse[A]
  /**
   * The [[Get]] request could not be fulfill according to the given
   * [[ReadConsistency consistency level]] and [[ReadConsistency#timeout timeout]].
   */
  final case class GetFailure[A <: ReplicatedData](key: Key[A], request: Optional[Any])
    extends GetResponse[A]

  object Update {

    private def modifyWithInitial[A <: ReplicatedData](initial: A, modify: A ⇒ A): Option[A] ⇒ A = {
      case Some(data) ⇒ modify(data)
      case None       ⇒ modify(initial)
    }
  }
  /**
   * Send this message to the local `Replicator` to update a data value for the
   * given `key`. The `Replicator` will reply with one of the [[UpdateResponse]] messages.
   *
   * The current data value for the `key` is passed as parameter to the `modify` function.
   * It is `None` if there is no value for the `key`, and otherwise `Some(data)`. The function
   * is supposed to return the new value of the data, which will then be replicated according to
   * the given `writeConsistency`.
   *
   * The `modify` function is called by the `Replicator` actor and must therefore be a pure
   * function that only uses the data parameter and stable fields from enclosing scope. It must
   * for example not access `sender()` reference of an enclosing actor.
   */
  final case class Update[A <: ReplicatedData] private (key: Key[A], writeConsistency: WriteConsistency,
                                                        replyTo: ActorRef[UpdateResponse[A]], request: Optional[Any])(val modify: Option[A] ⇒ A)
    extends Command with NoSerializationVerificationNeeded {

    /**
     * Modify value of local `Replicator` and replicate with given `writeConsistency`.
     *
     * The current value for the `key` is passed to the `modify` function.
     * If there is no current data value for the `key` the `initial` value will be
     * passed to the `modify` function.
     */
    def this(
      key: Key[A], initial: A, writeConsistency: WriteConsistency, replyTo: ActorRef[UpdateResponse[A]], modify: JFunction[A, A]) =
      this(key, writeConsistency, replyTo, Optional.empty[Any])(
        Update.modifyWithInitial(initial, data ⇒ modify.apply(data)))

    /**
     * Modify value of local `Replicator` and replicate with given `writeConsistency`.
     *
     * The current value for the `key` is passed to the `modify` function.
     * If there is no current data value for the `key` the `initial` value will be
     * passed to the `modify` function.
     *
     * The optional `request` context is included in the reply messages. This is a convenient
     * way to pass contextual information (e.g. original sender) without having to use `ask`
     * or local correlation data structures.
     */
    def this(
      key: Key[A], initial: A, writeConsistency: WriteConsistency, replyTo: ActorRef[UpdateResponse[A]],
      request: Optional[Any], modify: JFunction[A, A]) =
      this(key, writeConsistency, replyTo, request)(Update.modifyWithInitial(initial, data ⇒ modify.apply(data)))

  }

  @DoNotInherit sealed abstract class UpdateResponse[A <: ReplicatedData] extends NoSerializationVerificationNeeded {
    def key: Key[A]
    def request: Optional[Any]
    def getRequest: Optional[Any] = request
  }
  final case class UpdateSuccess[A <: ReplicatedData](key: Key[A], request: Optional[Any])
    extends UpdateResponse[A] with DeadLetterSuppression

  @DoNotInherit sealed abstract class UpdateFailure[A <: ReplicatedData] extends UpdateResponse[A]

  /**
   * The direct replication of the [[Update]] could not be fulfill according to
   * the given [[WriteConsistency consistency level]] and
   * [[WriteConsistency#timeout timeout]].
   *
   * The `Update` was still performed locally and possibly replicated to some nodes.
   * It will eventually be disseminated to other replicas, unless the local replica
   * crashes before it has been able to communicate with other replicas.
   */
  final case class UpdateTimeout[A <: ReplicatedData](key: Key[A], request: Optional[Any]) extends UpdateFailure[A]
  /**
   * If the `modify` function of the [[Update]] throws an exception the reply message
   * will be this `ModifyFailure` message. The original exception is included as `cause`.
   */
  final case class ModifyFailure[A <: ReplicatedData](key: Key[A], errorMessage: String, cause: Throwable, request: Optional[Any])
    extends UpdateFailure[A] {
    override def toString: String = s"ModifyFailure [$key]: $errorMessage"
  }
  /**
   * The local store or direct replication of the [[Update]] could not be fulfill according to
   * the given [[WriteConsistency consistency level]] due to durable store errors. This is
   * only used for entries that have been configured to be durable.
   *
   * The `Update` was still performed in memory locally and possibly replicated to some nodes,
   * but it might not have been written to durable storage.
   * It will eventually be disseminated to other replicas, unless the local replica
   * crashes before it has been able to communicate with other replicas.
   */
  final case class StoreFailure[A <: ReplicatedData](key: Key[A], request: Optional[Any])
    extends UpdateFailure[A] with DeleteResponse[A] {

    override def getRequest: Optional[Any] = request
  }

  /**
   * Register a subscriber that will be notified with a [[Changed]] message
   * when the value of the given `key` is changed. Current value is also
   * sent as a [[Changed]] message to a new subscriber.
   *
   * Subscribers will be notified periodically with the configured `notify-subscribers-interval`,
   * and it is also possible to send an explicit `FlushChanges` message to
   * the `Replicator` to notify the subscribers immediately.
   *
   * The subscriber will automatically be unregistered if it is terminated.
   *
   * If the key is deleted the subscriber is notified with a [[Deleted]]
   * message.
   */
  final case class Subscribe[A <: ReplicatedData](key: Key[A], subscriber: ActorRef[Changed[A]]) extends Command
  /**
   * Unregister a subscriber.
   *
   * @see [[Replicator.Subscribe]]
   */
  final case class Unsubscribe[A <: ReplicatedData](key: Key[A], subscriber: ActorRef[Changed[A]]) extends Command
  /**
   * The data value is retrieved with [[#get]] using the typed key.
   *
   * @see [[Replicator.Subscribe]]
   */
  final case class Changed[A <: ReplicatedData](key: Key[A])(data: A) {
    /**
     * The data value, with correct type.
     * Scala pattern matching cannot infer the type from the `key` parameter.
     */
    def get[T <: ReplicatedData](key: Key[T]): T = {
      require(key == this.key, "wrong key used, must use contained key")
      data.asInstanceOf[T]
    }

    /**
     * The data value. Use [[#get]] to get the fully typed value.
     */
    def dataValue: A = data
  }

  /**
   * Send this message to the local `Replicator` to delete a data value for the
   * given `key`. The `Replicator` will reply with one of the [[DeleteResponse]] messages.
   *
   * The optional `request` context is included in the reply messages. This is a convenient
   * way to pass contextual information (e.g. original sender) without having to use `ask`
   * or maintain local correlation data structures.
   */
  final case class Delete[A <: ReplicatedData](key: Key[A], consistency: WriteConsistency,
                                               replyTo: ActorRef[DeleteResponse[A]], request: Optional[Any])
    extends Command with NoSerializationVerificationNeeded {

    def this(key: Key[A], consistency: WriteConsistency, replyTo: ActorRef[DeleteResponse[A]]) =
      this(key, consistency, replyTo, Optional.empty())
  }

  sealed trait DeleteResponse[A <: ReplicatedData] extends NoSerializationVerificationNeeded {
    def key: Key[A]
    def request: Optional[Any]
    def getRequest: Optional[Any] = request
  }
  final case class DeleteSuccess[A <: ReplicatedData](key: Key[A], request: Optional[Any]) extends DeleteResponse[A]
  final case class ReplicationDeleteFailure[A <: ReplicatedData](key: Key[A], request: Optional[Any]) extends DeleteResponse[A]
  final case class DataDeleted[A <: ReplicatedData](key: Key[A], request: Optional[Any])
    extends RuntimeException with NoStackTrace with DeleteResponse[A] {
    override def toString: String = s"DataDeleted [$key]"
  }

  /**
   * Get current number of replicas, including the local replica.
   * Will reply to sender with [[ReplicaCount]].
   */
  final case class GetReplicaCount(replyTo: ActorRef[ReplicaCount]) extends Command

  /**
   * Current number of replicas. Reply to `GetReplicaCount`.
   */
  final case class ReplicaCount(n: Int)

  /**
   * INTERNAL API
   */
  @InternalApi private[akka] case object FlushChanges extends Command

  /**
   * The `FlushChanges` instance. Notify subscribers of changes now, otherwise they will be notified periodically
   * with the configured `notify-subscribers-interval`.
   */
  def flushChanges: Command = FlushChanges

}