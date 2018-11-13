def call() {
  Map pipelineCfg = readYaml(file: "${WORKSPACE}/pipeline.yaml")
  return pipelineCfg
}
