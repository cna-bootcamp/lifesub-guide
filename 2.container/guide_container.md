# 컨테이너 기술 실습
개발한 구독관리 서비스를 컨테이너 이미지로 빌드하고 배포하면서 컨테이너 기술에 대해 이해합니다.  

## 목차
- [컨테이너 기술 실습](#컨테이너-기술-실습)
  - [목차](#목차)
  - [VM 생성](#vm-생성)
  - [컨테이너 이미지 빌드](#컨테이너-이미지-빌드)
    - [1.Backend 애플리케이션](#1backend-애플리케이션)
    - [2.프론트엔드 애플리케이션](#2프론트엔드-애플리케이션)
    - [3.로컬에서 테스트](#3로컬에서-테스트)
  - [Push image](#push-image)
  - [컨테이너 실행](#컨테이너-실행)
  - [기타 Docker 명령](#기타-docker-명령)
  - [Docker Compose](#docker-compose)
    - [WSL에 Docker Compose 설치](#wsl에-docker-compose-설치)
    - [docker-compose.yml 작성](#docker-composeyml-작성)
    - [로컬에서 테스트](#로컬에서-테스트)
    - [서버에서 테스트](#서버에서-테스트)

---

## VM 생성
컨테이너를 실행할 VM을 생성합니다.  

1.[VM 생성](https://github.com/cna-bootcamp/handson-azure/blob/main/prepare/setup-server.md#bastion-vm-%EC%83%9D%EC%84%B1)  

2.[Docker 설치](https://github.com/cna-bootcamp/handson-azure/blob/main/prepare/setup-server.md#docker%EC%84%A4%EC%B9%98)  

3.마이구독 사용 포트 오픈  
Local 에서 수행합니다.  

환경변수를 정의합니다.  
```
export ID={본인ID}
```

VM의 NSG에 사용할 포트를 오픈 합니다.  
```
export PORT=18080 
az network nsg rule create \
--nsg-name ${ID}-bastionNSG \
--name Allow-HTTP-$PORT \
--priority 300 \
--access Allow \
--direction Inbound \
--protocol Tcp \
--source-port-ranges '*' \
--destination-port-ranges $PORT

export PORT=8081  
az network nsg rule create \
--nsg-name ${ID}-bastionNSG \
--name Allow-HTTP-$PORT \
--priority 310 \
--access Allow \
--direction Inbound \
--protocol Tcp \
--source-port-ranges '*' \
--destination-port-ranges $PORT

export PORT=8082  
az network nsg rule create \
--nsg-name ${ID}-bastionNSG \
--name Allow-HTTP-$PORT \
--priority 320 \
--access Allow \
--direction Inbound \
--protocol Tcp \
--source-port-ranges '*' \
--destination-port-ranges $PORT

export PORT=8083  
az network nsg rule create \
--nsg-name ${ID}-bastionNSG \
--name Allow-HTTP-$PORT \
--priority 330 \
--access Allow \
--direction Inbound \
--protocol Tcp \
--source-port-ranges '*' \
--destination-port-ranges $PORT
```

| [Top](#목차) |

---

## 컨테이너 이미지 빌드
개발이 어느정도 끝났습니다.   
컨테이너 이미지를 만들어서 이미지 레지스트리에 푸시 해야 겠죠.  

로컬에서 컨테이너 이미지를 생성합니다.     

### 1.Backend 애플리케이션 

Build Jar: 먼저 실행Jar파일부터 만듭니다.  
```
cd ~/workspace/lifesub
```

'gradlew'는 Spring Boot에서 기본으로 제공하는 컴파일러입니다.  
기존에 있는 jar파일을 먼저 지우기 위해 'clean'명령을 먼저 수행합니다.  
'-x test'는 테스크 코드는 수행하지 말라는 옵션입니다.  
```
service=member
./gradlew :${service}:clean :${service}:build -x test
service=mysub
./gradlew :${service}-infra:clean :${service}-infra:build -x test
service=recommend
./gradlew :${service}:clean :${service}:build -x test
```

이제 실행 Jar를 컨테이너 안으로 복사하여 컨테이너 이미지를 만듭니다.  

이미지 빌드 매니페스트인 Dockerfile이 필요 하겠죠?  
아래와 같이 표준화된 Dockerfile을 lifesub/container라는 디렉토리를 만들고   
Dockerfile이라는 이름으로 만듭니다.  
Build할 때와 실행할때의 이미지를 다른걸 사용한게 특이할겁니다.  
이는 실행 컨테이너 이미지의 사이즈를 작게 하기 위해서입니다.  
```
# Build stage
FROM openjdk:23-oraclelinux8 AS builder
ARG BUILD_LIB_DIR
ARG ARTIFACTORY_FILE
COPY ${BUILD_LIB_DIR}/${ARTIFACTORY_FILE} app.jar

# Run stage
FROM openjdk:23-slim  //최소한의 base image
ENV USERNAME k8s
ENV ARTIFACTORY_HOME /home/${USERNAME}
ENV JAVA_OPTS=""

# Add a non-root user
RUN adduser --system --group ${USERNAME} && \
    mkdir -p ${ARTIFACTORY_HOME} && \
    chown ${USERNAME}:${USERNAME} ${ARTIFACTORY_HOME}

WORKDIR ${ARTIFACTORY_HOME}
COPY --from=builder app.jar app.jar
RUN chown ${USERNAME}:${USERNAME} app.jar

USER ${USERNAME}

ENTRYPOINT [ "sh", "-c" ]
CMD ["java ${JAVA_OPTS} -jar app.jar"]
```

이제 이 매니페스트 파일로 이미지를 만듭니다.  
'--build-arg'파라미터로 서비스마다 다른 값을 넘겨서 동일한 매니페스트 파일을 이용할 수 있습니다.  
생성되는 이미지가 full path(registry/organization/repository:tag)가 아니라  
'repository:tag'만을 지정해서 만들어집니다.  
로컬에서 테스트 후 완료 되면 docker tag로 full path image를 만들려는 의도입니다.   
  
'--platform'은 생소하실겁니다.  
컨테이너를 실행할 환경을 지정하는 옵션입니다.  
이미지를 빌드하는 OS가 Linux가 아니면 이 옵션을 반드시 지정해야 합니다.  
우리는 Linux에서 빌드하기 때문에 사실 필요는 없습니다.  OS가 동일할때는 생략하는게 더 좋습니다.  
Buil container image  
```
# backend
cd ~/workspace/lifesub

DOCKER_FILE=Dockerfile
service=member
docker build \
  --platform linux/amd64 \
  --build-arg BUILD_LIB_DIR="${service}/build/libs" \
  --build-arg ARTIFACTORY_FILE="${service}.jar" \
  -f container/${DOCKER_FILE} \
  -t ${service}:latest .

service=mysub
docker build \
  --platform linux/amd64 \
  --build-arg BUILD_LIB_DIR="${service}-infra/build/libs" \
  --build-arg ARTIFACTORY_FILE="${service}.jar" \
  -f container/${DOCKER_FILE} \
  -t ${service}:latest .

service=recommend
docker build \
  --platform linux/amd64 \
  --build-arg BUILD_LIB_DIR="${service}/build/libs" \
  --build-arg ARTIFACTORY_FILE="${service}.jar" \
  -f container/${DOCKER_FILE} \
  -t ${service}:latest .
```

이미지가 잘 생성 되었는지 확인합니다.  
```
docker images
```
너무 많으면 grep이라는 linux명령을 활용할 수도 있습니다.  
```
docker images | grep member  
```

| [Top](#목차) |

---

### 2.프론트엔드 애플리케이션
이제 프론트엔드 애플리케이션인 'lifesub-web'의 컨테이너 이미지를 만들겠습니다.   

역시 Dockerfile부터 필요하겠죠.  
lifesub-web/container디렉토리를 만들고 'Dockerfile'을 아래 내용으로 만듭니다.   
backend의 주소를 담고 있는 '/usr/share/nginx/html/runtime-env.js' 파일을 동적으로 만드는 것에 주목 해 주세요.   
public/index.html에서 이 js파일을 include하기 때문에 각 백엔드의 주소 환경변수가 생깁니다.   
```
# Build stage
FROM node:20-slim AS builder
ARG PROJECT_FOLDER

ENV NODE_ENV=production

WORKDIR /app

# Install dependencies
COPY ${PROJECT_FOLDER}/package*.json ./
RUN npm ci --only=production

# Build application
COPY ${PROJECT_FOLDER} .
RUN npm run build

# Run stage
FROM nginx:stable-alpine

ARG BUILD_FOLDER
ARG EXPORT_PORT
ARG REACT_APP_MEMBER_URL
ARG REACT_APP_MYSUB_URL
ARG REACT_APP_RECOMMEND_URL

# Create nginx user if it doesn't exist
RUN adduser -S nginx || true

# Copy build files
COPY --from=builder /app/build /usr/share/nginx/html

# Create runtime config
#index.html의 헤더에서 이 값을 읽어 환경변수를 생성함  
#api.js에서 이 환경변수를 이용함: 예) window.__runtime_config__.MEMBER_URL

RUN echo "window.__runtime_config__ = { \
    MEMBER_URL: '${REACT_APP_MEMBER_URL}', \
    MYSUB_URL: '${REACT_APP_MYSUB_URL}', \
    RECOMMEND_URL: '${REACT_APP_RECOMMEND_URL}' \
}" > /usr/share/nginx/html/runtime-env.js

# Copy and process nginx configuration
COPY ${BUILD_FOLDER}/nginx.conf /etc/nginx/templates/default.conf.template

# Add custom nginx settings
RUN echo "client_max_body_size 100M;" > /etc/nginx/conf.d/client_max_body_size.conf
RUN echo "proxy_buffer_size 128k;" > /etc/nginx/conf.d/proxy_buffer_size.conf
RUN echo "proxy_buffers 4 256k;" > /etc/nginx/conf.d/proxy_buffers.conf
RUN echo "proxy_busy_buffers_size 256k;" > /etc/nginx/conf.d/proxy_busy_buffers_size.conf

# Set permissions
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    chmod -R 755 /usr/share/nginx/html && \
    chown -R nginx:nginx /var/cache/nginx && \
    chown -R nginx:nginx /var/log/nginx && \
    chown -R nginx:nginx /etc/nginx/conf.d && \
    touch /var/run/nginx.pid && \
    chown -R nginx:nginx /var/run/nginx.pid

USER nginx

EXPOSE ${EXPORT_PORT}

CMD ["nginx", "-g", "daemon off;"]
```

Dockerfile에 보면 nginx의 설정 파일이 필요하다는 것을 알 수 있습니다.  
```
COPY ${BUILD_FOLDER}/nginx.conf /etc/nginx/templates/default.conf.template
```

container 디렉토리 밑에 아래 내용으로 nginx.conf파일을 만듭니다.  
listen하는 포트가 18080으로 되어 있음을 잠깐 기억해 주세요.  
```
server {
    listen 18080;
    server_name localhost;
    
    location / {
        root /usr/share/nginx/html;
        index index.html index.htm;
        try_files $uri $uri/ /index.html;
        
        # Cache static files
        location ~* \.(jpg|jpeg|png|gif|ico|css|js)$ {
            expires 1y;
            add_header Cache-Control "public, no-transform";
        }
    }
    
    # Health check endpoint
    location /health {
        access_log off;
        return 200 'healthy\n';
        add_header Content-Type text/plain;
    }
    
    # Error pages
    error_page 500 502 503 504 /50x.html;
    location = /50x.html {
        root /usr/share/nginx/html;
    }
}
```

이제 이 Docker파일로 이미지를 만들겠습니다.   

먼저 라이브러리를 최신본으로 한번 더 설치합니다.   
Dockerfile에서 'npm install'이 아니라 'npm ci'로 라이브러리를 설치하는데  
이때 package.json이 아니라 package-lock.json을 이용합니다.  
이 두 파일의 내용이 다르면 에러가 나기 때문에 맞춰주기 위해서입니다.   
참고로 'ci'는 'clean install'의 약자입니다.  
```
# frontend
cd ~/workspace/lifesub-web
npm install # package.json과 package-lock.json 일치를 위해
```

이미지 빌드를 합니다.  
Dockerfile에서 요구하는 Argument를 적절하게 넘겨야 합니다.  
EXPORT_PORT는 nginx.conf에서 정의한 '18080'으로 당연히 넘겨야겠죠?  
```
docker build \
  --platform linux/amd64 \
  --build-arg PROJECT_FOLDER="." \
  --build-arg REACT_APP_MEMBER_URL="http://{VM IP}:8081" \
  --build-arg REACT_APP_MYSUB_URL="http://{VM IP}:8082" \
  --build-arg REACT_APP_RECOMMEND_URL="http://{VM IP}:8083" \
  --build-arg BUILD_FOLDER="container" \
  --build-arg EXPORT_PORT="18080" \
  -f container/Dockerfile \
  -t lifesub-web:latest .
```

예시)    
```
docker build \
  --platform linux/amd64 \
  --build-arg PROJECT_FOLDER="." \
  --build-arg REACT_APP_MEMBER_URL="http://20.39.207.118:8081" \
  --build-arg REACT_APP_MYSUB_URL="http://20.39.207.118:8082" \
  --build-arg REACT_APP_RECOMMEND_URL="http://20.39.207.118:8083" \
  --build-arg BUILD_FOLDER="container" \
  --build-arg EXPORT_PORT="18080" \
  -f container/Dockerfile \
  -t lifesub-web:latest .
```

이미지가 잘 생성 되었는지 확인해 봅시다.  
```
docker images
```

| [Top](#목차) |

---

### 3.로컬에서 테스트  
로컬에서 먼저 컨테이너를 실행 해 보는게 좋겠죠.  
잠깐! 그런데 lifesub-web 이미지를 만들 때 backend의 주소를 VM주소로 해서 만들었습니다.  
이러면 로컬에서 테스트가 안되겠죠?   
아래와 같이 backend의 주소를 로컬로 만들고 다시 빌드부터 합니다.  
기존 이미지는 푸시할 때 사용할 것이므로 이미지명에 '-local'을 붙여서 만듭시다.  
```
cd ~/workspace/lifesub-web
docker build \
  --platform linux/amd64 \
  --build-arg PROJECT_FOLDER="." \
  --build-arg REACT_APP_MEMBER_URL="http://localhost:8081" \
  --build-arg REACT_APP_MYSUB_URL="http://localhost:8082" \
  --build-arg REACT_APP_RECOMMEND_URL="http://localhost:8083" \
  --build-arg BUILD_FOLDER="container" \
  --build-arg EXPORT_PORT="18080" \
  -f container/Dockerfile \
  -t lifesub-web-local:latest .
```

이제 로컬에서 컨테이너를 실행해 봅니다.   
```
docker run -d --name member --rm -p 8081:8081 \
-e POSTGRES_HOST={member db 서비스 L/B IP} \
-e ALLOWED_ORIGINS=http://localhost:18080 \
member:latest

docker run -d --name mysub --rm -p 8082:8082 \
-e POSTGRES_HOST={mysub db 서비스 L/B IP} \
-e ALLOWED_ORIGINS=http://localhost:18080 \
mysub:latest

docker run -d --name recommend --rm -p 8083:8083 \
-e POSTGRES_HOST={recommend db 서비스 L/B IP} \
-e ALLOWED_ORIGINS=http://localhost:18080 \
recommend:latest

docker run -d --name lifesub-web --rm -p 18080:18080 \
lifesub-web-local:latest
```

예시)  
```
docker run -d --name member --rm -p 8081:8081 \
-e POSTGRES_HOST=20.249.132.14 \
-e ALLOWED_ORIGINS=http://localhost:18080 \
member:latest

docker run -d --name mysub --rm -p 8082:8082 \
-e POSTGRES_HOST=20.249.132.34 \
-e ALLOWED_ORIGINS=http://localhost:18080 \
mysub:latest

docker run -d --name recommend --rm -p 8083:8083 \
-e POSTGRES_HOST=20.249.132.47 \
-e ALLOWED_ORIGINS=http://localhost:18080 \
recommend:latest

docker run -d --name lifesub-web-local --rm -p 18080:18080 \
lifesub-web-local:latest
```

잘 실행되었는지 보는 명령은?  
네, docker ps입니다.  
```
docker ps
```

이제 브라우저에서 lifesub-web의 주소를 입력해서 확인하면 되겠죠?  
http://localhost:18080으로 접근합니다.  
ID는 user01~user10이고 암호는 'Passw0rd'입니다.  
로그인 잘되고 다른 기능도 잘 되는지 확인합니다.   

잘 되시나요?  
잘 하셨습니다.   
이제 다음 단계로 갑시다!    

| [Top](#목차) |

---

## Push image

이제 이미지를 이미지 레지스트리로 푸시할 차례입니다.  
우리는 DockerHub(docker.io)가 아닌 자신의 ACR(Azure Container Registry)에 푸시할겁니다.  
  
이미지 푸시를 위해서는 이미지 레지스트리에 로그인부터 해야겠죠?  
ACR 로그인: 아래 명령으로 id와 pw를 알아냅니다.  
ACR명은 {본인ID}cr입니다.  
id는 ACR이름과 동일하고, 암호는 첫번째 암호값입니다.       
```
az acr credential show --name {ACR명}
docker login {ACR명}.azurecr.io
```

예시)     
```
$ az acr credential show --name dg0200cr 
{
  "passwords": [
    {
      "name": "password",
      "value": "8Fd9/heysqn4sJS+myHkt/CVk0Zw7jE4DTd9M+pRj6+ACRBukRsv"
    },
    {
      "name": "password2",
      "value": "X3Nd+2oVSwOMz2ikXyGuLGQjxUJRVXFkVyv+2DOdpa+ACRAo7LPW"
    }
  ],
  "username": "dg0200cr"
}
$ docker login dg0200cr.azurecr.io 
Username: dg0200cr
Password: 
Login Succeeded
```

로그인 되었으면 이제 푸시하면 됩니다.  
하지만 우리가 만든 이미지는 repository와 tag만 있습니다.   
이를 fullname으로 바꿔줘야 합니다.   

이때 기존 이미지를 바꾸는게 아니라 'Docker tag' 명령으로 링크 이미지를 만들 수 있습니다.  
```
docker tag member:latest {ACR명}.azurecr.io/lifesub/member:latest
docker tag mysub:latest {ACR명}.azurecr.io/lifesub/mysub:latest
docker tag recommend:latest {ACR명}.azurecr.io/lifesub/recommend:latest
docker tag lifesub-web:latest {ACR명}.azurecr.io/lifesub/lifesub-web:latest
```

예시)  
```
docker tag member:latest dg0200cr.azurecr.io/lifesub/member:latest
docker tag mysub:latest dg0200cr.azurecr.io/lifesub/mysub:latest
docker tag recommend:latest dg0200cr.azurecr.io/lifesub/recommend:latest
docker tag lifesub-web:latest dg0200cr.azurecr.io/lifesub/lifesub-web:latest
```

이미지 태깅이 잘 되었는지 확인하고 푸시합니다.   
```
docker images
```

```
docker push {ACR명}.azurecr.io/lifesub/member:latest
docker push {ACR명}.azurecr.io/lifesub/mysub:latest 
docker push {ACR명}.azurecr.io/lifesub/recommend:latest
docker push {ACR명}.azurecr.io/lifesub/lifesub-web:latest
```

예시)   
```
docker push dg0200cr.azurecr.io/lifesub/member:latest
docker push dg0200cr.azurecr.io/lifesub/mysub:latest 
docker push dg0200cr.azurecr.io/lifesub/recommend:latest
docker push dg0200cr.azurecr.io/lifesub/lifesub-web:latest
```

| [Top](#목차) |

---

##  컨테이너 실행
컨테이너를 실행할 VM을 SSH세션을 눌러 접속합니다.     
아직 안 만들었으면 아래 링크를 참조하여 만듭니다.  
https://github.com/cna-bootcamp/handson-azure/blob/main/prepare/setup-server.md#mobaxterm-%EC%84%B8%EC%85%98-%EC%9E%91%EC%84%B1  


이미지를 다운로드 해야 하기 때문에 ACR에 로그인부터 합니다.   
```
az acr credential show --name {ACR명}
docker login {ACR명}.azurecr.io
```

드디어 서버에 컨테이너를 실행할 시간입니다.   
백엔드부터 실행해 봅시다.  
```
docker run -d --name member --rm -p 8081:8081 \
-e POSTGRES_HOST={member db 서비스 L/B IP} \
-e ALLOWED_ORIGINS=http://{VM IP}:18080 \
{ACR명}.azurecr.io/lifesub/member:latest

docker run -d --name mysub --rm -p 8082:8082 \
-e POSTGRES_HOST={mysub db 서비스 L/B IP} \
-e ALLOWED_ORIGINS=http://{VM IP}:18080 \
{ACR명}.azurecr.io/lifesub/mysub:latest

docker run -d --name recommend --rm -p 8083:8083 \
-e POSTGRES_HOST={recommend db 서비스 L/B IP} \
-e ALLOWED_ORIGINS=http://{VM IP}:18080 \
{ACR명}.azurecr.io/lifesub/recommend:latest
```
예시)
```
docker run -d --name member --rm -p 8081:8081 \
-e POSTGRES_HOST=20.249.132.14 \
-e ALLOWED_ORIGINS=http://20.39.207.118:18080 \
dg0200cr.azurecr.io/lifesub/member:latest

docker run -d --name mysub --rm -p 8082:8082 \
-e POSTGRES_HOST=20.249.132.34 \
-e ALLOWED_ORIGINS=http://20.39.207.118:18080 \
dg0200cr.azurecr.io/lifesub/mysub:latest

docker run -d --name recommend --rm -p 8083:8083 \
-e POSTGRES_HOST=20.249.132.47 \
-e ALLOWED_ORIGINS=http://20.39.207.118:18080 \
dg0200cr.azurecr.io/lifesub/recommend:latest
```

프론트엔드도 실행합니다.   
```
docker run -d --name lifesub-web --rm -p 18080:18080 \
{ACR명}.azurecr.io/lifesub/lifesub-web:latest
```

예시)
```
docker run -d --name lifesub-web --rm -p 18080:18080 \
dg0200cr.azurecr.io/lifesub/lifesub-web:latest
```

잘 실행되었는지 프로세스를 확인 하고요.  
```
docker ps
```

각 서비스의 로그도 확인해 봅니다.   
특히 백엔드는 데이터베이스에 잘 접속이 되는지를 확인해야 합니다.  
```
docker log -f {container명 또는 container id}  
```

브라우저에서 최종 확인 합니다.    
브라우저에서 http://{VM IP}:18080으로 접근해서 테스트 해봅니다.  
로컬에서 잘 되었으니 서버에서도 잘 될겁니다.  

| [Top](#목차) |

---

## 기타 Docker 명령  
로컬에서 기타 Docker 명령을 실습해 보겠습니다.  

**1.컨테이너 중지/시작/삭제**  
각 컨테이너를 중지해 보십시오.   
```
docker stop lifesub-web-local member mysub recommend
```
컨테이너가 중지되면 프로세스가 사라지는게 아니라 중지만 됩니다.   
중지된 컨테이너 프로세스를 보려면 ?   
네, docker ps -a 로 보면 됩니다.   
해볼까요? 어라! 아무것도 안보이네요.  
```
docker ps -a 
```
  
왜 그럴까요?   
컨테이너 실행할 때 옵션을 찬찬히 볼까요?   
```
docker run -d --name member --rm -p 8081:8081 \
-e POSTGRES_HOST=20.249.132.14 \
-e ALLOWED_ORIGINS=http://localhost:18080 \
member:latest

docker run -d --name mysub --rm -p 8082:8082 \
-e POSTGRES_HOST=20.249.132.34 \
-e ALLOWED_ORIGINS=http://localhost:18080 \
mysub:latest

docker run -d --name recommend --rm -p 8083:8083 \
-e POSTGRES_HOST=20.249.132.47 \
-e ALLOWED_ORIGINS=http://localhost:18080 \
recommend:latest

docker run -d --name lifesub-web-local --rm -p 18080:18080 \
lifesub-web-local:latest
```

어떤 옵션 때문에 컨테이너 중지 시 바로 프로세스가 삭제되었는지 찾으셨나요?  
  

    
'--rm'옵션 때문입니다.  
이 옵션을 지우고 다시 실행하고 다시 중지 해 봅시다.  
```
docker run -d --name member -p 8081:8081 \
-e POSTGRES_HOST=20.249.132.14 \
-e ALLOWED_ORIGINS=http://localhost:18080 \
member:latest

docker run -d --name mysub -p 8082:8082 \
-e POSTGRES_HOST=20.249.132.34 \
-e ALLOWED_ORIGINS=http://localhost:18080 \
mysub:latest

docker run -d --name recommend -p 8083:8083 \
-e POSTGRES_HOST=20.249.132.47 \
-e ALLOWED_ORIGINS=http://localhost:18080 \
recommend:latest

docker run -d --name lifesub-web-local -p 18080:18080 \
lifesub-web-local:latest
```

```
docker stop lifesub-web-local member mysub recommend
docker ps -a
```
이젠 'EXIT'상태의 컨테이너가 보일겁니다.   
  

중지된 컨테이너를 다시 시작해 봅시다.   
```
docker start lifesub-web-local member mysub recommend
docker ps
```

잘 다시 실행되죠?  

  
다시 중지시키고 이번엔 완전히 삭제 해 봅시다.  
```
docker stop lifesub-web-local member mysub recommend
docker ps -a
```

docker rm으로 영구 삭제 합니다.  
```
docker rm lifesub-web-local member mysub recommend
docker ps -a
```

**2.이미지 지우기**  
4개 이미지가 더 이상 필요 없다고 가정해 봅시다.  
이미지를 지우려면 docker rmi 명령을 사용하면 됩니다.   
이미지를 정리 안하면 스토리지를 차지하기 때문에 정기적으로 지우는게 좋습니다.  
member, mysub은 다음 실습에 사용하므로 나머지 이미만 삭제해 봅시다.  
```
docker rmi lifesub-web-local recommend
docker images 
```

docker tag를 사용해서 만든 이미지가 있으면 잘 안지워질때가 있습니다.   
이때는 '--force'옵션을 추가하시면 됩니다.   

**3.중지된 컨테이너 정리**  
일일히 컨테이너나 이미지를 정리하는게 번거로울 수 있습니다.   
일괄적으로 처리하는 명령이 있습니다.  
먼저 중지된 컨테이너들을 모두 한꺼번에 삭제하는 명령은?  
docker container prune 입니다.   
테스트를 위해 '--rm'옵션 없이 컨테이너를 실행하고 중지하십시오.   
```
docker run -d --name member -p 8081:8081 \
-e POSTGRES_HOST=20.249.132.14 \
-e ALLOWED_ORIGINS=http://localhost:18080 \
member:latest

docker run -d --name mysub -p 8082:8082 \
-e POSTGRES_HOST=20.249.132.34 \
-e ALLOWED_ORIGINS=http://localhost:18080 \
mysub:latest

docker stop member mysub
docker ps -a
```

```
docker container prune
```

이제 정지된 컨테이너가 모두 삭제되었을겁니다.  
```
docker ps -a
```

**4.컨테이너로 명령 실행 하기**  
컨테이너 안에 원하는 파일이 있는지, 환경변수는 잘 생성되었는지 보고 싶을 수 있습니다.  
이때 컨테이너 내부로 명령을 보내야 하는데 이때 docker exec 를 사용합니다.  
우선 member서비스를 다시 실행하고 테스트 해 보죠.  
```
docker run -d --name member -p 8081:8081 \
-e POSTGRES_HOST=20.249.132.14 \
-e ALLOWED_ORIGINS=http://localhost:18080 \
member:latest
```

docker exec -it {container name / id} {command} 
```
docker exec -it member env
docker exec -it member ls -al /
```

이걸 응용해서 컨테이너 안으로 들어갈 수도 있습니다.   
'bash'를 지원 안하면 'sh'을 사용하십시오.   
```
docker exec -it member bash
ls -al
```

**5.파일 복사하기**  
컨테이너 내부로 파일을 복사하거나, 반대로 컨테이너 내의 파일을 밖으로 복사할 수 있습니다.  
docker cp {소스 경로} {타겟경로}의 문법으로 사용하며, 컨테이너의 경로는 {컨테이너명/id:경로}형식으로 지정합니다.   
```
cd ~/workspace/lifesub

docker exec -it member mkdir -p /tmp
docker cp member/build.gradle member:/tmp/build.gradle
docker exec -it member ls -al /tmp
```

반대로 컨테이너 안의 파일을 복사해 볼까요?
```
docker cp member:/home/k8s/app.jar ./member.jar
ls -al
```

**6.이미지 압축하기/압축파일에서 이미지 로딩하기**  
폐쇄망안으로 컨테이너 이미지를 반입할 때 많이 사용합니다.   
인터넷망에서 이미지를 압축파일로 만들어 반입한 후 내부망에서 이미지를 압축파일로 부터 로딩하는 방법입니다.  
```
docker save mysub:latest -o mysub.tar
ls -al mysub.tar
```

```
docker rmi mysub
docker images mysub 
```

```
docker load -i mysub.tar
docker images mysub
```

**7.불필요한 이미지 정리**  
더 이상 안 사용하는 이미지를 남기면 스토리지만 낭비됩니다.  
그렇다고 일일히 찾기도 힘들죠.  
이때 쓸 수 있는 명령이 docker image prune -a 입니다.  
이미지를 사용하는 컨테이너가 있으면 안되기 때문에 모든 컨테이너를 삭제합니다.  
```
docker stop $(docker ps -q)
```

```
docker container prune
```

```
docker images
docker image prune -a
docker images
```

**8.컨테이너간 통신**  
서버 사이드에서 컨테이너 간 통신을 위해 가상의 통신망을 이용할 수 있습니다.  
docker의 network 객체로 가상의 통신망을 만들고 컨테이너 실행 시 동일한 network 객체를 사용하면  
컨테이너간 컨테이너 이름으로 통신할 수 있게 됩니다.   
member 서비스의 database를 K8S에 배포한 DB를 사용하지 않고,  
Docker 컨테이너로 실행한 DB를 사용하도록 해 보겠습니다.   

member 이미지를 다시 생성합니다.    
```
cd ~/workspace/lifesub

DOCKER_FILE=Dockerfile
service=member
docker build \
  --build-arg BUILD_LIB_DIR="${service}/build/libs" \
  --build-arg ARTIFACTORY_FILE="${service}.jar" \
  -f container/${DOCKER_FILE} \
  -t ${service}:latest .
```

컨테이너간 통신을 위한 네트워크 객체를 만듭니다.  
```
docker network create lifesub-network
docker network ls
```

PostgreSQL DB 컨테이너를 Docker로 실행합니다.  
이때 network를 lifesub-network로 지정합니다.   
  
'-v'옵션이 처음 나왔는데 이는 Host의 특정 볼륨을 컨테이너 안으로 마운트 시키는 역할을 합니다.   

```
mkdir -p $HOME/postgres_data

docker run -d \
  --name member-db \
  --network lifesub-network \
  -e POSTGRES_USER=admin \
  -e POSTGRES_PASSWORD=Passw0rd \
  -e POSTGRES_DB=member \
  -p 5432:5432 \
  -v $HOME/postgres_data:/var/lib/postgresql/data \
  postgres:latest
```

이제 member 컨테이너를 실행하면서 동일한 가상 네트워크인 'lifesub-network'를 사용하도록 합니다.   
POSTGRES_HOST는 k8s의 DB pod가 아니라 위에서 실행한 container 이름으로 지정합니다.   
```
docker run -d --name member --rm -p 8081:8081 \
--network lifesub-network \
-e POSTGRES_HOST=member-db \
member:latest
```

웹 브라우저를 열고 member 서비스의 swagger page를 엽니다.  
http://localhost:8081/swagger-ui.html   

로그인 테스트를 해보면 local db container에 container명(member-db)으로 연결되어  
정상적으로 수행되는것을 확인하실 수 있을겁니다.    

| [Top](#목차) |

---

## Docker Compose
Docker Comnpose는 여러개의 관련성 있는 서비스의 이미지빌드/Pull/Push/컨테이너 실행을 편리하게 해주는 툴입니다.   

### WSL에 Docker Compose 설치   
```
sudo apt-get update
sudo apt-get install docker-compose-plugin
```

Docker 재시작
```
sudo service docker restart
```

설치 확인
```
docker compose version 
```

| [Top](#목차) |

---

> **Tip: CLI명 변경**  
> 최신 Docker에서는 CLI로 'docker-compose' 대신 'docker compose'명령을 사용할 것을 권장합니다.   
 

### docker-compose.yml 작성  
docker-compose.yml은 이미지빌드/Pull/Push/컨테이너 실행을 관리하기 위한 매니페스트 파일입니다.  
기본 파일명이 'docker-compose.yml'이고 다른 파일로 변경해도 되며, 여러개의 파일을 만드는 것도 당연히 가능합니다.  

아래와 같이 파일을 작성합니다.   
lifesub-web-local은 로컬 테스트용입니다.  포트 충돌 방지를 위해 외부 포트를 '18081'으로 지정합니다.  
또한 백엔드 서비스의 'ALLOW_ORIGINS'에도 허용할 3개의 host를 모두 지정하였습니다.   
```
cd ~/workspace
vi docker-compose.yml
```

```
services:
  # Backend Services
  member:
    build:
      context: ${WORKSPACE}/lifesub
      dockerfile: container/Dockerfile
      args:
        BUILD_LIB_DIR: "member/build/libs"
        ARTIFACTORY_FILE: "member.jar"
    image: {ACR명}.azurecr.io/lifesub/member:latest
    container_name: member
    ports:
      - "8081:8081"
    environment:
      - POSTGRES_HOST={DB Service L/B IP}
      - ALLOWED_ORIGINS=http://localhost:18080,http://localhost:18081,http://{VM IP}:18080
    restart: unless-stopped

  mysub:
    build:
      context: ${WORKSPACE}/lifesub
      dockerfile: container/Dockerfile
      args:
        BUILD_LIB_DIR: "mysub-infra/build/libs"
        ARTIFACTORY_FILE: "mysub.jar"
    image: {ACR명}.azurecr.io/lifesub/mysub:latest
    container_name: mysub
    ports:
      - "8082:8082"
    environment:
      - POSTGRES_HOST={DB Service L/B IP}
      - ALLOWED_ORIGINS=http://localhost:18080,http://localhost:18081,http://{VM IP}:18080
    restart: unless-stopped

  recommend:
    build:
      context: ${WORKSPACE}/lifesub
      dockerfile: container/Dockerfile
      args:
        BUILD_LIB_DIR: "recommend/build/libs"
        ARTIFACTORY_FILE: "recommend.jar"
    image: {ACR명}.azurecr.io/lifesub/recommend:latest
    container_name: recommend
    ports:
      - "8083:8083"
    environment:
      - POSTGRES_HOST={DB Service L/B IP}
      - ALLOWED_ORIGINS=http://localhost:18080,http://localhost:18081,http://{VM IP}:18080
    restart: unless-stopped

  # Frontend Service
  lifesub-web:
    build:
      context: ${WORKSPACE}/lifesub-web
      dockerfile: container/Dockerfile
      args:
        PROJECT_FOLDER: "."
        REACT_APP_MEMBER_URL: "http://{VM IP}:8081"
        REACT_APP_MYSUB_URL: "http://{VM IP}:8082"
        REACT_APP_RECOMMEND_URL: "http://{VM IP}:8083"
        BUILD_FOLDER: "container"
        EXPORT_PORT: "18080"
    image: {ACR명}.azurecr.io/lifesub/lifesub-web:latest
    container_name: lifesub-web
    ports:
      - "18080:18080"
    restart: unless-stopped

  # Frontend Service
  lifesub-web-local:
    build:
      context: ${WORKSPACE}/lifesub-web
      dockerfile: container/Dockerfile
      args:
        PROJECT_FOLDER: "."
        REACT_APP_MEMBER_URL: "http://localhost:8081"
        REACT_APP_MYSUB_URL: "http://localhost:8082"
        REACT_APP_RECOMMEND_URL: "http://localhost:8083"
        BUILD_FOLDER: "container"
        EXPORT_PORT: "18080"
    image: lifesub-web-local:latest
    container_name: lifesub-web-local
    ports:
      - "18081:18080"
    restart: unless-stopped

networks:
  default:
    name: lifesub-nw

```

예시)
```
services:
  # Backend Services
  member:
    build:
      context: ${WORKSPACE}/lifesub
      dockerfile: container/Dockerfile
      args:
        BUILD_LIB_DIR: "member/build/libs"
        ARTIFACTORY_FILE: "member.jar"
    image: dg0200cr.azurecr.io/lifesub/member:latest
    container_name: member
    ports:
      - "8081:8081"
    environment:
      - POSTGRES_HOST=20.249.132.14
      - ALLOWED_ORIGINS=http://localhost:18080,http://localhost:18081,http://20.39.207.118:18080
    restart: unless-stopped

  mysub:
    build:
      context: ${WORKSPACE}/lifesub
      dockerfile: container/Dockerfile
      args:
        BUILD_LIB_DIR: "mysub-infra/build/libs"
        ARTIFACTORY_FILE: "mysub.jar"
    image: dg0200cr.azurecr.io/lifesub/mysub:latest
    container_name: mysub
    ports:
      - "8082:8082"
    environment:
      - POSTGRES_HOST=20.249.132.34
      - ALLOWED_ORIGINS=http://localhost:18080,http://localhost:18081,http://20.39.207.118:18080
    restart: unless-stopped

  recommend:
    build:
      context: ${WORKSPACE}/lifesub
      dockerfile: container/Dockerfile
      args:
        BUILD_LIB_DIR: "recommend/build/libs"
        ARTIFACTORY_FILE: "recommend.jar"
    image: dg0200cr.azurecr.io/lifesub/recommend:latest
    container_name: recommend
    ports:
      - "8083:8083"
    environment:
      - POSTGRES_HOST=20.249.132.47
      - ALLOWED_ORIGINS=http://localhost:18080,http://localhost:18081,http://20.39.207.118:18080
    restart: unless-stopped

  # Frontend Service
  lifesub-web:
    build:
      context: ${WORKSPACE}/lifesub-web
      dockerfile: container/Dockerfile
      args:
        PROJECT_FOLDER: "."
        REACT_APP_MEMBER_URL: "http://20.39.207.118:8081"
        REACT_APP_MYSUB_URL: "http://20.39.207.118:8082"
        REACT_APP_RECOMMEND_URL: "http://20.39.207.118:8083"
        BUILD_FOLDER: "container"
        EXPORT_PORT: "18080"
    image: dg0200cr.azurecr.io/lifesub/lifesub-web:latest
    container_name: lifesub-web
    ports:
      - "18080:18080"
    restart: unless-stopped

  # Frontend Service(Local 테스트용)
  lifesub-web-local:
    build:
      context: ${WORKSPACE}/lifesub-web
      dockerfile: container/Dockerfile
      args:
        PROJECT_FOLDER: "."
        REACT_APP_MEMBER_URL: "http://localhost:8081"
        REACT_APP_MYSUB_URL: "http://localhost:8082"
        REACT_APP_RECOMMEND_URL: "http://localhost:8083"
        BUILD_FOLDER: "container"
        EXPORT_PORT: "18081"
    image: lifesub-web-local:latest
    container_name: lifesub-web-local
    ports:
      - "18081:18080"
    restart: unless-stopped
    
networks:
  default:
    name: lifesub-nw
```

| [Top](#목차) |

---

### 로컬에서 테스트
기존 이미지를 모두 삭제 합니다.  
```
docker stop $(docker ps -q)
```
```
docker container prune
```
```
docker image prune -a
docker images
```

이미지를 한꺼번에 빌드합니다.  
'build' 뒤에 스페이스로 구분하여 특정 서비스명만 지정할 수도 있습니다.  
```
cd ~/workspace
export WORKSPACE=~/workspace
docker compose build 
```

```
docker images 
```

컨테이너를 동시에 실행합니다.  
'-d'를 붙여 백그라운드로 실행하는게 좋습니다.   
이 옵션을 안 붙이면 forground로 실행되고 CTRL-C를 누르면 중지되서 불편합니다.   
'up' 뒤에 스페이스로 구분하여 특정 서비스명만 지정할 수도 있습니다.  
```
docker compose up -d  
docker compose ps
```

브라우저에서 http://localhost:18081으로 접근하여 테스트 합니다.   

컨테이너를 한꺼번에 중단합니다.  
뒤에 스페이스로 구분하여 특정 서비스명만 지정할 수도 있습니다.  
```
docker compose down  
```

| [Top](#목차) |

---

### 서버에서 테스트 
이미지를 한꺼번에 업로드 합니다.  단, lifesub-web-local은 제외 합니다. 
```
docker login {ACR명}.azurecr.io
docker compose push lifesub-web member mysub recommend 
```

SSH세션을 눌러 VM에 로그인 합니다.  


docker-compose 설치를 설치 합니다.    
```
sudo apt  install docker-compose
```

기존 이미지를 모두 삭제 합니다.  
```
docker stop $(docker ps -q)
```
```
docker container prune
```
```
docker image prune -a
docker images
```

docker-compose.yml 파일을 만듭니다.   
로컬의 docker-compose.yml 파일과 유사하게 생성하는데   
각 서비스의 build 섹션을 제거하고 lifesub-web-local은 필요 없으므로 제외합니다.    
```
mkdir ~/workspace 
cd ~/workspace
vi docker-compose.yml 
```

예제)
```
services:
  # Backend Services
  member:
    image: dg0200cr.azurecr.io/lifesub/member:latest
    container_name: member
    ports:
      - "8081:8081"
    environment:
      - POSTGRES_HOST=20.249.132.14
      - ALLOWED_ORIGINS=http://localhost:18080,http://localhost:18081,http://20.39.207.118:18080
    restart: unless-stopped

  mysub:
    image: dg0200cr.azurecr.io/lifesub/mysub:latest
    container_name: mysub
    ports:
      - "8082:8082"
    environment:
      - POSTGRES_HOST=20.249.132.34
      - ALLOWED_ORIGINS=http://localhost:18080,http://localhost:18081,http://20.39.207.118:18080
    restart: unless-stopped

  recommend:
    image: dg0200cr.azurecr.io/lifesub/recommend:latest
    container_name: recommend
    ports:
      - "8083:8083"
    environment:
      - POSTGRES_HOST=20.249.132.47
      - ALLOWED_ORIGINS=http://localhost:18080,http://localhost:18081,http://20.39.207.118:18080
    restart: unless-stopped

  # Frontend Service
  lifesub-web:
    image: dg0200cr.azurecr.io/lifesub/lifesub-web:latest
    container_name: lifesub-web
    ports:
      - "18080:18080"
    restart: unless-stopped
    
networks:
  default:
    name: lifesub-nw

```

이미지 다운로드를 합니다.  
사실 이 과정은 생략해도 됩니다.  실행 시 로컬에 이미지가 없으면 자동으로 다운로드하기 때문입니다.  
```
export WORKSPACE=~/workspace

docker compose pull  
```

이제 서버에서 컨테이너를 한꺼번에 실행 합니다.    
```
docker compose up -d 
docker compose ps
```
브라우저에서 http://{VM IP}:18080으로 접근하여 테스트 합니다.    


컨테이너를 중단 합니다.  
```
docker compose down  
```

이미지를 모두 삭제 합니다.  
```
docker image prune -a 
```

| [Top](#목차) |

---

Container 이해의 모든 과정을 완료 하셨습니다.  
수고 많으셨습니다.  
  
![](images/2025-02-16-00-12-05.png)
