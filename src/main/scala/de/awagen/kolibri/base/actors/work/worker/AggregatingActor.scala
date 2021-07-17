/**
  * Copyright 2021 Andreas Wagenmann
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */


package de.awagen.kolibri.base.actors.work.worker

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, PoisonPill, Props}
import de.awagen.kolibri.base.actors.work.worker.AggregatingActor._
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages._
import de.awagen.kolibri.base.config.AppConfig.config
import de.awagen.kolibri.base.config.AppConfig.config.{kolibriDispatcherName, useAggregatorBackpressure}
import de.awagen.kolibri.base.io.writer.Writers.Writer
import de.awagen.kolibri.base.processing.execution.expectation.ExecutionExpectation
import de.awagen.kolibri.datatypes.io.KolibriSerializable
import de.awagen.kolibri.datatypes.tagging.Tags.{StringTag, Tag}
import de.awagen.kolibri.datatypes.types.WithCount
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator

import scala.concurrent.ExecutionContextExecutor

object AggregatingActor {

  // TODO: we might wannna have a RateExpectation, meaning elements to aggregate must be there at least every
  // given time step
  def props[U, V <: WithCount](aggregatorSupplier: () => Aggregator[ProcessingMessage[U], V],
                  expectationSupplier: () => ExecutionExpectation,
                  owner: ActorRef,
                  jobPartIdentifier: JobPartIdentifiers.JobPartIdentifier,
                  writer: Option[Writer[V, Tag, _]],
                  sendResultDataToSender: Boolean): Props =
    Props(new AggregatingActor[U, V](aggregatorSupplier, expectationSupplier, owner, jobPartIdentifier, writer, sendResultDataToSender))
      .withDispatcher(kolibriDispatcherName)

  trait AggregatingActorCmd extends KolibriSerializable

  trait AggregatingActorEvent extends KolibriSerializable

  case object Close extends AggregatingActorCmd

  case object ProvideStateAndStop

  case object ReportResults extends AggregatingActorCmd

  case object Housekeeping

  case object ACK

}

class AggregatingActor[U, V <: WithCount](val aggregatorSupplier: () => Aggregator[ProcessingMessage[U], V],
                                          val expectationSupplier: () => ExecutionExpectation,
                                          val owner: ActorRef,
                                          val jobPartIdentifier: JobPartIdentifiers.JobPartIdentifier,
                                          val writerOpt: Option[Writer[V, Tag, _]],
                                          val sendResultDataToSender: Boolean = true)
  extends Actor with ActorLogging {

  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = context.system.dispatchers.lookup(kolibriDispatcherName)

  val expectation: ExecutionExpectation = expectationSupplier.apply()
  expectation.init
  val aggregator: Aggregator[ProcessingMessage[U], V] = aggregatorSupplier.apply()
  val cancellableSchedule: Cancellable = context.system.scheduler.scheduleAtFixedRate(
    initialDelay = config.runnableExecutionActorHousekeepingInterval,
    interval = config.runnableExecutionActorHousekeepingInterval,
    receiver = self,
    message = Housekeeping)

  def handleExpectationStateAndCloseIfFinished(adjustReceive: Boolean): Unit = {
    if (expectation.succeeded || expectation.failed) {
      cancellableSchedule.cancel()
      log.info(s"expectation succeeded: ${expectation.succeeded}, expectation failed: ${expectation.failed}")
      log.info(s"sending aggregation state for batch: ${jobPartIdentifier.batchNr}")
      if (sendResultDataToSender) {
        owner ! AggregationState(aggregator.aggregation, jobPartIdentifier.jobId, jobPartIdentifier.batchNr, expectation.deepCopy)
      }
      else {
        // TODO: use alternative msg here, setting data to null is bad idea
        owner ! AggregationState(null, jobPartIdentifier.jobId, jobPartIdentifier.batchNr, expectation.deepCopy)
      }
      writerOpt.foreach(writer => {
        writer.write(aggregator.aggregation, StringTag(jobPartIdentifier.jobId))
      })
      if (adjustReceive) {
        context.become(closedState)
      }
    }
  }


  override def receive: Receive = openState

  def openState: Receive = {
    case e: BadCorn[U] =>
      aggregator.add(e)
      if (useAggregatorBackpressure) sender() ! ACK
    case aggregationResult: AggregationState[V] =>
      log.info("received aggregation result event with count: {}", aggregationResult.data.count)
      aggregator.addAggregate(aggregationResult.data)
      expectation.accept(aggregationResult)
      log.info("overall partial result count: {}", aggregator.aggregation.count)
      log.debug("expectation state: {}", expectation.statusDesc)
      log.debug(s"expectation: $expectation")
      handleExpectationStateAndCloseIfFinished(true)
      if (useAggregatorBackpressure) sender() ! ACK
    case result: Corn[U] =>
      log.debug("received single result event: {}", result)
      log.debug("expectation state: {}", expectation.statusDesc)
      log.debug(s"expectation: $expectation")
      aggregator.add(result)
      expectation.accept(result)
      handleExpectationStateAndCloseIfFinished(true)
      if (useAggregatorBackpressure) sender() ! ACK
    case result@Corn(aggregator: Aggregator[ProcessingMessage[U], V]) =>
      log.debug("received aggregated result event: {}", result)
      log.debug("expectation state: {}", expectation.statusDesc)
      log.debug(s"expectation: $expectation")
      aggregator.addAggregate(aggregator.aggregation)
      expectation.accept(aggregator)
      handleExpectationStateAndCloseIfFinished(true)
      if (useAggregatorBackpressure) sender() ! ACK
    case Close =>
      log.debug("aggregator switched to closed state")
      cancellableSchedule.cancel()
      context.become(closedState)
    case ReportResults =>
      handleExpectationStateAndCloseIfFinished(true)
    case ProvideStateAndStop =>
      log.debug("Providing aggregation state and stopping aggregator")
      cancellableSchedule.cancel()
      owner ! AggregationState(aggregator.aggregation, jobPartIdentifier.jobId,
        jobPartIdentifier.batchNr, expectation.deepCopy)
      self ! PoisonPill
    case Housekeeping =>
      handleExpectationStateAndCloseIfFinished(adjustReceive = true)
    case ReportResults =>
      sender() ! AggregationState(aggregator.aggregation, jobPartIdentifier.jobId,
        jobPartIdentifier.batchNr, expectation.deepCopy)
    case e =>
      log.warning("Received unmatched msg: {}", e)
  }

  def closedState: Receive = {
    case ReportResults =>
      sender() ! AggregationState(aggregator.aggregation, jobPartIdentifier.jobId,
        jobPartIdentifier.batchNr, expectation.deepCopy)
    case _ =>
  }
}
