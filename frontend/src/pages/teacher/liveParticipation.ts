import type { TeacherStudentDay1Summary } from '../../features/day1/api/day1'
import type { TeacherStudentFlow } from '../../features/flow/api/teacherFlow'
import type { TeacherQuizStudent } from '../../features/quiz/api/quiz'

export function getConnectedQuizCounts(students: TeacherQuizStudent[]) {
  const connected = students.filter((student) => student.online)
  return {
    total: connected.length,
    submitted: connected.filter((student) => student.submissionStatus === 'SUBMITTED').length,
    inProgress: connected.filter((student) => student.submissionStatus === 'IN_PROGRESS').length,
    notStarted: connected.filter((student) => student.submissionStatus === 'NOT_STARTED').length,
    students: connected,
  }
}

export function getConnectedDay1Counts(students: TeacherStudentDay1Summary[]) {
  const connected = students.filter((student) => student.online)
  return {
    total: connected.length,
    active: connected.filter((student) => !student.inactive).length,
    depositConfirmed: connected.filter((student) => student.depositConfirmed).length,
    investmentConfirmed: connected.filter((student) => student.investmentConfirmed).length,
    purchaseParticipants: connected.filter((student) => student.purchased).length,
    inactive: connected.filter((student) => student.inactive).length,
  }
}

export function getStudentsNotAtCurrentActivity(
  students: TeacherStudentFlow[],
  currentStage: string,
) {
  return students.filter((student) => (
    student.studentStatus === 'ACTIVE'
    && student.flow !== null
    && student.flow.flowMode !== 'LOCKED'
    && student.flow.effectiveStage !== currentStage
  ))
}
