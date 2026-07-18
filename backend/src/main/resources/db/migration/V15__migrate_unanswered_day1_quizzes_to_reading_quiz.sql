WITH eligible_quizzes AS (
    SELECT quiz.id
    FROM quizzes quiz
    WHERE quiz.day_number = 1
      AND NOT EXISTS (
          SELECT 1
          FROM quiz_submissions submission
          WHERE submission.quiz_id = quiz.id
      )
),
reading_quiz_content (order_no, prompt, options, correct_answer) AS (
    VALUES
        (1, $prompt$《담을 넘은 아이》의 주인공 이름은 무엇인가요?$prompt$,
         $options$[{"id":"A","text":"해님"},{"id":"B","text":"효진"},{"id":"C","text":"푸실"},{"id":"D","text":"귀손"}]$options$::jsonb, 'C'),
        (2, $prompt$《담을 넘은 아이》를 쓴 사람은 누구인가요?$prompt$,
         $options$[{"id":"A","text":"김정민"},{"id":"B","text":"이영환"},{"id":"C","text":"황선미"},{"id":"D","text":"유은실"}]$options$::jsonb, 'A'),
        (3, $prompt$《담을 넘은 아이》를 출판한 출판사는 어디인가요?$prompt$,
         $options$[{"id":"A","text":"비룡소"},{"id":"B","text":"창비"},{"id":"C","text":"문학동네"},{"id":"D","text":"웅진주니어"}]$options$::jsonb, 'A'),
        (4, $prompt$푸실이의 어머니가 대감댁에 가게 된 이유는 무엇인가요?$prompt$,
         $options$[{"id":"A","text":"푸실이에게 글을 가르치기 위해서"},{"id":"B","text":"귀손이의 약값을 갚고 젖어미로 일하기 위해서"},{"id":"C","text":"새로운 집으로 이사하기 위해서"},{"id":"D","text":"효진 아가씨를 만나기 위해서"}]$options$::jsonb, 'B'),
        (5, $prompt$푸실이가 글을 배우고 싶어 한 가장 큰 이유는 무엇인가요?$prompt$,
         $options$[{"id":"A","text":"다른 사람에게 자랑하기 위해"},{"id":"B","text":"자신의 생각을 알고 세상을 더 넓게 보기 위해"},{"id":"C","text":"돈을 많이 벌기 위해"},{"id":"D","text":"집안일을 하지 않기 위해"}]$options$::jsonb, 'B'),
        (6, $prompt$효진의 아버지는 푸실이가 가진 책을 보고 놀랍니다. 푸실이와 대화를 나눈 뒤, 마지막에 무슨 약속을 꼭 지키라고 했나요?$prompt$,
         $options$[{"id":"A","text":"책을 읽고 나서 돌려주라는 약속"},{"id":"B","text":"책의 주인을 꼭 찾아주라는 약속"},{"id":"C","text":"책의 값으로 쌀 두 가마니를 내겠냐는 약속"},{"id":"D","text":"글을 배워서 책을 꼭 읽겠다는 약속"}]$options$::jsonb, 'D'),
        (7, $prompt$'어머니가 대감마님댁에 간 지도 달포나 지났다.'에서 '달포'의 뜻은 무엇인가요?$prompt$,
         $options$[{"id":"A","text":"한 달의 절반"},{"id":"B","text":"딱 한 달"},{"id":"C","text":"한 달이 조금 넘는 기간"},{"id":"D","text":"한 달이 조금 안 되는 기간"}]$options$::jsonb, 'C'),
        (8, $prompt$책 제목에서 ‘담’이 의미하는 것으로 가장 알맞은 것은 무엇인가요?$prompt$,
         $options$[{"id":"A","text":"마을을 지키는 실제 성벽만을 뜻한다."},{"id":"B","text":"사람들이 함께 쉬는 장소를 뜻한다."},{"id":"C","text":"푸실이를 가로막는 차별과 사회의 규칙을 뜻한다."},{"id":"D","text":"푸실이의 가족이 사는 집을 뜻한다."}]$options$::jsonb, 'C'),
        (9, $prompt$152쪽, 이 책의 마지막 문장은 무엇인지 고르시오.$prompt$,
         $options$[{"id":"A","text":"어찌 살 것입니까."},{"id":"B","text":"저는 담을 넘겠습니다."},{"id":"C","text":"더 힘차게 앞으로 나아가겠습니다."}]$options$::jsonb, 'A'),
        (10, $prompt$《담을 넘은 아이》가 우리에게 전하는 생각으로 가장 알맞은 것은 무엇인가요?$prompt$,
         $options$[{"id":"A","text":"옛날의 규칙은 언제나 지켜야 한다."},{"id":"B","text":"태어난 환경에 따라 사람의 가능성이 정해진다."},{"id":"C","text":"어려움이 생기면 자신의 꿈을 포기해야 한다."},{"id":"D","text":"차별과 편견을 넘어 자신의 삶을 스스로 선택할 수 있다."}]$options$::jsonb, 'D')
)
UPDATE quiz_questions question
SET prompt = content.prompt,
    options = content.options,
    correct_answer = content.correct_answer,
    explanation = NULL
FROM eligible_quizzes quiz, reading_quiz_content content
WHERE question.quiz_id = quiz.id
  AND question.order_no = content.order_no;

