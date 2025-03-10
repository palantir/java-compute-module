> [!WARNING]
> This SDK is in an incubating phase and may change at any time.

# java-compute-module

A Java library for creating a Compute Module.

## Getting Started

Below is a simple guide to get started with the java-compute-module.

---

### 1. Initialize a Gradle Project

From your terminal, initialize a new Gradle project with the Application plugin:

```bash
gradle init
```

This creates a basic Gradle structure with an Application plugin applied and a sample main class.

---

### 2. Configure Your build.gradle

In your newly created project's build.gradle, add the necessary plugins, dependencies, and configurations:

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    // for testing
    testImplementation 'junit:junit:4.13.2'
    
    // common libraries
    implementation 'com.google.guava:guava:31.1-jre'
    implementation 'com.palantir.safe-logging:logger:3.7.0'
    implementation 'com.palantir.safe-logging:preconditions:3.7.0'
    implementation 'com.palantir.safe-logging:safe-logging:3.7.0'
    implementation 'org.slf4j:slf4j-api:1.7.36'
    
    // includes java-compute-module
    implementation 'com.palantir.computemodules:java-compute-module:0.0.0'
    
    // Jackson for JSON manipulation
    implementation 'com.fasterxml.jackson.core:jackson-core:2.18.2'
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.18.2'
}

java {
    toolchain {
        // specify Java version
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    // define main class
    mainClass = 'App'
}
```

---

### 3. Create Your Main Application

Within the generated src/main/java folder, create (or update) a class named App. Below is a simple example:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.palantir.computemodule.ComputeModule;
import com.palantir.computemodule.Context;

public class App {

    public static void main(String[] args) {

        ComputeModule cm = ComputeModule.builder()
                .add(App::hello, String.class, String.class, "hello")
                .build()
                .start();
    }

    static String hello(Context context, String name) {
        return "hello " + name;
    }
}
```

1. **Importing Classes**:
   - We import the necessary classes from the `java-compute-module` library to utilize its functionality in our application.

2. **Creating a ComputeModule Instance**:
   - We instantiate a `ComputeModule` using a builder pattern.
   - During this process, we attach the `hello` function to the module using the `.add` method.
   - The method signature `.add(App::hello, String.class, String.class, "hello")` specifies:
      - `App::hello`: The function to be executed, referenced from the `App` class.
      - `String.class` (input type): The function accepts a `String` as input.
      - `String.class` (output type): The function returns a `String`.
      - `"hello"`: A unique identifier for the function within the module.

3. **Starting the Compute Module**:
   - When the application launches, the compute module is started by invoking the `.start()` method.
   - This initiates the module and makes the registered functions, like `hello`, available for execution.
---

### 4. Build and Deploy with Docker

Containerize your application and then upload the resulting Docker image to Foundry. Once uploaded, you can reference your newly created image in a compute module. Example of Dockerfile:

```dockerfile
FROM --platform=linux/amd64 gradle:jdk21 AS build
WORKDIR /src

COPY . /src
RUN gradle build --no-daemon
RUN unzip /src/app/build/distributions/app.zip -d /src

FROM --platform=linux/amd64 eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /src/app /app
USER 5000

CMD ["sh", "/app/bin/app"]
```

Steps to follow:
1. Place this Dockerfile in your project's root directory (alongside the Gradle files).
2. Run the build process:
   ```bash
   docker build --platform=linux/amd64 -t your-image-name .
   ```
3. Push the built image to your Foundry Docker registry.
4. Use the image in a compute module. 