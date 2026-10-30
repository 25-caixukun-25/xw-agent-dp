<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listShopTypes, listShopsByType } from '@/api'
import type { Shop, ShopType } from '@/api'
import { coverStyle } from '@/utils/image'

// 与后端 ShopServiceImpl.queryShopByType 的 SystemConstants.DEFAULT_PAGE_SIZE 保持一致
const PAGE_SIZE = 5

const route = useRoute()
const router = useRouter()

const types = ref<ShopType[]>([])
const activeTypeId = ref<number | null>(null)
const shops = ref<Shop[]>([])
const current = ref(1)
const hasNext = ref(false)
const loading = ref(false)
const sentinel = ref<HTMLElement | null>(null)
const loadingMore = ref(false)
let observer: IntersectionObserver | null = null

function currentTypeName() {
  return types.value.find((item) => item.id === activeTypeId.value)?.name || '全部'
}

async function loadTypes() {
  try {
    types.value = await listShopTypes()
    const fromQuery = Number(route.query.typeId)
    const matched = types.value.find((item) => item.id === fromQuery)
    activeTypeId.value = matched?.id ?? types.value[0]?.id ?? null
  } catch {
    ElMessage.error('分类加载失败，请检查后端服务')
  }
}

async function loadShops(reset = true) {
  if (activeTypeId.value == null) {
    return
  }
  if (reset) {
    loading.value = true
    current.value = 1
  } else {
    if (!hasNext.value || loadingMore.value) {
      return
    }
    loadingMore.value = true
  }
  try {
    const data = await listShopsByType({ typeId: activeTypeId.value, current: current.value })
    const list = data || []
    shops.value = reset ? list : [...shops.value, ...list]
    hasNext.value = list.length === PAGE_SIZE
  } catch {
    if (reset) {
      shops.value = []
      hasNext.value = false
    }
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function loadMore(depth = 0) {
  if (!hasNext.value || loading.value || loadingMore.value) {
    return
  }
  current.value += 1
  await loadShops(false)
  await nextTick()
  if (depth < 3 && hasNext.value) {
    const el = sentinel.value
    if (el && el.getBoundingClientRect().top < window.innerHeight) {
      loadMore(depth + 1)
    }
  }
}

function setupObserver() {
  observer?.disconnect()
  if (!sentinel.value) {
    return
  }
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting) {
        loadMore()
      }
    },
    { rootMargin: '240px' }
  )
  observer.observe(sentinel.value)
}

function selectType(id: number) {
  if (activeTypeId.value === id) {
    return
  }
  activeTypeId.value = id
  router.replace({ query: { typeId: String(id) } })
  loadShops(true)
}

function scoreText(shop: Shop) {
  return shop.score == null ? '暂无评分' : (shop.score / 10).toFixed(1)
}

function openShop(id: number) {
  router.push({ name: 'shop-detail', params: { id } })
}

function maybeFillViewport() {
  if (!hasNext.value || loading.value || loadingMore.value) {
    return
  }
  const el = sentinel.value
  if (!el) {
    return
  }
  if (el.getBoundingClientRect().top < window.innerHeight + 120) {
    loadMore()
  }
}

function onWindowScroll() {
  maybeFillViewport()
}

// 列表长度变化就评估一次，不依赖挂载时序
watch(
  () => shops.value.length,
  async () => {
    await nextTick()
    maybeFillViewport()
  }
)

onMounted(async () => {
  await loadTypes()
  await loadShops()
  await nextTick()
  setupObserver()
  maybeFillViewport()
  window.addEventListener('scroll', onWindowScroll, { passive: true })
})

onUnmounted(() => {
  observer?.disconnect()
  observer = null
  window.removeEventListener('scroll', onWindowScroll)
})
</script>

