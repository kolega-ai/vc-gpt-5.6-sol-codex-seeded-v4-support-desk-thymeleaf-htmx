pipeline {
  agent any
  stages {
    stage('Build and test') { steps { sh './gradlew clean test bootJar' } }
    stage('Reports') { steps { junit allowEmptyResults: false, testResults: '**/build/test-results/test/*.xml' } }
  }
  post { always { archiveArtifacts artifacts: '**/build/libs/*.jar', fingerprint: true } }
}
