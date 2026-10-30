<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check, Location, Plus, Star } from '@element-plus/icons-vue'
import { followUser, getUser, getUserInfo, isFollowing, listBlogsOfUser } from '@/api'
import type { Blog, UserInfo } from '@/api'
import type { CurrentUser } from '@/stores/user'
import { useUserStore } from '@/stores/user'
import { imageUrl } from '@/utils/image'

const PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const user = ref<CurrentUser | null>(null)
const info = ref<UserInfo | null>(null)
const following = ref(false)
const followLoading = ref(false)
const blogs = ref<Blog[]>([])
const current = ref(1)
const hasNext = ref(false)
const loadingBlogs = ref(false)
const notFound = ref(false)

const targetId = computed(() => String(route.params.id || ''))
const isSelf = computed(() => String(userStore.user?.id ?? '') === targetId.value)

async function loadUser() {
  notFound.value = false
  try {
    user.value = await getUser(targetId.value)
    if (!user.value) {
      notFound.value = true
      return
    }
  } catch {
    notFound.value = true
    return
  }
  try {
    info.value = await getUserInfo(targetId.value)
  } catch {
    info.value = null
  }
  if (!isSelf.value) {
    try {
      following.value = await isFollowing(targetId.value)
    } catch {
      following.value = false
    }
  }
}

async function loadBlogs() {
  loadingBlogs.value = true
  try {
    const data = await listBlogsOfUser(targetId.value, current.value)
    blogs.value = data || []
    hasNext.value = (data?.length || 0) === PAGE_SIZE
  } catch {
    blogs.value = []
    hasNext.value = false
  } finally {
    loadingBlogs.value = false
  }
}

async function toggleFollow() {
  followLoading.value = true
  try {
    await followUser(targetId.value, !following.value)
    following.value = !following.value
    ElMessage.success(following.value ? '已关注' : '已取消关注')
    if (info.value) {
      info.value.fans = Math.max(0, (info.value.fans ?? 0) + (following.value ? 1 : -1))
    }
  } catch {
    // 拦截器已提示
  } finally {
    followLoading.value = false
  }
}

function go(page: number) {
  if (page < 1 || (page > current.value && !hasNext.value)) {
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

async function reload() {
  current.value = 1
  await userStore.loadUser()
  await loadUser()
  await loadBlogs()
}

watch(targetId, () => reload())

onMounted(reload)
</script>

<template>
  <section class="other-profile">
    <el-empty v-if="notFound" description="这位用户不存在或已注销">
      <el-button type="primary" @click="router.push({ name: 'home' })">回到首页</el-button>
    </el-empty>

    <div v-else class="layout">
      <aside class="side">
        <div class="card user-card">
          <el-avatar :size="72" :src="user?.icon">
            {{ user?.nickName?.slice(0, 1) || 'U' }}
          </el-avatar>
          <h2>{{ user?.nickName || '加载中…' }}</h2>
          <p class="uid">ID：{{ user?.id ?? '-' }}</p>

          <div class="stats">
            <div class="stat">
              <strong>{{ info?.followee ?? 0 }}</strong>
              <span>关注</span>
            </div>
            <div class="stat">
              <strong>{{ info?.fans ?? 0 }}</strong>
              <span>粉丝</span>
            </div>
          </div>

          <ul class="meta-list">
            <li>
              <el-icon><Location /></el-icon>
              {{ info?.city || '未填写城市' }}
            </li>
            <li class="introduce">{{ info?.introduce || '这个人很懒，还没有写介绍' }}</li>
          </ul>

          <div class="side-actions">
            <el-button
              v-if="isSelf"
              type="primary"
              plain
              @click="router.push({ name: 'profile' })"
            >
              这是你自己 · 去个人中心
            </el-button>
            <el-button
              v-else
              :type="following ? 'default' : 'primary'"
              :icon="following ? Check : Plus"
              :loading="followLoading"
              @click="toggleFollow"
            >
              {{ following ? '已关注' : '关注 TA' }}
            </el-button>
          </div>
        </div>
      </aside>

      <main class="content">
        <div class="section-head">
          <h3>TA 的笔记</h3>
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
                </div>
              </div>
            </article>
          </div>

          <el-empty v-else description="TA 还没有发布笔记" />
        </template>

        <div v-if="!loadingBlogs && blogs.length" class="pager">
          <el-button :disabled="current === 1" @click="go(current - 1)">上一页</el-button>
          <span class="pager-num">第 {{ current }} 页</span>
          <el-button :disabled="!hasNext" @click="go(current + 1)">下一页</el-button>
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

.user-card {
  padding: 24px 20px;
  text-align: center;
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
  margin-top: 16px;
}

.side-actions :deep(.el-button) {
  width: 100%;
  margin-left: 0;
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
  gap: 16px;
  margin-top: 24px;
}

.pager-num {
  font-size: 14px;
  color: var(--text-sub);
}

@media (max-width: 900px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
