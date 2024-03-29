### Steps
- install docker
- install kubectl
- install kubens (namespace management): https://github.com/ahmetb/kubectx#installation
    - Linux: ```sudo apt install kubectx```
    - or: 
  ``` 
      wget https://raw.githubusercontent.com/ahmetb/kubectx/master/kubectx
      wget https://raw.githubusercontent.com/ahmetb/kubectx/master/kubens
      chmod +x kubectx kubens
      sudo mv kubens kubectx /usr/local/bin
  ```
- install helm (https://helm.sh/docs/intro/install/):
  - e.g via install script:
  ```
  curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/master/scripts/get-helm-3
  chmod 700 get_helm.sh
  ./get_helm.sh
  ```
- create cluster: ```kind create cluster --name kind1```
- kolibri namespace: ```kubectl create namespace kolibri```
- switching to namespace: ```kubens kolibri```
- installing helm chart for httpserver: 
  ```helm install kolibri-cluster --debug ./kolibri-service```
- uninstall httpserver install: ```helm uninstall kolibri-cluster```
- delete kind cluster: ```kind delete cluster```


### Use local registry with kind
As documented here https://kind.sigs.k8s.io/docs/user/local-registry/
as of now local docker registry has to be created and images pushed to 
that specific registry. The script located at ```./scripts/kind-with-registry.sh```
(delete and remove registry when you dont need it anymore with 
```docker container stop kind-registry && docker container rm -v kind-registry```)
reflects the script provided in the above link and sets up a local registry
for kind and starts kind cluster with that registry enabled.
Note that it also defines extraMounts, mounting a local directory into kind cluster that 
can then be used in persistent volume definition. This is needed to access files from local file
system on your machine. **Make sure to adjust the _hostPath_ setting in the script before executing!** 
After it is executed, images can be pushed and used as follows:
- (Optional) docker pull (random example image), e.g : ```docker pull gcr.io/google-samples/hello-app:1.0```
- tagging image to use local kind registry: ```docker tag gcr.io/google-samples/hello-app:1.0 localhost:5000/hello-app:1.0```
- push image to local kind registry: ```docker push localhost:5000/hello-app:1.0```
- image can then be used as follows: ```kubectl create deployment hello-server --image=localhost:5000/hello-app:1.0```
- any local image tagged for the new registry and pushed there 
  can be used within the kind deployment, e.g for image tagged and pushed as localhost:5000/image:foo
  can be used within deployment as image localhost:5000/image:foo.
  
In our case (substitute version accordingly to the version used in docker image tag):
- ./scripts/kind-with-registry.sh (needed once to create repo and start kind cluster with repo enabled)
- Then we tag and push push all images to make them available within kind registry:
  - ```docker tag kolibri-base:0.1.0-rc0 localhost:5000/kolibri-base:0.1.0-rc0```
  - ```docker tag response-juggler:0.1.0 localhost:5000/response-juggler:0.1.0```
  - ```docker push localhost:5000/kolibri-base:0.1.0-rc0```
  - ```docker push localhost:5000/response-juggler:0.1.0```
- create namespace: ```kubectl create namespace kolibri```  
- switch namespace: ```kubens kolibri```  
- install kolibri service (in helm-charts folder): ```helm install kolibri-cluster --debug ./kolibri-service```
- install response-juggler (in helm-charts folder): ```helm install response-juggler --debug ./response-juggler```
- uninstall service: ```helm uninstall kolibri-cluster```
- uninstall response-juggler: ```helm uninstall response-juggler```
- to be able to access apps from local host, use port forwarding: 
  - service: ```kubectl port-forward [kolibri-service-httpserver-pod-name] 80:8000```
  - juggler: ```kubectl port-forward [juggler-podName] 81:80```
``` curl localhost:80/hello``` should provide a response (see: https://kubernetes.io/docs/tasks/access-application-cluster/port-forward-access-application-cluster/)
- if you want to ssh into a pod: ```kubectl exec -it [pod-name] -- sh``` 
- after you executed the command for port-forward on the kolibri httpserver pod, you can execute ```start_searcheval_qFromFile_kindCluster.sh```
  (located in scripts-folder) to execute an example grid search evaluation (note that both the response-juggler provides randomly
  shuffled results and the judgement list used to evaluate is also just an example with random judgements).
  Note that for this to work, both the install commands for kolibri-cluster and response-juggler need to be executed.
- for scaling up compute pods: ```kubectl scale --replicas=4 deployment/kolibri-service-compute```
  
### Using hpa locally
- to allow hpa to pickup pod metrics locally, metrics server needs to be installed:
https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale-walkthrough/