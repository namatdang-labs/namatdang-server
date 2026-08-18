-- 새 애플리케이션 배포 전에 기존 알림 타입을 현재 이벤트 이름으로 변경한다.
UPDATE notifications
SET notification_type = 'DEAL_CREATED'
WHERE notification_type = 'DEAL_PUBLISHED';
