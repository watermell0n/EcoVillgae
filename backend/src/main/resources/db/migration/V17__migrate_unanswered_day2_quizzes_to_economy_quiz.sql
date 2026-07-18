ALTER TABLE room_rules
    ALTER COLUMN day2_quiz_count SET DEFAULT 10;

CREATE TEMPORARY TABLE day2_economy_quiz_content (
    order_no INTEGER PRIMARY KEY,
    prompt TEXT NOT NULL,
    options JSONB NOT NULL,
    correct_answer VARCHAR(50) NOT NULL,
    explanation TEXT NOT NULL
);

INSERT INTO day2_economy_quiz_content (
    order_no, prompt, options, correct_answer, explanation
)
VALUES
    (1,
     $prompt$내가 일해서 받는 돈을 무엇이라고 할까요?$prompt$,
     $options$[{"id":"A","text":"세금"},{"id":"B","text":"월급"},{"id":"C","text":"이자"},{"id":"D","text":"투자"}]$options$::jsonb,
     'B',
     $explanation$일을 한 대가로 받는 돈을 월급이라고 합니다.$explanation$),
    (2,
     $prompt$국가나 지역을 위해 내는 돈을 무엇이라고 할까요?$prompt$,
     $options$[{"id":"A","text":"월급"},{"id":"B","text":"이자"},{"id":"C","text":"세금"},{"id":"D","text":"저축"}]$options$::jsonb,
     'C',
     $explanation$세금은 국가나 지역 사회의 공공서비스와 공동체 운영을 위해 내는 돈입니다.$explanation$),
    (3,
     $prompt$세금을 내고 실제로 받게 되는 금액을 무엇이라고 할까요?$prompt$,
     $options$[{"id":"A","text":"총소득"},{"id":"B","text":"실수령액"},{"id":"C","text":"공동기금"},{"id":"D","text":"이자"}]$options$::jsonb,
     'B',
     $explanation$총소득에서 세금처럼 빠져나가는 금액을 뺀 뒤 실제로 받는 돈이 실수령액입니다.$explanation$),
    (4,
     $prompt$돈을 은행에 맡겨 나중에 사용하도록 모아 두는 것을 무엇이라고 할까요?$prompt$,
     $options$[{"id":"A","text":"소비"},{"id":"B","text":"세금"},{"id":"C","text":"투자"},{"id":"D","text":"저축"}]$options$::jsonb,
     'D',
     $explanation$저축은 지금 쓰지 않은 돈을 은행 등에 맡겨 미래에 사용하도록 모으는 것입니다.$explanation$),
    (5,
     $prompt$은행에 돈을 맡기면 돌려받는 추가 금액을 무엇이라고 할까요?$prompt$,
     $options$[{"id":"A","text":"세금"},{"id":"B","text":"월급"},{"id":"C","text":"인플레이션"},{"id":"D","text":"이자"}]$options$::jsonb,
     'D',
     $explanation$은행에 돈을 맡기면 약속된 조건에 따라 원금에 더해 이자를 받을 수 있습니다.$explanation$),
    (6,
     $prompt$미래에 더 많은 돈을 얻기 위해 돈을 사용하지만, 손해를 볼 수도 있는 활동은 무엇이라고 할까요?$prompt$,
     $options$[{"id":"A","text":"투자"},{"id":"B","text":"저축"},{"id":"C","text":"세금"},{"id":"D","text":"월급"}]$options$::jsonb,
     'A',
     $explanation$투자는 더 큰 수익을 기대할 수 있지만 원금 손실의 위험도 있는 활동입니다.$explanation$),
    (7,
     $prompt$필요한 물건이나 서비스를 사기 위해 돈을 사용하는 것을 무엇이라고 할까요?$prompt$,
     $options$[{"id":"A","text":"저축"},{"id":"B","text":"투자"},{"id":"C","text":"소비"},{"id":"D","text":"이자"}]$options$::jsonb,
     'C',
     $explanation$필요한 물건이나 서비스를 사기 위해 돈을 사용하는 활동을 소비라고 합니다.$explanation$),
    (8,
     $prompt$분산투자를 하는 가장 큰 이유는 무엇이라고 할까요?$prompt$,
     $options$[{"id":"A","text":"더 많이 벌기 위해"},{"id":"B","text":"위험을 나누기 위해"},{"id":"C","text":"세금을 줄이기 위해"},{"id":"D","text":"빨리 쓰기 위해"}]$options$::jsonb,
     'B',
     $explanation$분산투자는 돈을 여러 곳에 나누어 투자해 한 곳의 손실이 전체에 미치는 위험을 줄이는 방법입니다.$explanation$),
    (9,
     $prompt$돈의 가치가 떨어져서 물건 가격이 오르는 현상을 무엇이라고 할까요?$prompt$,
     $options$[{"id":"A","text":"디플레이션"},{"id":"B","text":"인플레이션"},{"id":"C","text":"이자율"},{"id":"D","text":"세금"}]$options$::jsonb,
     'B',
     $explanation$인플레이션은 전반적인 물가가 올라 같은 돈으로 살 수 있는 양이 줄어드는 현상입니다.$explanation$),
    (10,
     $prompt$우리 반에서 모은 세금을 가장 알맞게 사용하는 방법은 무엇일까요?$prompt$,
     $options$[{"id":"A","text":"한 학생이 원하는 장난감을 산다."},{"id":"B","text":"선생님이 개인적으로 사용한다."},{"id":"C","text":"반 전체가 사용할 공용 물품을 산다."},{"id":"D","text":"세금을 가장 많이 낸 학생에게 모두 준다."}]$options$::jsonb,
     'C',
     $explanation$세금은 특정 개인이 아니라 공동체 구성원 모두에게 필요한 공용 물품이나 서비스에 사용하는 것이 알맞습니다.$explanation$);

