package gov.cms.ab2d.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.api.publish.maven.MavenPublication

class ParentPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.allprojects {
            apply plugin: 'com.jfrog.artifactory'
//            apply plugin: 'java-library'
            apply plugin: 'maven-publish'
            apply plugin: 'checkstyle'
            apply plugin: 'jacoco'
            group 'gov.cms.ab2d'


            sourceCompatibility = 17
            targetCompatibility = 17

            project.ext {
                artifactoryLoc = project.hasProperty('artifactory_contextUrl') ? project.artifactory_contextUrl
                        : System.getenv()['ARTIFACTORY_URL']

                // Override user and password
                artifactory_user = project.hasProperty('artifactory_user') ? project.artifactory_user
                        : System.getenv()['ARTIFACTORY_USER']
                artifactory_password = project.hasProperty('artifactory_password') ? project.artifactory_password
                        : System.getenv()['ARTIFACTORY_PASSWORD']

                sourcesRepo = 'ab2d-maven-repo'
                deployerRepo = 'ab2d-main'
                resolverRepo = 'ab2d-main'

                springBootVersion='2.6.3'
                testContainerVersion='1.16.3'
                lombokVersion = '1.18.22'


                failedTests = []
                skippedTests = []
                testSuites = []
            }


            project.getTasks().withType(Test) { testTask ->

                testTask.testLogging { logging ->
                    events TestLogEvent.FAILED,
                            TestLogEvent.SKIPPED,
                            TestLogEvent.STANDARD_OUT,
                            TestLogEvent.STANDARD_ERROR
                    exceptionFormat TestExceptionFormat.FULL
                    showExceptions true
                    showCauses true
                    showStackTraces true
                }

                afterTest { desc, result ->
                    if (result.resultType == TestResult.ResultType.FAILURE) {
                        project.ext.failedTests.add(desc)
                    }

                    if (result.resultType == TestResult.ResultType.SKIPPED) {
                        project.ext.skippedTests.add(desc)
                    }
                }

                afterSuite { desc, result ->
                    if (desc.parent) return // Only summarize results for whole modules

                    String summary = "${testTask.project.name}:${testTask.name} results: ${result.resultType} " +
                            "(" +
                            "Tests run ${result.testCount}, " +
                            "succeeded ${result.successfulTestCount}, " +
                            "failed ${result.failedTestCount}, " +
                            "skipped ${result.skippedTestCount}" +
                            ") "
                    project.ext.testSuites += summary
                }
            }


            project.repositories {
                maven {
                    url = "${artifactoryLoc}/${sourcesRepo}"
                    credentials {
                        username = project.artifactory_user
                        password = project.artifactory_password
                    }
                }
            }

            project.publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java
                    }
                }
            }

            project.artifactory {
                contextUrl = project.artifactoryLoc

                publish {
                    repository {
                        repoKey = "${deployerRepo}"
                        username = project.artifactory_user
                        password = project.artifactory_password
                        maven = true
                    }
                    defaults {
                        publications('mavenJava')
                        publishArtifacts = true
                        publishBuildInfo = false
                    }
                }
                resolve {
                    repository {
                        repoKey = "${resolverRepo}"
                        username = project.artifactory_user
                        password = project.artifactory_password
                        maven = true
                    }
                }
            }
            // In each Project run to check if the version exists in the repository
            project.task("lookForArtifacts") {
                doLast {
                    //This is the parent build where nothing gets published. Skip it.
                    if(project.name != 'ab2d-libs')
                    //Set path to repository that might exist if previous published on this version.
                    //Sets project name and if it exists in repository. Print out so jenkins can take it.
                        System.out.print("'''"+project.name + ":" + urlExists("${artifactoryLoc}/${deployerRepo}/gov/cms/ab2d/${project.name}/${project.version}/${project.name}-${project.version}.jar"))
                }
            }

            if (artifactory_user == null || artifactory_user.isEmpty()) {
                println("Artifactory user not set");
            }
            if (artifactory_password == null || artifactory_password.isEmpty()) {
                println("Artifactory password not set");
            }
            println("Artifactory loc? " + project.getProperty('artifactoryLoc'))

            println("Branch is " + gitBranch())

            project.dependencies {
                implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
                testImplementation "org.testcontainers:testcontainers:$testContainerVersion"
                testImplementation "org.testcontainers:postgresql:$testContainerVersion"
                testImplementation "org.testcontainers:junit-jupiter:$testContainerVersion"
//                testImplementation 'gov.cms.ab2d:common:0.0.1-SNAPSHOT'
                implementation "org.projectlombok:lombok:$lombokVersion"
                implementation(platform(annotationProcessor("org.projectlombok:lombok:$lombokVersion")))
                annotationProcessor("org.projectlombok:lombok:$lombokVersion")

            }

            project.jar {
                processResources.exclude('checkstyle.xml')
                classifier "main".equalsIgnoreCase(gitBranch()) || "main".equalsIgnoreCase(System.getenv('BRANCH_NAME')) ? "" : "SNAPSHOT"
                System.out.println("**** building branch - " + gitBranch() + ", classifier - " + classifier + " - CI branch - " + System.getenv('BRANCH_NAME'))
            }

            project.test {
                useJUnitPlatform()
                finalizedBy jacocoTestReport // report is always generated after tests run
            }

            project.jacocoTestReport {
                reports {
                    xml.enabled true
                }
            }

            project.jacoco {
                toolVersion = "0.8.7"
                reportsDirectory = layout.buildDirectory.dir("$buildDir/reports/jacoco")
            }

            project.checkstyle {
                configFile file("$rootDir/config/checkstyle.xml")
            }

        }

        project.getGradle().buildFinished {

            if (!project.ext.skippedTests.isEmpty()) {
                println "Skipped Tests: "
                for (String skippedTestDesc : project.ext.skippedTests) {
                    println "\t" + skippedTestDesc
                }
            }

            if (!project.ext.failedTests.isEmpty()) {
                println "Failing Tests: "
                for (String failedTestDesc : project.ext.failedTests) {
                    println "\t" + failedTestDesc
                }
            }

            if (!project.ext.testSuites.isEmpty()) {
                println "Test Suite Summary: "
                println project.ext.testSuites
            }
        }
    }

    //Check if url exist in the repository
    private boolean urlExists(repositoryUrl) {

        try {
            def connection = (HttpURLConnection) new URL(repositoryUrl).openConnection()

            connection.setRequestProperty("Authorization", "Basic " + getBase64EncodedCredentials())
            connection.setConnectTimeout(10000)
            connection.setReadTimeout(10000)
            connection.setRequestMethod("HEAD")

            def responseCode = connection.getResponseCode()

            if (responseCode == 401) {
                throw new RuntimeException("Unauthorized MavenUser user. Please provide valid username and password.")
            }

            return (200 == responseCode)

        } catch (IOException ignored) {
            println(ignored)
            return false
        }
    }

    private String gitBranch() {
        String branch = ""
        def proc = "git rev-parse --abbrev-ref HEAD".execute()
        proc.in.eachLine { line -> branch = line }
        proc.err.eachLine { line -> println line }
        proc.waitFor()
        branch
    }

    private String getBase64EncodedCredentials() {
        System.out.println(artifactory_password);
        String s = artifactory_user + ":" + artifactory_password
        return s.bytes.encodeBase64().toString()
    }



}
