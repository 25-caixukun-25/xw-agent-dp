<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { EditPen, Location, Star, TrophyBase } from '@element-plus/icons-vue'
import { getSignCount, getUserInfo, listMyBlogs, signToday } from '@/api'
import type { Blog, UserInfo } from '@/api'
import { useUserStore } from '@/stores/user'
import { imageList, imageUrl } from '@/utils/image'

const PAGE_SIZE = 10

const router = useRouter()
const userStore = useUserStore()

const info = ref<UserInfo | null>(null)
const signDays = ref(0)
const signing = ref(false)
const blogs = ref<Blog[]>([])
const current = ref(1)
const hasNext = ref(false)
const loadingBlogs = ref(false)

async function loadInfo() {
  const userId = userStore.user?.id
  if (!userId) {
    return
  }
  try {
    info.value = await getUserInfo(userId)
  } catch {
    info.value = null
  }
  try {
    signDays.value = await getSignCount()
  } catch {
    signDays.value = 0
  }
}

async function loadBlogs() {
  loadingBlogs.value = true
  try {
    const data = await listMyBlogs(current.value)
    blogs.value = data || []
    hasNext.value = (data?.length || 0) === PAGE_SIZE
  } catch {
    blogs.value = []
    hasNext.value = false
  } finally {
    loadingBlogs.value = false
  }
}

async function handleSign() {
  signing.value = true
  try {
    await signToday()
    signDays.value = await getSignCount()
    ElMessage.success(`签到成功，已连续签到 ${signDays.value} 天`)
  } catch {
    // 拦截器已提示
  } finally {
    signing.value = false
  }
}

/**
 * 后端 /blog/of/me 不返回总数，用「已加载条数 + 是否还有下一页」推算总条数，
 * 让分页器能显示页码
 */
const blogTotal = computed(() => {
  const loaded = (current.value - 1) * PAGE_SIZE + blogs.value.length
  return hasNext.value ? loaded + PAGE_SIZE : loaded
})

function go(page: number) {
  if (page === current.value || page < 1) {
    return
  }
  current.value = page
  loadBlogs()
}

function openBlog(id: number) {
  router.push({ name: 'blog-detail', params: { id } })
}

function coverOf(blog: Blog) {
  return imageUrl(blog.images)
}

onMounted(async () => {
  await userStore.loadUser()
  await loadInfo()
  await loadBlogs()
})
</script>

