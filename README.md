# Gradle Parent Plugin
This project creates a plugin that contains all standard gradle code/configs for ab2d projects.

To utilize this plugin in your project first configure the settings.gradle file
```
pluginManagement {
    repositories {
        maven {
            url = "${System.getenv().get("ARTIFACTORY_URL") ?: (System.getProperty('artifactory_contextUrl'))}" +"/ab2d-main"
            credentials {
                username = "${System.getenv().get("ARTIFACTORY_USER") ?: (System.getProperty('artifactory_user'))}"
                password = "${System.getenv().get("ARTIFACTORY_PASSWORD") ?: (System.getProperty('artifactory_password'))}"
            }
        }
        gradlePluginPortal() //need to grab from gradles main repo for some of the plugins
    }
}
```


then in build.gradle apply the plugin
```
plugins {
   id "gov.cms.ab2d.plugin" version "{Version Number}"
}

apply plugin: 'gov.cms.ab2d.plugin'
```

Currently, there are no scripts to deploy the plugin. Just use the local commands.
```
gradle publishPluginMavenPublicationToMavenRepository
```
