## VM 생성

- [VM 생성](https://github.com/cna-bootcamp/handson-azure/blob/main/prepare/setup-server.md#bastion-vm-%EC%83%9D%EC%84%B1)  

- [Docker 설치](https://github.com/cna-bootcamp/handson-azure/blob/main/prepare/setup-server.md#docker%EC%84%A4%EC%B9%98)  

- 마이구독 사용 포트 오픈
```
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

## 컨테이너 이미지 빌드 
Backend 애플리케이션 

Build Jar
```
service=member
./gradlew :${service}:clean :${service}:build -x test
service=mysub
./gradlew :${service}-infra:clean :${service}-infra:build -x test
service=recommend
./gradlew :${service}:clean :${service}:build -x test
```

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

이미지 생성 확인
```
docker images
```

프론트엔드 애플리케이션
```
# frontend
cd ~/workspace/lifesub-web
npm install # package.json과 package-lock.json 일치를 위해
```

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

이미지 생성 확인
```
docker images
```

## Push image
ACR 로그인 
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

이미지 태그
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

이미지 생성 확인
```
docker images
```

이미지 푸시

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

## 실행
VM 접속   
```
ssh azureuser@{VM IP}
```
예시)
```
ssh azureuser@4.217.232.54
```

ACR 로그인
```
az acr credential show --name {ACR명}
docker login {ACR명}.azurecr.io
```


백엔드
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

프론트엔드 실행 
```
docker run -d --name lifesub-web --rm -p 18080:18080 \
{ACR명}.azurecr.io/lifesub/lifesub-web:latest
```

예시)
```
docker run -d --name lifesub-web --rm -p 18080:18080 \
dg0200cr.azurecr.io/lifesub/lifesub-web:latest
```

프로세스 확인
```
docker ps
```

## 확인  
브라우저에서 http://{VM IP}:18080으로 접근  

---

## Docker Compose
### docker-compose.yml  

```
version: '3.8'

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
      - ALLOWED_ORIGINS=http://localhost:18080,http://{VM IP}:18080
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
      - ALLOWED_ORIGINS=http://localhost:18080,http://{VM IP}:18080
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
      - ALLOWED_ORIGINS=http://localhost:18080,http://{VM IP}:18080
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

networks:
  default:
    name: lifesub-network

```

예시)
```
version: '3.8'

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
      - ALLOWED_ORIGINS=http://localhost:18080,http://20.39.207.118:18080
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
      - ALLOWED_ORIGINS=http://localhost:18080,http://20.39.207.118:18080
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
      - ALLOWED_ORIGINS=http://localhost:18080,http://20.39.207.118:18080
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

networks:
  default:
    name: lifesub-network
```

### 테스트
기존 이미지 모두 삭제
```
docker ps
docker stop {
```

빌드
```
export WORKSPACE=~/home/workspace
docker-compose build 
```

### 실행
```
docker-compose up -d 
```

### 중단
```
docker-compose down  
```

### 이미지 업로드
```
docker-compose push  
```