<template>
  <section class="shop-list">
    <div class="page-head">
      <div>
        <h1>{{ currentTypeName() }}</h1>
        <p class="muted">按分类浏览全部店铺，点开卡片查看详情与优惠券</p>
      </div>
      <el-button text type="primary" @click="router.push({ name: 'home' })">
        回到首页推荐
      </el-button>
    </div>

    <nav class="tabs">
      <button
        v-for="type in types"
        :key="type.id"
        class="tab"
        :class="{ active: type.id === activeTypeId }"
        @click="selectType(type.id)"
      >
        {{ type.name }}
      </button>
    </nav>

    <div v-if="loading" class="grid">
      <div v-for="i in 3" :key="i" class="card skeleton-card">
        <el-skeleton animated>
          <template #template>
            <el-skeleton-item variant="image" style="width: 100%; height: 150px" />
            <div style="padding: 16px">
              <el-skeleton-item variant="h3" style="width: 60%" />
              <el-skeleton-item variant="text" style="width: 90%; margin-top: 12px" />
            </div>
          </template>
        </el-skeleton>
      </div>
    </div>

    <template v-else>
      <div v-if="shops.length" class="grid">
        <article
          v-for="shop in shops"
          :key="shop.id"
          class="card shop-card"
          @click="openShop(shop.id)"
        >
          <div class="cover" :style="coverStyle(shop.images, shop.id)">
            <span class="cover-badge">{{ currentTypeName() }}</span>
          </div>
          <div class="body">
            <h3 class="name">{{ shop.name }}</h3>
            <div class="meta">
              <span class="score" :class="{ empty: shop.score == null }">
                {{ scoreText(shop) }} 分
              </span>
              <span class="price">人均 ¥{{ shop.avgPrice ?? '-' }}</span>
              <span class="sold">已售 {{ shop.sold ?? 0 }}</span>
            </div>
            <p class="addr">
              <span class="area">{{ shop.area || '杭州' }}</span>
              <span class="address">{{ shop.address }}</span>
            </p>
            <p v-if="shop.openHours" class="hours muted">营业 {{ shop.openHours }}</p>
          </div>
        </article>
      </div>

      <el-empty v-else description="这个分类下暂时没有店铺" />
    </template>

    <div v-if="!loading && shops.length" ref="sentinel" class="sentinel">
      <span v-if="loadingMore">正在加载更多…</span>
      <span v-else-if="!hasNext">已经到底啦，共 {{ shops.length }} 家店</span>
      <span v-else class="sentinel-more">
        继续下滑查看更多
        <el-button link type="primary" size="small" @click="loadMore()">加载更多</el-button>
      </span>
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
  letter-spacing: 0.5px;
}

.page-head p {
  margin: 0;
  font-size: 13px;
}

.tabs {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 6px;
  margin-bottom: 16px;
  scrollbar-width: none;
}

.tabs::-webkit-scrollbar {
  display: none;
}

.tab {
  flex: none;
  padding: 8px 18px;
  font-size: 14px;
  color: var(--text-sub);
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.18s ease;
}

.tab:hover {
  color: var(--brand);
  border-color: rgba(255, 102, 51, 0.4);
}

.tab.active {
  color: #fff;
  background: var(--brand);
  border-color: var(--brand);
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.shop-card {
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.shop-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 24px rgba(31, 35, 41, 0.09);
}

.cover {
  position: relative;
  height: 150px;
  background-size: cover;
  background-position: center;
}

.cover-badge {
  position: absolute;
  left: 12px;
  top: 12px;
  padding: 3px 10px;
  font-size: 12px;
  color: #fff;
  background: rgba(0, 0, 0, 0.35);
  border-radius: 999px;
  backdrop-filter: blur(4px);
}

.body {
  padding: 14px 16px 16px;
}

.name {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  margin-bottom: 8px;
}

.score {
  color: #f59e0b;
  font-weight: 600;
}

.score.empty {
  color: #9ca3af;
  font-weight: 400;
}

.price,
.sold {
  color: var(--text-sub);
}

.addr {
  margin: 0;
  font-size: 13px;
  color: var(--text-sub);
  display: flex;
  gap: 6px;
  white-space: nowrap;
  overflow: hidden;
}

.addr .area {
  flex: none;
  color: var(--brand);
}

.addr .address {
  overflow: hidden;
  text-overflow: ellipsis;
}

.hours {
  margin: 6px 0 0;
  font-size: 12px;
}

.sentinel {
  padding: 26px 0 6px;
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
}

.sentinel-more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.skeleton-card {
  padding-bottom: 8px;
}

@media (max-width: 1000px) {
  .grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
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
