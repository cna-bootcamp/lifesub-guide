// settings.gradle
rootProject.name = 'plan-management'

include 'common'
include 'plan-command-service'
include 'plan-query-service'

// build.gradle
plugins {
    id 'org.springframework.boot' version '3.2.1' apply false
    id 'io.spring.dependency-management' version '1.1.4' apply false
    id 'java'
}

subprojects {
    apply plugin: 'java'
    apply plugin: 'org.springframework.boot'
    apply plugin: 'io.spring.dependency-management'
    
    group = 'com.telco'
    version = '0.0.1-SNAPSHOT'
    sourceCompatibility = '17'
    
    repositories {
        mavenCentral()
    }
    
    dependencies {
        // Spring Boot Starter
        implementation 'org.springframework.boot:spring-boot-starter'
        
        // Lombok
        compileOnly 'org.projectlombok:lombok'
        annotationProcessor 'org.projectlombok:lombok'
        
        // Test
        testImplementation 'org.springframework.boot:spring-boot-starter-test'
    }
}

// common/build.gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'io.swagger.core.v3:swagger-annotations:2.2.8'
}

// plan-command-service/build.gradle
dependencies {
    implementation project(':common')
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.postgresql:postgresql'
    implementation 'com.azure:azure-messaging-servicebus:7.15.1'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0'
}

// plan-query-service/build.gradle
dependencies {
    implementation project(':common')
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-mongodb'
    implementation 'org.springframework.boot:spring-boot-starter-cache'
    implementation 'com.azure:azure-messaging-servicebus:7.15.1'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0'
}

// common/src/main/java/com/telco/common/dto/ApiResponse.java
package com.telco.common.dto;

import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class ApiResponse<T> {
    private final Integer status;
    private final String message;
    private final T data;
    private final LocalDateTime timestamp;

    private ApiResponse(Integer status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "Success", data);
    }

    public static <T> ApiResponse<T> error(Integer status, String message) {
        return new ApiResponse<>(status, message, null);
    }
}