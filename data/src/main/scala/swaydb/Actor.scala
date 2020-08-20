/*
 * Copyright (c) 2020 Simer JS Plaha (simer.j@gmail.com - @simerplaha)
 *
 * This file is a part of SwayDB.
 *
 * SwayDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SwayDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with SwayDB. If not, see <https://www.gnu.org/licenses/>.
 *
 * Additional permission under the GNU Affero GPL version 3 section 7:
 * If you modify this Program or any covered work, only by linking or
 * combining it with separate works, the licensors of this Program grant
 * you additional permission to convey the resulting work.
 */

package swaydb

import java.util.{TimerTask, UUID}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import java.util.function.IntUnaryOperator

import com.typesafe.scalalogging.LazyLogging
import swaydb.IO.ExceptionHandler
import swaydb.data.config.ActorConfig
import swaydb.data.config.ActorConfig.QueueOrder
import swaydb.data.util.{Functions, Futures}

import scala.annotation.tailrec
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

sealed trait ActorRef[-T, S] { self =>

  def name: String

  def executionContext: ExecutionContext

  def send(message: T): Unit

  def ask[R, X[_]](message: ActorRef[R, Unit] => T)(implicit bag: Bag.Async[X]): X[R]

  /**
   * Sends a message to this actor with delay
   */
  def send(message: T, delay: FiniteDuration): TimerTask

  def ask[R, X[_]](message: ActorRef[R, Unit] => T, delay: FiniteDuration)(implicit bag: Bag.Async[X]): Actor.Task[R, X]

  def totalWeight: Int

  def messageCount: Int

  def isEmpty: Boolean

  def hasMessages: Boolean =
    totalWeight > 0

  def recover[M <: T, E: ExceptionHandler](f: (M, IO[E, Actor.Error], Actor[T, S]) => Unit): ActorRef[T, S]

  def recoverException[M <: T](f: (M, IO[Throwable, Actor.Error], Actor[T, S]) => Unit): ActorRef[T, S] =
    recover[M, Throwable](f)

  def receiveAllForce[BAG[_]](retryOnBusyDelay: FiniteDuration)(implicit bag: Bag.Async[BAG]): BAG[Unit]

  def receiveAllForceBlocking(retryCounts: Int, block: FiniteDuration): Try[Unit]

  def terminate[BAG[_]]()(implicit bag: Bag[BAG]): BAG[Unit]

  def onPreTerminate(f: Actor[T, S] => Unit): ActorRef[T, S]

  def onPostTerminate(f: Actor[T, S] => Unit): ActorRef[T, S]

  def terminateAfter(timeout: FiniteDuration): ActorRef[T, S]

  def isTerminated: Boolean

  def hasRecovery: Boolean

  def clear(): Unit

  def terminateAndClear[BAG[_]]()(implicit bag: Bag[BAG]): BAG[Unit]

  def terminateAndRecoverAsync[BAG[_]](retryOnBusyDelay: FiniteDuration)(implicit bag: Bag.Async[BAG]): BAG[Unit]

  def terminateAndRecoverSync[BAG[_]](retryOnBusyDelay: FiniteDuration)(implicit bag: Bag.Sync[BAG]): BAG[Unit]
}

object Actor {

  private[swaydb] val incrementDelayBy = 100.millisecond

  class Task[R, T[_]](val task: T[R], val timer: TimerTask)

  sealed trait Error
  object Error {
    case object TerminatedActor extends Actor.Error
  }

