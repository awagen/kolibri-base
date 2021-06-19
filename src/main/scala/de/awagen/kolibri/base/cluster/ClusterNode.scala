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

package de.awagen.kolibri.base.cluster

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.stream.Materializer
import de.awagen.kolibri.base.actors.routing.RoutingActor
import de.awagen.kolibri.base.config.AppConfig
import de.awagen.kolibri.base.config.AppConfig.config
import de.awagen.kolibri.base.config.AppConfig.config.node_roles
import de.awagen.kolibri.base.http.server.BaseRoutes._
import de.awagen.kolibri.base.http.server.{BaseRoutes, HttpServer}
import org.slf4j.{Logger, LoggerFactory}

import java.util.Objects
import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}


/**
  * App object to start a new cluster node
  */
object ClusterNode extends App {

  val logger: Logger = LoggerFactory.getLogger(ClusterNode.getClass.toString)
  private[this] var setup: SystemSetup = _
  if (args.length > 0 && args(0).toBoolean) {
    startSystemSetup(None)
  }

  def getSystemSetup: SystemSetup = {
    if (Objects.nonNull(setup)) {
      setup
    }
    else {
      startSystemSetup(None)
      setup
    }

  }

  def startSystemSetup(route: Option[Route]): Unit = {
    synchronized {
      if (Objects.isNull(setup)) {
        setup = SystemSetup(route)
        logger.info("System setup initialized")
      }
      else {
        logger.warn("System setup already initialized, ignoring")
      }
    }
  }

  case class SystemSetup(route: Option[Route] = None) {

    implicit val actorSystem: ActorSystem = startSystem()
    implicit val mat: Materializer = Materializer(actorSystem)
    implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
    // need to initialiize the BaseRoutes to start Supervisor actor in current actorSystem
    BaseRoutes.init
    val usedRoute: Route = route.getOrElse(simpleHelloRoute ~ streamingUserRoutes ~ clusterStatusRoutee ~ killAllJobs
      ~ getJobStatus ~ killJob ~ getJobWorkerStatus ~ getRunningJobIds ~ executeDistributedPiCalculationExample ~ startSearchEval
      ~ executeDistributedPiCalculationExampleWithoutSerialization)
    val isHttpServerNode: Boolean = node_roles.contains(config.HTTP_SERVER_ROLE)

    logger.info(s"Node roles: $node_roles")
    logger.info(s"isHttpServerNode: $isHttpServerNode")

    if (isHttpServerNode) {
      logger.info("Starting httpserver")
      HttpServer.startHttpServer(usedRoute, interface = config.http_server_host, port = config.http_server_port).onComplete {
        case Success(serverBinding) => logger.info(s"listening to ${serverBinding.localAddress}")
        case Failure(error) => logger.info(s"error on server start: ${error.getMessage}")
      }
    }

    /**
      * @return
      */
    def startSystem(): ActorSystem = {
      val system = ActorSystem(config.applicationName, AppConfig.config.baseConfig)
      AkkaManagement(system).start()
      ClusterBootstrap(system).start()
      Cluster(system).registerOnMemberUp({
        if (config.startClusterSingletonRouter) {
          ClusterSingletonUtils.createClusterSingletonManager(system, RoutingActor.defaultProps)
        }
        logger.info("Cluster is up!")
      })
      system
    }
  }

}
