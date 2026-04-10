#!/usr/bin/env python3
"""
WindWatch AI-SCADA - LLM Microservice (FastAPI + LangChain)
RAG-powered fault analysis assistant for wind turbine operations.

Usage:
    pip install fastapi uvicorn pydantic
    # With Ollama (local LLM): pip install langchain langchain-community ollama
    # With OpenAI: pip install langchain langchain-openai
    uvicorn llm_api:app --host 0.0.0.0 --port 8002 --reload

Endpoints:
    POST /api/llm/ask      - Ask a question about turbine fault analysis
    GET  /api/llm/models   - List available models
    GET  /health           - Health check
"""

import sys
import time
import random
import logging
import os
from typing import List, Optional

# Windows 환경에서 stdout/stderr를 UTF-8로 강제 설정
if sys.stdout and hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')
if sys.stderr and hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8')

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(name)s: %(message)s',
    datefmt='%H:%M:%S'
)
logger = logging.getLogger('WindWatch-LLM')

# ---- Try importing LangChain / Ollama ----
LANGCHAIN_AVAILABLE = False
OLLAMA_AVAILABLE = False
chain = None

try:
    from langchain_community.llms import Ollama
    from langchain_core.prompts import PromptTemplate
    import ollama as ollama_client

    # Test Ollama connection
    try:
        models = ollama_client.list()
        OLLAMA_AVAILABLE = True
        LANGCHAIN_AVAILABLE = True

        OLLAMA_MODEL = os.getenv('OLLAMA_MODEL', 'llama3')
        llm = Ollama(model=OLLAMA_MODEL, temperature=0.3)

        prompt = PromptTemplate(
            input_variables=["context", "question"],
            template="""당신은 풍력 발전기 운영 및 유지보수 전문가 AI 어시스턴트입니다.
IEC 61400 표준, 풍력 터빈 유지보수 매뉴얼, 고장 진단 방법론에 정통합니다.

참고 정보:
{context}

질문: {question}

전문적이고 명확한 답변을 한국어로 제공하세요.
가능하면 구체적인 조치 순서와 안전 주의사항을 포함하세요."""
        )
        chain = prompt | llm
        logger.info(f"Ollama LLM ready with model: {OLLAMA_MODEL}")
    except Exception as e:
        logger.warning(f"Ollama not running: {e}. Using mock mode.")
        OLLAMA_AVAILABLE = False

except ImportError:
    logger.info("LangChain/Ollama not installed. Running in mock mode.")

# ---- Knowledge Base (Mock RAG) ----
KNOWLEDGE_BASE = {
    "gearbox": [
        {
            "title": "풍력발전기 기어박스 유지보수 매뉴얼 v3.2",
            "content": "기어박스 온도가 85°C를 초과할 경우 즉각적인 점검이 필요합니다. 90°C 이상에서는 비상 정지를 권고합니다. 정상 운전 온도 범위: 40-75°C. 오일 교환 주기: 5,000 운전시간 또는 1년.",
            "source": "내부 문서 / Maintenance-GB-001"
        },
        {
            "title": "IEC 61400-4 기어박스 설계 표준",
            "content": "운전 온도 한계치는 최대 90°C이며, 연속 운전 권장 온도는 75°C입니다. 기어박스 진동 허용 한계: 4.5 mm/s (ISO 10816-21 기준).",
            "source": "IEC Standard / 61400-4:2012"
        }
    ],
    "blade": [
        {
            "title": "블레이드 점검 및 수리 가이드",
            "content": "블레이드 균열 발견 시 즉시 운전 중단 후 전문 검사 필요. 전연부 침식: 최대 10mm 깊이까지 현장 보수 가능. 그 이상의 손상은 블레이드 교체 검토. 정기 점검 주기: 6개월.",
            "source": "내부 문서 / Blade-Inspection-Guide-v2"
        },
        {
            "title": "DNVGL-ST-0376 로터 블레이드 표준",
            "content": "블레이드 구조 결함 판단 기준 및 수명 평가 방법론. 균열 크기 3mm 이상은 즉각 수리 요구사항.",
            "source": "DNVGL Standard / ST-0376:2015"
        }
    ],
    "vibration": [
        {
            "title": "풍력터빈 진동 진단 및 상태 모니터링",
            "content": "드라이브트레인 진동 분석 방법론. RMS 진동값 기준: 정상 0-2.5 mm/s, 주의 2.5-5.0 mm/s, 경고 5.0-7.5 mm/s, 긴급 7.5+ mm/s.",
            "source": "내부 기술 문서 / CM-VIB-001"
        }
    ],
    "power": [
        {
            "title": "발전량 저하 원인 분석 매뉴얼",
            "content": "예상 발전량 대비 10% 이상 저하 시 조사 필요. 주요 원인: 1) 피치 시스템 오류 2) 발전기 결함 3) 전력 변환기 이상 4) 블레이드 표면 오염.",
            "source": "내부 문서 / Power-Performance-001"
        }
    ],
    "general": [
        {
            "title": "풍력발전기 연간 예방 정비 계획",
            "content": "연간 정기 점검 항목: 기어박스 오일 샘플링(분기별), 블레이드 육안 검사(6개월), 베어링 교체 검토(5년), 전기 시스템 점검(연 1회).",
            "source": "운영 표준 / PM-Annual-001"
        }
    ]
}