  def deadActor[T, S](): ActorRef[T, S] =
    new ActorRef[T, S] {
      override def name: String = "Dead actor"
      override def executionContext: ExecutionContext = throw new Exception("Dead Actor")
      override def send(message: T): Unit = throw new Exception("Dead Actor")
      override def ask[R, X[_]](message: ActorRef[R, Unit] => T)(implicit bag: Bag.Async[X]): X[R] = throw new Exception("Dead Actor")
      override def send(message: T, delay: FiniteDuration): TimerTask = throw new Exception("Dead Actor")
      override def ask[R, X[_]](message: ActorRef[R, Unit] => T, delay: FiniteDuration)(implicit bag: Bag.Async[X]): Task[R, X] = throw new Exception("Dead Actor")
      override def totalWeight: Int = throw new Exception("Dead Actor")
      override def messageCount: Int = throw new Exception("Dead Actor")
      override def isEmpty: Boolean = throw new Exception("Dead Actor")
      override def recover[M <: T, E: ExceptionHandler](f: (M, IO[E, Error], Actor[T, S]) => Unit): ActorRef[T, S] = throw new Exception("Dead Actor")
      override def receiveAllForce[BAG[_]](retryOnBusyDelay: FiniteDuration)(implicit bag: Bag.Async[BAG]): BAG[Unit] = throw new Exception("Dead Actor")
      override def receiveAllForceBlocking(retryCounts: Int, block: FiniteDuration): Try[Unit] = throw new Exception("Dead Actor")
      override def isTerminated: Boolean = throw new Exception("Dead Actor")
      override def clear(): Unit = throw new Exception("Dead Actor")
      override def terminateAndRecoverAsync[BAG[_]](retryOnBusyDelay: FiniteDuration)(implicit bag: Bag.Async[BAG]): BAG[Unit] = throw new Exception("Dead Actor")
      override def terminateAfter(timeout: FiniteDuration): ActorRef[T, S] = throw new Exception("Dead Actor")
      override def terminateAndRecoverSync[BAG[_]](retryOnBusyDelay: FiniteDuration)(implicit bag: Bag.Sync[BAG]): BAG[Unit] = throw new Exception("Dead Actor")
      override def hasRecovery: Boolean = throw new Exception("Dead Actor")
      override def onPreTerminate(f: Actor[T, S] => Unit): ActorRef[T, S] = throw new Exception("Dead Actor")
      override def onPostTerminate(f: Actor[T, S] => Unit): ActorRef[T, S] = throw new Exception("Dead Actor")
      override def terminate[BAG[_]]()(implicit bag: Bag[BAG]): BAG[Unit] = throw new Exception("Dead Actor")
      override def terminateAndClear[BAG[_]]()(implicit bag: Bag[BAG]): BAG[Unit] = throw new Exception("Dead Actor")
    }

  def cacheFromConfig[T](config: ActorConfig,
                         stashCapacity: Int,
                         weigher: T => Int)(execution: (T, Actor[T, Unit]) => Unit): ActorRef[T, Unit] =
    config match {
      case config: ActorConfig.Basic =>
        cache[T](
          name = config.name,
          stashCapacity = stashCapacity,
          weigher = weigher
        )(execution)(config.ec, QueueOrder.FIFO)

      case config: ActorConfig.Timer =>
        timerCache(
          name = config.name,
          stashCapacity = stashCapacity,
          interval = config.delay,
          weigher = weigher
        )(execution)(config.ec, QueueOrder.FIFO)

      case config: ActorConfig.TimeLoop =>
        timerLoopCache(
          name = config.name,
          stashCapacity = stashCapacity,
          interval = config.delay,
          weigher = weigher
        )(execution)(config.ec, QueueOrder.FIFO)
    }

  def cacheFromConfig[T, S](config: ActorConfig,
                            state: S,
                            stashCapacity: Int,
                            weigher: T => Int)(execution: (T, Actor[T, S]) => Unit): ActorRef[T, S] =
    config match {
      case config: ActorConfig.Basic =>
        cache[T, S](
          name = config.name,
          state = state,
          stashCapacity = stashCapacity,
          weigher = weigher
        )(execution)(config.ec, QueueOrder.FIFO)

      case config: ActorConfig.Timer =>
        timerCache[T, S](
          name = config.name,
          state = state,
          stashCapacity = stashCapacity,
          interval = config.delay,
          weigher = weigher
        )(execution)(config.ec, QueueOrder.FIFO)

      case config: ActorConfig.TimeLoop =>
        timerLoopCache[T, S](
          name = config.name,
          state = state,
          stashCapacity = stashCapacity,
          interval = config.delay,
          weigher = weigher
        )(execution)(config.ec, QueueOrder.FIFO)
    }