<template>
  <section class="profile">
    <div class="layout">
      <aside class="side">
        <div class="card user-card">
          <div class="avatar-wrap">
            <el-avatar :size="72" :src="userStore.user?.icon">
              {{ userStore.user?.nickName?.slice(0, 1) || 'U' }}
            </el-avatar>
          </div>
          <h2>{{ userStore.user?.nickName || '未登录用户' }}</h2>
          <p class="uid">ID：{{ userStore.user?.id ?? '-' }}</p>

          <div class="stats">
            <div class="stat">
              <strong>{{ info?.followee ?? 0 }}</strong>
              <span>关注</span>
            </div>
            <div class="stat">
              <strong>{{ info?.fans ?? 0 }}</strong>
              <span>粉丝</span>
            </div>
            <div class="stat">
              <strong>{{ info?.credits ?? 0 }}</strong>
              <span>积分</span>
            </div>
          </div>

          <ul class="meta-list">
            <li>
              <el-icon><Location /></el-icon>
              {{ info?.city || '未填写城市' }}
            </li>
            <li>
              <el-icon><TrophyBase /></el-icon>
              Lv.{{ info?.level ?? 1 }} 会员
            </li>
            <li class="introduce">{{ info?.introduce || '这个人很懒，还没有写介绍' }}</li>
          </ul>

          <div class="side-actions">
            <el-button type="primary" :icon="EditPen" @click="router.push({ name: 'blog-new' })">
              发布笔记
            </el-button>
            <el-button @click="router.push({ name: 'blogs' })">逛逛笔记</el-button>
          </div>
        </div>

        <div class="card sign-card">
          <div class="sign-head">
            <div>
              <strong>每日签到</strong>
              <p class="muted">已连续签到 {{ signDays }} 天</p>
            </div>
            <el-button type="primary" :loading="signing" round @click="handleSign">
              签到
            </el-button>
          </div>
          <p class="sign-tip">签到数据用 Redis BitMap 按年存储，同一天重复签到不会叠加</p>
        </div>
      </aside>

      <main class="content">
        <div class="section-head">
          <h3>我的笔记</h3>
          <span class="muted">共 {{ blogs.length }} 条</span>
        </div>

        <div v-if="loadingBlogs" class="blog-list">
          <div v-for="i in 3" :key="i" class="card blog-item">
            <el-skeleton :rows="2" animated />
          </div>
        </div>

        <template v-else>
          <div v-if="blogs.length" class="blog-list">
            <article
              v-for="blog in blogs"
              :key="blog.id"
              class="card blog-item"
              @click="openBlog(blog.id)"
            >
              <div
                v-if="coverOf(blog)"
                class="thumb"
                :style="{ backgroundImage: `url('${coverOf(blog)}')` }"
              />
              <div v-else class="thumb thumb-empty">
                <el-icon :size="20"><Star /></el-icon>
              </div>
              <div class="blog-body">
                <h4>{{ blog.title }}</h4>
                <p class="excerpt">{{ blog.content || '暂无正文' }}</p>
                <div class="blog-meta">
                  <span>{{ blog.createTime?.replace('T', ' ').slice(0, 16) || '' }}</span>
                  <span>❤ {{ blog.liked ?? 0 }}</span>
                  <span>💬 {{ blog.comments ?? 0 }}</span>
                  <span v-if="imageList(blog.images).length">
                    🖼 {{ imageList(blog.images).length }} 张
                  </span>
                </div>
              </div>
            </article>
          </div>

          <el-empty v-else description="还没有发布过笔记">
            <el-button type="primary" @click="router.push({ name: 'blog-new' })">
              写第一篇笔记
            </el-button>
          </el-empty>
        </template>

        <div v-if="!loadingBlogs && blogs.length" class="pager">
          <el-pagination
            background
            layout="prev, pager, next"
            :current-page="current"
            :page-size="PAGE_SIZE"
            :total="blogTotal"
            @current-change="go"
          />
        </div>
      </main>
    </div>
  </section>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 20px;
  align-items: start;
}

.side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.user-card {
  padding: 24px 20px;
  text-align: center;
}

.avatar-wrap {
  display: flex;
  justify-content: center;
}

.user-card h2 {
  margin: 12px 0 4px;
  font-size: 18px;
}

.uid {
  margin: 0;
  font-size: 12px;
  color: #9ca3af;
}

.stats {
  display: flex;
  justify-content: space-around;
  margin: 18px 0;
  padding: 14px 0;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat strong {
  font-size: 18px;
  color: var(--brand);
}

.stat span {
  font-size: 12px;
  color: var(--text-sub);
}

.meta-list {
  margin: 0;
  padding: 0;
  list-style: none;
  text-align: left;
}

.meta-list li {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--text-sub);
}

.meta-list .introduce {
  display: block;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed rgba(0, 0, 0, 0.08);
  line-height: 1.7;
}

.side-actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}

.side-actions :deep(.el-button) {
  flex: 1;
  margin-left: 0;
}

.sign-card {
  padding: 18px 20px;
}

.sign-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.sign-head strong {
  font-size: 15px;
}

.sign-head p {
  margin: 4px 0 0;
  font-size: 12px;
}

.sign-tip {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #9ca3af;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

.section-head h3 {
  margin: 0;
  font-size: 18px;
}

.section-head .muted {
  font-size: 13px;
}

.blog-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.blog-item {
  display: flex;
  gap: 14px;
  padding: 14px;
  cursor: pointer;
  transition: box-shadow 0.18s ease, transform 0.18s ease;
}

.blog-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(31, 35, 41, 0.08);
}

.thumb {
  flex: none;
  width: 92px;
  height: 92px;
  border-radius: 10px;
  background-size: cover;
  background-position: center;
}

.thumb-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #cbd5e1;
  background: #f1f5f9;
}

.blog-body {
  min-width: 0;
  flex: 1;
}

.blog-body h4 {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.excerpt {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-sub);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.blog-meta {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: #9ca3af;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 24px;
}

@media (max-width: 900px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
