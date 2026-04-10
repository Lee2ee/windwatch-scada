# WindWatch AI-SCADA

> 풍력발전 지능형 모니터링 시스템 — AI-powered Wind Turbine SCADA Platform

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen?logo=springboot)
![Python](https://img.shields.io/badge/Python-3.11-blue?logo=python)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

---

## 개요

WindWatch는 풍력발전기 운영을 위한 실시간 AI-SCADA(Supervisory Control and Data Acquisition) 플랫폼입니다.
Spring Boot 기반의 메인 서버와 Python FastAPI 마이크로서비스를 결합하여 다음 기능을 제공합니다.

- **실시간 대시보드** — WebSocket 기반 센서 데이터 시각화 (ECharts)
- **AI 블레이드 점검** — YOLOv8 컴퓨터 비전을 통한 결함 탐지
- **LLM 고장 분석** — Ollama(Llama-3) RAG 기반 자연어 진단 어시스턴트
- **하이브리드 AI** — 로컬(보안) / 클라우드(정확도) AI 선택 가능
- **Spring Batch** — 일일 자동 보고서 생성

---

## 시스템 아키텍처

```
┌─────────────────────────────────────────────┐
│              Spring Boot :8080               │
│  Thymeleaf · WebSocket · Security · Batch   │
│  H2(dev) / MySQL(prod) · Redis Cache        │
└────────┬─────────────┬───────────┬──────────┘
         │             │           │
    :8001│        :8002│      :8003│
┌────────▼──┐  ┌───────▼───┐ ┌───▼────────┐
│ Vision API│  │  LLM API  │ │ Simulator  │
│  YOLOv8  │  │ Ollama/   │ │ Sensor     │
│ FastAPI  │  │ Llama-3   │ │ Data Gen   │
└───────────┘  └───────────┘ └────────────┘
```

---

## 기술 스택

| 레이어 | 기술 |
|---|---|
| Backend | Java 17, Spring Boot 3.2.5, Spring Security, Spring Data JPA, Spring Batch, WebSocket (STOMP/SockJS) |
| Frontend | Thymeleaf, Bootstrap 5, ECharts, Vanilla JS |
| AI (Local) | Python 3.11, FastAPI, Ollama (Llama-3), LangChain, YOLOv8 |
| AI (Cloud) | OpenAI GPT-4o, Google Cloud Vision API |
| Database | H2 (개발), MySQL 8.x (운영) |
| Cache | Redis |
| Build | Maven 3.x |

---

## 화면 구성

| 화면 | 설명 |
|---|---|
| 로그인 | 폼 기반 인증, RBAC |
| 대시보드 | 풍력터빈 5기 실시간 모니터링, 발전량·RPM·온도 차트 |
| AI 블레이드 점검 | 이미지 업로드 → YOLOv8 결함 탐지 → 결과 시각화 |
| 이벤트 이력 | 알람·이상 이벤트 조회 및 Excel 내보내기 |
| AI 어시스턴트 | LLM 기반 고장 진단 채팅 (RAG 지식베이스 포함) |

---

## 시작하기

### 사전 요구사항

- Java 17+
- Maven 3.8+
- Python 3.11+
- [Ollama](https://ollama.com) (로컬 LLM 사용 시)
- Redis (선택, 없으면 캐시 비활성화로 동작)

### 설치 및 실행

**1. 저장소 클론**
```bash
git clone https://github.com/<your-username>/scada_hmi.git
cd scada_hmi
```

**2. Python 환경 설정**
```bash
python -m venv venv
venv\Scripts\activate          # Windows
# source venv/bin/activate     # Linux/Mac

pip install -r python/requirements.txt
```

**3. Ollama 모델 준비** (로컬 LLM 사용 시)
```bash
ollama serve
ollama pull llama3
```

**4. Spring Boot 실행**
```bash
./mvnw spring-boot:run
```

Python 마이크로서비스(Vision :8001, LLM :8002)는 Spring Boot 기동 시 자동으로 실행됩니다.

**5. 접속**
```
http://localhost:8080
```

기본 계정: `admin / admin123` (DataInitializer에서 생성)

---

## 환경 변수 (운영 환경)

운영 배포 시 아래 환경 변수를 설정하십시오. `.env` 파일은 절대 커밋하지 마십시오.

```env
# Database
DB_HOST=localhost
DB_PORT=3306
DB_NAME=windwatch
DB_USERNAME=your_db_user
DB_PASSWORD=your_db_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password

# AI (Cloud, 선택)
OPENAI_API_KEY=sk-...

# Python Services
OLLAMA_MODEL=llama3
```

운영 프로파일 활성화:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

---

## 프로젝트 구조

```
scada_hmi/
├── src/main/java/com/windwatch/scada/
│   ├── api/             # REST Controllers (Turbine, Vision, LLM)
│   ├── config/          # Security, WebSocket, Redis, Batch, PythonLauncher
│   ├── controller/      # MVC Controllers (Dashboard, Inspection, Assistant...)
│   ├── model/           # JPA Entities
│   ├── dto/             # Data Transfer Objects
│   ├── service/         # Business Logic + AI Services
│   └── repository/      # Spring Data JPA Repositories
├── src/main/resources/
│   ├── templates/       # Thymeleaf HTML
│   └── static/          # CSS, JS
├── python/
│   ├── llm/             # LLM API (Ollama + LangChain)
│   ├── vision/          # Vision API (YOLOv8)
│   └── simulator/       # Sensor Data Simulator
└── pom.xml
```

---

## API 엔드포인트

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/turbines` | 전체 터빈 목록 |
| `GET` | `/api/turbines/{id}/data` | 터빈 실시간 데이터 |
| `POST` | `/api/vision/detect` | 블레이드 이미지 결함 탐지 |
| `POST` | `/api/llm/ask` | AI 고장 분석 질의 |
| `GET` | `/actuator/health` | 헬스 체크 |

---

## 라이선스

MIT License — 자유롭게 사용, 수정, 배포 가능합니다.