  /**
   * Basic stateless Actor that processes all incoming messages sequentially.
   *
   * On each message send (!) the Actor is woken up if it's not already running.
   */
  def apply[T](name: String)(execution: (T, Actor[T, Unit]) => Unit)(implicit ec: ExecutionContext,
                                                                     queueOrder: QueueOrder[T]): ActorRef[T, Unit] =
    apply[T, Unit](name, state = ())(execution)

  def apply[T, S](name: String, state: S)(execution: (T, Actor[T, S]) => Unit)(implicit ec: ExecutionContext,
                                                                               queueOrder: QueueOrder[T]): ActorRef[T, S] =
    new Actor[T, S](
      name = name,
      state = state,
      queue = ActorQueue(queueOrder),
      stashCapacity = 0,
      weigher = _ => 1,
      cached = false,
      execution = execution,
      scheduler = Scheduler(),
      interval = None,
      preTerminate = None,
      postTerminate = None,
      recovery = None
    )

  def cache[T](name: String,
               stashCapacity: Int,
               weigher: T => Int)(execution: (T, Actor[T, Unit]) => Unit)(implicit ec: ExecutionContext,
                                                                          queueOrder: QueueOrder[T]): ActorRef[T, Unit] =
    cache[T, Unit](
      name = name,
      state = (),
      stashCapacity = stashCapacity,
      weigher = weigher
    )(execution)

  def cache[T, S](name: String,
                  state: S,
                  stashCapacity: Int,
                  weigher: T => Int)(execution: (T, Actor[T, S]) => Unit)(implicit ec: ExecutionContext,
                                                                          queueOrder: QueueOrder[T]): ActorRef[T, S] =
    new Actor[T, S](
      name = name,
      state = state,
      queue = ActorQueue(queueOrder),
      stashCapacity = stashCapacity,
      weigher = Functions.safe((_: T) => 1, weigher),
      cached = true,
      execution = execution,
      scheduler = Scheduler(),
      interval = None,
      preTerminate = None,
      postTerminate = None,
      recovery = None
    )

  def timer[T](name: String,
               stashCapacity: Int,
               interval: FiniteDuration)(execution: (T, Actor[T, Unit]) => Unit)(implicit ec: ExecutionContext,
                                                                                 queueOrder: QueueOrder[T]): ActorRef[T, Unit] =
    timer(
      name = name,
      state = (),
      stashCapacity = stashCapacity,
      interval = interval
    )(execution)

  /**
   * Processes messages at regular intervals.
   *
   * If there are no messages in the queue the timer
   * is stopped and restarted only when a new message is added the queue.
   */
  def timer[T, S](name: String,
                  state: S,
                  stashCapacity: Int,
                  interval: FiniteDuration)(execution: (T, Actor[T, S]) => Unit)(implicit ec: ExecutionContext,
                                                                                 queueOrder: QueueOrder[T]): ActorRef[T, S] =
    new Actor[T, S](
      name = name,
      state = state,
      queue = ActorQueue(queueOrder),
      stashCapacity = stashCapacity,
      weigher = _ => 1,
      cached = false,
      execution = execution,
      scheduler = Scheduler(),
      interval = Some(new Interval(interval, false)),
      preTerminate = None,
      postTerminate = None,
      recovery = None
    )

  def timerCache[T](name: String,
                    stashCapacity: Int,
                    weigher: T => Int,
                    interval: FiniteDuration)(execution: (T, Actor[T, Unit]) => Unit)(implicit ec: ExecutionContext,
                                                                                      queueOrder: QueueOrder[T]): ActorRef[T, Unit] =
    timerCache[T, Unit](
      name = name,
      state = (),
      stashCapacity = stashCapacity,
      weigher = weigher,
      interval = interval
    )(execution)