def retrieve_context(question: str) -> list:
    """Simple keyword-based document retrieval (mock RAG)."""
    question_lower = question.lower()
    retrieved = []

    keywords = {
        "gearbox": ["기어박스", "gearbox", "gear", "오일", "온도"],
        "blade": ["블레이드", "blade", "균열", "침식", "crack", "erosion"],
        "vibration": ["진동", "vibration", "vib", "베어링", "소음"],
        "power": ["발전량", "power", "출력", "피치", "pitch"],
        "general": ["점검", "정비", "maintenance", "inspection", "예방"]
    }

    for category, kws in keywords.items():
        if any(kw in question_lower for kw in kws):
            retrieved.extend(KNOWLEDGE_BASE.get(category, []))

    if not retrieved:
        retrieved = KNOWLEDGE_BASE["general"]

    return retrieved[:3]  # Return top 3 relevant docs

# ---- Pydantic Models ----
class LlmRequest(BaseModel):
    question: str = Field(..., min_length=1, max_length=2000)
    context: Optional[str] = Field(default="", description="Additional context from SCADA data")
    engine: Optional[str] = Field(default="LOCAL", description="LOCAL or CLOUD")

class Reference(BaseModel):
    title: str
    excerpt: str
    source: str

class LlmResponse(BaseModel):
    answer: str
    references: List[Reference]
    modelUsed: str
    processingTimeMs: int
    documentsRetrieved: int = 0

# ---- Mock Responses ----
MOCK_RESPONSES = {
    "gearbox": """기어박스 온도 이상 원인 분석 및 조치 방안:

**즉각적인 조치 (지금 해야 할 일):**
1. 터빈 출력을 50%로 제한 운전
2. 기어박스 냉각 팬 동작 상태 확인
3. 오일 온도 및 압력 게이지 모니터링 강화

**근본 원인 가능성:**
- **윤활유 열화** (확률 60%): 운전 시간에 따른 점도 저하
- **냉각 시스템 이상** (확률 25%): 팬 고장 또는 냉각수 부족
- **과부하 운전** (확률 15%): 고풍속 구간 연속 운전

**점검 순서:**
1. 오일 샘플 채취 → 분석 기관 제출
2. 냉각 팬 회전수 확인 (정격 대비)
3. 필터 막힘 여부 확인
4. 72시간 이내 전문 기술자 점검 예약

**안전 기준:** 온도 90°C 초과 시 즉시 비상 정지 실행""",

    "vibration": """진동 이상 분석 및 정비 가이드:

**진동 수준 평가 (ISO 10816-21):**
- 현재 측정값에 따른 상태 분류
- 5mm/s 초과: 단기 운전 제한 권고
- 7.5mm/s 초과: 즉시 정지 필요

**주요 원인 분석:**
1. **메인 베어링 마모** - FFT 분석에서 BPFI/BPFO 주파수 확인
2. **로터 불균형** - 1P 주파수 성분 확인
3. **기어 결함** - GMF(기어 메시 주파수) 사이드밴드 확인

**권장 조치:**
1. CMS(상태 모니터링 시스템) 데이터 추출 및 분석
2. 연속 모니터링 강화 (1분 간격)
3. 오일 샘플 채취 및 금속 입자 분석
4. 전문 진동 분석가 현장 점검 요청""",

    "default": """요청하신 풍력 발전기 관련 내용을 분석했습니다.

**분석 결과:**
- 증상 데이터를 기반으로 가능한 원인들을 검토했습니다.
- 관련 기술 문서 및 표준을 참조하여 권장 조치를 도출했습니다.

**권장 조치:**
1. 현장 정기 점검 일정 확인 및 앞당기기 검토
2. 관련 센서 데이터 트렌드 분석 (최근 72시간)
3. 유사 사례 이력 데이터베이스 검색
4. 필요 시 제조사 기술 지원 요청

**안전 우선 원칙:**
의심스러운 상황에서는 항상 안전을 최우선으로 하여 운전을 중단하고 전문가 점검을 받으세요."""
}

