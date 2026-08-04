/**
 * 캐릭터 공통 코어. 웹과 Expo가 **같은 파일**을 참조한다.
 *
 * 여기 있는 모듈은 플랫폼 중립이어야 한다. `react-native`, `react-dom`, DOM API,
 * Metro `require()`를 넣지 않는다. 에셋 해석처럼 플랫폼이 갈리는 부분은 각 앱의
 * `assetMap`이 담당한다.
 *
 * 물리적 위치가 Expo 트리 안인 이유: Metro는 프로젝트 루트 밖의 파일을 기본 설정으로
 * 읽지 못하지만 Vite는 상대경로로 읽을 수 있다. 이 배치가 양쪽 모두 추가 설정 없이
 * 단일 사본을 공유하는 유일한 방법이다. 모노레포로 재배치할 때 별도 패키지로 뽑는다.
 */

export * from './canvas';
export * from './catalog';
export * from './composition';
export * from './items';
export * from './layerOrder';
export * from './palette';
export * from './types';
