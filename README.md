# Discord Server Template Report

Discord has introduced a [Server Template](https://support.discord.com/hc/en-us/articles/360041033511-Server-Templates) feature to make it easier to clone the key setup of a server.  The information in a template can also be used for visualization and analysis purposes.

This program is a simple server that will download and parse a template in Discord's [JSON format](https://github.com/discord/discord-api-docs/pull/1712) and render it with advice about possible redundancies in its [permission](https://discord.com/developers/docs/topics/permissions) configuration.  No special access is required beyond turning on the template feature for your server.

## Demo
There's a [demo instance](https://fierce-spire-69643.herokuapp.com) on Heroku.

### Running Locally
1. Clone the project.
2. Open a command prompt and change to the project directory.
3. Download a JDK (11 or higher) (ex: [JDK15](https://github.com/AdoptOpenJDK/openjdk15-binaries/releases/download/jdk-15%2B36/OpenJDK15U-jdk_x64_windows_hotspot_15_36.zip) from [adoptopenjdk](https://adoptopenjdk.net/)) and extract it.
4. Type `SET JAVA_HOME={path-to-dir-where-you-extracted-the-jdk}`
5. Type `gradlew run`
6. Wait for the program to build and start (it will hang on the `:run` task with `Application started` in the log).
7. Navigate to http://localhost:8080/ 
8. Turn on the Server Template for your server.
9. Paste the link/key in the lookup box and submit.
