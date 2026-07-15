import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('../../pages/public/HomePage.vue'),
    },
    {
      path: '/student/join',
      name: 'student-join',
      component: () => import('../../pages/student/StudentJoinPage.vue'),
    },
    {
      path: '/student/setup',
      name: 'student-setup',
      component: () => import('../../pages/student/StudentSetupPage.vue'),
    },
    {
      path: '/student/login',
      name: 'student-login',
      component: () => import('../../pages/student/StudentLoginPage.vue'),
    },
    {
      path: '/student/room/:roomId',
      name: 'student-room',
      component: () => import('../../pages/student/StudentRoomPage.vue'),
    },
    {
      path: '/teacher/login',
      name: 'teacher-login',
      component: () => import('../../pages/teacher/TeacherLoginPage.vue'),
    },
    {
      path: '/teacher/rooms',
      name: 'teacher-rooms',
      component: () => import('../../pages/teacher/TeacherRoomsPage.vue'),
    },
  ],
})

export default router
