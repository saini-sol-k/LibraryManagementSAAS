/*
 * End-to-end CI/CD for the Library SaaS backend.
 *
 *   Checkout -> Preflight -> Maven Build -> Tests -> Docker Build -> Push to local
 *   registry -> Kubernetes Deploy -> Rollout Verification -> Health Check ->
 *   Smoke Tests -> Security Verification
 *
 * Agent: Windows, Windows PowerShell 5.1. No PowerShell 7-only parameters are used
 * (notably -SkipHttpErrorCheck, which does not exist on 5.1).
 *
 * Environment facts this pipeline depends on, all verified on this machine:
 *  - The Kubernetes node (desktop-control-plane) keeps its own containerd image
 *    store, so a locally built image is NOT visible to it. Images are pushed to a
 *    registry container on the host and pulled back by the node at 192.168.65.254.
 *  - NodePort is not published to the Windows host on this node, so the service is
 *    ClusterIP and reached through `kubectl port-forward`.
 *  - MySQL runs as a host Docker container on port 3310 (library-saas-mysql), a
 *    dedicated instance for this project, not inside the cluster.
 *
 * Required Jenkins credentials:
 *   library-saas-jwt-secret  (Secret text)      >= 32 bytes
 *   library-saas-mysql       (Username/password)
 */

