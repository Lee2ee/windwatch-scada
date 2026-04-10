-- ============================================================
-- WindWatch SCADA — DDL (H2 / MySQL 호환)
-- ============================================================

CREATE TABLE IF NOT EXISTS turbines (
    turbine_id         VARCHAR(50) PRIMARY KEY,
    turbine_name       VARCHAR(100) NOT NULL,
    location           VARCHAR(200),
    rated_capacity_kw  DOUBLE DEFAULT 2000.0,
    active             BOOLEAN DEFAULT TRUE,
    installed_at       TIMESTAMP
);

CREATE TABLE IF NOT EXISTS users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(255) NOT NULL,
    email      VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS turbine_data (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    turbine_id   VARCHAR(50),
    wind_speed   DOUBLE,
    rotor_rpm    DOUBLE,
    power_output DOUBLE,
    gearbox_temp DOUBLE,
    vibration    DOUBLE,
    pitch_angle  DOUBLE,
    status       VARCHAR(50),
    recorded_at  TIMESTAMP
);

-- 시계열 쿼리 성능 인덱스
CREATE INDEX IF NOT EXISTS idx_turbine_data_turbine_time
    ON turbine_data (turbine_id, recorded_at DESC);

CREATE TABLE IF NOT EXISTS scada_events (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    turbine_id   VARCHAR(50),
    event_type   VARCHAR(50),
    severity     VARCHAR(50),
    message      VARCHAR(500),
    parameter    VARCHAR(100),
    value        DOUBLE,
    threshold    DOUBLE,
    status       VARCHAR(50) DEFAULT 'ACTIVE',
    resolved_by  VARCHAR(255),
    occurred_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at  TIMESTAMP
);

-- 이벤트 검색 성능 인덱스
CREATE INDEX IF NOT EXISTS idx_scada_events_status_time
    ON scada_events (status, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_scada_events_turbine_time
    ON scada_events (turbine_id, occurred_at DESC);

-- ============================================================
-- 배치 리포트 테이블 (Spring Batch 집계 결과 저장)
-- ============================================================
CREATE TABLE IF NOT EXISTS batch_reports (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_date      DATE NOT NULL,
    report_type      VARCHAR(50) NOT NULL,   -- DAILY / WEEKLY / MONTHLY
    turbine_id       VARCHAR(50),            -- NULL = 전체 요약
    total_turbines   INT,
    avg_power_kw     DOUBLE,
    max_power_kw     DOUBLE,
    total_energy_kwh DOUBLE,
    avg_wind_speed   DOUBLE,
    avg_gearbox_temp DOUBLE,
    critical_events  INT DEFAULT 0,
    warning_events   INT DEFAULT 0,
    availability_pct DOUBLE,                 -- 가동률 (%)
    file_path        VARCHAR(500),           -- 생성된 Excel 파일 경로
    generated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status           VARCHAR(50) DEFAULT 'COMPLETED'  -- COMPLETED / FAILED
);

CREATE INDEX IF NOT EXISTS idx_batch_reports_date_type
    ON batch_reports (report_date DESC, report_type);

-- ============================================================
-- 기상 데이터 캐시 테이블
-- ============================================================
CREATE TABLE IF NOT EXISTS weather_data (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    location         VARCHAR(100),
    wind_speed       DOUBLE,
    wind_direction   VARCHAR(20),
    temperature      DOUBLE,
    humidity         INT,
    pressure         DOUBLE,
    weather_condition VARCHAR(100),
    source           VARCHAR(50),   -- KMA / OpenWeather / MOCK
    observed_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_weather_data_location_time
    ON weather_data (location, observed_at DESC);
