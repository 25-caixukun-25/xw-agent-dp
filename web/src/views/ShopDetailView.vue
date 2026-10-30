<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Clock, Location } from '@element-plus/icons-vue'
import { getShop, listVouchers } from '@/api'
import type { Shop, Voucher } from '@/api'
import { useAssistantStore } from '@/stores/assistant'

const GRADIENTS = [
  'linear-gradient(135deg, #ffd9c7 0%, #ffb08a 100%)',
  'linear-gradient(135deg, #ffe7ba 0%, #ffc069 100%)',
  'linear-gradient(135deg, #d6e4ff 0%, #adc6ff 100%)',
  'linear-gradient(135deg, #d9f7be 0%, #95de64 100%)'
]

const route = useRoute()
const router = useRouter()
const assistant = useAssistantStore()

const shop = ref<Shop | null>(null)
const vouchers = ref<Voucher[]>([])
const loading = ref(true)
const voucherLoading = ref(true)
const loadError = ref(false)
const activeImage = ref(0)
const brokenImages = ref<Record<number, boolean>>({})

const images = computed(() => {
  const raw = shop.value?.images || ''
  return raw.split(',').map((item) => item.trim()).filter(Boolean)
})

const coverStyle = computed(() => {
  const gradient = GRADIENTS[(shop.value?.id ?? 0) % GRADIENTS.length]
  const url = images.value[activeImage.value]
  if (!url || brokenImages.value[activeImage.value] || !/^https?:\/\//.test(url)) {
    return { backgroundImage: gradient }
  }
  return { backgroundImage: `url("${url}"), ${gradient}` }
})

function thumbStyle(index: number) {
  const gradient = GRADIENTS[(shop.value?.id ?? 0) % GRADIENTS.length]
  const url = images.value[index]
  if (!url || brokenImages.value[index] || !/^https?:\/\//.test(url)) {
    return { backgroundImage: gradient }
  }
  return { backgroundImage: `url("${url}"), ${gradient}` }
}

function scoreText() {
  return shop.value?.score == null ? '暂无' : (shop.value.score / 10).toFixed(1)
}

function yuan(value?: number) {
  if (value == null) {
    return '-'
  }
  const amount = value / 100
  return Number.isInteger(amount) ? String(amount) : amount.toFixed(1)
}

function formatRules(rules?: string) {
  return (rules || '').replace(/\\n/g, '\n')
}

async function loadShop(id: string) {
  loading.value = true
  loadError.value = false
  try {
    shop.value = await getShop(id)
    assistant.setShopContext({ id: shop.value.id, name: shop.value.name })
  } catch {
    loadError.value = true
    shop.value = null
  } finally {
    loading.value = false
  }
}

async function loadVouchers(id: string) {
  voucherLoading.value = true
  try {
    vouchers.value = (await listVouchers(id)) || []
  } catch {
    vouchers.value = []
  } finally {
    voucherLoading.value = false
  }
}

function reload() {
  const id = String(route.params.id)
  activeImage.value = 0
  brokenImages.value = {}
  loadShop(id)
  loadVouchers(id)
}

function askAi() {
  if (!shop.value) {
    return
  }
  assistant.open(`这家店怎么样：${shop.value.name}（店铺ID：${shop.value.id}）`)
}

function grab() {
  ElMessage.info('演示环境未开放支付，秒杀下单可通过接口体验')
}

watch(
  () => route.params.id,
  (id) => {
    if (id) {
      activeImage.value = 0
      brokenImages.value = {}
      loadShop(String(id))
      loadVouchers(String(id))
    }
  }
)

onMounted(() => {
  reload()
})

onUnmounted(() => {
  assistant.setShopContext(null)
})
</script>