  def timerCache[T, S](name: String,
                       state: S,
                       stashCapacity: Int,
                       weigher: T => Int,
                       interval: FiniteDuration)(execution: (T, Actor[T, S]) => Unit)(implicit ec: ExecutionContext,
                                                                                      queueOrder: QueueOrder[T]): ActorRef[T, S] =
    new Actor[T, S](
      name = name,
      state = state,
      queue = ActorQueue(queueOrder),
      stashCapacity = stashCapacity,
      weigher = weigher,
      cached = true,
      execution = execution,
      scheduler = Scheduler(),
      interval = Some(new Interval(interval, false)),
      preTerminate = None,
      postTerminate = None,
      recovery = None
    )

  /**
   * Stateless [[timerLoop]]
   */
  def timerLoop[T](name: String,
                   stashCapacity: Int,
                   interval: FiniteDuration)(execution: (T, Actor[T, Unit]) => Unit)(implicit ec: ExecutionContext,
                                                                                     queueOrder: QueueOrder[T]): ActorRef[T, Unit] =
    timerLoop(
      name = name,
      state = (),
      stashCapacity = stashCapacity,
      interval = interval
    )(execution)

  /**
   * Checks the message queue for new messages at regular intervals
   * indefinitely and processes them if the queue is non-empty.
   *
   * Use .submit instead of !. There should be a type-safe way of handling this but.
   */
  def timerLoop[T, S](name: String,
                      state: S,
                      stashCapacity: Int,
                      interval: FiniteDuration)(execution: (T, Actor[T, S]) => Unit)(implicit ec: ExecutionContext,
                                                                                     queueOrder: QueueOrder[T]): ActorRef[T, S] =
    new Actor[T, S](
      name = name,
      state = state,
      queue = ActorQueue(queueOrder),
      stashCapacity = stashCapacity,
      weigher = _ => 1,
      cached = false,
      execution = execution,
      scheduler = Scheduler(),
      interval = Some(new Interval(interval, true)),
      preTerminate = None,
      postTerminate = None,
      recovery = None
    )

  def timerLoopCache[T](name: String,
                        stashCapacity: Int,
                        weigher: T => Int,
                        interval: FiniteDuration)(execution: (T, Actor[T, Unit]) => Unit)(implicit ec: ExecutionContext,
                                                                                          queueOrder: QueueOrder[T]): ActorRef[T, Unit] =
    timerLoopCache[T, Unit](
      name = name,
      state = (),
      stashCapacity = stashCapacity,
      weigher = weigher,
      interval = interval
    )(execution)

  def timerLoopCache[T, S](name: String,
                           state: S,
                           stashCapacity: Int,
                           weigher: T => Int,
                           interval: FiniteDuration)(execution: (T, Actor[T, S]) => Unit)(implicit ec: ExecutionContext,
                                                                                          queueOrder: QueueOrder[T]): ActorRef[T, S] =
    new Actor[T, S](
      name = name,
      state = state,
      queue = ActorQueue(queueOrder),
      stashCapacity = stashCapacity,
      weigher = weigher,
      cached = true,
      execution = execution,
      scheduler = Scheduler(),
      interval = Some(new Interval(interval, true)),
      preTerminate = None,
      postTerminate = None,
      recovery = None
    )

  def wire[T](name: String, impl: T)(implicit ec: ExecutionContext): ActorWire[T, Unit] =
    new ActorWire(
      name = name,
      impl = impl,
      interval = None,
      state = ()
    )

  def wire[T, S](name: String, impl: T, state: S)(implicit ec: ExecutionContext): ActorWire[T, S] =
    new ActorWire(
      name = name,
      impl = impl,
      interval = None,
      state = state
    )

  def wireTimer[T](name: String,
                   interval: FiniteDuration,
                   stashCapacity: Int,
                   impl: T)(implicit ec: ExecutionContext): ActorWire[T, Unit] =
    new ActorWire(
      name = name,
      impl = impl,
      interval = Some((interval, stashCapacity)),
      state = ()
    )

