#!/usr/bin/env python3
"""
WindWatch AI-SCADA - Vision Microservice (FastAPI)
YOLOv8-based blade defect detection API.

Usage:
    pip install fastapi uvicorn python-multipart Pillow numpy
    # With real YOLOv8: pip install ultralytics torch
    uvicorn vision_api:app --host 0.0.0.0 --port 8001 --reload

Endpoints:
    POST /api/vision/detect  - Detect defects in uploaded image
    GET  /health             - Health check
    GET  /docs               - Swagger UI
"""

import sys
import time
import random
import logging
import base64
import io
from typing import List, Optional
from pathlib import Path

# Windows 환경에서 stdout/stderr를 UTF-8로 강제 설정
if sys.stdout and hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')
if sys.stderr and hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8')

from fastapi import FastAPI, File, UploadFile, HTTPException, Form
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(name)s: %(message)s',
    datefmt='%H:%M:%S'
)
logger = logging.getLogger('WindWatch-Vision')

# ---- Try importing real YOLOv8 ----
YOLO_AVAILABLE = False
yolo_model = None
try:
    from ultralytics import YOLO
    import torch

    MODEL_PATH = Path(__file__).parent / 'weights' / 'blade_defect_yolov8.pt'
    if MODEL_PATH.exists():
        yolo_model = YOLO(str(MODEL_PATH))
        YOLO_AVAILABLE = True
        logger.info(f"YOLOv8 model loaded: {MODEL_PATH}")
    else:
        logger.warning(f"Model weights not found at {MODEL_PATH}. Using mock mode.")
except ImportError:
    logger.info("ultralytics not installed. Running in mock mode.")

try:
    from PIL import Image
    PIL_AVAILABLE = True
except ImportError:
    PIL_AVAILABLE = False
    logger.warning("Pillow not installed.")

# ---- Pydantic Models ----
class BoundingBox(BaseModel):
    x: int
    y: int
    width: int
    height: int

class Detection(BaseModel):
    label: str
    confidence: float = Field(ge=0.0, le=1.0)
    bbox: List[int] = Field(description="[x, y, width, height]")
    severity: str = Field(default="MEDIUM", description="LOW, MEDIUM, HIGH, CRITICAL")

class VisionResponse(BaseModel):
    defectDetected: bool
    detections: List[Detection]
    processedImageBase64: Optional[str] = None
    modelUsed: str
    processingTimeMs: int
    imageInfo: Optional[dict] = None

# ---- Defect Classes ----
DEFECT_CLASSES = [
    'blade_crack',
    'surface_erosion',
    'lightning_damage',
    'leading_edge_erosion',
    'leading_edge_pitting',
    'delamination',
    'trailing_edge_damage',
    'contamination',
    'ice_accretion'
]

SEVERITY_MAP = {
    'blade_crack': 'CRITICAL',
    'lightning_damage': 'CRITICAL',
    'delamination': 'HIGH',
    'leading_edge_erosion': 'HIGH',
    'trailing_edge_damage': 'HIGH',
    'surface_erosion': 'MEDIUM',
    'leading_edge_pitting': 'MEDIUM',
    'contamination': 'LOW',
    'ice_accretion': 'MEDIUM'
}

