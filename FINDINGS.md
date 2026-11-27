# 테스트 보강 결과 (브랜치 `test/coverage-and-ci`)

> pstack `poteto-mode` 스킬은 모델이 호출할 수 없게 막혀 있어 일반 방식으로 진행했다.

## 테스트 수 (`mvn clean verify`, 로컬 JDK 21 / CI JDK 17)

| 모듈 | 기준선 | 최종 | skipped(버그 재현) | failures | errors |
|---|---|---|---|---|---|
| sync-monitor | 4 | 91 | 6 | 0 | 0 |
| shift-helper | 5 | 56 | 3 | 0 | 0 |
| **합계** | **9** | **147** | **9** | **0** | **0** |

기준선은 전부 통과 상태였다.

## 추가한 테스트

**sync-monitor**
- `BookingImportServiceTest`: 신규 import 필드 매핑, 채널별 동일 외부 ID 분리, 상태 변경 → updated + `UPDATED` 로그, PENDING→IMPORTED 동기화 감지, 결과 메시지, 빈 피드
- `MockBookingReservationConnectorTest`: 데모 시나리오 불변식(8건, 키 유일, 날짜·지연 유효, PENDING 1건)
- `ReservationServiceTest`, `SyncLogServiceTest`: 조회/404, acknowledge 시 채널 호출·상태·로그
- `RoomAssignmentServiceTest`: 연박(back-to-back) 허용, 타입/비활성/정원 필터, 취소 배정 비차단, 400/404/409 경로, 배정 로그, 배정 객실 맵
- `AutoAssignmentServiceTest`: 빈 방 NEW, 재배치(MOVED) 후 객실별 비중첩, preview 무저장, commit 저장·로그, 체크인 손님 LOCKED, 불가 시 409·무변경, 퇴실 배정 무시, 타 객실 타입 격리, ACKNOWLEDGED 허용, PENDING/중복 배정/404 거부
- `DashboardServiceTest`: 0 상태, 오버부킹 위험(빈 방 없음/비활성·소형 방/타입 없음/취소 제외), 평균 지연(누락·음수 제외, 소수점), PENDING만 미반영 집계
- `ReservationControllerTest`, `DashboardAndImportControllerTest`(`@WebMvcTest`): JSON 형태, 상태코드 매핑, `ApiExceptionHandler` 에러 바디, CORS(localhost 허용/외부 거부)
- `DataInitializerTest`: 10개 시드, 재실행 멱등
- `SyncMonitorFlowIntegrationTest`(`@SpringBootTest`): import 멱등 → 후보 조회 → 배정 → 대시보드 반영, PENDING 배정 거부, 404

**shift-helper**
- `DomainModelTest`: Reservation/Room/Assignment 검증·동작, `AssignmentPlan.infeasible`
- `AssignmentPlannerTest`(+8): 연박 재사용, LOCKED 보고, 오늘 체크인 = LOCKED, 정원·비중첩 불변식, 불가 메시지
- `AssignmentApplicationServiceTest`, `MasterDataApplicationServiceTest`: in-memory 포트 + 고정 Clock으로 preview/commit/재배치/404/409/사용불가 객실 제외
- `PersistenceAdapterTest`(`@DataJpaTest`): 어댑터 왕복, in-service 필터, `findActiveOn` 경계(퇴실일 = 기준일 제외), move 시 같은 행 갱신, 객실 번호 유일 제약
- `ShiftHelperApiIntegrationTest`(`@SpringBootTest`): REST 전 구간(등록/토글/예약/preview/commit/재배치/400/404/409). 이 모듈엔 컨텍스트 로드 테스트도 없었다.

## CI
`.github/workflows/ci.yml`: push(main)·PR에서 JDK 17(temurin) + `mvn -B clean verify`, surefire 리포트 업로드. 프론트엔드는 범위 밖.

## 발견한 버그 (미수정 — 판단 필요, 재현 테스트는 `@Disabled`)

각 재현 테스트는 `-DargLine="-Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition"`로 활성화하면 실패하는 것을 확인했다.

