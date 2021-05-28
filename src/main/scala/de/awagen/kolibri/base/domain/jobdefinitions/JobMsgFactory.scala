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

package de.awagen.kolibri.base.domain.jobdefinitions

import akka.NotUsed
import akka.actor.Props
import akka.stream.scaladsl.Flow
import de.awagen.kolibri.base.actors.work.aboveall.SupervisorActor.{ProcessActorRunnableJobCmd, ProcessActorRunnableTaskJobCmd}
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.base.io.writer.Writers.Writer
import de.awagen.kolibri.base.processing.execution.expectation.ExecutionExpectation
import de.awagen.kolibri.base.processing.execution.job.{ActorRunnable, ActorRunnableSinkType}
import de.awagen.kolibri.base.processing.execution.task.Task
import de.awagen.kolibri.datatypes.ClassTyped
import de.awagen.kolibri.datatypes.collections.generators.IndexedGenerator
import de.awagen.kolibri.datatypes.mutable.stores.TypeTaggedMap
import de.awagen.kolibri.datatypes.tagging.TaggedWithType
import de.awagen.kolibri.datatypes.tagging.Tags.Tag
import de.awagen.kolibri.datatypes.types.DataStore
import de.awagen.kolibri.datatypes.types.SerializableCallable.{SerializableFunction1, SerializableSupplier}
import de.awagen.kolibri.datatypes.values.aggregation.Aggregators.Aggregator

import scala.concurrent.duration._


/**
  * Provide helper methods take data and definitions whats to be computed and generate a
  * ProcessActorRunnableJobCmd or ProcessActorRunnableTaskJobCmd thats passable to Supervisor
  * to manage execution of the job
  */
object JobMsgFactory {


  def createActorRunnableJobCmd[T, V, V1, V2, W](jobId: String,
                                             data: T,
                                             dataBatchGenerator: SerializableFunction1[T, IndexedGenerator[Batch[V]]],
                                             transformerFlow: Flow[V, ProcessingMessage[V1], NotUsed],
                                             processingActorProps: Option[Props],
                                             expectationGenerator: SerializableFunction1[Int, ExecutionExpectation],
                                             aggregatorSupplier: SerializableSupplier[Aggregator[TaggedWithType[Tag] with DataStore[V2], W]],
                                             writer: Writer[W, Tag, Any],
                                             returnType: ActorRunnableSinkType.Value,
                                             allowedTimePerBatchInSeconds: Long,
                                             allowedTimeForJobInSeconds: Long): ProcessActorRunnableJobCmd[V, V1, V2, W] = {
    val batches: IndexedGenerator[Batch[V]] = dataBatchGenerator.apply(data)
    val mapFunc: SerializableFunction1[Batch[V], ActorRunnable[V, V1, V2, W]] = new SerializableFunction1[Batch[V], ActorRunnable[V, V1, V2, W]] {
      override def apply(v1: Batch[V]): ActorRunnable[V, V1, V2, W] = ActorRunnable(
        jobId = jobId,
        batchNr = v1.batchNr,
        supplier = v1.data,
        transformer = transformerFlow,
        processingActorProps = processingActorProps,
        expectationGenerator = expectationGenerator,
        aggregationSupplier = aggregatorSupplier,
        returnType = returnType,
        1 minute,
        1 minute)
    }
    val actorRunnableBatches: IndexedGenerator[ActorRunnable[V, V1, V2, W]] = batches.mapGen(mapFunc)
    new ProcessActorRunnableJobCmd[V, V1, V2, W](
      jobId = jobId,
      processElements = actorRunnableBatches,
      aggregatorSupplier = aggregatorSupplier,
      writer = writer,
      allowedTimePerBatch = FiniteDuration(allowedTimePerBatchInSeconds, SECONDS),
      allowedTimeForJob = FiniteDuration(allowedTimeForJobInSeconds, SECONDS)
    )
  }


  def createActorRunnableTaskJobCmd[T, W](jobId: String,
                                          data: T,
                                          dataBatchGenerator: SerializableFunction1[T, IndexedGenerator[Batch[TypeTaggedMap with TaggedWithType[Tag]]]],
                                          resultDataKey: ClassTyped[ProcessingMessage[Any]],
                                          tasks: Seq[TaskDefinitions.Val[Any]],
                                          aggregatorSupplier: SerializableSupplier[Aggregator[TaggedWithType[Tag] with DataStore[Any], W]],
                                          writer: Writer[W, Tag, Any],
                                          allowedTimePerBatchInSeconds: Long,
                                          allowedTimeForJobInSeconds: Long): ProcessActorRunnableTaskJobCmd[W] = {
    val batches: IndexedGenerator[Batch[TypeTaggedMap with TaggedWithType[Tag]]] = dataBatchGenerator.apply(data)
    val taskMapFunc: SerializableFunction1[TaskDefinitions.Val[Any], Task[_]] = new SerializableFunction1[TaskDefinitions.Val[Any], Task[_]] {
      override def apply(v1: TaskDefinitions.Val[Any]): Task[_] = v1.task
    }
    new ProcessActorRunnableTaskJobCmd[W](
      jobId = jobId,
      dataIterable = batches,
      tasks = tasks.map(taskMapFunc),
      resultKey = resultDataKey,
      aggregatorSupplier = aggregatorSupplier,
      writer = writer,
      allowedTimePerBatch = FiniteDuration(allowedTimePerBatchInSeconds, SECONDS),
      allowedTimeForJob = FiniteDuration(allowedTimeForJobInSeconds, SECONDS)
    )
  }

}
