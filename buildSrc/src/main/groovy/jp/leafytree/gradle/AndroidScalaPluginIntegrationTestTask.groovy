/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jp.leafytree.gradle

import com.google.common.io.ByteStreams
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

public class AndroidScalaPluginIntegrationTestTask extends DefaultTask {
    @TaskAction
    def run() {
        def travis = System.getenv("TRAVIS").toString().toBoolean()
        [
                ["app", ["connectedAndroidTest"], false],
                ["lib", ["connectedAndroidTest"], false],
                ["appAndLib", ["assembleDebug", "assembleDebug2", "connectedAndroidTest", "connectedAndroidTestFlavor1Debug", "connectedAndroidTestFlavor2Debug"], false],
                ["appAndLib", ["connectedAndroidTest"], true],
                ["noScala", ["connectedAndroidTest"], false],
                ["useScalaOnlyTest", ["connectedAndroidTest"], false],
        ].each { projectName, gradleArgs, runOnTravis ->
            gradleArgs = ["clean", *gradleArgs, "uninstallAll"]
            [
                    ["1.12", false, "2.10.4", "0.12.1", "android-20", "20.0.0", "8", "20"],
                    ["1.12", true,  "2.11.2", "0.12.1", "android-20", "20.0.0", "8", "20"],
            ].each { testParameters ->
                if (!travis || (runOnTravis && testParameters[1])) {
                    def gradleVersion = testParameters[0]
                    def gradleWrapperProperties = getGradleWrapperProperties(gradleVersion)
                    def gradleProperties = getGradleProperties(testParameters.drop(2))
                    println "Test $gradleArgs gradleVersion:$gradleVersion $gradleProperties"
                    runProject(projectName, gradleArgs, gradleWrapperProperties, gradleProperties)
                }
            }
        }
    }

    def getGradleWrapperProperties(gradleVersion) {
        def gradleWrapperProperties = new Properties()
        gradleWrapperProperties.putAll([
                distributionBase: "GRADLE_USER_HOME",
                distributionPath: "wrapper/dists",
                zipStoreBase: "GRADLE_USER_HOME",
                zipStorePath: "wrapper/dists",
                distributionUrl: "http://services.gradle.org/distributions/gradle-" + gradleVersion + "-bin.zip",
        ])
        gradleWrapperProperties
    }

    def getGradleProperties(scalaLibraryVersion, androidPluginVersion, androidPluginCompileSdkVersion,
                            androidPluginBuildToolsVersion, androidPluginMinSdkVersion, androidPluginTargetSdkVersion) {
        // def snaphotRepositoryUrl = "http://saturday06.github.io/gradle-android-scala-plugin/repository/snapshot"
        def snaphotRepositoryUrl = [project.buildFile.parentFile.absolutePath, "gh-pages", "repository", "snapshot"].join(File.separator)
        def gradleProperties = new Properties()
        gradleProperties.putAll([
                "org.gradle.jvmargs": "-Xmx2048m -XX:MaxPermSize=1024m -XX:+HeapDumpOnOutOfMemoryError",
                snaphotRepositoryUrl: snaphotRepositoryUrl,
                scalaLibraryVersion: scalaLibraryVersion,
                scalaDependencyVersion: scalaLibraryVersion.split("\\.").take(2).join("."),
                androidScalaPluginVersion: "1.0-SNAPSHOT",
                androidPluginVersion: androidPluginVersion,
                androidPluginCompileSdkVersion: androidPluginCompileSdkVersion,
                androidPluginBuildToolsVersion: androidPluginBuildToolsVersion,
                androidPluginMinSdkVersion: androidPluginMinSdkVersion,
                androidPluginTargetSdkVersion: androidPluginTargetSdkVersion,
                androidPluginIncremental: "true",
                androidPluginPreDexLibraries: "false",
                androidPluginJumboMode: "false",
        ])
        gradleProperties
    }

    def runProject(projectName, tasks, gradleWrapperProperties, gradleProperties) {
        def baseDir = new File([project.buildFile.parentFile.absolutePath, "src", "integTest"].join(File.separator))
        def projectDir = new File([baseDir.absolutePath, "project", projectName].join(File.separator))
        new File(baseDir, ["gradle", "wrapper", "gradle-wrapper.properties"].join(File.separator)).withWriter {
            gradleWrapperProperties.store(it, getClass().getName())
        }
        new File(projectDir, "gradle.properties").withWriter {
            gradleProperties.store(it, getClass().getName())
        }
        def gradleWrapper = new GradleWrapper(baseDir)
        def stdout = new StringBuilder()
        def stderr = new StringBuilder()
        def args = ["--stacktrace", "--project-dir", projectDir.absolutePath] + tasks
        println "gradlew $args"
        def process = gradleWrapper.execute(args)
        Thread.start { ByteStreams.copy(process.in, System.out) }
        Thread.start { ByteStreams.copy(process.err, System.err) }
        process.waitFor()
        // process.waitForProcessOutput(System.out, System.err)
        if (process.exitValue() != 0) {
            throw new IOException("process.exitValue != 0 but ${process.exitValue()}")
        }
    }
}
