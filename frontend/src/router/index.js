import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
    meta: { guest: true }
  },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Chat',
        component: () => import('../views/ChatView.vue')
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/NotFoundView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

let guestInitPromise = null

router.beforeEach(async (to, from, next) => {
  const sessionId = localStorage.getItem('sessionId')

  if (to.meta.requiresAuth && !sessionId) {
    // trigger guest session creation, then proceed
    if (!guestInitPromise) {
      const { useAuthStore } = await import('../stores/auth')
      guestInitPromise = useAuthStore().initGuestSession()
    }
    await guestInitPromise
    next()
  } else if (to.meta.guest && sessionId && to.query.register !== 'true') {
    next({ name: 'Chat' })
  } else {
    next()
  }
})

export default router