WITH eligible_quizzes AS (
    SELECT quiz.id
    FROM quizzes quiz
    WHERE quiz.day_number = 1
      AND NOT EXISTS (
          SELECT 1
          FROM quiz_submissions submission
          WHERE submission.quiz_id = quiz.id
      )
),
reading_quiz_content (order_no, prompt, options, correct_answer) AS (
    VALUES
        (1, $prompt$《담을 넘은 아이》의 주인공 이름은 무엇인가요?$prompt$, $options$[{"id":"A","text":"해님"},{"id":"B","text":"효진"},{"id":"C","text":"푸실"},{"id":"D","text":"귀손"}]$options$::jsonb, 'C'),
        (2, $prompt$《담을 넘은 아이》를 쓴 사람은 누구인가요?$prompt$, $options$[{"id":"A","text":"김정민"},{"id":"B","text":"이영환"},{"id":"C","text":"황선미"},{"id":"D","text":"유은실"}]$options$::jsonb, 'A'),
        (3, $prompt$《담을 넘은 아이》를 출판한 출판사는 어디인가요?$prompt$, $options$[{"id":"A","text":"비룡소"},{"id":"B","text":"창비"},{"id":"C","text":"문학동네"},{"id":"D","text":"웅진주니어"}]$options$::jsonb, 'A'),
        (4, $prompt$푸실이의 어머니가 대감댁에 가게 된 이유는 무엇인가요?$prompt$, $options$[{"id":"A","text":"푸실이에게 글을 가르치기 위해서"},{"id":"B","text":"귀손이의 약값을 갚고 젖어미로 일하기 위해서"},{"id":"C","text":"새로운 집으로 이사하기 위해서"},{"id":"D","text":"효진 아가씨를 만나기 위해서"}]$options$::jsonb, 'B'),
        (5, $prompt$푸실이가 글을 배우고 싶어 한 가장 큰 이유는 무엇인가요?$prompt$, $options$[{"id":"A","text":"다른 사람에게 자랑하기 위해"},{"id":"B","text":"자신의 생각을 알고 세상을 더 넓게 보기 위해"},{"id":"C","text":"돈을 많이 벌기 위해"},{"id":"D","text":"집안일을 하지 않기 위해"}]$options$::jsonb, 'B'),
        (6, $prompt$효진의 아버지는 푸실이가 가진 책을 보고 놀랍니다. 푸실이와 대화를 나눈 뒤, 마지막에 무슨 약속을 꼭 지키라고 했나요?$prompt$, $options$[{"id":"A","text":"책을 읽고 나서 돌려주라는 약속"},{"id":"B","text":"책의 주인을 꼭 찾아주라는 약속"},{"id":"C","text":"책의 값으로 쌀 두 가마니를 내겠냐는 약속"},{"id":"D","text":"글을 배워서 책을 꼭 읽겠다는 약속"}]$options$::jsonb, 'D'),
        (7, $prompt$'어머니가 대감마님댁에 간 지도 달포나 지났다.'에서 '달포'의 뜻은 무엇인가요?$prompt$, $options$[{"id":"A","text":"한 달의 절반"},{"id":"B","text":"딱 한 달"},{"id":"C","text":"한 달이 조금 넘는 기간"},{"id":"D","text":"한 달이 조금 안 되는 기간"}]$options$::jsonb, 'C'),
        (8, $prompt$책 제목에서 ‘담’이 의미하는 것으로 가장 알맞은 것은 무엇인가요?$prompt$, $options$[{"id":"A","text":"마을을 지키는 실제 성벽만을 뜻한다."},{"id":"B","text":"사람들이 함께 쉬는 장소를 뜻한다."},{"id":"C","text":"푸실이를 가로막는 차별과 사회의 규칙을 뜻한다."},{"id":"D","text":"푸실이의 가족이 사는 집을 뜻한다."}]$options$::jsonb, 'C'),
        (9, $prompt$152쪽, 이 책의 마지막 문장은 무엇인지 고르시오.$prompt$, $options$[{"id":"A","text":"어찌 살 것입니까."},{"id":"B","text":"저는 담을 넘겠습니다."},{"id":"C","text":"더 힘차게 앞으로 나아가겠습니다."}]$options$::jsonb, 'A'),
        (10, $prompt$《담을 넘은 아이》가 우리에게 전하는 생각으로 가장 알맞은 것은 무엇인가요?$prompt$, $options$[{"id":"A","text":"옛날의 규칙은 언제나 지켜야 한다."},{"id":"B","text":"태어난 환경에 따라 사람의 가능성이 정해진다."},{"id":"C","text":"어려움이 생기면 자신의 꿈을 포기해야 한다."},{"id":"D","text":"차별과 편견을 넘어 자신의 삶을 스스로 선택할 수 있다."}]$options$::jsonb, 'D')
)
INSERT INTO quiz_questions (
    id, quiz_id, order_no, question_type, prompt,
    options, correct_answer, explanation
)
SELECT md5(quiz.id::text || ':day1-reading-quiz:' || content.order_no::text)::uuid,
       quiz.id,
       content.order_no,
       'MULTIPLE_CHOICE',
       content.prompt,
       content.options,
       content.correct_answer,
       NULL
FROM eligible_quizzes quiz
CROSS JOIN reading_quiz_content content
ON CONFLICT (quiz_id, order_no) DO NOTHING;

UPDATE quizzes quiz
SET title = '독서 퀴즈',
    question_count = 10,
    updated_at = CURRENT_TIMESTAMP
WHERE quiz.day_number = 1
  AND NOT EXISTS (
      SELECT 1
      FROM quiz_submissions submission
      WHERE submission.quiz_id = quiz.id
  );

UPDATE room_rules rules
SET day1_quiz_count = 10,
    updated_at = CURRENT_TIMESTAMP
FROM quizzes quiz
WHERE quiz.room_id = rules.room_id
  AND quiz.day_number = 1
  AND NOT EXISTS (
      SELECT 1
      FROM quiz_submissions submission
      WHERE submission.quiz_id = quiz.id
  );