pipeline {

    agent any

    // Jenkins build steps inherit the machine PATH, where java resolves to JDK 17.
    // This pins JDK 21 for every step and sets JAVA_HOME so Maven compiles with it.
    tools {
        jdk "jdk21"
    }

    options {
        timestamps()
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '5'))
    }

    parameters {
        booleanParam(
            name: 'RUN_INTEGRATION_TESTS',
            defaultValue: true,
            description: 'Full 104-test suite. Requires Docker on the agent for Testcontainers. ' +
                         'Uncheck for unit tests only (-Punit-tests).')
        string(
            name: 'K8S_NAMESPACE',
            defaultValue: 'library-saas',
            description: 'Target Kubernetes namespace.')
        string(
            name: 'REGISTRY',
            defaultValue: '192.168.65.254:5000',
            description: 'Local registry as addressed BY THE CLUSTER NODE. Pushes go to ' +
                         'localhost:5000, which is the same registry container.')
        string(
            name: 'VERIFY_PORT',
            defaultValue: '8095',
            description: 'Local port used for the port-forward during verification. ' +
                         'Must not collide with 8080/8081/8082/8090.')
    }

    environment {
        // Pipeline parameters are only injected into the shell environment once they
        // have been registered by a first run, so they are re-exported here to make
        // every stage independent of that. $env:K8S_NAMESPACE etc. are then always set.
        K8S_NAMESPACE = "${params.K8S_NAMESPACE}"
        REGISTRY      = "${params.REGISTRY}"
        VERIFY_PORT   = "${params.VERIFY_PORT}"

        IMAGE_NAME   = "library-saas-backend"
        APP_JAR      = 'target\\library-saas-backend-0.0.1-SNAPSHOT.jar'
        DEPLOYMENT   = 'library-saas-backend'
        PF_PID_FILE  = 'deploy\\portforward.pid'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_SHA = bat(script: '@git rev-parse --short HEAD', returnStdout: true).trim()
                    env.IMAGE_TAG = "${env.BUILD_NUMBER}-${env.GIT_SHA}"
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.GIT_SHA}"
                    echo "Image tag for this build: ${env.IMAGE_TAG}"
                }
            }
        }

        stage('Preflight') {
            steps {
                powershell '''
                    $ErrorActionPreference = "Stop"

                    # cmd /c avoids PowerShell 5.1 wrapping native stderr in ErrorRecords.
                    $javaOut = cmd /c "java -version 2>&1"
                    $first = @($javaOut)[0]
                    Write-Host "java   : $first"
                    if ($first -notmatch '"(2[1-9]|[3-9][0-9])') {
                        throw "JDK 21 or newer required on the agent. Found: $first"
                    }

                    foreach ($t in @("mvn -v","docker --version","kubectl version --client=true -o yaml","git --version")) {
                        $o = cmd /c "$t 2>&1"
                        if ($LASTEXITCODE -ne 0) { throw "Preflight failed running: $t" }
                        Write-Host ("{0,-8}: {1}" -f $t.Split(" ")[0], (@($o)[0]))
                    }

                    # Cluster must be reachable before anything else is attempted.
                    $nodes = cmd /c "kubectl get nodes --no-headers 2>&1"
                    if ($LASTEXITCODE -ne 0) { throw "Kubernetes cluster unreachable: $nodes" }
                    Write-Host "cluster: $(@($nodes)[0])"

                    # The registry must be up, or the node cannot pull the image later.
                    try {
                        $r = Invoke-WebRequest -Uri "http://localhost:5000/v2/" -UseBasicParsing -TimeoutSec 10
                        Write-Host "registry: HTTP $($r.StatusCode)"
                    } catch {
                        throw "Local registry on localhost:5000 is not responding. Start it with: docker run -d --name local-registry -p 5000:5000 --restart=always registry:2"
                    }

                    New-Item -ItemType Directory -Force -Path deploy | Out-Null
                '''
            }
        }

        stage('Maven Build') {
            steps {
                bat 'mvn -B -DskipTests clean package'
                powershell '''
                    $ErrorActionPreference = "Stop"
                    if (-not (Test-Path $env:APP_JAR)) { throw "Expected jar not produced: $env:APP_JAR" }
                    Write-Host ("jar: {0:N1} MB" -f ((Get-Item $env:APP_JAR).Length/1MB))
                '''
            }
        }

        stage('Tests') {
            steps {
                script {
                    if (params.RUN_INTEGRATION_TESTS) {
                        bat 'mvn -B test'
                    } else {
                        bat 'mvn -B -Punit-tests test'
                    }
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Docker Build') {
            steps {
                powershell '''
                    $ErrorActionPreference = "Stop"
                    $tag = $env:IMAGE_TAG
                    cmd /c "docker build -t $env:IMAGE_NAME`:$tag . 2>&1" | Select-Object -Last 5
                    if ($LASTEXITCODE -ne 0) { throw "docker build failed" }

                    $img = cmd /c "docker images $env:IMAGE_NAME`:$tag --format `"{{.Repository}}:{{.Tag}} {{.Size}}`" 2>&1"
                    if (-not $img) { throw "image not found after build" }
                    Write-Host "built: $img"
                '''
            }
        }

        stage('Push to local registry') {
            steps {
                // The cluster node cannot see the Docker daemon's image store, so the
                // image is published to a registry both sides can reach. Pushing via
                // localhost avoids needing an insecure-registry entry in the daemon.
                powershell '''
                    $ErrorActionPreference = "Stop"
                    $tag = $env:IMAGE_TAG
                    cmd /c "docker tag $env:IMAGE_NAME`:$tag localhost:5000/$env:IMAGE_NAME`:$tag 2>&1"
                    if ($LASTEXITCODE -ne 0) { throw "docker tag failed" }
                    cmd /c "docker push localhost:5000/$env:IMAGE_NAME`:$tag 2>&1" | Select-Object -Last 3
                    if ($LASTEXITCODE -ne 0) { throw "docker push failed" }

                    $tags = Invoke-RestMethod -Uri "http://localhost:5000/v2/$env:IMAGE_NAME/tags/list" -TimeoutSec 20
                    if ($tags.tags -notcontains $tag) { throw "tag $tag not present in registry after push" }
                    Write-Host "registry now holds: $($tags.tags -join ', ')"
                '''
            }
        }

        stage('Kubernetes Deploy') {
            steps {
                withCredentials([
                    string(credentialsId: 'library-saas-jwt-secret', variable: 'JWT_SECRET'),
                    usernamePassword(credentialsId: 'library-saas-mysql',
                                     usernameVariable: 'MYSQL_USER',
                                     passwordVariable: 'MYSQL_PASSWORD')
                ]) {
                    powershell '''
                        $ErrorActionPreference = "Stop"
                        $ns = $env:K8S_NAMESPACE

                        cmd /c "kubectl apply -f k8s/namespace.yaml -f k8s/configmap.yaml -f k8s/deployment.yaml -f k8s/service.yaml 2>&1"
                        if ($LASTEXITCODE -ne 0) { throw "kubectl apply failed" }

                        # The Secret is built from Jenkins credentials at deploy time and is
                        # never written to a file in the repository. Values stay masked.
                        $yaml = cmd /c "kubectl -n $ns create secret generic library-saas-secrets --from-literal=JWT_SECRET=$env:JWT_SECRET --from-literal=MYSQL_USER=$env:MYSQL_USER --from-literal=MYSQL_PASSWORD=$env:MYSQL_PASSWORD --dry-run=client -o yaml 2>&1"
                        if ($LASTEXITCODE -ne 0) { throw "secret render failed" }
                        $yaml | Out-File -FilePath "deploy\\secret.tmp.yaml" -Encoding ascii
                        cmd /c "kubectl apply -f deploy\\secret.tmp.yaml 2>&1" | Out-Null
                        $applied = $LASTEXITCODE
                        Remove-Item "deploy\\secret.tmp.yaml" -Force -ErrorAction SilentlyContinue
                        if ($applied -ne 0) { throw "secret apply failed" }
                        Write-Host "secret applied (values not logged)"

                        $image = "$env:REGISTRY/$env:IMAGE_NAME`:$env:IMAGE_TAG"
                        cmd /c "kubectl -n $ns set image deployment/$env:DEPLOYMENT app=$image 2>&1"
                        if ($LASTEXITCODE -ne 0) { throw "kubectl set image failed" }
                        Write-Host "deployment image set to $image"
                    '''
                }
            }
        }

        stage('Rollout Verification') {
            steps {
                powershell '''
                    $ErrorActionPreference = "Stop"
                    $ns = $env:K8S_NAMESPACE

                    cmd /c "kubectl -n $ns rollout status deployment/$env:DEPLOYMENT --timeout=300s 2>&1"
                    if ($LASTEXITCODE -ne 0) {
                        Write-Host "--- pods ---";   cmd /c "kubectl -n $ns get pods 2>&1"
                        Write-Host "--- events ---"; cmd /c "kubectl -n $ns get events --sort-by=.lastTimestamp 2>&1" | Select-Object -Last 20
                        throw "rollout did not complete"
                    }

                    # rollout status alone is not proof. Verify (a) the Deployment
                    # template carries this build's tag and (b) a Ready pod is actually
                    # running it. Selecting items[0] is wrong: during a rolling update
                    # the old, still-terminating pod can be listed first, which makes
                    # the check read the previous build's image.
                    $deployImg = cmd /c "kubectl -n $ns get deployment $env:DEPLOYMENT -o jsonpath=`"{.spec.template.spec.containers[0].image}`" 2>&1"
                    Write-Host "deployment image : $deployImg"
                    if ("$deployImg" -notlike "*:$env:IMAGE_TAG") {
                        throw "deployment template runs $deployImg, expected tag $env:IMAGE_TAG"
                    }

                    # Wait for a Ready pod on this exact image; old pods may still be
                    # terminating for a few seconds after rollout status returns.
                    $deadline = (Get-Date).AddSeconds(120)
                    $match = $null
                    while ((Get-Date) -lt $deadline) {
                        $rows = cmd /c "kubectl -n $ns get pods -l app.kubernetes.io/component=backend --field-selector=status.phase=Running -o jsonpath=`"{range .items[*]}{.metadata.name} {.status.containerStatuses[0].ready} {.spec.containers[0].image}{'\\n'}{end}`" 2>&1"
                        foreach ($row in @($rows)) {
                            $parts = "$row".Trim().Split(@(' '), [StringSplitOptions]::RemoveEmptyEntries)
                            if ($parts.Count -ge 3 -and $parts[1] -eq "true" -and $parts[2] -like "*:$env:IMAGE_TAG") {
                                $match = $parts; break
                            }
                        }
                        if ($match) { break }
                        Start-Sleep -Seconds 5
                    }

                    if (-not $match) {
                        cmd /c "kubectl -n $ns get pods -o wide 2>&1"
                        throw "no Ready pod is running image tag $env:IMAGE_TAG"
                    }
                    Write-Host "pod   : $($match[0])"
                    Write-Host "ready : $($match[1])"
                    Write-Host "image : $($match[2])"
                '''
            }
        }

        stage('Health Check') {
            steps {
                powershell '''
                    $ErrorActionPreference = "Stop"
                    $ns = $env:K8S_NAMESPACE
                    $port = $env:VERIFY_PORT

                    # Service is ClusterIP (node ports are not published on this node type),
                    # so verification goes through a port-forward held open for the
                    # remaining stages and torn down in post{}.
                    # Win32_Process.Create rather than Start-Process: a child started
                    # with -NoNewWindow inherits Jenkins' stdout handle and the build
                    # step then blocks forever waiting for that handle to close. This
                    # launch is fully detached, so the step returns immediately.
                    # Win32_Process.Create does no PATH resolution (it returns rc=9,
                    # "path not found"), so kubectl must be given as an absolute path.
                    $kubectl = (Get-Command kubectl -ErrorAction Stop).Source
                    $cmdline = "`"$kubectl`" -n $ns port-forward service/$env:DEPLOYMENT $port`:8080"
                    $res = Invoke-CimMethod -ClassName Win32_Process -MethodName Create `
                           -Arguments @{ CommandLine = $cmdline }
                    if ($res.ReturnValue -ne 0) { throw "failed to start port-forward (rc=$($res.ReturnValue))" }
                    $res.ProcessId | Out-File -FilePath $env:PF_PID_FILE -Encoding ascii
                    Write-Host "port-forward PID $($res.ProcessId) on localhost:$port"

                    $deadline = (Get-Date).AddSeconds(120)
                    $up = $false
                    while ((Get-Date) -lt $deadline) {
                        try {
                            $r = Invoke-RestMethod -Uri "http://localhost:$port/actuator/health" -TimeoutSec 3 -UseBasicParsing
                            if ($r.status -eq "UP") { $up = $true; break }
                        } catch { }
                        Start-Sleep -Seconds 3
                    }
                    if (-not $up) {
                        Get-Content "deploy\\portforward-err.log" -ErrorAction SilentlyContinue
                        cmd /c "kubectl -n $ns logs deployment/$env:DEPLOYMENT --tail=60 2>&1"
                        throw "application did not report UP through the service within 120s"
                    }
                    Write-Host "health: UP via Kubernetes service"
                '''
            }
        }

        stage('Smoke Tests') {
            steps {
                powershell '''
                    $ErrorActionPreference = "Stop"
                    $base = "http://localhost:$env:VERIFY_PORT"
                    $script:failures = @()

                    # Windows PowerShell 5.1 has no -SkipHttpErrorCheck, so a non-2xx
                    # response throws and the status is read off the exception.
                    function Get-Status($method, $path, $headers) {
                        try {
                            $r = Invoke-WebRequest -Uri "$base$path" -Method $method `
                                 -Headers $headers -TimeoutSec 15 -UseBasicParsing
                            return [int]$r.StatusCode
                        } catch {
                            if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
                            return -1
                        }
                    }
                    function Check($name, $actual, $expected) {
                        if ($actual -eq $expected) {
                            Write-Host ("PASS  {0,-30} {1}" -f $name, $actual)
                        } else {
                            Write-Host ("FAIL  {0,-30} got {1}, expected {2}" -f $name, $actual, $expected)
                            $script:failures += $name
                        }
                    }

                    $body = '{"identifier":"manager1@brightfuture.example","password":"Password@123"}'
                    $login = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post `
                             -ContentType "application/json" -Body $body -TimeoutSec 20
                    if (-not $login.data.accessToken) { throw "login did not return an access token" }
                    $auth = @{ Authorization = "Bearer $($login.data.accessToken)" }

                    Check "authenticated read"      (Get-Status GET  "/api/students/1" $auth)        200
                    Check "no token rejected"       (Get-Status GET  "/api/students/1" @{})          401
                    Check "missing student"         (Get-Status GET  "/api/students/1001111" $auth)  404
                    Check "cross-tenant student"    (Get-Status GET  "/api/students/4" $auth)        403
                    Check "insufficient permission" (Get-Status GET  "/api/organizations/1" $auth)   403
                    Check "unmapped path"           (Get-Status GET  "/api/students/1/none" $auth)   404
                    Check "method not allowed"      (Get-Status POST "/api/students/1" $auth)        405
                    Check "openapi document"        (Get-Status GET  "/v3/api-docs" @{})             200
                    Check "actuator health public"  (Get-Status GET  "/actuator/health" @{})         200
                    Check "actuator env protected"  (Get-Status GET  "/actuator/env" @{})            401

                    if ($script:failures.Count -gt 0) {
                        throw "smoke tests failed: $($script:failures -join ', ')"
                    }
                    Write-Host "all smoke tests passed"
                '''
            }
        }

        stage('Security Verification') {
            steps {
                powershell '''
                    $ErrorActionPreference = "Stop"
                    $base = "http://localhost:$env:VERIFY_PORT"
                    $ns = $env:K8S_NAMESPACE
                    $problems = @()

                    $body = '{"identifier":"manager1@brightfuture.example","password":"Password@123"}'
                    $login = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post `
                             -ContentType "application/json" -Body $body -TimeoutSec 20
                    $spoof = @{
                        Authorization  = "Bearer $($login.data.accessToken)"
                        "X-Library-Id" = "2"
                    }

                    # 1. The tenant header must not widen access.
                    $list = Invoke-RestMethod -Uri "$base/api/students" -Headers $spoof -TimeoutSec 20
                    $libs = @($list.data.content.libraryId | Sort-Object -Unique)
                    if ($libs.Count -ne 1 -or $libs[0] -ne 1) {
                        $problems += "tenant header widened scope: saw libraries $($libs -join ',')"
                    } else { Write-Host "PASS  X-Library-Id cannot widen tenant scope" }

                    # 2. No session cookie: the API must stay stateless.
                    $r = Invoke-WebRequest -Uri "$base/actuator/health" -TimeoutSec 15 -UseBasicParsing
                    if ($r.Headers["Set-Cookie"]) {
                        $problems += "Set-Cookie present - session management is not STATELESS"
                    } else { Write-Host "PASS  no session cookie issued" }

                    # 3. Credentials must not appear in the pod log.
                    $log = cmd /c "kubectl -n $ns logs deployment/$env:DEPLOYMENT --tail=4000 2>&1" | Out-String
                    if ($log -match "Password@123") {
                        $problems += "plaintext password found in pod log"
                    } else { Write-Host "PASS  no plaintext password in pod log" }
                    if ($log -match "eyJ[A-Za-z0-9_-]{15,}\\.[A-Za-z0-9_-]{15,}") {
                        $problems += "JWT found in pod log"
                    } else { Write-Host "PASS  no JWT in pod log" }

                    if ($problems.Count -gt 0) {
                        throw ("security verification failed: " + ($problems -join "; "))
                    }
                '''
            }
        }
    }

    post {
        always {
            powershell '''
                $ErrorActionPreference = "SilentlyContinue"
                if (Test-Path $env:PF_PID_FILE) {
                    $procId = Get-Content $env:PF_PID_FILE
                    Write-Host "stopping port-forward PID $procId"
                    Stop-Process -Id $procId -Force
                    Remove-Item $env:PF_PID_FILE -Force
                }
            '''
            archiveArtifacts artifacts: 'deploy/*.log', allowEmptyArchive: true
        }
        success {
            script {
                echo "SUCCESS: ${env.IMAGE_NAME}:${env.IMAGE_TAG} deployed and verified in namespace ${params.K8S_NAMESPACE}"
            }
        }
        failure {
            powershell '''
                $ErrorActionPreference = "SilentlyContinue"
                $ns = $env:K8S_NAMESPACE
                Write-Host "--- pods ---";       cmd /c "kubectl -n $ns get pods -o wide 2>&1"
                Write-Host "--- describe ---";   cmd /c "kubectl -n $ns describe deployment/$env:DEPLOYMENT 2>&1" | Select-Object -Last 30
                Write-Host "--- app log ---";    cmd /c "kubectl -n $ns logs deployment/$env:DEPLOYMENT --tail=80 2>&1"
            '''
        }
    }
}
