<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'

import {
  addManagedStudent,
  getClassSettings,
  getManagedStudents,
  resetManagedStudentPassword,
  saveClassSettings,
  updateManagedStudent,
  type ClassSettings,
  type FundCostOptionSetting,
  type ManagedStudent,
  type ProductSetting,
} from '../../features/management/api/management'
import { getApiErrorMessage } from '../../shared/api/errors'

const props = defineProps<{ roomId: string; view: 'STUDENTS' | 'SETTINGS' }>()
const emit = defineEmits<{ refreshed: [] }>()

const loading = ref(true)
const saving = ref(false)
const errorMessage = ref('')
const noticeMessage = ref('')
const settings = ref<ClassSettings | null>(null)
const students = ref<ManagedStudent[]>([])
const settingsTab = ref<'ECONOMY' | 'QUIZ' | 'INVESTMENT' | 'SHOP' | 'FUND'>('ECONOMY')
const selectedQuizDay = ref(1)
const newStudentNumber = ref(1)
const newStudentNickname = ref('')
const showSaveConfirm = ref(false)
const pendingResetStudent = ref<ManagedStudent | null>(null)
const selectedStudentId = ref<string | null>(null)
const lockedRoleStudentIds = ref<Set<string>>(new Set())
const productImageError = ref('')
const discontinuedProductIds = ref<string[]>([])
const failedProductImages = ref(new Set<ProductSetting>())

const selectedQuiz = computed(() => settings.value?.quizzes.find((quiz) => quiz.dayNumber === selectedQuizDay.value))
const selectedStudent = computed(() => students.value.find((student) => student.studentId === selectedStudentId.value) ?? null)
const statusLabel = (status: string) => ({ INVITED: '입장 전', ACTIVE: '참여 중', ABSENT: '결석', DISABLED: '수업 제외' }[status] ?? status)
const passwordStatusLabel = (student: ManagedStudent) => {
  if (student.passwordConfigured) return '비밀번호 설정됨 · 원문은 안전하게 보호됨'
  return student.status === 'INVITED' ? '비밀번호 미설정' : '비밀번호 재설정 필요'
}
const formatRateInput = (bps: number) => bps / 100
const investmentExamplePrincipal = 100
const investmentExampleProfit = (bps: number) => {
  const multiplied = investmentExamplePrincipal * bps
  if (multiplied === 0) return 0
  const rounded = Math.floor((Math.abs(multiplied) + 5_000) / 10_000)
  return multiplied < 0 ? -rounded : rounded
}
const signedBibim = (amount: number) => `${amount > 0 ? '+' : amount < 0 ? '−' : ''}${Math.abs(amount)}비빔`
const roleOptions = computed(() => settings.value?.roleCatalog ?? [])
const suggestedRoleName = (participantNumber: number) =>
  roleOptions.value.find((role) => role.participantNumber === participantNumber)?.roleName ?? ''

onMounted(load)
watch(() => props.roomId, () => {
  selectedStudentId.value = null
  void load()
})

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [nextSettings, nextStudents] = await Promise.all([
      getClassSettings(props.roomId),
      getManagedStudents(props.roomId),
    ])
    settings.value = structuredClone(nextSettings)
    failedProductImages.value = new Set()
    discontinuedProductIds.value = []
    students.value = structuredClone(nextStudents)
    lockedRoleStudentIds.value = new Set(
      nextStudents.filter((student) => Boolean(student.roleName)).map((student) => student.studentId),
    )
    newStudentNumber.value = Math.max(0, ...students.value.map((student) => student.participantNumber)) + 1
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '관리 정보를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

async function confirmSaveSettings() {
  if (!settings.value) return
  saving.value = true
  errorMessage.value = ''
  try {
    settings.value = structuredClone(await saveClassSettings(
      props.roomId,
      settings.value,
      discontinuedProductIds.value,
    ))
    discontinuedProductIds.value = []
    noticeMessage.value = '수업 설정을 저장했습니다. 학생 화면에도 최신 값이 반영됩니다.'
    showSaveConfirm.value = false
    emit('refreshed')
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '수업 설정을 저장하지 못했습니다.')
  } finally {
    saving.value = false
  }
}