<template>
  <div class="detail">
    <button class="back" type="button" @click="router.back()">
      <el-icon><ArrowLeft /></el-icon>
      返回
    </button>

    <div v-if="loading" class="card loading-card">
      <el-skeleton animated>
        <template #template>
          <el-skeleton-item variant="image" style="width: 100%; height: 320px" />
          <div style="padding: 24px">
            <el-skeleton-item variant="h1" style="width: 40%" />
            <el-skeleton-item variant="text" style="width: 70%; margin-top: 16px" />
            <el-skeleton-item variant="text" style="width: 55%; margin-top: 10px" />
          </div>
        </template>
      </el-skeleton>
    </div>

    <div v-else-if="loadError" class="card">
      <el-empty description="店铺不存在或加载失败">
        <el-button type="primary" @click="reload">重新加载</el-button>
      </el-empty>
    </div>

    <template v-else-if="shop">
      <section class="hero card">
        <div class="gallery">
          <div class="main-image" :style="coverStyle" />
          <div v-if="images.length > 1" class="thumbs">
            <button
              v-for="(image, index) in images.slice(0, 5)"
              :key="image + index"
              type="button"
              class="thumb"
              :class="{ active: index === activeImage }"
              :style="thumbStyle(index)"
              @click="activeImage = index"
            />
          </div>
        </div>

        <div class="summary">
          <h1>{{ shop.name }}</h1>

          <div class="stats">
            <span class="score" :class="{ empty: shop.score == null }">
              {{ scoreText() }}<small> 分</small>
            </span>
            <i class="split" />
            <span>人均 ¥{{ shop.avgPrice ?? '-' }}</span>
            <i class="split" />
            <span>{{ shop.area || '杭州' }}</span>
          </div>

          <p class="row">
            <el-icon><Location /></el-icon>
            <span>{{ shop.address || '地址待补充' }}</span>
          </p>
          <p v-if="shop.openHours" class="row">
            <el-icon><Clock /></el-icon>
            <span>营业时间 {{ shop.openHours }}</span>
          </p>
          <p class="row muted small">
            已售 {{ shop.sold ?? 0 }} · {{ shop.comments ?? 0 }} 条评价
          </p>

          <div class="actions">
            <el-button type="primary" @click="askAi">问问 AI 这家店</el-button>
            <el-button @click="router.push({ name: 'home' })">继续逛逛</el-button>
          </div>
        </div>
      </section>

      <section class="vouchers card">
        <header class="section-head">
          <h2>优惠券</h2>
          <span class="muted">到店出示券码即可核销</span>
        </header>

        <el-skeleton v-if="voucherLoading" :rows="2" animated />

        <div v-else-if="vouchers.length" class="voucher-list">
          <article v-for="voucher in vouchers" :key="voucher.id" class="voucher">
            <div class="amount">
              <span class="num">{{ yuan(voucher.actualValue) }}</span>
              <span class="unit">元</span>
            </div>
            <div class="info">
              <h3>{{ voucher.title }}</h3>
              <p v-if="voucher.subTitle" class="muted">{{ voucher.subTitle }}</p>
              <p class="rules">{{ formatRules(voucher.rules) }}</p>
            </div>
            <div class="action">
              <span class="pay">
                {{ (voucher.payValue ?? 0) > 0 ? `售价 ¥${yuan(voucher.payValue)}` : '免费领取' }}
              </span>
              <el-button type="primary" size="small" @click="grab">立即抢购</el-button>
            </div>
          </article>
        </div>

        <el-empty v-else description="这家店暂时没有可用优惠券" :image-size="90" />
      </section>
    </template>
  </div>
</template>

<style scoped>
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

.loading-card {
  overflow: hidden;
}

.hero {
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  overflow: hidden;
}

.gallery {
  padding: 16px;
}

.main-image {
  height: 320px;
  border-radius: 10px;
  background-size: cover;
  background-position: center;
}

.thumbs {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.thumb {
  width: 64px;
  height: 48px;
  padding: 0;
  border: 2px solid transparent;
  border-radius: 8px;
  background-size: cover;
  background-position: center;
  cursor: pointer;
}

.thumb.active {
  border-color: var(--brand);
}

.summary {
  padding: 28px 28px 24px 8px;
}

.summary h1 {
  margin: 0 0 14px;
  font-size: 26px;
  line-height: 1.3;
}

.stats {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: var(--text-sub);
  margin-bottom: 18px;
}

.score {
  font-size: 22px;
  font-weight: 600;
  color: #f59e0b;
}

.score small {
  font-size: 13px;
  font-weight: 400;
}

.score.empty {
  font-size: 16px;
  color: #9ca3af;
}

.split {
  width: 1px;
  height: 14px;
  background: rgba(0, 0, 0, 0.1);
}

.row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 10px;
  font-size: 14px;
  color: var(--text-sub);
  line-height: 1.6;
}

.row.muted {
  color: #9ca3af;
}

.row.small {
  font-size: 13px;
}

.actions {
  display: flex;
  gap: 12px;
  margin-top: 22px;
}

.vouchers {
  margin-top: 20px;
  padding: 22px 24px 26px;
}

.section-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}

.section-head h2 {
  margin: 0;
  font-size: 18px;
}

.section-head span {
  font-size: 12px;
}

.voucher-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.voucher {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px 20px;
  background: var(--brand-soft);
  border: 1px dashed rgba(255, 102, 51, 0.35);
  border-radius: 10px;
}

.amount {
  flex: none;
  display: flex;
  align-items: baseline;
  gap: 2px;
  min-width: 92px;
  color: var(--brand);
}

.amount .num {
  font-size: 30px;
  font-weight: 600;
}

.amount .unit {
  font-size: 13px;
}

.info {
  flex: 1;
  min-width: 0;
}

.info h3 {
  margin: 0 0 4px;
  font-size: 15px;
}

.info p {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
}

.rules {
  margin-top: 4px !important;
  color: #9ca3af;
  white-space: pre-line;
}

.action {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.pay {
  font-size: 12px;
  color: var(--text-sub);
}

@media (max-width: 900px) {
  .hero {
    grid-template-columns: 1fr;
  }

  .summary {
    padding: 8px 20px 24px;
  }

  .voucher {
    flex-wrap: wrap;
  }
}
</style>
