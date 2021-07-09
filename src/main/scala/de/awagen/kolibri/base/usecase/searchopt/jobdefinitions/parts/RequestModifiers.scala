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

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpEntity}
import de.awagen.kolibri.base.http.client.request.RequestTemplateBuilder
import de.awagen.kolibri.base.processing.modifiers.Modifier
import de.awagen.kolibri.base.processing.modifiers.RequestTemplateBuilderModifiers.{BodyModifier, HeaderModifier, RequestParameterModifier}
import de.awagen.kolibri.datatypes.collections.generators.{ByFunctionNrLimitedIndexedGenerator, IndexedGenerator}
import de.awagen.kolibri.datatypes.multivalues.OrderedMultiValues

import scala.collection.immutable

object RequestModifiers {

  /**
    * Transform OrderedMultiValues into multiple generators of RequestTemplateBuilder modifiers.
    * Specifically RequestParameterModifiers, setting the defined request parameter values when applied.
    *
    * @param multiValues - the distinct values to take into account. One generator per single value name.
    * @return
    */
  def multiValuesToRequestParamModifiers(multiValues: OrderedMultiValues, replace: Boolean): Seq[IndexedGenerator[RequestParameterModifier]] = {
    multiValues.values
      .map(x => ByFunctionNrLimitedIndexedGenerator.createFromSeq(
        x.getAll.map(y =>
          RequestParameterModifier(
            params = immutable.Map(x.name -> Seq(y.toString)),
            replace = replace)
        )
      ))
  }

  /**
    * Transform OrderedMultiValues into multiple generators of RequestTemplateBuilder modifiers.
    * Specifically HeaderModifiers, setting the defined header values when applied.
    *
    * @param multiValues - the distinct values to take into account. One generator per single value name.
    * @return
    */
  def multiValuesToHeaderModifiers(multiValues: OrderedMultiValues, replace: Boolean): Seq[IndexedGenerator[HeaderModifier]] = {
    multiValues.values
      .map(x => ByFunctionNrLimitedIndexedGenerator.createFromSeq(
        x.getAll.map(y =>
          HeaderModifier(
            headers = Seq(RawHeader(x.name, y.toString)),
            replace = replace
          )
        )
      ))
  }

  /**
    * Given sequence of bodies of given content type, create generator of BodyModifiers.
    * When applied, those modifiers set the respective body for the RequestTemplateBuilder.
    *
    * @param bodies : Seq of the distinct body values
    * @return
    */
  def bodiesToBodyModifier(bodies: Seq[String], contentType: ContentType = ContentTypes.`application/json`): IndexedGenerator[BodyModifier] = {
    ByFunctionNrLimitedIndexedGenerator.createFromSeq(bodies).mapGen(bodyValue => {
      BodyModifier(HttpEntity(contentType, bodyValue.getBytes))
    })
  }

  /**
    * Container for the distinct values to generate Modifier[RequestTemplateBuilder] from. One single parameter name
    * in the case of params and headers a generator is created providing all defined values.
    * For bodies its a single generator providing all distinct body values. bodyContentType determines the format
    * of the passed body
    *
    * @param params
    * @param headers
    * @param bodies
    * @param bodyContentType
    */
  case class RequestPermutation(params: OrderedMultiValues,
                                headers: OrderedMultiValues,
                                bodies: Seq[String],
                                bodyContentType: ContentType = ContentTypes.`application/json`) {
    val paramModifierGenerators: Seq[IndexedGenerator[RequestParameterModifier]] = multiValuesToRequestParamModifiers(params, replace = false)
      .filter(gen => gen.size > 0)
    val headerModifierGenerators: Seq[IndexedGenerator[HeaderModifier]] = multiValuesToHeaderModifiers(headers, replace = false)
      .filter(gen => gen.size > 0)
    val bodyModifierGenerator: Option[IndexedGenerator[BodyModifier]] = if (bodies.isEmpty) None else Some(bodiesToBodyModifier(bodies, bodyContentType))

    def getModifierSeq: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = {
      val combined: Seq[IndexedGenerator[Modifier[RequestTemplateBuilder]]] = paramModifierGenerators ++ headerModifierGenerators
      bodyModifierGenerator.map(x => combined :+ x).getOrElse(combined)
    }
  }


}