| # | 모듈 | 내용 | 재현 테스트 |
|---|---|---|---|
| 1 | sync-monitor | 재-import가 로컬 `ACKNOWLEDGED`를 채널 값 `IMPORTED_TO_PMS`로 되돌리고 `updated`로 집계한다. 채널 상태와 PMS 로컬 상태의 소유권 규칙이 없다. | `BookingImportServiceTest#reimportDoesNotRevertLocalAcknowledgement` |
| 2 | sync-monitor | 수동 배정은 `IMPORTED_TO_PMS`만 허용해 `ACKNOWLEDGED` 예약은 409. 자동 배정은 둘 다 허용 → 규칙 불일치. acknowledge 후엔 수동 배정 불가. | `RoomAssignmentServiceTest#acknowledgedReservationCanBeAssignedManually` |
| 3 | sync-monitor | 자동 배정 재배치 시 객실 후보를 **대상 예약 인원**으로만 거르고, 옮겨지는 기존 예약의 인원은 확인하지 않는다 → 4인 예약이 2인실로 MOVED. (shift-helper planner는 항목별로 검사함) | `AutoAssignmentServiceTest#reshuffleNeverPlacesAPartyInARoomBelowItsSize` |
| 4 | sync-monitor | 대시보드 미배정·오버부킹 위험 집계가 `CONFIRMED`만 본다 → `MODIFIED` 예약이 빠진다. | `DashboardServiceTest#modifiedReservationStillCountsAsUnassignedAndRisk` |
| 5 | sync-monitor | 채널에서 `CANCELLED`가 들어와도 기존 `RoomAssignment`가 `ASSIGNED`로 남아 객실을 계속 점유한다(후보·오버부킹·자동배정 모두 영향). | `RoomAssignmentServiceTest#cancelledReservationNoLongerBlocksItsRoom` |
| 6 | shift-helper | 사용불가로 바뀐 객실에 있던 사전도착 배정이 MOVED로 나오지만 `previousRoomNumber`가 null(planner가 in-service 객실만 받음). | `AssignmentPlannerTest#moved_out_of_an_out_of_service_room_reports_previous_room_number` |
| 7 | 양쪽 | 재배치 planner가 사전도착 배정을 현재 방과 무관하게 처음부터 다시 놓아, 옮길 필요가 없어도 MOVED가 생긴다(동률 시 목록 첫 방 선택). 현장에서 불필요한 객실 이동 지시. | `AssignmentPlannerTest#pre_arrival_guest_keeps_its_room_when_no_move_is_needed`, `AutoAssignmentServiceTest#preArrivalAssignmentKeepsItsRoomWhenNoMoveIsNeeded` |
| 8 | shift-helper | 중복 객실 번호 등록 시 `DataIntegrityViolationException`이 처리되지 않아 500. | `ShiftHelperApiIntegrationTest#duplicateRoomNumberIsClientError` |

명백하고 작은 버그로 판단해 바로 고친 것은 없다(프로덕션 코드 변경 0).

## 사람이 확인할 항목
- #1·#2: `pmsSyncStatus`의 출처(채널 vs PMS 로컬)와 `ACKNOWLEDGED` 이후 허용 동작 정의.
- #3·#7: planner 수정 방향(항목별 정원 체크, 현재 방 유지 우선 tie-break). sync-monitor와 shift-helper에 같은 알고리즘이 중복돼 있다.
- #5: 취소 시 배정 자동 해제 여부(import 시점 vs 별도 작업).
- `AutoAssignmentService`는 `LocalDate.now()`를 직접 써서 테스트가 "오늘 기준 상대 날짜"에 의존한다. shift-helper처럼 `Clock` 주입을 권장.
- shift-helper `application.properties`의 `jdbc:h2:mem:shift;DB_CLOSE_DELAY=-1` + `ddl-auto=update`는 같은 JVM의 컨텍스트 간 데이터가 남는다. 통합 테스트는 `@Transactional` 롤백으로 격리했다.
- 로컬은 JDK 21에서 검증. JDK 17은 CI에서 확인.

## 못 한 것
- 프론트엔드(`frontend-shift`, `sync-monitor/frontend`) 테스트·CI — 범위 밖.
- 커버리지 리포트(JaCoCo) — 새 의존성 추가를 피했다.
- 위 버그 수정.
