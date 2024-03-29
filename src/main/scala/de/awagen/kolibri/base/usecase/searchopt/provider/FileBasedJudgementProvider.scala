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


package de.awagen.kolibri.base.usecase.searchopt.provider

import de.awagen.kolibri.base.config.AppConfig.persistenceModule.persistenceDIModule
import de.awagen.kolibri.base.io.reader.{FileReaderUtils, Reader}
import de.awagen.kolibri.base.usecase.searchopt.parse.TypedJsonSelectors.{SingleValueSelector, TypedJsonSeqSelector}
import play.api.libs.json.Json

import scala.io.Source


object FileBasedJudgementProvider {

  case class JudgementFileCSVFormatConfig(judgement_list_delimiter: String,
                                          judgement_file_columns: Int,
                                          judgement_file_judgement_column: Int,
                                          judgement_file_search_term_column: Int,
                                          judgement_file_product_id_column: Int)

  val defaultJudgementFileFormatConfig: JudgementFileCSVFormatConfig = JudgementFileCSVFormatConfig(
    judgement_list_delimiter = "\u0000",
    judgement_file_columns = 3,
    judgement_file_judgement_column = 2,
    judgement_file_search_term_column = 0,
    judgement_file_product_id_column = 1)

  /**
    * file based judgement provider assuming the file format is CSV
    *
    * @param filepath                  - file path
    * @param judgementFileFormatConfig - the column config indicating from which columns to extract the data
    * @param queryProductDelimiter     - separator of query and productId to use when creating the key to store the judgement under
    * @return
    */
  def createCSVBasedProvider(filepath: String,
                             judgementFileFormatConfig: JudgementFileCSVFormatConfig = defaultJudgementFileFormatConfig,
                             queryProductDelimiter: String = "\u0000"): FileBasedJudgementProvider = {
    new FileBasedJudgementProvider(filepath,
      persistenceDIModule.reader,
      csvSourceToJudgementMapping(
        judgementFileFormatConfig = judgementFileFormatConfig,
        queryProductDelimiter = queryProductDelimiter
      ),
      queryProductDelimiter = queryProductDelimiter
    )
  }

  /**
    * Creates the file based judgement provider assuming the file contains per line a json that represents the data state for a single query
    *
    * @param filepath               - file path
    * @param jsonQuerySelector      - selector to extract the query from a single json (in this case a single line in the file)
    * @param jsonProductsSelector   - selector to retrieve all products in order of appearance
    * @param jsonJudgementsSelector - selector to retrieve the judgements in order of appearance
    * @param queryProductDelimiter  - separator of query and productId to use when creating the key to store the judgement under
    * @return
    */
  def createJsonLineBasedProvider(filepath: String,
                                  jsonQuerySelector: SingleValueSelector[Any],
                                  jsonProductsSelector: TypedJsonSeqSelector,
                                  jsonJudgementsSelector: TypedJsonSeqSelector,
                                  queryProductDelimiter: String = "\u0000"): FileBasedJudgementProvider = {
    new FileBasedJudgementProvider(
      filepath,
      persistenceDIModule.reader,
      jsonLineSourceToJudgementMapping(
        jsonQuerySelector,
        jsonProductsSelector,
        jsonJudgementsSelector,
        queryProductDelimiter)
    )
  }

  def convertStringOrDoubleAnyToDouble(any: Any): Double = {
    any match {
      case str: String => str.toDouble
      case _ => any.asInstanceOf[Double]
    }
  }

