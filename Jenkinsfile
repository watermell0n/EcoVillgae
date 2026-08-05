pipeline {
    agent any

    parameters {
        booleanParam(
            name: 'REQUIRE_DB_TESTS',
            defaultValue: true,
            description: 'Docker 기반 PostgreSQL Testcontainers 테스트의 skip을 실패로 처리'
        )
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        CI = 'true'
    }

    stages {
        stage('Toolchain') {
            steps {
                sh 'java -version'
                sh 'node --version'
                sh 'terraform version'
                sh 'docker version'
            }
        }

        stage('Contracts and Schema') {
            steps {
                sh 'node scripts/validate-contract-coverage.mjs'
                sh 'node scripts/validate-api-schema-consistency.mjs'
                sh 'node scripts/validate-ai-internal-contract.mjs'
                sh 'node scripts/validate-spring-openapi-contract.mjs'
                sh 'node scripts/validate-data-model.mjs'
                sh 'node scripts/validate-physical-schema.mjs'
                sh 'node scripts/validate-database-implementation.mjs'
                sh 'node scripts/validate-infrastructure.mjs'
            }
        }

        stage('Terraform') {
            steps {
                dir('infra/terraform') {
                    sh 'terraform fmt -check -recursive'
                    sh 'terraform init -backend=false -input=false'
                    sh 'terraform validate'
                }
            }
        }

        stage('Backend') {
            steps {
                sh 'chmod +x backend/gradlew'
                dir('backend') {
                    sh './gradlew clean test build --no-daemon'
                }
            }
        }

        stage('Backend Image') {
            when {
                expression {
                    return fileExists('backend/Dockerfile')
                }
            }
            steps {
                sh 'docker build --tag ecovillage-backend:${BUILD_NUMBER} --file backend/Dockerfile .'
            }
        }

        stage('PostgreSQL Test Guard') {
            when {
                expression {
                    return params.REQUIRE_DB_TESTS
                }
            }
            steps {
                sh '''
                    report="backend/build/test-results/test/TEST-com.b201.ecovillage.SchemaIntegrityTest.xml"
                    test -f "$report"
                    grep -q '<testcase' "$report"
                    if grep -Eq 'skipped="[1-9][0-9]*"' "$report"; then
                        echo "PostgreSQL Testcontainers tests were skipped."
                        exit 1
                    fi
                '''
            }
        }

        stage('Student App') {
            when {
                expression {
                    return fileExists('student-app/expo/package.json')
                }
            }
            steps {
                dir('student-app/expo') {
                    sh 'npm ci'
                    sh 'npm run typecheck'
                    sh 'npx expo export --platform web --output-dir .expo-export-check'
                    sh 'test -f .expo-export-check/index.html'
                }
            }
        }

        stage('Teacher Web') {
            when {
                expression {
                    return fileExists('teacher-web/package.json')
                }
            }
            steps {
                dir('teacher-web') {
                    sh 'corepack enable'
                    sh 'pnpm install --frozen-lockfile'
                    sh 'pnpm typecheck'
                    sh 'pnpm build'
                    sh 'test -f .next/BUILD_ID'
                }
            }
        }

        stage('AI Service') {
            when {
                expression {
                    return fileExists('ai-service/pyproject.toml')
                }
            }
            steps {
                dir('ai-service') {
                    sh 'python -m pip install --upgrade pip'
                    sh 'python -m pip install -e ".[dev]"'
                    sh 'python -m ruff check src tests'
                    sh 'python -m pytest tests'
                }
            }
        }
    }

    post {
        always {
            junit(
                testResults: 'backend/build/test-results/test/*.xml',
                allowEmptyResults: true
            )
        }
        success {
            archiveArtifacts(
                artifacts: 'backend/build/libs/*.jar',
                fingerprint: true,
                allowEmptyArchive: true
            )
        }
    }
}
