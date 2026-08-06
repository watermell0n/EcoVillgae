/**
 * 경제학습 수업자료를 바탕으로 사람이 작성한 4지선다 문제은행이다.
 *
 * <p>AI 초안(QuizDraftWorkspace)과 달리 매번 새로 만들지 않는 고정 문항이다.
 * 넌센스 문제은행이 서버 리소스 파일로 있는 것과 같은 이유로 표를 쓰지 않지만,
 * 이쪽은 교사 화면에서 고르는 즉시 기존 문항 API로 등록하면 되므로 웹에 둔다.
 * 난이도는 등록 전 선택 기준일 뿐이라 문항 저장 스키마에는 들어가지 않는다.
 */

export type EconomyQuizDifficulty = "EASY" | "NORMAL" | "HARD";

export type EconomyQuizChoice = {
  key: string;
  text: string;
};

export type EconomyQuizQuestion = {
  id: string;
  difficulty: EconomyQuizDifficulty;
  /** 수업자료 이미지 번호. 신용등급처럼 뒤에 끼워 넣은 자료는 `17-1` 형태다. */
  lessonNo: string;
  lessonTitle: string;
  category: string;
  question: string;
  choices: EconomyQuizChoice[];
  correctChoiceKey: string;
  explanation: string;
};

export const economyQuizBankVersion = "economy-bank-v1";

export const economyQuizDifficulties: ReadonlyArray<{
  value: EconomyQuizDifficulty;
  label: string;
  description: string;
}> = [
  {
    value: "EASY",
    label: "쉬움",
    description: "일상에서 바로 겪는 기초 개념입니다. 돈의 역할, 수입과 지출, 예금과 적금처럼 눈에 보이는 내용을 묻습니다."
  },
  {
    value: "NORMAL",
    label: "보통",
    description: "개념 사이의 관계를 이해하고 간단한 계산이 필요합니다. 실수령액, 기회비용, 금리, 신용을 묻습니다."
  },
  {
    value: "HARD",
    label: "어려움",
    description: "여러 개념이 얽히거나 나라 전체의 경제를 다룹니다. 복리, 분산투자, 인플레이션, 환율, GDP를 묻습니다."
  }
];

