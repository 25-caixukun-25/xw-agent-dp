<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import AiAssistant from '@/components/AiAssistant.vue'

const userStore = useUserStore()
const router = useRouter()

onMounted(() => {
  userStore.loadUser()
})

function logout() {
  userStore.clear()
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="layout">
    <header class="header">
      <div class="container header-inner">
        <router-link to="/" class="brand">
          智觅生活
          <span class="brand-badge">AI</span>
        </router-link>

        <nav class="nav">
          <router-link to="/" exact-active-class="active">首页</router-link>
          <router-link to="/blogs" active-class="active">探店笔记</router-link>
        </nav>

        <div class="user-area">
          <template v-if="userStore.user">
            <el-avatar :size="28" :src="userStore.user.icon">
              {{ userStore.user.nickName?.slice(0, 1) }}
            </el-avatar>
            <span class="nickname">{{ userStore.user.nickName }}</span>
            <el-button link type="info" @click="logout">退出</el-button>
          </template>
        </div>
      </div>
    </header>

    <main class="main container">
      <router-view />
    </main>

    <footer class="footer">
      智觅生活 · Spring Boot 3.5 + Spring AI + Vue 3
    </footer>

    <AiAssistant />
  </div>
</template>

<style scoped>
.layout {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #fff;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.header-inner {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 32px;
}

.brand {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.brand-badge {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 6px;
  font-size: 12px;
  font-weight: 500;
  color: #fff;
  background: var(--brand);
  border-radius: 6px;
  vertical-align: middle;
}

.nav {
  display: flex;
  gap: 24px;
  font-size: 15px;
  color: var(--text-sub);
}

.nav a {
  padding: 6px 0;
  border-bottom: 2px solid transparent;
}

.nav a.active {
  color: var(--brand);
  border-bottom-color: var(--brand);
  font-weight: 500;
}

.user-area {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.nickname {
  color: var(--text-main);
}

.main {
  flex: 1;
  padding: 24px 0 40px;
}

.footer {
  padding: 24px 0;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
}
</style>
