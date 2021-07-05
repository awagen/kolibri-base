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


package de.awagen.kolibri.base.usecase.searchopt.jobdefinitions.parts

import akka.http.scaladsl.model.{HttpMethods, HttpProtocols}
import de.awagen.kolibri.base.actors.work.worker.ProcessingMessages.ProcessingMessage
import de.awagen.kolibri.base.http.client.request.{RequestTemplate, RequestTemplateBuilder}
import de.awagen.kolibri.datatypes.tagging.{TagType, Tags}

object RequestTemplatesAndBuilders {

  /**
    * Supplier of RequestTemplateBuilder, e.g to generate fresh instances per new processing element generated
    *
    * @param contextPath : String - The actual context path for the request. On which host, port and connection type
    *                    the request lands is defined by the Connections used for the processing flow.
    * @param fixedParams : Map[String, Seq[String]] - Mapping of the actual parameters used as fix parameters (one parameter name
    *                    can hold multiple values, all would be set in the corresponding request)
    * @return
    */
  def getRequestTemplateBuilderSupplier(contextPath: String,
                                        fixedParams: Map[String, Seq[String]]): () => RequestTemplateBuilder = () => {
    new RequestTemplateBuilder()
      .withContextPath(contextPath)
      .withProtocol(HttpProtocols.`HTTP/1.1`)
      .withHttpMethod(HttpMethods.GET)
      .withParams(fixedParams)
  }


  /**
    * tagging actual ProcessingMessage[RequestTemplate], allowing using properties of RequestTemplate to use
    * for aggregation.
    *
    * @param param : String - The actual parameter name used to tag the processed element with the parameter's value
    * @return
    */
  def taggerByParameter(param: String): ProcessingMessage[RequestTemplate] => ProcessingMessage[RequestTemplate] = {
    msg => {
      msg.withTags(TagType.AGGREGATION,
        Set(Tags.ParameterSingleValueTag(
          Map(param -> msg.data.getParameter(param).map(y => y.mkString("-")).getOrElse(""))
        ))
      )
    }
  }

}
