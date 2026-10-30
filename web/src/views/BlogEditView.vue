<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Close, Picture, Plus, Search } from '@element-plus/icons-vue'
import { createBlog, deleteBlogImage, searchShops, uploadBlogImage } from '@/api'
import type { Shop } from '@/api'
import { imageUrl } from '@/utils/image'

const MAX_IMAGES = 9
const MAX_SIZE_MB = 5

const router = useRouter()

const title = ref('')
const content = ref('')
const shopId = ref<number | null>(null)
const shopOptions = ref<Shop[]>([])
const shopLoading = ref(false)
const images = ref<{ name: string; url: string }[]>([])
const uploading = ref(false)
const submitting = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

async function searchShop(keyword: string) {
  if (!keyword.trim()) {
    shopOptions.value = []
    return
  }
  shopLoading.value = true
  try {
    shopOptions.value = await searchShops(keyword.trim())
  } catch {
    shopOptions.value = []
  } finally {
    shopLoading.value = false
  }
}

function pickFiles() {
  fileInput.value?.click()
}

async function onFilesPicked(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''

  const remain = MAX_IMAGES - images.value.length
  if (remain <= 0) {
    ElMessage.warning(`最多上传 ${MAX_IMAGES} 张图片`)
    return
  }

  for (const file of files.slice(0, remain)) {
    if (!file.type.startsWith('image/')) {
      ElMessage.warning(`${file.name} 不是图片，已跳过`)
      continue
    }
    if (file.size > MAX_SIZE_MB * 1024 * 1024) {
      ElMessage.warning(`${file.name} 超过 ${MAX_SIZE_MB}MB，已跳过`)
      continue
    }
    uploading.value = true
    try {
      const name = await uploadBlogImage(file)
      images.value.push({ name, url: imageUrl(name) })
    } catch {
      ElMessage.error(`${file.name} 上传失败`)
    } finally {
      uploading.value = false
    }
  }
}

async function removeImage(index: number) {
  const [removed] = images.value.splice(index, 1)
  if (!removed) {
    return
  }
  try {
    await deleteBlogImage(removed.name)
  } catch {
    // 删除失败不影响页面，服务端文件可能残留
  }
}

async function submit() {
  if (!title.value.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  if (!content.value.trim()) {
    ElMessage.warning('请填写正文')
    return
  }
  submitting.value = true
  try {
    const id = await createBlog({
      title: title.value.trim(),
      content: content.value.trim(),
      images: images.value.map((item) => item.name).join(','),
      ...(shopId.value != null ? { shopId: shopId.value } : {})
    })
    ElMessage.success('发布成功')
    if (id != null) {
      router.replace({ name: 'blog-detail', params: { id } })
    } else {
      router.replace({ name: 'blogs' })
    }
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  // 预置一批店铺，用户点开下拉就能选，不必先输入
  searchShop('')
})
</script>

<template>
  <section class="blog-edit">
    <div class="page-head">
      <div>
        <h1>发布探店笔记</h1>
        <p class="muted">记录真实的到店体验，图片会存到服务端 /imgs 目录</p>
      </div>
      <el-button @click="router.back()">取消</el-button>
    </div>

    <div class="card editor">
      <div class="field">
        <label>标题</label>
        <el-input
          v-model="title"
          maxlength="40"
          show-word-limit
          placeholder="一句话说清这家店好在哪，比如「人均 60 的宝藏小馆」"
        />
      </div>

      <div class="field">
        <label>关联店铺<span class="optional">（可选）</span></label>
        <el-select
          v-model="shopId"
          filterable
          remote
          clearable
          reserve-keyword
          :remote-method="searchShop"
          :loading="shopLoading"
          placeholder="搜索店铺名称后选择"
          style="width: 100%"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
          <el-option
            v-for="shop in shopOptions"
            :key="shop.id"
            :label="shop.name"
            :value="shop.id"
          >
            <span>{{ shop.name }}</span>
            <span class="option-sub">{{ shop.area }}</span>
          </el-option>
        </el-select>
      </div>

      <div class="field">
        <label>正文</label>
        <el-input
          v-model="content"
          type="textarea"
          :rows="8"
          maxlength="1000"
          show-word-limit
          resize="none"
          placeholder="环境、口味、服务、性价比……写你最在意的点"
        />
      </div>

      <div class="field">
        <label>
          图片<span class="optional">（{{ images.length }}/{{ MAX_IMAGES }}）</span>
        </label>
        <div class="uploader">
          <div v-for="(item, index) in images" :key="item.name" class="thumb">
            <img :src="item.url" :alt="item.name" />
            <button type="button" class="remove" @click="removeImage(index)">
              <el-icon><Close /></el-icon>
            </button>
          </div>

          <button
            v-if="images.length < MAX_IMAGES"
            type="button"
            class="add"
            :disabled="uploading"
            @click="pickFiles"
          >
            <el-icon :size="22"><Plus /></el-icon>
            <span>{{ uploading ? '上传中…' : '添加图片' }}</span>
          </button>

          <input
            ref="fileInput"
            type="file"
            accept="image/*"
            multiple
            hidden
            @change="onFilesPicked"
          />
        </div>
        <p class="hint">
          <el-icon><Picture /></el-icon>
          支持 jpg / png / webp，单张不超过 {{ MAX_SIZE_MB }}MB，最多 {{ MAX_IMAGES }} 张
        </p>
      </div>

      <div class="actions">
        <el-button :loading="submitting" type="primary" size="large" @click="submit">
          发布笔记
        </el-button>
        <el-button size="large" @click="router.push({ name: 'blogs' })">先去看看别人的</el-button>
      </div>
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

.editor {
  padding: 24px;
}

.field {
  margin-bottom: 22px;
}

.field label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 500;
}

.optional {
  font-weight: 400;
  font-size: 12px;
  color: #9ca3af;
}

.option-sub {
  float: right;
  margin-left: 12px;
  font-size: 12px;
  color: #9ca3af;
}

.uploader {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.thumb {
  position: relative;
  width: 108px;
  height: 108px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid rgba(0, 0, 0, 0.06);
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.remove {
  position: absolute;
  top: 4px;
  right: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  color: #fff;
  background: rgba(0, 0, 0, 0.5);
  border: none;
  border-radius: 50%;
  cursor: pointer;
}

.remove:hover {
  background: rgba(0, 0, 0, 0.7);
}

.add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 108px;
  height: 108px;
  font-size: 12px;
  color: var(--text-sub);
  background: #fafbfc;
  border: 1px dashed rgba(0, 0, 0, 0.14);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.18s ease;
}

.add:hover:not(:disabled) {
  color: var(--brand);
  border-color: var(--brand);
  background: var(--brand-soft);
}

.add:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.hint {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 10px 0 0;
  font-size: 12px;
  color: #9ca3af;
}

.actions {
  display: flex;
  gap: 12px;
  padding-top: 8px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.actions :deep(.el-button) {
  margin-left: 0;
}

@media (max-width: 640px) {
  .page-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .editor {
    padding: 16px;
  }

  .thumb,
  .add {
    width: 88px;
    height: 88px;
  }
}
</style>
