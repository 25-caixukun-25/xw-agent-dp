<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Location } from '@element-plus/icons-vue'
import { listShopTypes, listShopsByType } from '@/api'
import type { Shop, ShopType } from '@/api'

const PAGE_SIZE = 5
const DEMO_COORDS = { x: 120.149993, y: 30.334229 }
const GRADIENTS = [
  'linear-gradient(135deg, #ffd9c7 0%, #ffb08a 100%)',
  'linear-gradient(135deg, #ffe7ba 0%, #ffc069 100%)',
  'linear-gradient(135deg, #d6e4ff 0%, #adc6ff 100%)',
  'linear-gradient(135deg, #d9f7be 0%, #95de64 100%)',
  'linear-gradient(135deg, #efdbff 0%, #b37feb 100%)',
  'linear-gradient(135deg, #ffd6e7 0%, #ff85c0 100%)'
]

const router = useRouter()

const types = ref<ShopType[]>([])
const activeTypeId = ref<number | null>(null)
const shops = ref<Shop[]>([])
const current = ref(1)
const hasNext = ref(false)
const loading = ref(false)
const typesLoading = ref(true)
const nearby = ref(false)
const coords = ref<{ x: number; y: number } | null>(null)

async function loadTypes() {
  typesLoading.value = true
  try {
    types.value = await listShopTypes()
    if (types.value.length > 0) {
      activeTypeId.value = types.value[0].id
    }
  } catch {
    ElMessage.error('分类加载失败，请检查后端服务')
  } finally {
    typesLoading.value = false
  }
}

async function loadShops() {
  if (activeTypeId.value == null) {
    return
  }
  loading.value = true
  try {
    const data = await listShopsByType({
      typeId: activeTypeId.value,
      current: current.value,
      ...(nearby.value && coords.value ? { x: coords.value.x, y: coords.value.y } : {})
    })
    shops.value = data || []
    hasNext.value = (data?.length || 0) === PAGE_SIZE
  } catch {
    shops.value = []
    hasNext.value = false
  } finally {
    loading.value = false
  }
}

function selectType(id: number) {
  if (activeTypeId.value === id) {
    return
  }
  activeTypeId.value = id
  current.value = 1
  loadShops()
}

function go(page: number) {
  if (page < 1 || (page > current.value && !hasNext.value)) {
    return
  }
  current.value = page
  loadShops()
}

function useDemoLocation() {
  coords.value = DEMO_COORDS
  nearby.value = true
  current.value = 1
  loadShops()
}

function onNearbyChange(value: boolean) {
  if (!value) {
    coords.value = null
    current.value = 1
    loadShops()
    return
  }
  if (!navigator.geolocation) {
    ElMessage.warning('当前浏览器不支持定位，已切换为演示位置')
    useDemoLocation()
    return
  }
  navigator.geolocation.getCurrentPosition(
    (position) => {
      coords.value = { x: position.coords.longitude, y: position.coords.latitude }
      current.value = 1
      loadShops()
    },
    () => {
      ElMessage.warning('定位失败或未授权，已切换为演示位置（杭州西湖）')
      useDemoLocation()
    },
    { timeout: 8000 }
  )
}

function coverStyle(shop: Shop) {
  const first = shop.images?.split(',')[0]?.trim()
  const gradient = GRADIENTS[shop.id % GRADIENTS.length]
  if (first && /^https?:\/\//.test(first)) {
    return { backgroundImage: `url("${first}"), ${gradient}` }
  }
  return { backgroundImage: gradient }
}

function scoreText(shop: Shop) {
  return shop.score == null ? '暂无评分' : (shop.score / 10).toFixed(1)
}

function distanceText(shop: Shop) {
  if (shop.distance == null) {
    return ''
  }
  return shop.distance < 1000 ? `${Math.round(shop.distance)} m` : `${(shop.distance / 1000).toFixed(1)} km`
}

function openShop(id: number) {
  router.push({ name: 'shop-detail', params: { id } })
}

onMounted(async () => {
  await loadTypes()
  await loadShops()
})
</script>

<template>
  <section class="home">
    <div class="page-head">
      <div>
        <h1>发现好店</h1>
        <p class="muted">杭州 · 共 {{ types.length }} 个分类，点开卡片看详情</p>
      </div>
      <div class="nearby">
        <el-icon><Location /></el-icon>
        <span>附近 5 公里</span>
        <el-switch v-model="nearby" :disabled="loading" @change="onNearbyChange" />
      </div>
    </div>

    <div v-if="typesLoading" class="tabs-skeleton">
      <el-skeleton :rows="1" animated />
    </div>
    <nav v-else class="tabs">
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

    <div v-if="nearby && coords" class="filter-tip">
      已按距离排序 · 坐标 {{ coords.x.toFixed(4) }}, {{ coords.y.toFixed(4) }}
      <el-button link type="primary" @click="onNearbyChange(false)">取消</el-button>
    </div>

    <div v-if="loading" class="grid">
      <div v-for="i in 6" :key="i" class="card skeleton-card">
        <el-skeleton animated>
          <template #template>
            <el-skeleton-item variant="image" style="width: 100%; height: 150px" />
            <div style="padding: 16px">
              <el-skeleton-item variant="h3" style="width: 60%" />
              <el-skeleton-item variant="text" style="width: 90%; margin-top: 12px" />
              <el-skeleton-item variant="text" style="width: 70%; margin-top: 8px" />
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
          <div class="cover" :style="coverStyle(shop)">
            <span class="cover-badge">{{ types.find((t) => t.id === shop.typeId)?.name }}</span>
            <span v-if="distanceText(shop)" class="cover-distance">{{ distanceText(shop) }}</span>
          </div>
          <div class="body">
            <h3 class="name">{{ shop.name }}</h3>
            <div class="meta">
              <span class="score" :class="{ empty: shop.score == null }">{{ scoreText(shop) }} 分</span>
              <span class="price">人均 ¥{{ shop.avgPrice ?? '-' }}</span>
            </div>
            <p class="addr">
              <span class="area">{{ shop.area || '杭州' }}</span>
              <span class="address">{{ shop.address }}</span>
            </p>
            <p class="hours muted" v-if="shop.openHours">营业 {{ shop.openHours }}</p>
          </div>
        </article>
      </div>

      <el-empty v-else description="这个分类下暂时没有店铺">
        <el-button v-if="nearby" type="primary" plain @click="useDemoLocation">
          使用演示位置（杭州西湖）
        </el-button>
      </el-empty>
    </template>

    <div v-if="!loading && shops.length" class="pager">
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
  letter-spacing: 0.5px;
}

.page-head p {
  margin: 0;
  font-size: 13px;
}

.nearby {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--text-sub);
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

.filter-tip {
  margin-bottom: 14px;
  padding: 10px 14px;
  font-size: 13px;
  color: var(--brand-dark);
  background: var(--brand-soft);
  border-radius: 10px;
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

.cover-distance {
  position: absolute;
  right: 12px;
  bottom: 12px;
  padding: 3px 10px;
  font-size: 12px;
  color: #fff;
  background: rgba(255, 102, 51, 0.9);
  border-radius: 999px;
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

.price {
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
