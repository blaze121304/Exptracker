# ExpTracker 프로젝트 분석 보고서
> 작성일: 2026-05-21 | 버전: v1.0

---

## 1. 프로젝트 개요

**ExpTracker**는 **카드사 푸시 알림**을 자동으로 파싱해 지출을 기록하는 **Android 네이티브 앱**이다.  
홈 화면 위젯으로 월별 달력 형태의 지출 현황을 제공하며, 결제일 기준 청구 주기 통계를 보여준다.

- 언어: Kotlin
- UI: Jetpack Compose (Material3) + Jetpack Glance (위젯)
- DB: Room (SQLite), v5
- 알림 수신: NotificationListenerService
- minSdk: 26 (Android 8.0) / targetSdk: 34 (Android 14)
- 빌드: Gradle KTS + KSP

---

## 2. 디렉토리 구조

```
Exptracker/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/exptracker/
│       │   ├── MainActivity.kt          # 메인 화면 (통계, 결제일 설정)
│       │   ├── DetailActivity.kt        # 날짜별 지출 상세 Bottom Sheet 팝업
│       │   ├── data/
│       │   │   ├── SimpleExpense.kt     # Room Entity
│       │   │   ├── ExpenseDao.kt        # DAO + VendorTotal 데이터 클래스
│       │   │   └── ExpenseDatabase.kt   # DB 싱글턴 (v2→v5 마이그레이션)
│       │   ├── service/
│       │   │   ├── CardParser.kt        # CardParser 인터페이스 + ParsedExpense
│       │   │   ├── LotteCardParser.kt   # 롯데카드 알림 파서
│       │   │   └── ExpenseNotificationService.kt  # NotificationListenerService
│       │   └── widget/
│       │       ├── ExpenseWidget.kt         # GlanceAppWidget (달력 UI 전체)
│       │       ├── ExpenseWidgetReceiver.kt # GlanceAppWidgetReceiver
│       │       ├── ChangeMonthCallback.kt   # 위젯 월 변경 액션 콜백
│       │       └── SelectDateCallback.kt    # 위젯 날짜 선택 액션 콜백
│       └── res/
│           ├── xml/expense_widget_info.xml  # 위젯 프로바이더 메타데이터
│           ├── values/themes.xml            # Material3 테마 + 팝업 테마
│           └── anim/slide_up|down.xml       # 슬라이드 애니메이션
├── build.gradle.kts
├── settings.gradle.kts
└── .claude/
    ├── CLAUDE.md
    └── SAVE.md                          # (이 파일)
```

---

## 3. 아키텍처

### 레이어 구조

```
┌─────────────────────────────────────────────────┐
│  알림 수신 (ExpenseNotificationService)          │
│  카드사 파서 → Room DB Insert → 위젯 갱신 트리거  │
└─────────────────┬───────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────┐
│  Room DB (expense_database_PRD.db)              │
│  expenses 테이블 (id, amount, vendor,           │
│                   date, time, cardName)          │
└────────────┬────────────────────────────────────┘
             │
      ┌──────┴──────┐
      │             │
┌─────▼─────┐ ┌────▼──────────────────────────────┐
│ MainActivity│ │ ExpenseWidget (Glance)            │
│ 통계/설정  │ │ 달력 UI + 날짜별 지출 목록         │
└─────────── ┘ └───────────────────────────────────┘
      │
┌─────▼─────────┐
│ DetailActivity │
│ 날짜 탭 → 팝업 │
└───────────────┘
```

### 알림 처리 플로우

```
카드사 앱 알림 발생
  └─ onNotificationPosted()
        └─ parsers.firstOrNull { canParse(title, pkg) }
              └─ parser.parse(title, body)
                    └─ dao.insert(SimpleExpense(...))
                          └─ ExpenseWidgetReceiver.updateWidget()
```

---

## 4. 주요 모듈 상세

### 4-1. `data/` — 데이터 레이어

**SimpleExpense** (Room Entity, `tableName = "expenses"`)

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | Int | PK, autoGenerate |
| `amount` | Int | 금액 (원 단위) |
| `vendor` | String | 상호명 |
| `date` | String | "yyyy-MM-dd" |
| `time` | String | "HH:mm" (기본값 "") |
| `cardName` | String | 카드사명 (기본값 "") |

**ExpenseDao** — 주요 쿼리

| 메서드 | 설명 |
|--------|------|
| `insert(expense)` | 지출 1건 저장 |
| `getTodayTotal(date)` | 오늘 지출 합계 (위젯) |
| `getRecentByDate(date)` | 날짜 최근 3건 (위젯) |
| `getByMonth(yearMonth)` | 월별 전체 지출 (달력) |
| `getByDate(date)` | 날짜별 지출 목록 (팝업) |
| `getTotalInRange(start, end)` | 결제 주기 합계 |
| `getVendorRankings(start, end)` | 결제처별 합계 랭킹 |