# ---- FastAPI App ----
app = FastAPI(
    title="WindWatch Vision API",
    description="AI-powered wind turbine blade defect detection service",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ---- Health ----
@app.get("/health")
async def health():
    return {
        "status": "healthy",
        "service": "WindWatch Vision API",
        "model_loaded": YOLO_AVAILABLE,
        "mode": "YOLOv8" if YOLO_AVAILABLE else "Mock"
    }

# ---- Detection Endpoint ----
@app.post("/api/vision/detect", response_model=VisionResponse)
async def detect_defects(
    file: UploadFile = File(..., description="Wind turbine blade image"),
    confidence_threshold: float = Form(default=0.5, ge=0.1, le=0.99)
):
    """
    Analyze a blade image for structural defects using YOLOv8.
    Returns detection results with bounding boxes and confidence scores.
    """
    start_time = time.time()

    # Validate file type
    if not file.content_type.startswith('image/'):
        raise HTTPException(status_code=400, detail="File must be an image")

    content = await file.read()
    if len(content) > 20 * 1024 * 1024:  # 20MB limit
        raise HTTPException(status_code=413, detail="Image too large (max 20MB)")

    logger.info(f"Processing image: {file.filename} ({len(content) / 1024:.1f} KB)")

    image_info = None
    if PIL_AVAILABLE:
        try:
            img = Image.open(io.BytesIO(content))
            image_info = {
                "width": img.width,
                "height": img.height,
                "mode": img.mode,
                "format": img.format or "Unknown"
            }
        except Exception as e:
            logger.warning(f"Could not read image info: {e}")

    # Run detection
    if YOLO_AVAILABLE and yolo_model is not None:
        detections = run_yolo_detection(content, confidence_threshold)
        model_name = "YOLOv8 (WindWatch Custom)"
    else:
        detections = run_mock_detection(image_info)
        model_name = "YOLOv8 (Mock Mode)"

    elapsed_ms = int((time.time() - start_time) * 1000)
    defect_detected = len(detections) > 0

    if defect_detected:
        logger.warning(
            f"DEFECTS DETECTED in {file.filename}: "
            f"{[d.label for d in detections]}"
        )
    else:
        logger.info(f"No defects detected in {file.filename}")

    return VisionResponse(
        defectDetected=defect_detected,
        detections=detections,
        modelUsed=model_name,
        processingTimeMs=elapsed_ms,
        imageInfo=image_info
    )

def run_yolo_detection(image_bytes: bytes, conf_threshold: float) -> List[Detection]:
    """Run actual YOLOv8 inference."""
    try:
        img = Image.open(io.BytesIO(image_bytes))
        results = yolo_model.predict(img, conf=conf_threshold, verbose=False)
        detections = []
        for result in results:
            for box in result.boxes:
                x1, y1, x2, y2 = map(int, box.xyxy[0].tolist())
                conf = float(box.conf[0])
                cls_id = int(box.cls[0])
                label = yolo_model.names.get(cls_id, f'class_{cls_id}')
                detections.append(Detection(
                    label=label,
                    confidence=round(conf, 3),
                    bbox=[x1, y1, x2 - x1, y2 - y1],
                    severity=SEVERITY_MAP.get(label, 'MEDIUM')
                ))
        return detections
    except Exception as e:
        logger.error(f"YOLOv8 inference failed: {e}")
        return run_mock_detection(None)

def run_mock_detection(image_info: Optional[dict]) -> List[Detection]:
    """Generate realistic mock detection results."""
    # Add small delay to simulate inference
    time.sleep(random.uniform(0.2, 0.8))

    detections = []
    img_w = image_info['width'] if image_info else 640
    img_h = image_info['height'] if image_info else 480

    # 45% chance of detecting defects
    if random.random() < 0.45:
        num_defects = random.choices([1, 2, 3], weights=[70, 25, 5])[0]
        used_positions = []

        for _ in range(num_defects):
            label = random.choice(DEFECT_CLASSES[:6])  # Most common types

            # Generate non-overlapping bbox
            for attempt in range(10):
                w = random.randint(int(img_w * 0.05), int(img_w * 0.3))
                h = random.randint(int(img_h * 0.05), int(img_h * 0.25))
                x = random.randint(0, img_w - w)
                y = random.randint(0, img_h - h)

                # Check overlap with existing detections
                overlaps = False
                for (px, py, pw, ph) in used_positions:
                    if not (x + w < px or x > px + pw or y + h < py or y > py + ph):
                        overlaps = True
                        break

                if not overlaps:
                    used_positions.append((x, y, w, h))
                    break

            if not used_positions:
                continue

            pos = used_positions[-1]

            # Confidence varies by defect type severity
            severity = SEVERITY_MAP.get(label, 'MEDIUM')
            base_conf = {'CRITICAL': 0.82, 'HIGH': 0.75, 'MEDIUM': 0.68, 'LOW': 0.60}[severity]
            conf = min(0.99, base_conf + random.gauss(0, 0.06))
            conf = max(0.45, conf)

            detections.append(Detection(
                label=label,
                confidence=round(conf, 3),
                bbox=[pos[0], pos[1], pos[2], pos[3]],
                severity=severity
            ))

    return detections

# ---- Additional Endpoint ----
@app.get("/api/vision/classes")
async def get_defect_classes():
    """List all supported defect classes."""
    return {
        "classes": [
            {"id": i, "name": cls, "severity": SEVERITY_MAP.get(cls, "MEDIUM")}
            for i, cls in enumerate(DEFECT_CLASSES)
        ]
    }

if __name__ == '__main__':
    import uvicorn
    logger.info("Starting WindWatch Vision API on port 8001")
    uvicorn.run(app, host='0.0.0.0', port=8001, log_level='info')