  def wireTimer[T, S](name: String,
                      interval: FiniteDuration,
                      stashCapacity: Int,
                      impl: T,
                      state: S)(implicit ec: ExecutionContext): ActorWire[T, S] =
    new ActorWire(
      name = name,
      impl = impl,
      interval = Some((interval, stashCapacity)),
      state = state
    )

  /**
   * Adjust delay based on the input parameter.
   *
   * It basically decides if the delay should be incremented or decremented to control
   * message overflow as quickly without hogging the thread for too long and without
   * keep messages in-memory for too long.
   */
  private[swaydb] def adjustDelay(currentQueueSize: Int,
                                  defaultQueueSize: Int,
                                  previousDelay: FiniteDuration,
                                  defaultDelay: FiniteDuration): FiniteDuration =
  //if there is no overflow increment previous delay or return the default it's overflow is controlled.
    if (currentQueueSize <= defaultQueueSize)
      (previousDelay + incrementDelayBy) min defaultDelay
    else { //else adjust overflow.
      val overflow = defaultQueueSize.toFloat / currentQueueSize
      val adjustDelay = previousDelay.toMillis * overflow
      FiniteDuration(adjustDelay.toLong, TimeUnit.MILLISECONDS)
    }
}

private class Interval(val delay: FiniteDuration, val isLoop: Boolean)

