# Weather Query Project

This project is developed by Java 8 with Sping boot, Thymeleaf and online weather service. It will display current weather for 3 Australian cities: Sydney, Melbourne and Wollongong. There should be a dropdown list on web page for city selection, when city is changed corresponding real-time weather information should be displayed.

After depoyed the project as below steps, you can visit the index page: http://localhost:8080

![Image of index page](https://github.com/scriptboykiller/intellij-idea-tutorial/blob/master/resources/1.png?raw=true)

Weathe table will be updated when country is selected.

![Image of index page](https://github.com/scriptboykiller/intellij-idea-tutorial/blob/master/resources/2.png?raw=true)

![Image of index page](https://github.com/scriptboykiller/intellij-idea-tutorial/blob/master/resources/3.png?raw=true)

# How to deploy and run the project
There is two way to deploy:
* JAR deployment
* Docker deployment

# Download Project
```
git clone https://github.com/scriptboykiller/WeatherQuery.git
```
Project layer：

```
my-site
    ├── Dockerfile
    ├── src
         ├── test
         ├── main/java
         └── main/resources
    └── pom.xml
        
```
### JAR deployment
use maven to build package

```
mvn clean package
```
find jar under **target** folder：
```
java -jar WeatherDemo-0.0.1-SNAPSHOT.jar
```
### Docker deployment
```
mvn package
```
Build docker image
```
docker build -t weatherdemo .
```
If you see the following message, it means that the building is successful.
```
Sending build context to Docker daemon  22.51MB
Step 1/4 : FROM openjdk:latest
 ---> ef36deb98f03
Step 2/4 : ADD target/WeatherDemo-0.0.1-SNAPSHOT.jar app.jar
 ---> 795c7499cd6d
Step 3/4 : ENTRYPOINT ["java","-jar","app.jar"]
 ---> Running in 4699a59f7d27
Removing intermediate container 4699a59f7d27
 ---> abee53f9fc77
Step 4/4 : EXPOSE 8080
 ---> Running in 572734fa7668
Removing intermediate container 572734fa7668
 ---> 17705cc4c021
Successfully built 17705cc4c021
Successfully tagged weatherdemo:latest
```
Then use ``docker images``to check the image：
```
DexterdeMBP:WeatherQuery dexter$ docker images
REPOSITORY          TAG                 IMAGE ID            CREATED             SIZE
weatherdemo         latest              17705cc4c021        4 minutes ago       491MB
tomcat              latest              5377fd8533c3        9 hours ago         506MB
openjdk             latest              ef36deb98f03        2 weeks ago         470MB

```
Run the image
```
docker run -p 8080:8080 -t weatherdemo
```

Use ``docker ps``to check the running image：

```
DexterdeMBP:~ dexter$ docker ps
CONTAINER ID        IMAGE               COMMAND               CREATED             STATUS              PORTS                    NAMES
20480877c226        weatherdemo         "java -jar app.jar"   20 seconds ago      Up 19 seconds       0.0.0.0:8080->8080/tcp   festive_lichterman
```