def get_mock_response(question: str, docs: list) -> str:
    """Generate context-aware mock response."""
    q_lower = question.lower()
    if any(kw in q_lower for kw in ['기어박스', '온도', 'gearbox', 'temperature']):
        return MOCK_RESPONSES['gearbox']
    elif any(kw in q_lower for kw in ['진동', 'vibration', '베어링', 'bearing']):
        return MOCK_RESPONSES['vibration']
    else:
        return MOCK_RESPONSES['default'] + f'\n\n질문: "{question}"'

# ---- FastAPI App ----
app = FastAPI(
    title="WindWatch LLM API",
    description="RAG-powered wind turbine fault analysis LLM service",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/health")
async def health():
    return {
        "status": "healthy",
        "service": "WindWatch LLM API",
        "ollama_available": OLLAMA_AVAILABLE,
        "langchain_available": LANGCHAIN_AVAILABLE,
        "mode": "Ollama RAG" if OLLAMA_AVAILABLE else "Mock"
    }

@app.post("/api/llm/ask", response_model=LlmResponse)
async def ask_question(request: LlmRequest):
    """
    Answer wind turbine fault analysis questions using RAG.
    Retrieves relevant documents from knowledge base and generates an answer.
    """
    start_time = time.time()
    question = request.question.strip()
    extra_context = request.context or ""

    if not question:
        raise HTTPException(status_code=400, detail="Question cannot be empty")

    logger.info(f"Question received: {question[:100]}...")

    # Retrieve relevant documents
    docs = retrieve_context(question)
    context_text = "\n\n".join([
        f"[문서: {d['title']}]\n{d['content']}" for d in docs
    ])
    if extra_context:
        context_text += f"\n\n[SCADA 실시간 데이터]\n{extra_context}"

    # Generate answer
    answer = ""
    model_used = ""

    if OLLAMA_AVAILABLE and chain is not None:
        try:
            logger.info(f"Invoking Ollama LLM (model: {os.getenv('OLLAMA_MODEL', 'llama3')})")
            result = chain.invoke({"context": context_text, "question": question})
            answer = result.get("text", result) if isinstance(result, dict) else str(result)
            model_used = f"Llama-3 (Ollama / {os.getenv('OLLAMA_MODEL', 'llama3')})"
        except Exception as e:
            logger.error(f"Ollama inference failed: {e}")
            answer = get_mock_response(question, docs)
            model_used = "Llama-3 (Mock Fallback)"
    else:
        # Mock mode: small delay to simulate inference
        time.sleep(random.uniform(0.5, 1.5))
        answer = get_mock_response(question, docs)
        model_used = "WindWatch Knowledge Base (Mock Mode)"

    elapsed_ms = int((time.time() - start_time) * 1000)
    logger.info(f"Response generated in {elapsed_ms}ms")

    # Build references
    references = [
        Reference(
            title=doc["title"],
            excerpt=doc["content"][:150] + "..." if len(doc["content"]) > 150 else doc["content"],
            source=doc["source"]
        )
        for doc in docs
    ]

    return LlmResponse(
        answer=answer,
        references=references,
        modelUsed=model_used,
        processingTimeMs=elapsed_ms,
        documentsRetrieved=len(docs)
    )

@app.get("/api/llm/models")
async def list_models():
    """List available LLM models."""
    models = [{"name": "mock", "type": "MOCK", "available": True}]
    if OLLAMA_AVAILABLE:
        try:
            import ollama as oc
            ollama_models = oc.list()
            for m in ollama_models.get("models", []):
                models.append({
                    "name": m.get("name", "unknown"),
                    "type": "OLLAMA",
                    "available": True,
                    "size": m.get("size", 0)
                })
        except Exception as e:
            logger.error(f"Failed to list Ollama models: {e}")
    return {"models": models}

@app.get("/api/llm/knowledge")
async def list_knowledge_base():
    """List available knowledge base documents."""
    docs = []
    for category, items in KNOWLEDGE_BASE.items():
        for item in items:
            docs.append({
                "category": category,
                "title": item["title"],
                "source": item["source"]
            })
    return {"documents": docs, "total": len(docs)}

if __name__ == '__main__':
    import uvicorn
    logger.info("Starting WindWatch LLM API on port 8002")
    uvicorn.run(app, host='0.0.0.0', port=8002, log_level='info')