class Actor[-T, S](val name: String,
                   val state: S,
                   queue: ActorQueue[(T, Int)],
                   stashCapacity: Int,
                   weigher: T => Int,
                   cached: Boolean,
                   execution: (T, Actor[T, S]) => Unit,
                   scheduler: Scheduler,
                   interval: Option[Interval],
                   preTerminate: Option[Actor[T, S] => Unit],
                   postTerminate: Option[Actor[T, S] => Unit],
                   recovery: Option[(T, IO[Throwable, Actor.Error], Actor[T, S]) => Unit])(implicit val executionContext: ExecutionContext) extends ActorRef[T, S] with LazyLogging { self =>

  //only a single thread can invoke preTerminate.
  private val terminateBool = new AtomicBoolean(false)
  //used to know which thread processes the postTermination function.
  @volatile private var postTerminateToken = UUID.randomUUID().toString
  private val busy = new AtomicBoolean(false)
  private val weight = new AtomicInteger(0)
  private val isBasic = interval.isEmpty
  private val isTimerLoop = interval.exists(_.isLoop)
  private val isTimerNoLoop = interval.exists(!_.isLoop)

  //minimum number of message to leave if the Actor is cached.
  private val fixedStashSize =
    if (cached)
      stashCapacity
    else
      0

  @volatile private var terminated = false
  @volatile private var task = Option.empty[TimerTask]

  override def totalWeight: Int =
    weight.get()

  def messageCount: Int =
    queue.size

  override def send(message: T, delay: FiniteDuration): TimerTask =
    scheduler.task(delay)(self send message)

  override def send(message: T): Unit =
    if (terminated) {
      recovery foreach {
        f =>
          f(message, IO.Right(Actor.Error.TerminatedActor), self)
      }
    } else {
      //message weight cannot be <= 0 as that could lead to messages in queue with empty weight.
      val messageWeight = weigher(message) max 1
      queue.add((message, messageWeight))

      val currentStashed =
        weight updateAndGet {
          new IntUnaryOperator {
            override def applyAsInt(currentWeight: Int): Int =
              currentWeight + messageWeight
          }
        }

      //skip wakeUp if it's a timerLoop. Run only if no task is scheduled for the task.
      //if eager wakeUp on overflow is required using timer instead of timerLoop.
      if (!(isTimerLoop && task.nonEmpty))
        wakeUp(currentStashed = currentStashed)
    }

  override def ask[R, X[_]](message: ActorRef[R, Unit] => T)(implicit bag: Bag.Async[X]): X[R] =
    bag.suspend {
      val promise = Promise[R]()

      implicit val queueOrder = QueueOrder.FIFO

      val replyTo: ActorRef[R, Unit] = Actor[R](name + "_response")((response, _) => promise.success(response))
      this send message(replyTo)

      bag fromPromise promise
    }

  override def ask[R, X[_]](message: ActorRef[R, Unit] => T, delay: FiniteDuration)(implicit bag: Bag.Async[X]): Actor.Task[R, X] = {
    val promise = Promise[R]()

    implicit val queueOrder = QueueOrder.FIFO

    val replyTo: ActorRef[R, Unit] = Actor[R](name + "_response")((response, _) => promise.success(response))
    val task = this.send(message(replyTo), delay)

    new Actor.Task(bag fromPromise promise, task)
  }

  @inline private def wakeUp(currentStashed: Int): Unit =
    if (isBasic)
      basicWakeUp(currentStashed)
    else
      timerWakeUp(currentStashed, this.stashCapacity)

  @inline private def basicWakeUp(currentStashed: Int): Unit = {
    val overflow = currentStashed - fixedStashSize
    val isOverflown = overflow > 0
    //do not check for terminated actor here for receive to apply recovery.
    if (isOverflown && busy.compareAndSet(false, true))
      Future(receive(overflow, wakeUpOnComplete = true))
  }

  /**
   * @param stashCapacity will mostly be >= [[Actor.stashCapacity]].
   *                      It can be < [[Actor.stashCapacity]] if the [[weigher]]
   *                      results in an item with weight > current overflow.
   *                      For example: if item's weight is 10 but overflow is 1. This
   *                      will result in the cached messages to be [[Actor.stashCapacity]] - 10.
   */
  private def timerWakeUp(currentStashed: Int, stashCapacity: Int): Unit =
    if (!terminated) {
      val overflow = currentStashed - stashCapacity
      val isOverflown = overflow > 0
      val hasMessages = currentStashed > 0

      //run timer if it's an overflow or if task is empty and there is work to do.
      if ((isOverflown || (task.isEmpty && (isTimerLoop || (isTimerNoLoop && hasMessages)))) && busy.compareAndSet(false, true)) {
        //if it's overflown then wakeUp Actor now!
        try {
          if (isOverflown)
            Future(receive(overflow, wakeUpOnComplete = true))

          //if there is no task schedule based on current stash so that eventually all messages get dropped without hammering.
          if (task.isEmpty)
            interval foreach {
              interval =>
                //cancel any existing task.
                task foreach {
                  task =>
                    task.cancel()
                    this.task = None
                }

                //schedule a task is there are messages to process or if the Actor is a looper.
                if (currentStashed > 0 || isTimerLoop) {
                  //reduce stash capacity to eventually processed stashed messages.
                  val nextTask =
                    scheduler.task(interval.delay) {
                      //clear the existing task so that next one gets scheduled/
                      this.task = None
                      //get the current weight during the schedule.
                      val currentStashedAtSchedule = weight.get

                      //adjust the stash capacity so that eventually all messages get processed.
                      val adjustedStashCapacity =
                        if (stashCapacity <= 0)
                          this.stashCapacity
                        else
                          (currentStashedAtSchedule / 2) max fixedStashSize //if cached stashCapacity cannot be lower than this.stashCapacity.

                      //schedule wakeUp.
                      timerWakeUp(
                        currentStashed = currentStashedAtSchedule,
                        stashCapacity = adjustedStashCapacity
                      )
                    }

                  task = Some(nextTask)
                }
            }
        } finally {
          //if receive was not executed set free after a task is scheduled.
          if (!isOverflown) {
            busy.set(false)
          }
        }
      }
    }

  private def receiveAllInFuture(retryOnBusyDelay: FiniteDuration): Future[Unit] =
    if (busy.compareAndSet(false, true))
      Future(receive(Int.MaxValue, wakeUpOnComplete = false)) flatMap {
        _ =>
          if (messageCount <= 0)
            Futures.unit
          else
            receiveAllInFuture(retryOnBusyDelay)
      }
    else
      scheduler(retryOnBusyDelay)(receiveAllInFuture(retryOnBusyDelay))

  /**
   * Forces the Actor to process all queued messages.
   *
   * @param retryOnBusyDelay delay to use if the actor is currently busy processing messages in another thread.
   */
  def receiveAllForce[BAG[_]](retryOnBusyDelay: FiniteDuration)(implicit bag: Bag.Async[BAG]): BAG[Unit] =
    bag.suspend(bag.fromFuture(receiveAllInFuture(retryOnBusyDelay)))

  override def receiveAllForceBlocking(retryCounts: Int, block: FiniteDuration): Try[Unit] =
    receiveAllBlocking(0, retryCounts, block)

  @tailrec
  private def receiveAllBlocking(retires: Int, maxRetries: Int, block: FiniteDuration): Try[Unit] =
    if (retires > maxRetries) {
      Failure(new Exception(s"Retries timeout. Retries: $retires. maxRetries: $maxRetries"))
    } else if (busy.compareAndSet(false, true)) {
      Try(receive(Int.MaxValue, wakeUpOnComplete = false)) match {
        case success @ Success(_) =>
          if (messageCount <= 0) {
            success
          } else {
            Thread.sleep(block.toMillis)
            receiveAllBlocking(0, maxRetries, block)
          }

        case failure @ Failure(_) =>
          failure
      }
    } else {
      Thread.sleep(block.toMillis)
      receiveAllBlocking(retires + 1, maxRetries, block)
    }


  private def receive(overflow: Int, wakeUpOnComplete: Boolean): Unit = {
    var processedWeight = 0
    var break = false
    try
      while (!break && processedWeight < overflow) {
        val messageAndWeight = queue.poll()
        if (messageAndWeight == null) {
          break = true
        } else {
          val (message, messageWeight) = messageAndWeight

          if (terminated)
            try //apply recovery if actor is terminated.
              recovery foreach {
                f =>
                  f(message, IO.Right(Actor.Error.TerminatedActor), self)
              }
            finally
              processedWeight += messageWeight
          else // else if the actor is not terminated, process the message.
            try
              execution(message, self)
            catch {
              case throwable: Throwable =>
                //apply recovery if failed to process messages.
                recovery foreach {
                  f =>
                    f(message, IO.Left(throwable), self)
                }
            } finally {
              processedWeight += messageWeight
            }
        }
      }
    finally {
      weight updateAndGet {
        new IntUnaryOperator {
          override def applyAsInt(currentWeight: Int): Int =
            currentWeight - processedWeight
        }
      }
      busy.set(false)
      //after setting busy to false fetch the totalWeight again.

      if (wakeUpOnComplete)
        wakeUp(currentStashed = totalWeight)
    }
  }

  override def recover[M <: T, E: ExceptionHandler](f: (M, IO[E, Actor.Error], Actor[T, S]) => Unit): ActorRef[T, S] =
    new Actor[T, S](
      name = name,
      state = state,
      queue = queue,
      stashCapacity = stashCapacity,
      weigher = weigher,
      cached = cached,
      execution = execution,
      interval = interval,
      scheduler = scheduler,
      preTerminate = preTerminate,
      postTerminate = postTerminate,
      recovery =
        Some {
          case (message: M@unchecked, error, actor) =>
            error match {
              case IO.Right(actorError) =>
                f(message, IO.Right(actorError), actor)

              case IO.Left(throwable) =>
                f(message, IO.Left(ExceptionHandler.toError(throwable)), actor)
            }

          case (_, error, _) =>
            error match {
              case IO.Right(Actor.Error.TerminatedActor) =>
                logger.error(s"""Failed to recover failed message. Actor("$name").""", new Exception("Cause: Terminated Actor"))

              case IO.Left(exception: Throwable) =>
                logger.error(s"""Failed to recover failed message. Actor("$name").""", exception)
            }
        }
    )

  override def onPreTerminate(f: Actor[T, S] => Unit): ActorRef[T, S] =
    new Actor[T, S](
      name = name,
      state = state,
      queue = queue,
      stashCapacity = stashCapacity,
      weigher = weigher,
      cached = cached,
      execution = execution,
      scheduler = scheduler,
      interval = interval,
      preTerminate = Some(f),
      postTerminate = postTerminate,
      recovery = recovery
    )

  override def onPostTerminate(f: Actor[T, S] => Unit): ActorRef[T, S] =
    new Actor[T, S](
      name = name,
      state = state,
      queue = queue,
      stashCapacity = stashCapacity,
      weigher = weigher,
      cached = cached,
      execution = execution,
      scheduler = scheduler,
      interval = interval,
      preTerminate = preTerminate,
      postTerminate = Some(f),
      recovery = recovery
    )

  private def terminateAndRecover[BAG[_]](retryOnBusyDelay: FiniteDuration)(implicit bag: Bag[BAG]): BAG[Unit] =
    bag.suspend {
      setTerminated()
      bag.flatMap(runPreTerminate()) {
        token =>
          if (recovery.isDefined) {
            bag match {
              case sync: Bag.Sync[BAG] =>
                implicit val theBag: Bag.Sync[BAG] = sync

                receiveAllForceBlocking(100, retryOnBusyDelay) match {
                  case Success(_) =>
                    runPostTerminate(token)

                  case Failure(exception) =>
                    bag.failure(exception)
                }

              case async: Bag.Async[BAG] =>
                implicit val theBag: Bag.Async[BAG] = async

                bag.flatMap(receiveAllForce(retryOnBusyDelay)) {
                  _ =>
                    runPostTerminate(token)
                }
            }
          } else {
            logger.error(s"""terminateAndRecover invoked on Actor("$name") with no recovery defined. Messages cleared.""")
            bag.and(terminateAndClear())(runPostTerminate(token))
          }
      }
    }

  /**
   * Terminates the Actor and applies [[recover]] function to all queued messages.
   *
   * If [[recover]] function  is not specified then all queues messages are cleared.
   */
  def terminateAndRecoverAsync[BAG[_]](retryOnBusyDelay: FiniteDuration)(implicit bag: Bag.Async[BAG]): BAG[Unit] =
    terminateAndRecover(retryOnBusyDelay)

  override def terminateAndRecoverSync[BAG[_]](retryOnBusyDelay: FiniteDuration)(implicit bag: Bag.Sync[BAG]): BAG[Unit] =
    terminateAndRecover(retryOnBusyDelay)(bag)

  override def terminateAndClear[BAG[_]]()(implicit bag: Bag[BAG]): BAG[Unit] =
    bag.transform(terminate()) {
      _ =>
        clear()
    }

  override def clear(): Unit =
    queue.clear()

  private def setTerminated(): Unit =
    terminated = true

  def terminate[BAG[_]]()(implicit bag: Bag[BAG]): BAG[Unit] =
    bag.suspend {
      setTerminated()
      //invoke terminator function
      bag.flatMap(runPreTerminate()) {
        token =>
          runPostTerminate(token)
      }
    }

  def terminateAfter(timeout: FiniteDuration): ActorRef[T, S] = {
    scheduler.task(timeout)(this.terminate[Future]())
    this
  }

  private def runPreTerminate[BAG[_]]()(implicit bag: Bag[BAG]): BAG[String] =
    if (terminateBool.compareAndSet(false, true)) {
      postTerminateToken = UUID.randomUUID().toString
      preTerminate match {
        case Some(function) =>
          bag {
            function(this)
            postTerminateToken
          }

        case None =>
          bag.success(postTerminateToken)
      }
    } else {
      bag.success("")
    }

  private def runPostTerminate[BAG[_]](token: String)(implicit bag: Bag[BAG]): BAG[Unit] =
    if (postTerminateToken == token)
      postTerminate match {
        case Some(function) =>
          bag.and(bag(function(this)))(bag.success(scheduler.terminate()))

        case None =>
          bag.success(scheduler.terminate())
      }
    else
      bag.unit

  override def hasRecovery: Boolean =
    recovery.isDefined

  def isTerminated: Boolean =
    terminated

  override def isEmpty: Boolean =
    queue.isEmpty

}
