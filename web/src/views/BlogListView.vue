<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Star } from '@element-plus/icons-vue'
import { listHotBlogs } from '@/api'
import type { Blog } from '@/api'

const PAGE_SIZE = 10
const GRADIENTS = [
  'linear-gradient(135deg, #ffd9c7 0%, #ffb08a 100%)',
  'linear-gradient(135deg, #ffe7ba 0%, #ffc069 100%)',
  'linear-gradient(135deg, #d6e4ff 0%, #adc6ff 100%)',
  'linear-gradient(135deg, #d9f7be 0%, #95de64 100%)',
  'linear-gradient(135deg, #efdbff 0%, #b37feb 100%)',
  'linear-gradient(135deg, #ffd6e7 0%, #ff85c0 100%)'
]

const router = useRouter()
const blogs = ref<Blog[]>([])
const current = ref(1)
const hasNext = ref(false)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await listHotBlogs(current.value)
    blogs.value = data || []
    hasNext.value = (data?.length || 0) === PAGE_SIZE
  } catch {
    blogs.value = []
    hasNext.value = false
    ElMessage.error('笔记加载失败')
  } finally {
    loading.value = false
  }
}

function go(page: number) {
  if (page < 1 || (page > current.value && !hasNext.value)) {
    return
  }
  current.value = page
  load()
}

function coverStyle(blog: Blog) {
  const gradient = GRADIENTS[blog.id % GRADIENTS.length]
  const first = blog.images?.split(',')[0]?.trim()
  if (first && /^https?:\/\//.test(first)) {
    return { backgroundImage: `url("${first}"), ${gradient}` }
  }
  return { backgroundImage: gradient }
}

function summary(text?: string) {
  if (!text) {
    return '作者没有写正文，点开看看吧'
  }
  const clean = text.replace(/\s+/g, ' ').trim()
  return clean.length > 60 ? `${clean.slice(0, 60)}…` : clean
}

function authorName(blog: Blog) {
  return blog.name || '匿名用户'
}

function authorInitial(blog: Blog) {
  return authorName(blog).slice(0, 1)
}

function open(blog: Blog) {
  router.push({ name: 'blog-detail', params: { id: blog.id } })
}

onMounted(load)
</script>

<template>
  <section class="blogs">
    <header class="page-head">
      <div>
        <h1>探店笔记</h1>
        <p class="muted">按点赞排序，看看大家都在推荐什么</p>
      </div>
      <el-button @click="router.push({ name: 'home' })">去逛店铺</el-button>
    </header>

    <div v-if="loading" class="grid">
      <div v-for="i in 4" :key="i" class="card skeleton-card">
        <el-skeleton animated>
          <template #template>
            <el-skeleton-item variant="image" style="width: 100%; height: 160px" />
            <div style="padding: 16px">
              <el-skeleton-item variant="h3" style="width: 70%" />
              <el-skeleton-item variant="text" style="width: 95%; margin-top: 12px" />
              <el-skeleton-item variant="text" style="width: 80%; margin-top: 8px" />
            </div>
          </template>
        </el-skeleton>
      </div>
    </div>

    <template v-else>
      <div v-if="blogs.length" class="grid">
        <article
          v-for="blog in blogs"
          :key="blog.id"
          class="card blog-card"
          @click="open(blog)"
        >
          <div class="cover" :style="coverStyle(blog)">
            <span class="liked">
              <el-icon><Star /></el-icon>
              {{ blog.liked ?? 0 }}
            </span>
          </div>
          <div class="body">
            <h3>{{ blog.title }}</h3>
            <p class="summary">{{ summary(blog.content) }}</p>
            <footer class="meta">
              <div class="author">
                <el-avatar :size="24" :src="blog.icon">{{ authorInitial(blog) }}</el-avatar>
                <span>{{ authorName(blog) }}</span>
              </div>
              <span class="muted">{{ blog.comments ?? 0 }} 条评论</span>
            </footer>
          </div>
        </article>
      </div>

      <el-empty v-else description="还没有笔记，去店铺详情页写一篇吧" />
    </template>

    <div v-if="!loading && blogs.length" class="pager">
      <el-button :disabled="current === 1" @click="go(current - 1)">上一页</el-button>
      <span class="pager-num">第 {{ current }} 页</span>
      <el-button :disabled="!hasNext" @click="go(current + 1)">下一页</el-button>
    </div>
  </section>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-head h1 {
  margin: 0 0 6px;
  font-size: 26px;
}

.page-head p {
  margin: 0;
  font-size: 13px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.blog-card {
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.blog-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 24px rgba(31, 35, 41, 0.09);
}

.cover {
  position: relative;
  height: 160px;
  background-size: cover;
  background-position: center;
}

.liked {
  position: absolute;
  right: 12px;
  bottom: 12px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  font-size: 12px;
  color: #fff;
  background: rgba(0, 0, 0, 0.35);
  border-radius: 999px;
  backdrop-filter: blur(4px);
}

.liked .el-icon {
  color: #ffd666;
}

.body {
  padding: 14px 16px 16px;
}

.body h3 {
  margin: 0 0 8px;
  font-size: 16px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.summary {
  margin: 0 0 14px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-sub);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
}

.author {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-main);
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 28px;
}

.pager-num {
  font-size: 14px;
  color: var(--text-sub);
}

.skeleton-card {
  padding-bottom: 8px;
}

@media (max-width: 800px) {
  .grid {
    grid-template-columns: 1fr;
  }

  .page-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
