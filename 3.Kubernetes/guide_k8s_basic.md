# Kubernetes 기초  

개발한 구독관리 서비스를 컨테이너 이미지로 빌드하고 배포하면서 컨테이너 기술에 대해 이해합니다.  

## 목차
- [Kubernetes 기초](#kubernetes-기초)
  - [목차](#목차)
  - [서비스 배포하기](#서비스-배포하기)
    - [lifesub-ns의 리소스 삭제](#lifesub-ns의-리소스-삭제)
    - [네임스페이스 생성](#네임스페이스-생성)
    - [Database 설치](#database-설치)
    - [Application 빌드](#application-빌드)
    - [Kubernetes Manifest 생성](#kubernetes-manifest-생성)
    - [Manifest 점검 및 실행](#manifest-점검-및-실행)
    - [manifest 실행](#manifest-실행)
    - [정상 배포 확인](#정상-배포-확인)
    - [테스트](#테스트)

## 서비스 배포하기

### lifesub-ns의 리소스 삭제  
```
helm delete member mysub recommend -n lifesub-ns
k delete pvc --all -n lifesub-ns
k delete deploy --all -n lifesub-ns
k delete cm --all -n lifesub-ns
k delete secret --all -n lifesub-ns
k delete ns lifesub-ns
```

### 네임스페이스 생성
```bash
kubectl create ns dg0200-lifesub-ns 2>/dev/null || true
kubens dg0200-lifesub-ns
```

### Database 설치
각 백엔드 서비스는 PostgreSQL DB를 사용:

Helm repository 추가 
```
$ helm repo ls
```

없으면 추가
```
$ helm repo add bitnami https://charts.bitnami.com/bitnami
$ helm repo update
```

```
cd ~/workspace/lifesub
chmod +x deployment/database/deploy_db.sh
./deployment/database/deploy_db.sh
```

### Application 빌드
먼저 ACR 로그인 정보를 확인하고 로그인:
```bash
# ACR 로그인
ACR_NAME="dg0200cr"
ACR_SERVER="${ACR_NAME}.azurecr.io"
ACR_USERNAME=$(az acr credential show -n ${ACR_NAME} --query "username" -o tsv)
ACR_PASSWORD=$(az acr credential show -n ${ACR_NAME} --query "passwords[0].value" -o tsv)

# Docker 로그인
echo ${ACR_PASSWORD} | docker login ${ACR_SERVER} -u ${ACR_USERNAME} --password-stdin
```

Backend Application 실행파일 작성:
```bash
# lifesub 디렉토리에서 수행
cd ~/workspace/lifesub

# member 서비스
./gradlew :member:clean :member:build -x test

# mysub 서비스
./gradlew :mysub-infra:clean :mysub-infra:build -x test

# recommend 서비스
./gradlew :recommend:clean :recommend:build -x test
```

Backend Application 이미지 생성:
```bash
# lifesub 디렉토리에서 수행
cd ~/workspace/lifesub

# member 서비스
docker build \
  --build-arg BUILD_LIB_DIR="member/build/libs" \
  --build-arg ARTIFACTORY_FILE="member.jar" \
  -f container/Dockerfile \
  -t dg0200cr.azurecr.io/lifesub/member:1.0.0 .

# mysub 서비스
docker build \
  --build-arg BUILD_LIB_DIR="mysub-infra/build/libs" \
  --build-arg ARTIFACTORY_FILE="mysub.jar" \
  -f container/Dockerfile \
  -t dg0200cr.azurecr.io/lifesub/mysub:1.0.0 .

# recommend 서비스
docker build \
  --build-arg BUILD_LIB_DIR="recommend/build/libs" \
  --build-arg ARTIFACTORY_FILE="recommend.jar" \
  -f container/Dockerfile \
  -t dg0200cr.azurecr.io/lifesub/recommend:1.0.0 .
```

Backend Application 이미지 푸시:
```bash
# lifesub 디렉토리에서 수행
docker push dg0200cr.azurecr.io/lifesub/member:1.0.0

# mysub 서비스
docker push dg0200cr.azurecr.io/lifesub/mysub:1.0.0

# recommend 서비스
docker push dg0200cr.azurecr.io/lifesub/recommend:1.0.0
```

Frontend Application 빌드:
```bash
# lifesub-web 디렉토리에서 수행
cd ~/workspace/lifesub-web

docker build \
  --build-arg PROJECT_FOLDER="." \
  --build-arg REACT_APP_MEMBER_URL="http://member" \
  --build-arg REACT_APP_MYSUB_URL="http://mysub" \
  --build-arg REACT_APP_RECOMMEND_URL="http://recommend" \
  --build-arg BUILD_FOLDER="container" \
  --build-arg EXPORT_PORT="18080" \
  -f container/Dockerfile \
  -t dg0200cr.azurecr.io/lifesub/lifesub-web:1.0.0 .
docker push dg0200cr.azurecr.io/lifesub/lifesub-web:1.0.0
```

### Kubernetes Manifest 생성
배포 디렉토리 생성:
```bash
cd ~/workspace
mkdir -p lifesub/deployment/manifest/{configmaps,secrets,deployments,services}
mkdir -p lifesub-web/deployment/manifest/{deployments,services}
```

### Manifest 점검 및 실행
DB Service 이름 확인:
```bash
kubectl get svc
```

### manifest 실행
확인된 DB Service 이름으로 ConfigMap의 DB Host 수정 후 manifest 실행
```bash
cd ~/workspace
# ConfigMap 생성
kubectl apply -f lifesub/deployment/manifest/configmaps/

# Secret 생성
kubectl apply -f lifesub/deployment/manifest/secrets/

# Backend 서비스 배포
kubectl apply -f lifesub/deployment/manifest/deployments/
kubectl apply -f lifesub/deployment/manifest/services/

# Frontend 서비스 배포
kubectl apply -f lifesub-web/deployment/manifest/deployments/
kubectl apply -f lifesub-web/deployment/manifest/services/
```

### 정상 배포 확인
```bash
# Pod 상태 확인
kubectl get pods -w

# Service 상태 확인
kubectl get svc

```

### 테스트
Frontend Service의 External IP로 접속하여 서비스 동작 확인
http://lifesub-web.{VM IP}.nip.io     

예시)   
http://lifesub-web.20.249.204.104.nip.io  


| [Top](#목차) |

---
