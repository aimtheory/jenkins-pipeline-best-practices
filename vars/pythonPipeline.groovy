def call() {
  docker.image(pipelineCfg()['pipelineType']).inside {
    stage('Test') {
      sh pipelineCfg()['testCommand']
    }
  }

  docker.image(pipelineCfg()['deployToolImage'].inside {
    stage('Deploy') {
      sh pipelineCfg()['deployCommand']
    }
  }
}