**ExpenseDatabase** — DB 싱글턴 (v5)

- DB 파일 위치: `externalFilesDir("databases")/` → 내부 저장소 폴백
- DEBUG 빌드: `expense_database_TEST.db`
- RELEASE 빌드: `expense_database_PRD.db`
- 마이그레이션 이력: v2→3 `cardName` 컬럼 추가 / v3→4 기존 row 롯데카드로 초기화 / v4→5 버전 번호만

### 4-2. `service/` — 알림 파싱 레이어

**CardParser** (인터페이스)

```kotlin
interface CardParser {
    fun canParse(title: String, packageName: String): Boolean
    fun parse(title: String, body: String): ParsedExpense?
}
```

**LotteCardParser** — 롯데카드 전용 파서

롯데카드 알림 구조:
```
EXTRA_TITLE   : "세븐일레븐 역삼태광점"   ← vendor
EXTRA_BIG_TEXT:
  line 0: "2,500원 승인"                 ← amount (amountRegex)
  line 1: "텔로 T라이트(2*7*)"           ← 카드명 (무시)
  line 2: "일시불, 04/22 12:34"          ← date + time (dateTimeRegex)
  line 3: "누적금액 3,729,201원"         ← 무시
```

- `canParse`: `packageName == "com.lcacApp" || packageName.contains("lotte") || title.contains("롯데카드")`
  - 롯데카드 앱 실제 패키지명: `com.lcacApp` (lotte 문자열 미포함이라 명시적으로 추가)
- 새 카드 지원 추가 시: `ExpenseNotificationService.parsers` 리스트에 `CardParser` 구현체 추가

**ExpenseNotificationService**

- `SupervisorJob + Dispatchers.IO` 코루틴 스코프로 DB 저장
- `onListenerDisconnected`에서 job cancel → 누수 방지
- DB insert 후 `ExpenseWidgetReceiver.updateWidget()` 호출로 위젯 즉시 갱신

### 4-3. `widget/` — 홈 화면 위젯

**ExpenseWidget** (GlanceAppWidget)

화면 구성 (상/하 50% 분할):
```
┌──────────────────────────────────┐
│ 헤더: 월 표시 | 결제일 | 결제주기 합계 │
├──────────────────────────────────┤  ← 상단 50%
│  일 월 화 수 목 금 토              │
│  [달력 행 0~2]                    │
│  [달력 행 3~5]                    │
│  < 이전달          다음달 >        │
├──────────────────────────────────┤
│  MM월 DD일 (요일)  합계 N원        │  ← 하단 50%
│  [지출 카드 LazyColumn]            │
└──────────────────────────────────┘
```

달력 셀 색상 티어 (지출 금액별):

