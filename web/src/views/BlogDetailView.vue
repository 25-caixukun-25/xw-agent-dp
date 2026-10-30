<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ChatDotRound, Star } from '@element-plus/icons-vue'
import { createComment, getBlog, likeBlog, listComments } from '@/api'
import type { Blog, BlogComment } from '@/api'
import { useAssistantStore } from '@/stores/assistant'
import { imageList, imageUrl } from '@/utils/image'

const GRADIENTS = [
  'linear-gradient(135deg, #ffd9c7 0%, #ffb08a 100%)',
  'linear-gradient(135deg, #ffe7ba 0%, #ffc069 100%)',
  'linear-gradient(135deg, #d6e4ff 0%, #adc6ff 100%)',
  'linear-gradient(135deg, #d9f7be 0%, #95de64 100%)',
  'linear-gradient(135deg, #efdbff 0%, #b37feb 100%)'
]
const COMMENT_PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()
const assistant = useAssistantStore()

const blog = ref<Blog | null>(null)
const loading = ref(true)
const loadError = ref(false)
const liking = ref(false)

const comments = ref<BlogComment[]>([])
const commentCurrent = ref(1)
const commentHasNext = ref(false)
const commentLoading = ref(false)
const commentInput = ref('')
const sendingComment = ref(false)

const bannerStyle = computed(() => {
  const gradient = GRADIENTS[(blog.value?.id ?? 0) % GRADIENTS.length]
  const url = imageUrl(blog.value?.images)
  return url ? { backgroundImage: `url("${url}"), ${gradient}` } : { backgroundImage: gradient }
})

const gallery = computed(() => imageList(blog.value?.images).slice(1, 7))
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

async function loadComments(reset = true) {
  if (!blog.value) {
    return
  }
  if (reset) {
    commentCurrent.value = 1
    commentLoading.value = true
  } else {
    if (!commentHasNext.value) {
      return
    }
    commentLoading.value = true
  }
  try {
    const data = await listComments(blog.value.id, commentCurrent.value)
    const list = data || []
    comments.value = reset ? list : [...comments.value, ...list]
    commentHasNext.value = list.length === COMMENT_PAGE_SIZE
  } catch {
    if (reset) {
      comments.value = []
    }
  } finally {
    commentLoading.value = false
  }
}

function loadMoreComments() {
  if (commentLoading.value || !commentHasNext.value) {
    return
  }
  commentCurrent.value += 1
  loadComments(false)
}

async function toggleLike() {
  if (!blog.value || liking.value) {
    return
  }
  liking.value = true
  try {
    await likeBlog(blog.value.id)
    const next = !blog.value.isLike
    blog.value.isLike = next
    blog.value.liked = Math.max(0, (blog.value.liked ?? 0) + (next ? 1 : -1))
  } catch {
    // 未登录时拦截器会跳转登录
  } finally {
    liking.value = false
  }
}

async function submitComment() {
  const content = commentInput.value.trim()
  if (!content || !blog.value) {
    return
  }
  sendingComment.value = true
  try {
    await createComment(blog.value.id, content)
    commentInput.value = ''
    ElMessage.success('评论成功')
    if (blog.value) {
      blog.value.comments = (blog.value.comments ?? 0) + 1
    }
    await loadComments(true)
  } catch {
    // 拦截器已提示
  } finally {
    sendingComment.value = false
  }
}

