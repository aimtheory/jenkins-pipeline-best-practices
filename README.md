# Jenkins Best Practices Example repo
This repo was created for the article at:
http://www.aimtheory.com/jenkins/pipeline/continuous-delivery/2018/10/01/jenkins-pipeline-global-shared-library-best-practices-part-2.html

by Ian David Rossi

We were glad to see that our initial post on [Jenkins Global Shared Library Best Practices](http://www.aimtheory.com/jenkins/pipeline/continuous-delivery/2017/12/02/jenkins-pipeline-global-shared-library-best-practices.html) has gotten a bit of attention, particularly [from a related post on Medium.com](https://medium.com/devopslinks/a-hacky-hackers-guide-to-jenkins-scripted-pipelines-part-4-dd49fcb0d62).

However, we really want to let you know what has happened since then. While we worked really hard to adhere to the "right" way of using global shared libraries--as in using `src` mainly for all pipeline functions as well as using `vars` for pipeline global variables--it did not prove to be very practical. And so, we would like to share our new best practices for Jenkins Pipeline Global Shared Libraries.

# Not Real Groovy
Unfortunately, it turns out that Jenkins Pipelines are not real Groovy. Yes, the coding language that is used is Groovy, but Jenkins does now allow you to use Groovy to its fullest extent. Groovy objects are not "real" objects in that they don't behave as you would expect objects to work, particularly when it comes to class inheritance, subclassing and the like. When Jenkins processes any classes that you may write in `src`, they are processed in a "special Jenkins way" and more or less get modified. And you have no control over this. So any attempt to apply the object-oriented model to Jenkins Global Shared Libraries eventually falls flat on its face.

Well, without going on and on, this is just one of the many shortcomings that Jenkins is starting to show in the cloud native era, [as its original creator attests to in a recent post](https://jenkins.io/blog/2018/08/31/shifting-gears/).

# A Pragmatic Approach
In practice, we have found that storing all pipeline functions in `vars` is actually quite a bit more practical. While there is no good way to do inheritance and the like with classes, there is the added benefit of not having to instantiate an object (`yourFunction.new()`) every single time you want to use it. We have seen this model for quite some time, and were initially averse to it, because we really wanted to use Jenkins Pipelines as intended--or "the right way"--but it has turned out to be far more practical to use `vars`.

# What Does It Look Like?
This simple example will just illustrate how you can provide input to a pipeline with a simple YAML file so you can centralize all of your pipelines into one library. All of this code can be found on Github.

The Jenkins shared library example: [https://github.com/aimtheory/jenkins-pipeline-best-practices](https://github.com/aimtheory/jenkins-pipeline-best-practices)

And the example app that uses it: [https://github.com/aimtheory/jenkins-pipeline-best-practices-python-test-app](https://github.com/aimtheory/jenkins-pipeline-best-practices-python-test-app)

You would have the following folder structure in a git repo:
```
+- vars
|   +- pipelineCfg.groovy
|   +- pythonPipeline.groovy
|   +- nodeJSPipeline.groovy
```
This repo would be configured in under *Manage Jenkins* > *Configure System* in the *Global Pipeline Libraries* section. In that section Jenkins requires you give this library a `Name`. Let's assume for this example that it is named `jenkins-pipeline-library`.

Let's assume that project repos would have a `pipeline.yaml` file in the project root that would provide input to the pipeline:
```yaml
# pipeline.yaml
type: python
runTests: true
testCommand: pytest test.py
deployUponTestSuccess: true
deployToolImage: bash
deployCommand: command goes here to deploy to
deployEnvironment: staging
```
You'll notice that a couple of data points are container image names to be used. That's because we're executing all of the pipeline steps inside containers. That way, Jenkins doesn't have to be preloaded with dependencies.

A quick breakdown:
- `type` identifies what type of project it is
- `runTests` allows developers to turn testing on or off
- `testImage` specifies the container image that will have the dependencies for testing
- `testCommand` is the test command to be executed inside of the test container
- `deployUponTestSuccess` allows the developer to decide if he wants to deploy automatically after successful tests
- `deployToolImage` specifies the container image that has he dependencies for deployment tools
- `deployCommand` specifies the deployment command to run inside the deploy image
- `deployEnvironment` specifies the environment to deploy to

Then, to utilize the shared pipeline library, the `Jenkinsfile` in the root of the project repo would look like:
```groovy
@Library('jenkins-pipeline-library') _
pythonPipeline()
```
So how does it all work? First, the following function is called to get all of the configuration data from the `pipeline.yaml` file:
```groovy
// vars/pipelineCfg.groovy
def call() {
  Map pipelineCfg = readYaml(file: "${WORKSPACE}/pipeline.yaml")
  return pipelineCfg
}
```
You can see the call to this function in `pythonPipeline()`, which is called by the `Jenkinsfile`.
```groovy
// vars/pythonPipeline.groovy
def call() {
  node {
    stage('Checkout') {
      checkout scm
    }
    def p = pipelineCfg()

    if (p.runTests == true) {
      docker.image(p.testImage).inside() {
        stage('Test') {
          sh 'pip install -r requirements.txt'
          sh p.testCommand
        }
      }
    }

    if (env.BRANCH_NAME == 'master' && p.deployUponTestSuccess == true) {
      docker.image(p.deployToolImage).inside {
        stage('Deploy') {
          sh "echo ${p.deployCommand} ${p.deployEnvironment}"
        }
      }
    }
  }
}
```
You can see the logic pretty easily here. The pipeline is checking if the developer wants to run tests. If so, it uses the test image that the developer has specified to run the tests. Then, if this is the master branch and a change was just merged, then it checks if the developer wants an automatic deployment. If so, it uses the deployment image that the developer specified to deploy the app. Finally, a dummy command is run showing that the deployment command can accept an environment argument also found in the `pipeline.yaml`.

The benefits of this approach are many, and probably not all covered below:
- Developers don't need to know how to write (quasi) Groovy code
- The interface in pipeline.yaml is really flexible, where entire data structures, lists, etc. can be passed as input to the pipeline
- All pipeline stages are run inside containers so Jenkins doesn't need to be preloaded with (test and deployment) dependencies

An original design we had was that `Jenkinsfiles` could actually just look more generic, like this:
```groovy
@Library('jenkins-pipeline-library') _
runPipeline()
```
and `runPipeline()` would just read the the project type from `pipeline.yaml` and dynamically run the proper function, like `pythonPipeline()`, `rubyPipeline()` or `nodeJSPipeline()`. But once again, because pipelines are really only quasi-Groovy, this isn't possible, and it's confirmed here: [https://issues.jenkins-ci.org/browse/JENKINS-37210](https://issues.jenkins-ci.org/browse/JENKINS-3721). So the downside is that each project would have to have a different-looking `Jenkinsfile`. But we're not done looking for a solution to that!

So, what do you think? We would love to hear your comments about this approach. In conclusion:
- Shield developers from Jenkins Pipelines and Groovy by adding a level of abstraction
- Run your pipeline stages in containers so that they are easily portable between CI systems
- Share pipeline logic for a more sustainable pipeline development

But we're not done yet! Stay tuned for more. Our next post will be about how you can actually get out of Groovy coding in Jenkins pipelines and write pipelines in any language you want!