| 금액 | 배경색 | 의미 |
|------|--------|------|
| 0원 | 초록 (#E8F5E9) | 절약 (Good!/짠돌이! 등 랜덤 메시지) |
| ~1만원 | 파랑 (#E3F2FD) | 소액 |
| ~3만원 | 노랑 (#FFF8E1) | 보통 |
| ~5만원 | 주황 (#FFF3E0) | 주의 |
| ~10만원 | 빨강 (#FFEBEE) | 과소비 |
| 10만원 초과 | 분홍 (#FCE4EC) | 위험 |

- 선택된 날짜: Purple (#1A2E5E) 배경
- 오늘: PurpleLight (#E8EDF7) 배경
- 미래 날짜: 색상 없음 (tiering 미적용)
- 이전달 이동만 가능 (미래 이동 차단)
- 최근 3개월 데이터 미리 조회 후 캐싱

**SelectDateCallback** — 날짜 탭 시 `SELECTED_DATE_KEY` 업데이트 + 위젯 리렌더

**ChangeMonthCallback** — `DELTA_PARAM(+1/-1)` 으로 표시 월 이동, 미래 방향 차단

### 4-4. `MainActivity` — 메인 앱 화면

기능:
1. **알림 접근 권한 확인** — `isNotificationListenerEnabled()` → 미허용 시 설정 화면 유도
2. **결제일 설정** — 1~28일, 저장 시 위젯도 갱신
3. **통계 확인** — 결제일 기준 현재 청구 주기 범위 계산 → 벤더별 랭킹 + 총액 표시
4. **지출 평가** — 총액 구간별 멘트 및 색상 피드백

지출 평가 구간:
| 총액 | 메시지 | 색상 |
|------|--------|------|
| 10만원 미만 | "부자되시겠어요! 이 기세 유지합시다!" | 초록 |
| ~25만원 | "적절합니다! 이렇게 유지합시다." | 파랑 |
| ~50만원 | "슬슬 과소비 냄새가 나는데요?" | 주황 |
| 50만원 초과 | "지갑이 홀쭉해요! 개선이 필요합니다!" | 빨강 |

**이스터에그**:
- 코나미 커맨드 (H H F F L R L R): "이건 코나미 커맨드인데?" 다이얼로그
- 변형 시퀀스 (H H F F FL FR FL FR): TEST DB ↔ PRD DB 토글
- 타임아웃 10초, 시퀀스 8개 유지

### 4-5. `DetailActivity` — 날짜별 지출 상세 팝업

- `WindowManager.LayoutParams.WRAP_CONTENT + Gravity.BOTTOM` — 하단 Bottom Sheet 스타일 팝업
- `setFinishOnTouchOutside(true)` — 외부 터치 시 닫힘
- 날짜 + 합계 헤더, 지출 항목 LazyColumn (최대 400dp)
- 위젯 날짜 탭 이벤트에서 `Intent(EXTRA_DATE)` 전달 방식으로 호출

---

## 5. 설정 & 영속성

| 항목 | 저장소 | 키 |
|------|--------|-----|
| 결제일 | SharedPreferences (`exptracker_prefs` / `exptracker_prefs_test`) | `billing_day` |
| DB 선택 (TEST/PRD) | SharedPreferences (`exptracker_db_prefs`) | `use_test_db` |
| 위젯 표시 월 | Glance DataStore (PreferencesGlanceStateDefinition) | `displayed_month` |
| 위젯 선택 날짜 | Glance DataStore | `selected_date` |

---

## 6. 의존성

```
androidx.compose:compose-bom:2024.02.00   # Compose BOM
androidx.core:core-ktx:1.12.0
androidx.activity:activity-compose:1.8.2
androidx.compose.material3:material3
androidx.glance:glance-appwidget:1.1.0    # 홈 위젯
androidx.glance:glance-material3:1.1.0
androidx.room:room-runtime:2.6.1          # 로컬 DB
androidx.room:room-ktx:2.6.1
ksp:room-compiler:2.6.1
com.google.android.material:material:1.11.0
kotlinx-coroutines-android:1.7.3
```

---

## 7. AndroidManifest 컴포넌트 요약

| 컴포넌트 | 클래스 | 역할 |
|---------|--------|------|
| Activity | `MainActivity` | 런처 진입점 (`singleTop`) |
| Activity | `DetailActivity` | 날짜별 상세 팝업 (exported=false) |
| Service | `ExpenseNotificationService` | 카드 알림 수신 (`BIND_NOTIFICATION_LISTENER_SERVICE`) |
| Receiver | `ExpenseWidgetReceiver` | 위젯 업데이트 수신 (`APPWIDGET_UPDATE`) |

---

## 8. 확장 포인트

### 새 카드사 추가

1. `CardParser` 인터페이스 구현체 작성 (예: `ShinhanCardParser`)
2. `ExpenseNotificationService.parsers` 리스트에 인스턴스 추가
3. 별도 코드 변경 없음

### DB 스키마 변경

1. `SimpleExpense`에 필드 추가
2. `ExpenseDatabase`의 `version` 증가
3. `Migration(N, N+1)` 객체 작성 후 `addMigrations()`에 등록

---

## 요약

ExpTracker는 **단일 카드사(롯데카드) 알림 자동 파싱 → Room 저장 → Glance 위젯 표시**의 심플한 파이프라인을 가진 개인용 지출 추적 앱이다.

- `CardParser` 인터페이스로 멀티 카드사 확장 준비
- `NotificationListenerService` 기반으로 별도 입력 없이 완전 자동 기록
- Glance 위젯이 앱의 주 사용 인터페이스 (달력 + 당일 지출 상세)
- TEST/PRD DB 분리로 개발 중 실데이터 오염 방지

## 9. 디버깅

### ADB 로그캣

PowerShell:
```powershell
& "C:\Users\rusty-lab\AppData\Local\Android\Sdk\platform-tools\adb.exe" logcat -s ExpTracker
```
- 디바이스 USB 디버깅 켜야 함. 안 잡히면 디버그 모드 재수행.

### 롯데카드 알림 포맷 (실측, 2026-05-21)

**오프라인 결제** — `EXTRA_TITLE` = 가맹점명
```
pkg   : com.lcacApp
title : 세븐일레븐 역삼태광점
big   : 1,000원 승인
        텔로 T라이트(2*7*)
        일시불, 05/21 16:46
        누적금액 4,425,629원
```

**온라인 결제** — `EXTRA_TITLE` = PG사명 (가맹점명 아님)
```
pkg   : com.lcacApp
title : 토스페이먼츠 주식회사
big   : 5,620원 승인
        텔로 T라이트(2*7*)
        일시불, 05/21 16:03
        누적금액 4,424,629원
```
> 온라인 결제 시 vendor가 실제 쇼핑몰명 대신 PG사명으로 저장됨 — 알림 구조상 어쩔 수 없음.