#!/usr/bin/env node
/**
 * 캐릭터 에셋을 정본에서 Expo 번들로 복제한다.
 *
 * 정본은 `student-app/public/character/`이고 Expo는 Metro `require()`로 번들에 담기
 * 때문에 같은 파일을 한 곳에서 참조할 수 없다. 파일을 복제하되 **어긋날 수 없게** 만든다.
 *
 *   node scripts/character/sync-assets.js           복제
 *   node scripts/character/sync-assets.js --check    차이만 보고, 쓰지 않음 (CI용)
 *
 * 에셋을 추가·수정할 때는 반드시 정본 쪽(SOURCE)을 고치고 이 스크립트를 실행한다.
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '../..');
const SOURCE = path.join(ROOT, 'student-app/public/character');
const TARGET = path.join(ROOT, 'student-app/expo/assets/character');
const CHECK = process.argv.includes('--check');

function walk(dir, base = dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, base, out);
    else out.push(path.relative(base, full).split(path.sep).join('/'));
  }
  return out;
}

const sourceFiles = walk(SOURCE).sort();
const targetFiles = walk(TARGET).sort();

const added = [];
const changed = [];
const removed = targetFiles.filter((f) => !sourceFiles.includes(f));

for (const rel of sourceFiles) {
  const from = path.join(SOURCE, rel);
  const to = path.join(TARGET, rel);
  if (!fs.existsSync(to)) {
    added.push(rel);
  } else if (!fs.readFileSync(from).equals(fs.readFileSync(to))) {
    changed.push(rel);
  }
}

if (!CHECK) {
  for (const rel of [...added, ...changed]) {
    const to = path.join(TARGET, rel);
    fs.mkdirSync(path.dirname(to), { recursive: true });
    fs.copyFileSync(path.join(SOURCE, rel), to);
  }
  for (const rel of removed) fs.rmSync(path.join(TARGET, rel));
  // 빈 디렉터리 정리
  const prune = (dir) => {
    if (!fs.existsSync(dir)) return;
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      if (entry.isDirectory()) prune(path.join(dir, entry.name));
    }
    if (fs.readdirSync(dir).length === 0 && dir !== TARGET) fs.rmdirSync(dir);
  };
  prune(TARGET);
}

const total = added.length + changed.length + removed.length;
for (const rel of added) console.log(`  + ${rel}`);
for (const rel of changed) console.log(`  ~ ${rel}`);
for (const rel of removed) console.log(`  - ${rel}`);

if (CHECK) {
  if (total) {
    console.error(
      `\n✗ Expo 에셋이 웹 정본과 다르다 (추가 ${added.length} / 변경 ${changed.length} / 삭제 ${removed.length}).\n` +
        '  node scripts/character/sync-assets.js 를 실행하고 결과를 커밋한다.',
    );
    process.exit(1);
  }
  console.log(`✅ Expo 에셋이 웹 정본과 일치한다 (${sourceFiles.length}개)`);
} else {
  console.log(
    total
      ? `\n✅ ${total}개 반영 완료 (총 ${sourceFiles.length}개)`
      : `✅ 이미 최신 상태 (${sourceFiles.length}개)`,
  );
}