WITH eligible_quizzes AS (
    SELECT quiz.id
    FROM quizzes quiz
    WHERE quiz.day_number = 2
      AND NOT EXISTS (
          SELECT 1
          FROM quiz_submissions submission
          WHERE submission.quiz_id = quiz.id
      )
)
UPDATE quiz_questions question
SET prompt = content.prompt,
    options = content.options,
    correct_answer = content.correct_answer,
    explanation = content.explanation
FROM eligible_quizzes quiz, day2_economy_quiz_content content
WHERE question.quiz_id = quiz.id
  AND question.order_no = content.order_no;

WITH eligible_quizzes AS (
    SELECT quiz.id
    FROM quizzes quiz
    WHERE quiz.day_number = 2
      AND NOT EXISTS (
          SELECT 1
          FROM quiz_submissions submission
          WHERE submission.quiz_id = quiz.id
      )
)
INSERT INTO quiz_questions (
    id, quiz_id, order_no, question_type, prompt,
    options, correct_answer, explanation
)
SELECT md5(quiz.id::text || ':day2-economy-quiz:' || content.order_no::text)::uuid,
       quiz.id,
       content.order_no,
       'MULTIPLE_CHOICE',
       content.prompt,
       content.options,
       content.correct_answer,
       content.explanation
FROM eligible_quizzes quiz
CROSS JOIN day2_economy_quiz_content content
ON CONFLICT (quiz_id, order_no) DO NOTHING;

UPDATE quizzes quiz
SET title = '경제 퀴즈',
    question_count = 10,
    updated_at = CURRENT_TIMESTAMP
WHERE quiz.day_number = 2
  AND NOT EXISTS (
      SELECT 1
      FROM quiz_submissions submission
      WHERE submission.quiz_id = quiz.id
  );

UPDATE room_rules rules
SET day2_quiz_count = 10,
    updated_at = CURRENT_TIMESTAMP
FROM quizzes quiz
WHERE quiz.room_id = rules.room_id
  AND quiz.day_number = 2
  AND NOT EXISTS (
      SELECT 1
      FROM quiz_submissions submission
      WHERE submission.quiz_id = quiz.id
  );

UPDATE rooms room
SET version = room.version + 1,
    sync_version = room.sync_version + 1,
    updated_at = CURRENT_TIMESTAMP
FROM quizzes quiz
WHERE quiz.room_id = room.id
  AND quiz.day_number = 2
  AND quiz.title = '경제 퀴즈'
  AND NOT EXISTS (
      SELECT 1
      FROM quiz_submissions submission
      WHERE submission.quiz_id = quiz.id
  );

DROP TABLE day2_economy_quiz_content;
