-- 26a4523(최초 캘린더 기능)에서 만든 description/start_time/end_time은
-- 4343af3에서 날짜 기반 설계로 바뀌며 엔티티에서 빠졌지만 컬럼은 드롭된 적이 없다.
-- start_time/end_time이 NOT NULL로 남아있어, 현재 INSERT 문이 이 컬럼들을 채우지 않으니
-- 매번 저장이 거부되어 캘린더 일정 추가가 500으로 실패하고 있었다(코드 어디서도 더 이상 참조하지 않음).
ALTER TABLE calendars
  DROP COLUMN description,
  DROP COLUMN end_time,
  DROP COLUMN start_time;
