import { Pressable, StyleSheet, Text } from "react-native";

import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { colors, radii } from "@/theme/tokens";

// 서버 공개 API가 연결되기 전까지는 교사가 정리한 기본 자료 목록을 보여 줍니다.
const materials = [
  ["월급과 실수령액", "월급, 세금, 실수령액의 관계를 알아봐요."],
  ["세금과 공동기금", "우리 반에 필요한 일을 위해 세금을 어떻게 쓰는지 알아봐요."],
  ["필요와 욕구 구분하기", "사고 싶은 것과 꼭 필요한 것을 구분해요."],
  ["우리 반 상점 운영", "가격과 재고를 보고 합리적으로 소비해요."],
  ["예금과 적금", "목돈을 맡기는 예금과 나눠 모으는 적금의 차이를 알아봐요."],
  ["빌린 돈과 상환", "대출과 이자, 약속한 때에 갚는 방법을 알아봐요."],
  ["청약과 내 집 마련", "내 집 마련을 위한 조건과 계획을 살펴봐요."],
  ["부동산과 주거 선택", "집을 고를 때 위치와 비용을 함께 생각해요."],
  ["단기 투자와 결과", "투자에는 수익과 손실의 가능성이 함께 있다는 것을 배워요."],
  ["장기 투자와 정보 판단", "여러 정보를 비교해 투자 판단을 해봐요."],
  ["친구와 안전하게 거래하기", "서로 동의하고 기록을 남기는 거래를 연습해요."],
  ["공동기금과 투표", "공동기금을 어디에 쓸지 투표로 정해요."],
  ["경제 개념 확인 퀴즈", "배운 경제 개념을 퀴즈로 확인해요."]
] as const;

export default function MaterialsScreen() {
  return (
    <Screen title="수업 자료" right={<BackButton />}>
      <Text style={styles.notice}>선생님이 공개한 자료를 여기에서 확인할 수 있어요.</Text>
      {materials.map(([title, detail], index) => (
        <Pressable key={title} style={styles.item}>
          <Text style={styles.round}>{index + 1}회차</Text>
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.detail}>{detail}</Text>
        </Pressable>
      ))}
    </Screen>
  );
}

const styles = StyleSheet.create({
  notice: { color: colors.muted600, fontSize: 15, lineHeight: 22 },
  item: { gap: 7, padding: 18, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: radii.card, backgroundColor: colors.white },
  round: { color: colors.green500, fontSize: 13, fontWeight: "900" },
  title: { color: colors.ink900, fontSize: 19, fontWeight: "900" },
  detail: { color: colors.muted600, fontSize: 15, lineHeight: 22 }
});
