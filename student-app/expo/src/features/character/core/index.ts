/**
 * 캐릭터 공통 코어.
 *
 * 여기 있는 모듈은 플랫폼 중립이어야 한다. `react-native`, `react-dom`, DOM API,
 * Metro `require()`를 넣지 않는다. 에셋 해석처럼 플랫폼이 갈리는 부분은
 * `assetMap`이 담당한다.
 *
 * Metro는 프로젝트 루트 밖의 파일을 기본 설정으로 읽지 못하므로 이 코어는 Expo 트리
 * 안에 둔다. 모노레포로 재배치할 때 별도 패키지로 뽑는다.
 */

export * from './canvas';
export * from './catalog';
export * from './composition';
export * from './items';
export * from './layerOrder';
export * from './palette';
export * from './types';