  /**
    * This assumes that the judgement file contains one json per line.
    * An example could be {"query", "products": [{"product_id": "abc", "score": 0.231}, ...]}, and the passed selectors
    * need to take the specifics of the format into account
    *
    * @param jsonQuerySelector      - selector to extract the query per json
    * @param jsonProductsSelector   - selector to extract the product_ids per json
    * @param jsonJudgementsSelector - selector to extract the judgements per json
    * @param queryProductDelimiter  - delimiter used to combine query and product to a single key
    * @return
    */
  def jsonLineSourceToJudgementMapping(jsonQuerySelector: SingleValueSelector[Any],
                                       jsonProductsSelector: TypedJsonSeqSelector,
                                       jsonJudgementsSelector: TypedJsonSeqSelector,
                                       queryProductDelimiter: String = "\u0000"): Source => Map[String, Double] = {
    source => {
      source.getLines()
        .map(line => line.trim)
        .filter(line => line.nonEmpty)
        .map(line => Json.parse(line))
        .flatMap(jsValue => {
          val query: String = jsonQuerySelector.select(jsValue).getOrElse("").asInstanceOf[String]
          val products: Seq[String] = jsonProductsSelector.select(jsValue).asInstanceOf[Seq[String]]
          val judgements: Seq[Double] = jsonJudgementsSelector.select(jsValue).map(x => convertStringOrDoubleAnyToDouble(x))
          val keys: Seq[String] = products.map(product => s"$query$queryProductDelimiter$product")
          keys zip judgements
        })
        .toMap
    }
  }

  /**
    * Transform a source in csv format into the judgement mapping.
    * The columns are taken from the respective format config
    *
    * @param judgementFileFormatConfig - config of columns
    * @param queryProductDelimiter     - the delimiter used to create the query - product - keys
    * @return
    */
  def csvSourceToJudgementMapping(judgementFileFormatConfig: JudgementFileCSVFormatConfig = defaultJudgementFileFormatConfig,
                                  queryProductDelimiter: String = "\u0000"): Source => Map[String, Double] = {
    FileReaderUtils.mappingFromCSVSource[Double](
      judgementFileFormatConfig.judgement_list_delimiter,
      judgementFileFormatConfig.judgement_file_columns,
      x => s"${x(judgementFileFormatConfig.judgement_file_search_term_column)}$queryProductDelimiter${x(judgementFileFormatConfig.judgement_file_product_id_column)}",
      x => x(judgementFileFormatConfig.judgement_file_judgement_column).toDouble)
  }


}

/**
  * File based judgement provider. Takes distinct mapping functions depending on the format
  *
  * @param filepath                     - path to the file
  * @param fileReader                   - reader to use
  * @param sourceToJudgementMappingFunc - mapping function of source to judgement mapping (assuming key = [query][queryProductDelimiter][productId]
  * @param queryProductDelimiter        - separator of query and productId for key generation
  */
private[provider] class FileBasedJudgementProvider(filepath: String,
                                                   fileReader: Reader[String, Seq[String]],
                                                   sourceToJudgementMappingFunc: Source => Map[String, Double],
                                                   queryProductDelimiter: String = "\u0000")
  extends JudgementProvider[Double] {

  private val judgementStorage: Map[String, Double] = readJudgementsFromFile(filepath)

  private[provider] def readJudgementsFromFile(filepath: String): Map[String, Double] = {
    sourceToJudgementMappingFunc.apply(fileReader.getSource(filepath))
  }

  override def retrieveJudgement(searchTerm: String, productId: String): Option[Double] = {
    judgementStorage.get(createKey(searchTerm, productId))
  }

  private[provider] def createKey(searchTerm: String, productId: String): String = {
    s"$searchTerm$queryProductDelimiter$productId"
  }

  private[provider] def keyToSearchTermAndProductId(key: String): (String, String) = {
    val parts = key.split(queryProductDelimiter)
    (parts.head, parts(1))
  }

  override def allJudgements: Map[String, Double] = collection.immutable.Map[String, Double]() ++ judgementStorage

  override def retrieveJudgementsForTerm(searchTerm: String): Map[String, Double] = judgementStorage
    .map(x => (keyToSearchTermAndProductId(x._1), x._2))
    .filter(x => x._1._1 == searchTerm)
    .map(x => (x._1._2, x._2))
}
