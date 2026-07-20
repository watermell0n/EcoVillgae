import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(_to, _from, savedPosition) {
    return savedPosition ?? { top: 0 }
  },
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
    {
      path: '/teacher/rooms/:roomId/control',
      name: 'teacher-room-control',
      component: () => import('../../pages/teacher/TeacherRoomControlPage.vue'),
    },
    {
      path: '/admin/login',
      name: 'admin-login',
      component: () => import('../../pages/admin/AdminLoginPage.vue'),
    },
    {
      path: '/admin',
      name: 'admin-dashboard',
      component: () => import('../../pages/admin/AdminDashboardPage.vue'),
    },
  ],
})

export default router
