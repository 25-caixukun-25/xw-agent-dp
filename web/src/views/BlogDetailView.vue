<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ChatDotRound, Star } from '@element-plus/icons-vue'
import { getBlog } from '@/api'
import type { Blog } from '@/api'
import { useAssistantStore } from '@/stores/assistant'

const GRADIENTS = [
  'linear-gradient(135deg, #ffd9c7 0%, #ffb08a 100%)',
  'linear-gradient(135deg, #ffe7ba 0%, #ffc069 100%)',
  'linear-gradient(135deg, #d6e4ff 0%, #adc6ff 100%)',
  'linear-gradient(135deg, #d9f7be 0%, #95de64 100%)',
  'linear-gradient(135deg, #efdbff 0%, #b37feb 100%)'
]

const route = useRoute()
const router = useRouter()
const assistant = useAssistantStore()

const blog = ref<Blog | null>(null)
const loading = ref(true)
const loadError = ref(false)

const bannerStyle = computed(() => {
  const gradient = GRADIENTS[(blog.value?.id ?? 0) % GRADIENTS.length]
  const first = blog.value?.images?.split(',')[0]?.trim()
  if (first && /^https?:\/\//.test(first)) {
    return { backgroundImage: `url("${first}"), ${gradient}` }
  }
  return { backgroundImage: gradient }
})

const authorName = computed(() => blog.value?.name || '匿名用户')

function formatTime(value?: string) {
  if (!value) {
    return ''
  }
  return String(value).replace('T', ' ').slice(0, 16)
}

async function load(id: string) {
  loading.value = true
  loadError.value = false
  try {
    blog.value = await getBlog(id)
  } catch {
    blog.value = null
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function askAi() {
  if (!blog.value) {
    return
  }
  assistant.open(`这篇探店笔记讲了什么：${blog.value.title}`)
}

function openShop() {
  if (blog.value?.shopId) {
    router.push({ name: 'shop-detail', params: { id: blog.value.shopId } })
  }
}

watch(
  () => route.params.id,
  (id) => {
    if (id) {
      load(String(id))
    }
  }
)

onMounted(() => {
  const id = route.params.id
  if (id) {
    load(String(id))
  }
})
</script>

<template>
  <div class="blog-detail">
    <button class="back" type="button" @click="router.back()">
      <el-icon><ArrowLeft /></el-icon>
      返回
    </button>

    <div v-if="loading" class="card article">
      <el-skeleton animated>
        <template #template>
          <el-skeleton-item variant="image" style="width: 100%; height: 260px" />
          <div style="padding: 28px">
            <el-skeleton-item variant="h1" style="width: 60%" />
            <el-skeleton-item variant="text" style="width: 40%; margin-top: 16px" />
            <el-skeleton-item variant="text" style="width: 100%; margin-top: 24px" />
            <el-skeleton-item variant="text" style="width: 92%; margin-top: 10px" />
          </div>
        </template>
      </el-skeleton>
    </div>

    <div v-else-if="loadError" class="card">
      <el-empty description="笔记不存在或已被删除">
        <el-button type="primary" @click="router.push({ name: 'blogs' })">返回列表</el-button>
      </el-empty>
    </div>

    <article v-else-if="blog" class="card article">
      <div class="banner" :style="bannerStyle" />

      <div class="content-wrap">
        <h1 class="title">{{ blog.title }}</h1>

        <div class="meta">
          <div class="author">
            <el-avatar :size="32" :src="blog.icon">{{ authorName.slice(0, 1) }}</el-avatar>
            <div>
              <p class="name">{{ authorName }}</p>
              <p class="time muted">{{ formatTime(blog.createTime) }}</p>
            </div>
          </div>
          <div class="stats">
            <span><el-icon><Star /></el-icon>{{ blog.liked ?? 0 }}</span>
            <span><el-icon><ChatDotRound /></el-icon>{{ blog.comments ?? 0 }}</span>
          </div>
        </div>

        <div class="body">{{ blog.content || '作者没有填写正文。' }}</div>

        <footer class="actions">
          <el-button v-if="blog.shopId" type="primary" plain @click="openShop">
            查看关联店铺
          </el-button>
          <el-button @click="askAi">问问 AI</el-button>
          <el-button link @click="router.push({ name: 'blogs' })">更多笔记</el-button>
        </footer>
      </div>
    </article>
  </div>
</template>

<style scoped>
.blog-detail {
  max-width: 900px;
  margin: 0 auto;
}

.back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 12px;
  padding: 6px 10px;
  font-size: 14px;
  color: var(--text-sub);
  background: transparent;
  border: none;
  cursor: pointer;
}

.back:hover {
  color: var(--brand);
}

.article {
  overflow: hidden;
}

.banner {
  height: 280px;
  background-size: cover;
  background-position: center;
}

.content-wrap {
  padding: 28px 40px 34px;
}

.title {
  margin: 0 0 18px;
  font-size: 26px;
  line-height: 1.45;
  word-break: break-word;
}

.meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 18px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.author {
  display: flex;
  align-items: center;
  gap: 10px;
}

.name {
  margin: 0;
  font-size: 14px;
}

.time {
  margin: 2px 0 0;
  font-size: 12px;
}

.stats {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: var(--text-sub);
}

.stats span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.body {
  margin-top: 22px;
  font-size: 15px;
  line-height: 1.9;
  color: #33383f;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.actions {
  display: flex;
  gap: 12px;
  margin-top: 28px;
}

@media (max-width: 700px) {
  .content-wrap {
    padding: 20px;
  }

  .banner {
    height: 180px;
  }
}
</style>
