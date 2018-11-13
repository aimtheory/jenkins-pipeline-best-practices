def call() {
  stage('Checkout') {
    checkout scm
  }
  Eval.me("#{pipelineCfg()['pipelineType']}Pipeline()")
}
