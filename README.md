# Kolibri Base

This project provides the mechanism to execute jobs based on akka, making use of clustering to distribute job batches.
![Alt text](images/kolibri.svg?raw=true "Kolibri Base")

## Writing tests with / without cluster startup

- the actor system for tests is started on per-test basis via letting the test extending KolibriTestKit (in case a
  cluster is needed for the test - despite being just 1-node cluster) or KolibriTestKitNoCluster (in case test needs no
  cluster at all)

## Notes on correct sender

- There are cases when sender() of a sent message is set to the default sender, which would be dead letter channel; one
  case where this happens is if a tell to some actor is made within an actor by calling an outside method that has no
  implicit actorRef: ActorRef attribute set. Here solutions are either to declare the implicit value on the external
  method (and propagating it down to where the tell is executed) or explicitly pass a sender and send it with the
  message, so that the other actor knows where to respond to.

## Troubleshoot

Connection refused:

- check ```ss -tnlp | grep :8558``` (e.g replace the port with the one used for contact probing (check discovery config/
  log messages for connection refused))
- in case firewall blocks something, might wanna check ```sudo tcpdump -n icmp```

## Build jar, build docker image, startup local

- you might temporarily need to clone kolibri-datatypes and publish locally (see kolibri-datatypes README on instructions)
- build jar (find it in target folder afterwards): ```./scripts/buildJar.sh```
- (optional) publish lib locally: ```sbt pubishLocal``` (afterwards to be found in ~/.ivy2/local)
- build docker image for local usage: ```sudo docker build . -t kolibri-base:0.1.0-alpha1```
- run single-node cluster (compute and httpserver role, access via localhost:
  8000): ```./scripts/docker_run_single_node.sh```
    - sets interface of http server to 0.0.0.0 to allow requests from hostsystem to localhost:8000 reach the service
      within the container
- start local 3-node cluster (one compute and httpserver node, two 'compute'-only nodes, access via localhost:
  8000): ```sudo docker-compose up```

## Example clustered job execution: pi-estimation with dart throws

A very simple example of a distributed job is the pi-calculation via dart throws, assuming circle bounded by square and
counting the propotion of throws landing in the circle. With increasing number of throws, the proportion should
approximate pi/4. Steps to execute this simple job example:

- build jar and docker image according to above calls
- start up cluster by ```docker-compose up``` command in project root
- example url call: ```http://localhost:8000/pi_calc?nrThrows=10000000&batchSize=10000&resultDir=/app/data```
  Note that giving the volume mount ```./kolibri-test:/app/data``` as given in the docker-compose, this will write into
  the projects root's ```kolibri-test``` folder per default into the file ```dartThrowResult.txt```. Note that the job
  is set up as a simple example that has no boundary on the nr of throws processed per second, and all flowing into
  aggregator, thus if the aggregator is asked and the response waited for, this can lead to ask timeouts. This differs
  between jobs, and most jobs take more time than simple random number pick between 0 and 1, reducing the number of
  result messages. Yet given the way the aggregating actor is instantiated in the same instance as the
  RunnableExecutionActor executing the ActorRunnable (which produces the result messages), the message passing doesnt
  need serialization but is done via ref passing.
- kill job: ```http://localhost:8000/stopJob?jobId=piCalculation```
- check status of job: ```http://localhost:8000/getJobStatus?jobId=piCalculation```

## Serialization

Within definitions of the jobs, care has to be taken to define all parts that are part of any message to be
serializable. The standard way for kolibri is the KolibriSerializable interface. Within the application.conf
section ```akka.actor.serializers```
the actual serializers are defined, while binding to specific classes is found
in ```akka.actor.serialization-bindings```. Here you will find that a serializer is bound to KolibriSerializable. Thus
on adding new definitions, make sure to make the parts extend this interface. Also, take into account that Lambda's are
not Serializable, so rather use the SerializableSupplier, SerializableFunction1 (function with one argument) and
SerializableFunction2 (function with two arguments) from the kolibri-datatypes library (or add new ones in case of more
arguments / other needs) or the respective Scala function interface, since scala functions extend Serializable. Avoid
using lambda's and java functions.

For testing purposes, within the test application.conf ```serialize-messages``` can be set true to serialize messages in
the tests even though they share the same jvm. ```serialize-creators``` does the same for Props (Props encapsulate the
info needed for an actor to create another one).

## Cluster singleton router

A cluster singleton router can be utilized via the below code snippet in case of config setting
startClusterSingletonRouter is set to true, which also causes the ClusterSingletonManager to be started at cluster
start.

```
val singletonProxyConfig: Config = ConfigFactory.load(config.SINGLETON_PROXY_CONFIG_PATH)
context.system.actorOf(
  ClusterSingletonProxy.props(
    singletonManagerPath = config.SINGLETON_MANAGER_PATH,
    settings = ClusterSingletonProxySettings.create(singletonProxyConfig)
  )
)
```

## License

The kolibri-base code is licensed under APL 2.0.