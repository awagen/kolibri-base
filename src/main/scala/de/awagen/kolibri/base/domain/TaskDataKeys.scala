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

package de.awagen.kolibri.base.domain

import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.base.processing.failure.TaskFailType.TaskFailType
import de.awagen.kolibri.datatypes.ClassTyped
import de.awagen.kolibri.datatypes.stores.MetricRow

import scala.reflect.runtime.universe._


object TaskDataKeys extends Enumeration {
  type TaskDataKeys = Val[_]

  case class Val[T: TypeTag](identifier: String) extends ClassTyped[T]

  val METRICS: TaskDataKeys.Val[MetricRow] = Val("metrics")
  val METRICS_PM: TaskDataKeys.Val[ProcessingMessage[MetricRow]] = Val("metrics_message")
  val METRICS_FAILED: TaskDataKeys.Val[TaskFailType] = Val("metrics generation failed")

}



