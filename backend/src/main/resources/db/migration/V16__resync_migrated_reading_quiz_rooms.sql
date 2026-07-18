UPDATE rooms room
SET version = room.version + 1,
    sync_version = room.sync_version + 1,
    updated_at = CURRENT_TIMESTAMP
FROM quizzes quiz
WHERE quiz.room_id = room.id
  AND quiz.day_number = 1
  AND quiz.title = '독서 퀴즈'
  AND NOT EXISTS (
      SELECT 1
      FROM quiz_submissions submission
      WHERE submission.quiz_id = quiz.id
  );