async function createStudent() {
  saving.value = true
  errorMessage.value = ''
  try {
    await addManagedStudent(props.roomId, newStudentNumber.value, newStudentNickname.value.trim())
    newStudentNickname.value = ''
    noticeMessage.value = `${newStudentNumber.value}번 학생의 입장 코드를 만들었습니다.`
    await load()
    emit('refreshed')
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '학생을 추가하지 못했습니다.')
  } finally {
    saving.value = false
  }
}

async function saveStudent(student: ManagedStudent) {
  saving.value = true
  errorMessage.value = ''
  try {
    const saved = await updateManagedStudent(props.roomId, student)
    const index = students.value.findIndex((item) => item.studentId === saved.studentId)
    if (index >= 0) students.value[index] = saved
    if (saved.roleName) {
      lockedRoleStudentIds.value = new Set([...lockedRoleStudentIds.value, saved.studentId])
    }
    noticeMessage.value = `${saved.participantNumber}번 학생 정보를 저장했습니다.`
    emit('refreshed')
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '학생 정보를 저장하지 못했습니다.')
  } finally {
    saving.value = false
  }
}

async function resetPassword() {
  if (!pendingResetStudent.value) return
  saving.value = true
  try {
    const saved = await resetManagedStudentPassword(props.roomId, pendingResetStudent.value.studentId)
    const index = students.value.findIndex((item) => item.studentId === saved.studentId)
    if (index >= 0) students.value[index] = saved
    noticeMessage.value = `${saved.participantNumber}번 학생이 다음 입장 때 닉네임은 그대로 두고 새 비밀번호만 설정합니다.`
    pendingResetStudent.value = null
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '비밀번호를 초기화하지 못했습니다.')
  } finally {
    saving.value = false
  }
}

function addProduct() {
  if (!settings.value) return
  const displayOrder = Math.min(1, ...settings.value.products.map((product) => product.displayOrder)) - 1
  settings.value.products.unshift({
    productId: null,
    name: '새 상품',
    description: '상품 설명을 입력해 주세요.',
    price: 0,
    imageUrl: null,
    currentStock: 10,
    displayOrder,
  })
  void nextTick(() => {
    const editor = document.querySelector<HTMLElement>('[data-new-product="true"]')
    editor?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    editor?.querySelector<HTMLInputElement>('input')?.focus()
  })
}

function removeProduct(product: ProductSetting) {
  if (!settings.value) return
  if (product.productId && !window.confirm(`‘${product.name}’ 상품을 판매 종료할까요? 기존 구매내역은 보존됩니다.`)) {
    return
  }
  if (product.productId && !discontinuedProductIds.value.includes(product.productId)) {
    discontinuedProductIds.value.push(product.productId)
  }
  settings.value.products = settings.value.products.filter((item) => item !== product)
}

function addFundCostOption() {
  if (!settings.value) return
  const displayOrder = Math.max(0, ...settings.value.fundCostOptions.map((option) => option.displayOrder)) + 1
  const option: FundCostOptionSetting = {
    optionId: null,
    name: '새 공동기금 내역',
    amount: 1,
    enabled: true,
    displayOrder,
  }
  settings.value.fundCostOptions.push(option)
  void nextTick(() => {
    const editor = document.querySelector<HTMLElement>('[data-new-fund-option="true"]')
    editor?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    editor?.querySelector<HTMLInputElement>('input')?.focus()
  })
}

function readFileAsDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
}

async function selectProductImage(product: ProductSetting, event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  productImageError.value = ''
  if (!file.type.startsWith('image/')) {
    productImageError.value = '이미지 파일만 선택할 수 있습니다.'
    return
  }
  try {
    product.imageUrl = await readFileAsDataUrl(file)
    clearProductImageError(product)
  } catch {
    productImageError.value = '이미지 파일을 읽지 못했습니다.'
  }
}

function markProductImageFailed(product: ProductSetting) {
  failedProductImages.value = new Set([...failedProductImages.value, product])
}

function clearProductImageError(product: ProductSetting) {
  const next = new Set(failedProductImages.value)
  next.delete(product)
  failedProductImages.value = next
}
</script>

