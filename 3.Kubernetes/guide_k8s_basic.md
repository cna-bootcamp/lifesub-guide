# Kubernetes 기초  

개발한 구독관리 서비스를 컨테이너 이미지로 빌드하고 배포하면서 컨테이너 기술에 대해 이해합니다.  

## 목차
- [Kubernetes 기초](#kubernetes-기초)
  - [목차](#목차)
  - [ingress controller 추가](#ingress-controller-추가)
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

## ingress controller 추가

```
controller:
  replicaCount: 1
  service:
    annotations:
      service.beta.kubernetes.io/azure-load-balancer-health-probe-request-path: /healthz
    loadBalancerIP: ""
    
    #해당 포트가 어떤 애플리케이션 프로토콜을 사용하는지 명시적으로 지정하는 옵션 비활성화
    #targetPort를 named port("http", "https")로 매핑하려고 시도해서 
    #Ingress Nginx Controller pod의 container port는 숫자(80, 443)로 정의되어 있어서 매핑이 실패 
    appProtocol: false  
    
  config:
    use-forwarded-headers: "true"
  resources:
    requests:
      cpu: 100m
      memory: 128Mi
    limits:
      cpu: 500m
      memory: 512Mi

```

```
helm upgrade -i ingress-nginx -f ingress-values.yaml -n ingress-basic ingress-nginx/ingress-nginx
```

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

---



### 네임스페이스 생성
```bash
kubectl create ns dg0200-lifesub-ns 2>/dev/null || true
kubens dg0200-lifesub-ns
```

### Database 설치
각 백엔드 서비스는 PostgreSQL DB를 사용:

Helm repository 추가 
```
helm repo ls
```

없으면 추가
```
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
```

```
cd ~/workspace/lifesub
chmod +x deployment/database/deploy_db.sh
./deployment/database/deploy_db.sh dg0200-lifesub-ns
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


Ingress control IP를 구함
```
k get svc -n ingress-basic
```

```bash
# lifesub-web 디렉토리에서 수행
cd ~/workspace/lifesub-web

ingress_host=$(kubectl get svc ingress-nginx-controller -n ingress-basic -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)
echo "Ingress Host: ${ingress_host}"  
docker build \
  --build-arg PROJECT_FOLDER="." \
  --build-arg REACT_APP_MEMBER_URL="http://${ingress_host}/member" \
  --build-arg REACT_APP_MYSUB_URL="http://${ingress_host}/mysub" \
  --build-arg REACT_APP_RECOMMEND_URL="http://${ingress_host}/recommend" \
  --build-arg BUILD_FOLDER="container" \
  --build-arg EXPORT_PORT="18080" \
  -f container/Dockerfile \
  -t dg0200cr.azurecr.io/lifesub/lifesub-web:1.0.0 .
```

예시)
```
docker build \
  --build-arg PROJECT_FOLDER="." \
  --build-arg REACT_APP_MEMBER_URL="http://20.249.185.127/member" \
  --build-arg REACT_APP_MYSUB_URL="http://20.249.185.127/mysub" \
  --build-arg REACT_APP_RECOMMEND_URL="http://20.249.185.127/recommend" \
  --build-arg BUILD_FOLDER="container" \
  --build-arg EXPORT_PORT="18080" \
  -f container/Dockerfile \
  -t dg0200cr.azurecr.io/lifesub/lifesub-web:1.0.0 .
```

Frontend Application 이미지 푸시:
```
docker push dg0200cr.azurecr.io/lifesub/lifesub-web:1.0.0
```

### Kubernetes Manifest 생성
배포 디렉토리 생성:
```bash
cd ~/workspace
mkdir -p lifesub/deployment/manifest/{configmaps,secrets,deployments,services}
mkdir -p lifesub-web/deployment/manifest/{deployments,services}
```

ALLOWED_ORIGINS값을 동적으로 변경하여 common-config.yaml 생성  
```
# Ingress External IP 가져오기
ingress_host=$(kubectl get svc ingress-nginx-controller -n ingress-basic -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)

cat > lifesub/deployment/manifest/configmaps/common-config.yaml << EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: common-config
data:
  JPA_DDL_AUTO: update
  JPA_SHOW_SQL: "true"
  ALLOWED_ORIGINS: "http://localhost:18080,http://localhost:18081,http://${ingress_host}"
EOF
```
### Manifest 점검 및 실행
DB Service 이름 확인:
```bash
kubectl get svc
```

### manifest 실행

기존 파드 삭제
```
cd ~/workspace
k delete -f lifesub/deployment/manifest/deployments/
k delete -f lifesub-web/deployment/manifest/deployments/
```

확인된 DB Service 이름으로 ConfigMap의 DB Host 수정 후 manifest 실행
```bash
cd ~/workspace

# Ingreess 생성
kubectl apply -f lifesub/deployment/manifest/ingresses/

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
# Service 상태 확인
kubectl get svc

# Pod 상태 확인
kubectl get pods -w

```

### 테스트
Frontend Service의 External IP로 접속하여 서비스 동작 확인

```
web_host=$(kubectl get svc lifesub-web -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)
echo "테스트 주소: http://lifesub-web.${web_host}.nip.io"
```

예시)   
http://lifesub-web.20.249.204.104.nip.io  


| [Top](#목차) |

---
