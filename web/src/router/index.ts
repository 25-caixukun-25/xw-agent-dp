import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import DefaultLayout from '@/layouts/DefaultLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      component: DefaultLayout,
      children: [
        { path: '', name: 'home', component: () => import('@/views/HomeView.vue') },
        { path: 'shop/:id', name: 'shop-detail', component: () => import('@/views/ShopDetailView.vue') },
        {
          path: 'shops',
          name: 'shop-list',
          component: () => import('@/views/ShopListView.vue')
        },
        { path: 'blogs', name: 'blogs', component: () => import('@/views/BlogListView.vue') },
        { path: 'blogs/:id', name: 'blog-detail', component: () => import('@/views/BlogDetailView.vue') },
        {
          path: 'blog/new',
          name: 'blog-new',
          component: () => import('@/views/BlogEditView.vue')
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/ProfileView.vue')
        },
        {
          path: 'user/:id',
          name: 'other-profile',
          component: () => import('@/views/OtherProfileView.vue')
        }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (!to.meta.public && !userStore.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && userStore.token) {
    return { name: 'home' }
  }
  return true
})

export default router
