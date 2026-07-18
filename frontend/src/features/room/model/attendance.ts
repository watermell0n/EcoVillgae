import type { TeacherRoomStudent } from '../api/room'

export function getLobbyAttendance(students: TeacherRoomStudent[]) {
  return {
    notJoined: students.filter((student) => (
      student.status !== 'ACTIVE' && student.status !== 'ABSENT'
    )),
    disconnected: students.filter((student) => (
      student.status === 'ACTIVE' && !student.online
    )),
  }
}

