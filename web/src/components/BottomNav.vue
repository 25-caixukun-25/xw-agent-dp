<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { EditPen, HomeFilled, Menu, Notebook, User } from '@element-plus/icons-vue'

const route = useRoute()

const items = [
  { key: 'home', label: '首页', icon: HomeFilled, to: { name: 'home' } },
  { key: 'shop-list', label: '分类', icon: Menu, to: { name: 'shop-list' } },
  { key: 'blog-new', label: '发布', icon: EditPen, to: { name: 'blog-new' }, primary: true },
  { key: 'blogs', label: '笔记', icon: Notebook, to: { name: 'blogs' } },
  { key: 'profile', label: '我的', icon: User, to: { name: 'profile' } }
]

/** 详情页要高亮对应的入口，否则底部栏在二级页面全灭 */
const activeKey = computed(() => {
  const name = String(route.name || '')
  if (name === 'shop-detail') {
    return 'shop-list'
  }
  if (name === 'blog-detail') {
    return 'blogs'
  }
  if (name === 'other-profile') {
    return 'profile'
  }
  return name
})
</script>

<template>
  <nav class="bottom-nav">
    <router-link
      v-for="item in items"
      :key="item.key"
      :to="item.to"
      class="nav-item"
      :class="{ active: activeKey === item.key, primary: item.primary }"
    >
      <span class="icon">
        <el-icon :size="item.primary ? 22 : 19">
          <component :is="item.icon" />
        </el-icon>
      </span>
      <span class="label">{{ item.label }}</span>
    </router-link>
  </nav>
</template>

<style scoped>
.bottom-nav {
  position: fixed;
  left: 50%;
  bottom: 16px;
  z-index: 1500;
  transform: translateX(-50%);
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  width: min(560px, calc(100% - 32px));
  padding: 6px 8px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(0, 0, 0, 0.05);
  border-radius: 999px;
  box-shadow: 0 12px 32px rgba(31, 35, 41, 0.12);
  backdrop-filter: blur(12px);
}

.nav-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 2px 0 4px;
  font-size: 11px;
  color: var(--text-sub);
  transition: color 0.18s ease;
}

.nav-item:hover {
  color: var(--brand);
}

.icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 32px;
  border-radius: 10px;
  transition: background 0.18s ease;
}

.nav-item.active {
  color: var(--brand);
  font-weight: 500;
}

.nav-item.active .icon {
  background: var(--brand-soft);
}

/* 中间的发布入口做成凸起圆钮，视觉上对应原版底栏的加号 */
.nav-item.primary {
  position: relative;
  flex: 0 0 auto;
  width: 76px;
  margin-top: -26px;
}

.nav-item.primary .icon {
  width: 48px;
  height: 48px;
  color: #fff;
  background: var(--brand);
  border-radius: 50%;
  box-shadow: 0 8px 20px rgba(255, 102, 51, 0.42);
}

.nav-item.primary:hover .icon,
.nav-item.primary.active .icon {
  background: var(--brand-dark);
}

.nav-item.primary .label {
  margin-top: 2px;
  color: var(--brand);
}

@media (min-width: 641px) {
  /* 宽屏已有顶部导航，底部导航隐藏，保证任何尺寸下只出现一份导航 */
  .bottom-nav {
    display: none;
  }
}

@media (max-width: 640px) {
  .bottom-nav {
    left: 0;
    bottom: 0;
    width: 100%;
    transform: none;
    padding: 6px 4px calc(6px + env(safe-area-inset-bottom, 0px));
    border-radius: 16px 16px 0 0;
    border-bottom: none;
  }

  .nav-item.primary {
    margin-top: -22px;
  }

  .nav-item.primary .icon {
    width: 44px;
    height: 44px;
  }
}
</style>