export const economyQuizBank: ReadonlyArray<EconomyQuizQuestion> = [
  {
    id: "eb-e01",
    difficulty: "EASY",
    lessonNo: "1",
    lessonTitle: "돈의역할",
    category: "14_돈과 가계생활",
    question: "돈이 없던 옛날에는 물건과 물건을 서로 바꿔서 썼어요. 돈이 생기면서 좋아진 점은 무엇일까요?",
    choices: [
      { key: "A", text: "물건값이 모두 똑같아졌어요" },
      { key: "B", text: "원하는 물건과 바꾸기가 훨씬 쉬워졌어요" },
      { key: "C", text: "물건을 만들지 않아도 되었어요" },
      { key: "D", text: "저축을 할 수 없게 되었어요" }
    ],
    correctChoiceKey: "B",
    explanation: "돈은 물건의 값을 정해 주고, 서로 바꾸기 쉽게 해 주는 역할을 해요. 그래서 물건끼리 직접 바꾸지 않아도 되지요."
  },
  {
    id: "eb-e02",
    difficulty: "EASY",
    lessonNo: "2",
    lessonTitle: "수입과 지출",
    category: "14_돈과 가계생활",
    question: "다음 중 '지출'에 해당하는 것은 무엇일까요?",
    choices: [
      { key: "A", text: "용돈을 받았어요" },
      { key: "B", text: "세뱃돈을 받았어요" },
      { key: "C", text: "문구점에서 공책을 샀어요" },
      { key: "D", text: "심부름을 해서 돈을 벌었어요" }
    ],
    correctChoiceKey: "C",
    explanation: "들어오는 돈은 수입, 나가는 돈은 지출이에요. 공책을 사면 돈이 나가니까 지출이에요."
  },
  {
    id: "eb-e03",
    difficulty: "EASY",
    lessonNo: "4",
    lessonTitle: "생산과소비",
    category: "04_우리 반 상점 운영",
    question: "빵집에서 빵을 만드는 일과, 그 빵을 사 먹는 일을 각각 무엇이라고 할까요?",
    choices: [
      { key: "A", text: "생산 - 소비" },
      { key: "B", text: "소비 - 생산" },
      { key: "C", text: "저축 - 지출" },
      { key: "D", text: "수입 - 지출" }
    ],
    correctChoiceKey: "A",
    explanation: "물건이나 서비스를 만드는 것은 생산, 그것을 사서 쓰는 것은 소비예요."
  },
  {
    id: "eb-e04",
    difficulty: "EASY",
    lessonNo: "5",
    lessonTitle: "유통이란",
    category: "04_우리 반 상점 운영",
    question: "농부가 기른 사과가 우리 집 근처 가게까지 오는 데 꼭 필요한 일은 무엇일까요?",
    choices: [
      { key: "A", text: "사과 씨앗을 심는 일" },
      { key: "B", text: "사과를 더 크게 키우는 일" },
      { key: "C", text: "사과값을 올리는 일" },
      { key: "D", text: "사과를 옮기고 가게에 나누어 주는 일" }
    ],
    correctChoiceKey: "D",
    explanation: "만든 물건을 사는 사람에게까지 옮기고 나누어 주는 과정을 유통이라고 해요."
  },
  {
    id: "eb-e05",
    difficulty: "EASY",
    lessonNo: "6",
    lessonTitle: "비교와 선택",
    category: "03_필요와 욕구 구분하기",
    question: "똑같은 색연필이 가 가게에서는 3,000원, 나 가게에서는 2,500원이에요. 품질도 같다면 어떻게 하는 것이 현명할까요?",
    choices: [
      { key: "A", text: "가 가게에서 사요" },
      { key: "B", text: "나 가게에서 사요" },
      { key: "C", text: "두 가게에서 모두 사요" },
      { key: "D", text: "비싼 것이 더 좋으니 가 가게에서 사요" }
    ],
    correctChoiceKey: "B",
    explanation: "품질이 같다면 값을 비교해서 더 싼 것을 고르는 게 현명한 선택이에요. 아낀 500원은 저축할 수 있어요."
  },
  {
    id: "eb-e06",
    difficulty: "EASY",
    lessonNo: "8",
    lessonTitle: "소비와 저축",
    category: "03_필요와 욕구 구분하기",
    question: "용돈 10,000원 가운데 3,000원을 쓰고 7,000원을 남겨 모아 두었어요. 남겨 둔 7,000원을 무엇이라고 할까요?",
    choices: [
      { key: "A", text: "소비" },
      { key: "B", text: "수입" },
      { key: "C", text: "저축" },
      { key: "D", text: "세금" }
    ],
    correctChoiceKey: "C",
    explanation: "쓰지 않고 모아 두는 것을 저축이라고 해요. 저축을 하면 나중에 더 큰 것을 살 수 있어요."
  },
  {
    id: "eb-e07",
    difficulty: "EASY",
    lessonNo: "10",
    lessonTitle: "예금이란",
    category: "05_예금과 적금",
    question: "모아 둔 돈을 한꺼번에 은행에 맡겨 두고, 필요할 때 찾아 쓰는 것을 무엇이라고 할까요?",
    choices: [
      { key: "A", text: "예금" },
      { key: "B", text: "적금" },
      { key: "C", text: "대출" },
      { key: "D", text: "보험" }
    ],
    correctChoiceKey: "A",
    explanation: "목돈을 한 번에 맡기는 것을 예금이라고 해요. 맡겨 둔 동안 이자도 받을 수 있어요."
  },
  {
    id: "eb-e08",
    difficulty: "EASY",
    lessonNo: "11",
    lessonTitle: "적금이란",
    category: "05_예금과 적금",
    question: "매달 정해진 돈을 조금씩 은행에 넣어서 모으는 방법을 무엇이라고 할까요?",
    choices: [
      { key: "A", text: "예금" },
      { key: "B", text: "투자" },
      { key: "C", text: "대출" },
      { key: "D", text: "적금" }
    ],
    correctChoiceKey: "D",
    explanation: "매달 나누어 넣어 모으는 것을 적금이라고 해요. 한 번에 큰돈이 없어도 꾸준히 모을 수 있어요."
  },
  {
    id: "eb-e09",
    difficulty: "EASY",
    lessonNo: "15",
    lessonTitle: "빌린돈과 상환",
    category: "06_빌린 돈과 상환",
    question: "친구에게 돈을 빌렸을 때 가장 중요하게 지켜야 할 것은 무엇일까요?",
    choices: [
      { key: "A", text: "빌린 사실을 비밀로 해요" },
      { key: "B", text: "약속한 날에 갚아요" },
      { key: "C", text: "더 많이 빌려요" },
      { key: "D", text: "갚지 않아도 괜찮아요" }
    ],
    correctChoiceKey: "B",
    explanation: "빌린 돈을 약속한 날에 갚는 것을 상환이라고 해요. 약속을 지키면 서로 믿을 수 있게 돼요."
  },
  {
    id: "eb-e10",
    difficulty: "EASY",
    lessonNo: "20",
    lessonTitle: "친구와 안전하게 거래하기",
    category: "11_친구와 안전하게 거래하기",
    question: "친구와 물건을 주고받는 거래를 할 때, 안전하게 하는 방법은 무엇일까요?",
    choices: [
      { key: "A", text: "말로만 정하고 그냥 넘어가요" },
      { key: "B", text: "한 사람이 정한 대로 해요" },
      { key: "C", text: "서로 동의하고 거래한 내용을 적어 둬요" },
      { key: "D", text: "아무도 모르게 몰래 해요" }
    ],
    correctChoiceKey: "C",
    explanation: "서로 동의하고 기록을 남기면 나중에 다툴 일이 줄어들어요. 이것이 안전한 거래의 기본이에요."
  },
  {
    id: "eb-n01",
    difficulty: "NORMAL",
    lessonNo: "3",
    lessonTitle: "월급과 실수령액",
    category: "01_월급과 실수령액",
    question: "월급이 200만 원인데 통장에 실제로 들어온 돈은 180만 원이었어요. 20만 원은 왜 줄어들었을까요?",
    choices: [
      { key: "A", text: "세금과 보험료 등이 먼저 빠져나갔기 때문이에요" },
      { key: "B", text: "은행이 가져갔기 때문이에요" },
      { key: "C", text: "회사가 실수했기 때문이에요" },
      { key: "D", text: "원래 월급이 180만 원이기 때문이에요" }
    ],
    correctChoiceKey: "A",
    explanation: "월급에서 세금과 보험료를 먼저 낸 뒤 남은 돈을 실수령액이라고 해요. 그래서 월급과 통장에 들어오는 돈이 달라요."
  },
  {
    id: "eb-n02",
    difficulty: "NORMAL",
    lessonNo: "7",
    lessonTitle: "기회비용이란",
    category: "03_필요와 욕구 구분하기",
    question: "3,000원으로 아이스크림과 만화책 가운데 하나만 살 수 있어요. 아이스크림을 골랐다면 기회비용은 무엇일까요?",
    choices: [
      { key: "A", text: "3,000원" },
      { key: "B", text: "내가 고른 아이스크림" },
      { key: "C", text: "포기한 만화책" },
      { key: "D", text: "기회비용은 없어요" }
    ],
    correctChoiceKey: "C",
    explanation: "무엇을 고르면 대신 포기하게 되는 것이 기회비용이에요. 아이스크림을 골랐으니 포기한 만화책이 기회비용이에요."
  },
  {
    id: "eb-n03",
    difficulty: "NORMAL",
    lessonNo: "9",
    lessonTitle: "예산세우기",
    category: "03_필요와 욕구 구분하기",
    question: "예산을 세우는 가장 큰 이유는 무엇일까요?",
    choices: [
      { key: "A", text: "돈을 하나도 쓰지 않으려고요" },
      { key: "B", text: "쓸 돈을 미리 정해 두고 계획대로 쓰려고요" },
      { key: "C", text: "남보다 돈을 많이 벌려고요" },
      { key: "D", text: "세금을 내지 않으려고요" }
    ],
    correctChoiceKey: "B",
    explanation: "예산은 들어올 돈과 나갈 돈을 미리 계획하는 것이에요. 계획이 있으면 꼭 필요한 곳에 돈을 쓸 수 있어요."
  },
  {
    id: "eb-n04",
    difficulty: "NORMAL",
    lessonNo: "12",
    lessonTitle: "원금과 이자",
    category: "05_예금과 적금",
    question: "은행에 10,000원을 맡겼더니 1년 뒤에 10,300원이 되었어요. 늘어난 300원을 무엇이라고 할까요?",
    choices: [
      { key: "A", text: "원금" },
      { key: "B", text: "세금" },
      { key: "C", text: "수수료" },
      { key: "D", text: "이자" }
    ],
    correctChoiceKey: "D",
    explanation: "처음 맡긴 10,000원은 원금, 더 붙은 300원은 이자예요. 은행은 돈을 맡아 준 대가로 이자를 줘요."
  },
  {
    id: "eb-n05",
    difficulty: "NORMAL",
    lessonNo: "13",
    lessonTitle: "금리란",
    category: "05_예금과 적금",
    question: "금리가 높아지면, 은행에 돈을 맡긴 사람에게는 어떤 일이 생길까요?",
    choices: [
      { key: "A", text: "받는 이자가 늘어나요" },
      { key: "B", text: "받는 이자가 줄어들어요" },
      { key: "C", text: "맡긴 원금이 줄어들어요" },
      { key: "D", text: "아무것도 달라지지 않아요" }
    ],
    correctChoiceKey: "A",
    explanation: "금리는 이자가 붙는 비율이에요. 금리가 높으면 같은 돈을 맡겨도 이자를 더 많이 받아요."
  },
  {
    id: "eb-n06",
    difficulty: "NORMAL",
    lessonNo: "16",
    lessonTitle: "대출이란",
    category: "06_빌린 돈과 상환",
    question: "은행에서 돈을 빌리는 것을 대출이라고 해요. 대출받은 돈은 갚을 때 어떻게 될까요?",
    choices: [
      { key: "A", text: "빌린 돈만 그대로 갚아요" },
      { key: "B", text: "빌린 돈보다 적게 갚아요" },
      { key: "C", text: "빌린 돈에 이자를 더해서 갚아요" },
      { key: "D", text: "갚지 않아도 괜찮아요" }
    ],
    correctChoiceKey: "C",
    explanation: "대출은 빌린 원금에 이자를 더해서 갚아야 해요. 그래서 꼭 필요할 때만 빌리고, 갚을 계획을 세워야 해요."
  },
  {
    id: "eb-n07",
    difficulty: "NORMAL",
    lessonNo: "17",
    lessonTitle: "신용이란",
    category: "11_친구와 안전하게 거래하기",
    question: "'신용이 좋다'는 말은 어떤 뜻일까요?",
    choices: [
      { key: "A", text: "돈이 아주 많아요" },
      { key: "B", text: "약속을 잘 지켜서 믿을 수 있어요" },
      { key: "C", text: "친구가 아주 많아요" },
      { key: "D", text: "물건을 늘 싸게 사요" }
    ],
    correctChoiceKey: "B",
    explanation: "신용은 약속을 잘 지켜서 얼마나 믿을 수 있는지를 뜻해요. 돈이 많은 것과는 달라요."
  },
  {
    id: "eb-n08",
    difficulty: "NORMAL",
    lessonNo: "18",
    lessonTitle: "연체란",
    category: "06_빌린 돈과 상환",
    question: "갚기로 약속한 날을 넘겨서 돈을 갚지 못하는 것을 무엇이라고 할까요?",
    choices: [
      { key: "A", text: "저축" },
      { key: "B", text: "예금" },
      { key: "C", text: "투자" },
      { key: "D", text: "연체" }
    ],
    correctChoiceKey: "D",
    explanation: "약속한 날을 넘기면 연체라고 해요. 연체를 하면 신용이 나빠지고, 더 많은 이자를 낼 수도 있어요."
  },
  {
    id: "eb-n09",
    difficulty: "NORMAL",
    lessonNo: "19",
    lessonTitle: "보험이란",
    category: "15_보험과 위험 대비",
    question: "사람들이 보험에 드는 이유는 무엇일까요?",
    choices: [
      { key: "A", text: "갑자기 큰일이 생겼을 때 도움을 받으려고요" },
      { key: "B", text: "매달 돈을 벌려고요" },
      { key: "C", text: "물건을 싸게 사려고요" },
      { key: "D", text: "세금을 내지 않으려고요" }
    ],
    correctChoiceKey: "A",
    explanation: "여러 사람이 조금씩 돈을 모아 두었다가, 누군가에게 큰일이 생기면 그 돈으로 도와주는 것이 보험이에요."
  },
  {
    id: "eb-n10",
    difficulty: "NORMAL",
    lessonNo: "34",
    lessonTitle: "세금이란",
    category: "02_세금과 공동기금",
    question: "우리가 낸 세금은 주로 어디에 쓰일까요?",
    choices: [
      { key: "A", text: "세금을 많이 낸 사람에게만 돌려줘요" },
      { key: "B", text: "은행의 이자로 쓰여요" },
      { key: "C", text: "학교, 도로, 병원처럼 모두가 함께 쓰는 곳에 쓰여요" },
      { key: "D", text: "회사 사장님의 월급으로 쓰여요" }
    ],
    correctChoiceKey: "C",
    explanation: "세금은 모두가 함께 쓰는 것들을 만들고 고치는 데 쓰여요. 우리 반 공동기금과 비슷한 원리예요."
  },
  {
    id: "eb-h01",
    difficulty: "HARD",
    lessonNo: "14",
    lessonTitle: "복리란",
    category: "05_예금과 적금",
    question: "100원에 이자가 붙어 110원이 되었어요. 복리라면 그다음 이자는 무엇을 기준으로 붙을까요?",
    choices: [
      { key: "A", text: "처음 넣은 100원" },
      { key: "B", text: "이자가 더해진 110원" },
      { key: "C", text: "이자인 10원" },
      { key: "D", text: "더는 이자가 붙지 않아요" }
    ],
    correctChoiceKey: "B",
    explanation: "복리는 이자에 다시 이자가 붙어요. 그래서 오래 모을수록 돈이 점점 더 빠르게 늘어나요."
  },
  {
    id: "eb-h02",
    difficulty: "HARD",
    lessonNo: "17-1",
    lessonTitle: "신용등급이란",
    category: "11_친구와 안전하게 거래하기",
    question: "신용등급을 좋게 만들려면 어떻게 해야 할까요?",
    choices: [
      { key: "A", text: "돈을 되도록 많이 빌려요" },
      { key: "B", text: "갚는 날을 자주 미뤄요" },
      { key: "C", text: "빌린 돈을 약속한 날에 잘 갚아요" },
      { key: "D", text: "은행에 아예 가지 않아요" }
    ],
    correctChoiceKey: "C",
    explanation: "신용등급은 약속을 잘 지키고 제때 갚아서 얼마나 믿을 수 있는지를 보여 주는 정도예요. 신용등급이 좋으면 거래할 때 도움이 돼요."
  },
  {
    id: "eb-h03",
    difficulty: "HARD",
    lessonNo: "26",
    lessonTitle: "위험과 분산투자",
    category: "09_단기 투자와 결과",
    question: "'달걀을 한 바구니에 모두 담지 말라'는 말은 투자에서 어떤 뜻일까요?",
    choices: [
      { key: "A", text: "여러 곳에 나누어 투자해서 위험을 줄이라는 뜻이에요" },
      { key: "B", text: "한 곳에 모두 투자하라는 뜻이에요" },
      { key: "C", text: "투자를 아예 하지 말라는 뜻이에요" },
      { key: "D", text: "달걀에 투자하라는 뜻이에요" }
    ],
    correctChoiceKey: "A",
    explanation: "한 곳에만 투자하면 그것이 잘못될 때 크게 손해를 봐요. 여러 곳에 나누어 투자하면 위험이 줄어들어요."
  },
  {
    id: "eb-h04",
    difficulty: "HARD",
    lessonNo: "29",
    lessonTitle: "수요와 공급",
    category: "04_우리 반 상점 운영",
    question: "인기 있는 장난감을 사려는 사람은 아주 많은데, 가게에 있는 물건은 얼마 없어요. 값은 어떻게 될까요?",
    choices: [
      { key: "A", text: "내려가요" },
      { key: "B", text: "변하지 않아요" },
      { key: "C", text: "공짜가 돼요" },
      { key: "D", text: "올라가요" }
    ],
    correctChoiceKey: "D",
    explanation: "사려는 사람(수요)이 많고 물건(공급)이 적으면 값이 올라가요. 반대로 물건이 남으면 값이 내려가요."
  },
  {
    id: "eb-h05",
    difficulty: "HARD",
    lessonNo: "31",
    lessonTitle: "인플레이션이란",
    category: "04_우리 반 상점 운영",
    question: "작년에 1,000원이던 과자가 올해 1,300원이 되었고, 다른 물건값도 함께 올랐어요. 이런 현상을 무엇이라고 할까요?",
    choices: [
      { key: "A", text: "인플레이션" },
      { key: "B", text: "디플레이션" },
      { key: "C", text: "저축" },
      { key: "D", text: "환율" }
    ],
    correctChoiceKey: "A",
    explanation: "물건값이 전체적으로 오르는 것을 인플레이션이라고 해요. 같은 돈으로 살 수 있는 것이 줄어들어요."
  },
  {
    id: "eb-h06",
    difficulty: "HARD",
    lessonNo: "32",
    lessonTitle: "디플레이션이란",
    category: "04_우리 반 상점 운영",
    question: "디플레이션이 일어나면 어떤 일이 생길까요?",
    choices: [
      { key: "A", text: "물건값이 전체적으로 올라가요" },
      { key: "B", text: "물건값이 전체적으로 내려가요" },
      { key: "C", text: "물건값이 두 배가 돼요" },
      { key: "D", text: "돈이 모두 사라져요" }
    ],
    correctChoiceKey: "B",
    explanation: "디플레이션은 물건값이 계속 내려가는 것이에요. 값이 더 내릴까 봐 사람들이 안 사게 되면 경제가 어려워질 수 있어요."
  },
  {
    id: "eb-h07",
    difficulty: "HARD",
    lessonNo: "33",
    lessonTitle: "경기호황과 불황",
    category: "16_경기와 경제지표",
    question: "회사가 잘되고 사람들이 물건을 많이 사며 일자리도 늘어나는 때를 무엇이라고 할까요?",
    choices: [
      { key: "A", text: "불황" },
      { key: "B", text: "연체" },
      { key: "C", text: "호황" },
      { key: "D", text: "관세" }
    ],
    correctChoiceKey: "C",
    explanation: "경제가 활발하게 잘 돌아가는 때를 호황, 반대로 어려운 때를 불황이라고 해요. 경기는 좋아졌다 나빠졌다를 되풀이해요."
  },
  {
    id: "eb-h08",
    difficulty: "HARD",
    lessonNo: "37",
    lessonTitle: "환율이란",
    category: "17_국제무역과 환율",
    question: "1달러를 바꾸는 데 필요한 돈이 1,300원에서 1,400원으로 올랐어요. 이때 어떤 일이 생길까요?",
    choices: [
      { key: "A", text: "외국 물건을 살 때 우리 돈이 더 많이 필요해요" },
      { key: "B", text: "외국 물건이 더 싸져요" },
      { key: "C", text: "아무것도 달라지지 않아요" },
      { key: "D", text: "달러를 쓸 수 없게 돼요" }
    ],
    correctChoiceKey: "A",
    explanation: "환율은 다른 나라 돈과 우리 돈을 바꾸는 비율이에요. 환율이 오르면 외국 물건을 살 때 우리 돈이 더 많이 들어요."
  },
  {
    id: "eb-h09",
    difficulty: "HARD",
    lessonNo: "38",
    lessonTitle: "관세란",
    category: "17_국제무역과 환율",
    question: "관세란 무엇일까요?",
    choices: [
      { key: "A", text: "물건을 만들 때 내는 세금이에요" },
      { key: "B", text: "은행에 내는 수수료예요" },
      { key: "C", text: "월급에서 빠지는 보험료예요" },
      { key: "D", text: "다른 나라에서 물건을 들여올 때 매기는 세금이에요" }
    ],
    correctChoiceKey: "D",
    explanation: "관세는 나라 사이에 물건이 오갈 때 매기는 세금이에요. 관세가 높으면 들여온 물건값이 비싸져요."
  },
  {
    id: "eb-h10",
    difficulty: "HARD",
    lessonNo: "39",
    lessonTitle: "국내총생산GDP",
    category: "16_경기와 경제지표",
    question: "국내총생산(GDP)은 무엇을 나타내는 숫자일까요?",
    choices: [
      { key: "A", text: "한 나라에 있는 돈을 모두 더한 것이에요" },
      { key: "B", text: "한 나라에서 일정 기간에 새로 만들어진 것들의 값을 모두 더한 것이에요" },
      { key: "C", text: "한 나라에 사는 사람의 수예요" },
      { key: "D", text: "한 나라가 외국에 판 물건의 개수예요" }
    ],
    correctChoiceKey: "B",
    explanation: "GDP는 한 나라가 일정 기간에 얼마나 많이 만들어 냈는지를 보여 주는 숫자예요. 나라 경제의 크기를 재는 대표적인 지표예요."
  }
];

export function economyQuizQuestionsOf(
  difficulty: EconomyQuizDifficulty
): EconomyQuizQuestion[] {
  return economyQuizBank.filter((question) => question.difficulty === difficulty);
}