function openAuthor(userId?: number) {
  if (userId) {
    router.push({ name: 'other-profile', params: { id: userId } })
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

async function reload(id: string) {
  await load(id)
  await loadComments(true)
}

watch(
  () => route.params.id,
  (id) => {
    if (id) {
      reload(String(id))
    }
  }
)

onMounted(() => {
  const id = route.params.id
  if (id) {
    reload(String(id))
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

    <template v-else-if="blog">
      <article class="card article">
        <div class="banner" :style="bannerStyle" />

        <div class="content-wrap">
          <h1 class="title">{{ blog.title }}</h1>

          <div class="meta">
            <div class="author" @click="openAuthor(blog.userId)">
              <el-avatar :size="32" :src="blog.icon">{{ authorName.slice(0, 1) }}</el-avatar>
              <div>
                <p class="name">{{ authorName }}</p>
                <p class="time muted">{{ formatTime(blog.createTime) }}</p>
              </div>
            </div>
            <div class="actions-inline">
              <button
                class="like-btn"
                type="button"
                :class="{ active: blog.isLike }"
                :disabled="liking"
                @click="toggleLike"
              >
                <el-icon><Star /></el-icon>
                {{ blog.isLike ? '已点赞' : '点赞' }} {{ blog.liked ?? 0 }}
              </button>
              <span class="comment-count">
                <el-icon><ChatDotRound /></el-icon>
                {{ blog.comments ?? 0 }}
              </span>
            </div>
          </div>

          <div class="body">{{ blog.content || '作者没有填写正文。' }}</div>

          <div v-if="gallery.length" class="gallery">
            <img v-for="(image, index) in gallery" :key="index" :src="image" alt="笔记配图" />
          </div>

          <footer class="actions">
            <el-button v-if="blog.shopId" type="primary" plain @click="openShop">
              查看关联店铺
            </el-button>
            <el-button @click="askAi">问问 AI</el-button>
            <el-button link @click="router.push({ name: 'blogs' })">更多笔记</el-button>
          </footer>
        </div>
      </article>

      <section class="card comments">
        <header class="comment-head">
          <h2>评论 {{ blog.comments ?? 0 }}</h2>
          <span class="muted">友善交流，说点有用的</span>
        </header>

        <div class="comment-form">
          <el-input
            v-model="commentInput"
            type="textarea"
            :rows="2"
            resize="none"
            maxlength="255"
            show-word-limit
            placeholder="说点什么…"
          />
          <el-button
            type="primary"
            :loading="sendingComment"
            :disabled="!commentInput.trim()"
            @click="submitComment"
          >
            发表评论
          </el-button>
        </div>

        <div v-if="comments.length" class="comment-list">
          <article v-for="item in comments" :key="item.id" class="comment-item">
            <el-avatar
              :size="34"
              :src="item.icon"
              class="comment-avatar"
              @click="openAuthor(item.userId)"
            >
              {{ (item.name || '匿').slice(0, 1) }}
            </el-avatar>
            <div class="comment-body">
              <div class="comment-meta">
                <span class="comment-name" @click="openAuthor(item.userId)">
                  {{ item.name || '匿名用户' }}
                </span>
                <span class="muted">{{ formatTime(item.createTime) }}</span>
              </div>
              <p class="comment-text">{{ item.content }}</p>
            </div>
          </article>
        </div>

        <div v-else-if="!commentLoading" class="comment-empty">
          <el-empty description="还没有评论，来抢沙发" :image-size="70" />
        </div>

        <div class="comment-more">
          <el-button v-if="commentHasNext" text :loading="commentLoading" @click="loadMoreComments">
            加载更多评论
          </el-button>
          <span v-else-if="comments.length" class="muted">已经到底了</span>
        </div>
      </section>
    </template>
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
  gap: 16px;
  padding-bottom: 18px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.author {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.author:hover .name {
  color: var(--brand);
}

.name {
  margin: 0;
  font-size: 14px;
}

.time {
  margin: 2px 0 0;
  font-size: 12px;
}

.actions-inline {
  display: flex;
  align-items: center;
  gap: 16px;
}

.like-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  font-size: 13px;
  color: var(--text-sub);
  background: #f7f8fa;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.18s ease;
}

.like-btn:hover {
  color: var(--brand);
  border-color: rgba(255, 102, 51, 0.4);
}

.like-btn.active {
  color: #fff;
  background: var(--brand);
  border-color: var(--brand);
}

.comment-count {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-sub);
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

.gallery {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 18px;
}

.gallery img {
  width: 100%;
  height: 150px;
  object-fit: cover;
  border-radius: 10px;
}

.actions {
  display: flex;
  gap: 12px;
  margin-top: 28px;
}

.comments {
  margin-top: 20px;
  padding: 22px 32px 26px;
}

.comment-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}

.comment-head h2 {
  margin: 0;
  font-size: 18px;
}

.comment-head span {
  font-size: 12px;
}

.comment-form {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 20px;
}

.comment-form :deep(.el-textarea) {
  flex: 1;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.comment-item {
  display: flex;
  gap: 12px;
}

.comment-avatar {
  flex: none;
  cursor: pointer;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-meta {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 4px;
}

.comment-name {
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.comment-name:hover {
  color: var(--brand);
}

.comment-meta .muted {
  font-size: 12px;
  color: #9ca3af;
}

.comment-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.comment-empty {
  padding: 6px 0 10px;
}

.comment-more {
  margin-top: 16px;
  text-align: center;
  font-size: 12px;
}

@media (max-width: 700px) {
  .content-wrap {
    padding: 20px;
  }

  .banner {
    height: 180px;
  }

  .comments {
    padding: 18px 20px 22px;
  }

  .meta {
    flex-direction: column;
    align-items: flex-start;
  }

  .comment-form {
    flex-direction: column;
    align-items: stretch;
  }

  .gallery {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