<template>
  <section class="teacher-management-workspace">
    <div v-if="loading" class="state-message">관리 정보를 불러오고 있습니다.</div>
    <template v-else>
      <header class="management-heading">
        <div>
          <h1>{{ view === 'STUDENTS' ? '학생 계정 관리' : '수업 설정' }}</h1>
          <p>{{ view === 'STUDENTS' ? '전학생, 결석, 늦은 참여와 재접속을 한 곳에서 관리합니다.' : '우리 반 수준에 맞게 콘텐츠와 경제 규칙을 조정합니다.' }}</p>
        </div>
        <button class="button-secondary" type="button" @click="load">최신 정보 불러오기</button>
      </header>

      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-if="noticeMessage" class="control-notice" role="status">{{ noticeMessage }}</p>

      <template v-if="view === 'STUDENTS'">
        <form class="student-add-panel" @submit.prevent="createStudent">
          <div><strong>새 학생 입장 코드 만들기</strong><span>추가한 학생은 현재 수업에 늦게 참여할 수 있습니다.</span></div>
          <label>학생 번호<input v-model.number="newStudentNumber" type="number" min="1" max="99" required></label>
          <label>닉네임<input v-model="newStudentNickname" maxlength="50" placeholder="첫 접속 때 정해도 됩니다"></label>
          <button type="submit" :disabled="saving">학생 추가</button>
        </form>

        <div v-if="!selectedStudent" class="managed-student-grid" aria-label="학생 계정 목록">
          <button
            v-for="student in students"
            :key="student.studentId"
            type="button"
            class="managed-student-card"
            @click="selectedStudentId = student.studentId"
          >
            <span class="managed-student-card__top">
              <span :class="['status-dot', { 'is-online': student.online }]" />
              <strong>{{ student.participantNumber }}번</strong>
              <span>{{ student.online ? '접속 중' : statusLabel(student.status) }}</span>
            </span>
            <strong class="managed-student-card__name">{{ student.nickname || '닉네임 미설정' }}</strong>
            <code>{{ student.participationCode }}</code>
            <small>{{ passwordStatusLabel(student) }}</small>
            <small>{{ student.lastSeenAt ? new Date(student.lastSeenAt).toLocaleString('ko-KR') : '접속 기록 없음' }}</small>
            <span class="managed-student-card__open">상세 설정 →</span>
          </button>
          <p v-if="students.length === 0" class="state-message">등록된 학생이 없습니다.</p>
        </div>

        <section v-else class="managed-student-detail" aria-labelledby="managed-student-detail-title">
          <header>
            <button class="button-secondary managed-student-detail__back" type="button" @click="selectedStudentId = null">← 학생 목록</button>
            <div>
              <p class="eyebrow">학생 상세 설정</p>
              <h2 id="managed-student-detail-title">{{ selectedStudent.participantNumber }}번 {{ selectedStudent.nickname || '닉네임 미설정' }}</h2>
              <code>{{ selectedStudent.participationCode }}</code>
            </div>
            <span :class="['managed-student-detail__connection', { 'is-online': selectedStudent.online }]">
              {{ selectedStudent.online ? '접속 중' : statusLabel(selectedStudent.status) }}
            </span>
          </header>
          <div class="managed-student-detail__body">
            <label>닉네임<input v-model="selectedStudent.nickname" maxlength="50" placeholder="미설정"></label>
            <label>상태<select v-model="selectedStudent.status"><option value="INVITED">입장 전</option><option value="ACTIVE">참여 중</option><option value="ABSENT">결석</option><option value="DISABLED">수업 제외</option></select></label>
            <label>1인1역 직업<input v-model.trim="selectedStudent.roleName" list="student-role-options" maxlength="50" :disabled="lockedRoleStudentIds.has(selectedStudent.studentId)" :placeholder="suggestedRoleName(selectedStudent.participantNumber) ? `추천: ${suggestedRoleName(selectedStudent.participantNumber)}` : '직업명을 입력하세요'"><small>{{ lockedRoleStudentIds.has(selectedStudent.studentId) ? '저장된 직업은 변경되지 않습니다.' : '최초 저장 후에는 변경할 수 없습니다.' }}</small></label>
            <datalist id="student-role-options"><option v-for="role in roleOptions" :key="role.participantNumber" :value="role.roleName">{{ role.participantNumber }}번</option></datalist>
            <dl>
              <div><dt>최근 접속</dt><dd>{{ selectedStudent.lastSeenAt ? new Date(selectedStudent.lastSeenAt).toLocaleString('ko-KR') : '접속 기록 없음' }}</dd></div>
              <div><dt>참여 코드</dt><dd>{{ selectedStudent.participationCode }}</dd></div>
              <div><dt>비밀번호</dt><dd>{{ selectedStudent.passwordConfigured ? '설정됨 (암호화 보호)' : selectedStudent.status === 'INVITED' ? '미설정' : '재설정 필요' }}</dd></div>
            </dl>
          </div>
          <footer>
            <button class="button-secondary" type="button" :disabled="saving" @click="pendingResetStudent = selectedStudent">비밀번호 초기화</button>
            <button type="button" :disabled="saving" @click="saveStudent(selectedStudent)">{{ saving ? '저장 중…' : '변경사항 저장' }}</button>
          </footer>
        </section>
      </template>

      <template v-else-if="settings">
        <div v-if="settings.editingLiveClass" class="live-edit-warning">진행 중인 수업입니다. 준비 상태인 퀴즈와 아직 확정되지 않은 경제 값만 신중하게 변경해 주세요.</div>
        <section class="class-identity-settings">
          <label>수업 제목<input v-model.trim="settings.roomName" maxlength="100" placeholder="예: 햇살반 경제교실"></label>
          <p>저장하면 교사·학생 화면의 수업 이름이 실시간으로 갱신됩니다.</p>
        </section>
        <nav class="management-tabs" aria-label="수업 설정 항목">
          <button v-for="tab in ([['ECONOMY','은행·경제'],['QUIZ','퀴즈'],['INVESTMENT','투자 상품'],['SHOP','상점 상품'],['FUND','공동기금']] as const)" :key="tab[0]" type="button" :class="{ 'is-active': settingsTab === tab[0] }" @click="settingsTab = tab[0]">{{ tab[1] }}</button>
        </nav>

        <section v-if="settingsTab === 'ECONOMY'" class="settings-form-grid">
          <label>초기 공동기금<input v-model.number="settings.economy.initialFundAmount" type="number" min="0" :disabled="settings.editingLiveClass"><span>비빔</span></label>
          <label>월급 지급 금액<input v-model.number="settings.economy.roleIncome" type="number" min="0"><span>비빔</span></label>
          <label>소득세율<input :value="formatRateInput(settings.economy.incomeTaxBps)" type="number" min="0" max="100" step="0.1" @input="settings.economy.incomeTaxBps = Number(($event.target as HTMLInputElement).value) * 100"><span>%</span></label>
          <label>예금 이율<input :value="formatRateInput(settings.economy.depositInterestBps)" type="number" min="0" max="100" step="0.1" @input="settings.economy.depositInterestBps = Number(($event.target as HTMLInputElement).value) * 100"><span>%</span></label>
          <label>금융 소득세율<input :value="formatRateInput(settings.economy.financialTaxBps)" type="number" min="0" max="100" step="0.1" @input="settings.economy.financialTaxBps = Number(($event.target as HTMLInputElement).value) * 100"><span>%</span></label>
          <label>예금 최소 금액<input v-model.number="settings.economy.depositMinAmount" type="number" min="1"><span>비빔</span></label>
          <label>예금 조정 단위<input v-model.number="settings.economy.depositUnit" type="number" min="1"><span>비빔</span></label>
          <label>투자 최소 금액<input v-model.number="settings.economy.investmentMinAmount" type="number" min="1"><span>비빔</span></label>
          <label>투자 조정 단위<input v-model.number="settings.economy.investmentUnit" type="number" min="1"><span>비빔</span></label>
          <label>1일차 정답 보상<input v-model.number="settings.economy.day1CorrectReward" type="number" min="0"><span>비빔</span></label>
          <label>2일차 정답 보상<input v-model.number="settings.economy.day2CorrectReward" type="number" min="0"><span>비빔</span></label>
        </section>

        <section v-else-if="settingsTab === 'QUIZ'" class="settings-stack">
          <div class="quiz-day-switch"><button v-for="day in [1,2]" :key="day" type="button" :class="{ 'is-active': selectedQuizDay === day }" @click="selectedQuizDay = day">{{ day }}일차 퀴즈</button></div>
          <article v-for="question in selectedQuiz?.questions" :key="question.questionId" class="settings-editor-card">
            <header><strong>{{ question.orderNo }}번 문제</strong><span>{{ selectedQuiz?.status === 'READY' ? '수정 가능' : '진행되어 잠김' }}</span></header>
            <label>문제<textarea v-model="question.prompt" rows="2" :disabled="selectedQuiz?.status !== 'READY'" /></label>
            <div class="quiz-option-editors"><label v-for="option in question.options" :key="option.id"><input v-model="question.correctAnswer" type="radio" :value="option.id" :disabled="selectedQuiz?.status !== 'READY'"><span>{{ option.id }}</span><input v-model="option.text" :disabled="selectedQuiz?.status !== 'READY'"></label></div>
            <label>해설<textarea v-model="question.explanation" rows="2" :disabled="selectedQuiz?.status !== 'READY'" /></label>
          </article>
        </section>

        <section v-else-if="settingsTab === 'INVESTMENT'" class="settings-stack">
          <article v-for="asset in settings.investments" :key="asset.assetId" class="settings-editor-card investment-editor">
            <header>
              <strong>{{ asset.name }}</strong>
              <span>{{ settings.investmentResultsLocked ? '학생 투자 확정으로 결과 잠김' : '학생 카드와 실제 정산에 반영' }}</span>
            </header>
            <div class="settings-form-grid">
              <label>상품명<input v-model="asset.name"></label>
              <label>위험도<select v-model="asset.riskLevel"><option value="LOW">낮음</option><option value="MEDIUM">보통</option><option value="HIGH">높음</option></select></label>
              <label>최소 예상 수익률<input :value="asset.minReturnBps / 100" type="number" step="0.1" @input="asset.minReturnBps = Number(($event.target as HTMLInputElement).value) * 100"><span>%</span></label>
              <label>최대 예상 수익률<input :value="asset.maxReturnBps / 100" type="number" step="0.1" @input="asset.maxReturnBps = Number(($event.target as HTMLInputElement).value) * 100"><span>%</span></label>
              <label class="investment-result-rate">결과 수익률<input :value="asset.actualReturnBps / 100" type="number" min="-100" max="1000" step="0.1" :disabled="settings.investmentResultsLocked" @input="asset.actualReturnBps = Number(($event.target as HTMLInputElement).value) * 100"><span>%</span></label>
              <div class="investment-result-preview" aria-live="polite">
                <small>결과값 미리보기</small>
                <strong>{{ investmentExamplePrincipal }}비빔 투자 → {{ investmentExamplePrincipal + investmentExampleProfit(asset.actualReturnBps) }}비빔</strong>
                <span>수익·손실 {{ signedBibim(investmentExampleProfit(asset.actualReturnBps)) }}</span>
              </div>
            </div>
            <p v-if="settings.investmentResultsLocked" class="investment-result-lock-notice">학생이 투자 금액을 확정해 결과 수익률은 더 이상 바꿀 수 없습니다.</p>
            <label>카드 요약<textarea v-model="asset.description" rows="2" /></label>
            <label>상세 투자 이야기<textarea v-model="asset.companyInfo" rows="8" /></label>
          </article>
        </section>

        <section v-else-if="settingsTab === 'SHOP'" class="settings-stack">
          <div class="settings-section-actions">
            <div><strong>상점 상품</strong><small>교사가 새 상품과 재고를 직접 준비할 수 있습니다.</small></div>
            <button type="button" @click="addProduct">+ 상품 추가</button>
          </div>
          <p v-if="productImageError" class="form-error" role="alert">{{ productImageError }}</p>
          <article
            v-for="product in settings.products"
            :key="product.productId ?? `new-${product.displayOrder}`"
            class="settings-editor-card"
            :data-new-product="product.productId ? undefined : 'true'"
          >
            <header>
              <strong>{{ product.name }}</strong>
              <div class="settings-product-card__actions">
                <span>{{ product.productId ? '등록 상품' : '새 상품' }}</span>
                <button class="button-secondary" type="button" @click="removeProduct(product)">
                  {{ product.productId ? '판매 종료' : '추가 취소' }}
                </button>
              </div>
            </header>
            <div class="settings-form-grid">
              <label>상품명<input v-model="product.name"></label>
              <label>가격<input v-model.number="product.price" type="number" min="0"><span>비빔</span></label>
              <label>재고<input v-model.number="product.currentStock" type="number" min="0" :disabled="Boolean(product.productId)"><span>개</span></label>
            </div>
            <label>상품 설명<textarea v-model="product.description" rows="2" /></label>
            <div class="product-image-inputs">
              <label class="product-file-picker">이미지 파일 선택<input type="file" accept="image/*" @change="selectProductImage(product, $event)"></label>
              <label>상품 이미지 주소<input v-model.trim="product.imageUrl" type="text" placeholder="https://example.com/product.jpg" @input="clearProductImageError(product)"></label>
              <button v-if="product.imageUrl" class="button-secondary" type="button" @click="product.imageUrl = null">이미지 제거</button>
            </div>
            <img v-if="product.imageUrl && !failedProductImages.has(product)" class="settings-product-preview" :src="product.imageUrl" :alt="`${product.name} 미리보기`" @error="markProductImageFailed(product)">
            <p v-else-if="product.imageUrl" class="product-image-fallback" role="status">이미지를 불러올 수 없어요. 파일 또는 주소를 다시 확인해 주세요.</p>
            <small>이미지 파일은 용량 제한 없이 선택할 수 있어요. · 현재 재고 {{ product.currentStock }}개</small>
          </article>
        </section>

        <section v-else class="settings-stack">
          <div class="settings-section-actions">
            <div><strong>공동기금 사용 내역</strong><small>2일차 공동기금 사용 단계에서 고를 내역과 금액을 준비합니다.</small></div>
            <button type="button" @click="addFundCostOption">+ 내역 추가</button>
          </div>
          <article
            v-for="option in settings.fundCostOptions"
            :key="option.optionId ?? `new-fund-${option.displayOrder}`"
            class="settings-editor-card fund-option-editor"
            :data-new-fund-option="option.optionId ? undefined : 'true'"
          >
            <header><strong>{{ option.name }}</strong><span>{{ option.enabled ? '사용 중' : '사용 안 함' }}</span></header>
            <div class="settings-form-grid">
              <label>내역 이름<input v-model.trim="option.name" maxlength="100" required></label>
              <label>필요 금액<input v-model.number="option.amount" type="number" min="1" required><span>비빔</span></label>
              <label class="settings-checkbox"><input v-model="option.enabled" type="checkbox"><span>학생 투표 준비 화면에서 사용</span></label>
            </div>
          </article>
        </section>

        <div class="settings-save-bar"><span>저장 전 값을 다시 확인해 주세요.</span><button type="button" @click="showSaveConfirm = true">변경사항 저장</button></div>
      </template>
    </template>
  </section>

  <div v-if="showSaveConfirm" class="modal-backdrop"><section class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="settings-confirm-title"><h2 id="settings-confirm-title">수업 설정을 저장할까요?</h2><p>저장한 내용은 학생 화면에 바로 반영될 수 있습니다.</p><p v-if="settings?.editingLiveClass" class="confirm-modal__warning">진행 중인 수업이므로 일부 학생의 화면이 바뀔 수 있어요.</p><div class="confirm-modal__actions"><button class="button-secondary" type="button" @click="showSaveConfirm = false">취소</button><button type="button" :disabled="saving" @click="confirmSaveSettings">저장하기</button></div></section></div>
  <div v-if="pendingResetStudent" class="modal-backdrop"><section class="confirm-modal" role="dialog" aria-modal="true"><h2>비밀번호를 초기화할까요?</h2><p>{{ pendingResetStudent.participantNumber }}번 학생은 닉네임을 그대로 유지하고 다음 입장 때 새 비밀번호만 설정합니다.</p><div class="confirm-modal__actions"><button class="button-secondary" type="button" @click="pendingResetStudent = null">취소</button><button type="button" :disabled="saving" @click="resetPassword">초기화</button></div></section></div>
</template>
