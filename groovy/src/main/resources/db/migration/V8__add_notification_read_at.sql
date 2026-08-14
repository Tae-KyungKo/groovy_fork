-- 알림 정리 배치(안읽음 7일/읽음 3일 후 자동 삭제)를 위한 컬럼과 인덱스.
-- read_at은 markRead() 호출 시점(=알림함 화면에서 사라지는 그 순간)에 채워진다.
ALTER TABLE notifications
  ADD COLUMN read_at datetime(6) DEFAULT NULL;

-- 정리 배치는 특정 유저가 아니라 전체를 is_read/시간 기준으로 스캔하므로,
-- 기존 (recipient_id, is_read) 인덱스로는 못 커버해서 별도로 추가한다.
CREATE INDEX idx_notifications_unread_created ON notifications (is_read, created_at);
CREATE INDEX idx_notifications_read_read_at ON notifications (is_read, read_at);
