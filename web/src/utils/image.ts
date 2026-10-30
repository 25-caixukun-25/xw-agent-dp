/**
 * 图片相关的小工具。
 *
 * 后端 /upload/blog 返回的是相对文件名（如 blogs/3/9/uuid.jpg），
 * 原版靠 nginx 提供 /imgs/**，本地只跑 vite + 8081 时没有 nginx，
 * 所以统一走 /api 前缀，由 vite 代理到后端的 /imgs/** 静态映射。
 */
const GRADIENTS = [
  'linear-gradient(135deg, #ffd9c7 0%, #ffb08a 100%)',
  'linear-gradient(135deg, #ffe7ba 0%, #ffc069 100%)',
  'linear-gradient(135deg, #d6e4ff 0%, #adc6ff 100%)',
  'linear-gradient(135deg, #d9f7be 0%, #95de64 100%)',
  'linear-gradient(135deg, #efdbff 0%, #b37feb 100%)',
  'linear-gradient(135deg, #ffd6e7 0%, #ff85c0 100%)'
]

/** 把后端返回的文件名/外链转成可访问地址 */
export function imageUrl(name?: string | null) {
  const first = name?.split(',')[0]?.trim()
  if (!first) {
    return ''
  }
  if (/^https?:\/\//.test(first)) {
    return first
  }
  // 种子数据里已经带了 /imgs 前缀（/imgs/shops/default.jpg）
  if (first.startsWith('/imgs/')) {
    return `/api${first}`
  }
  // 上传接口返回的是 blogs/x/y/uuid.jpg 这类相对文件名
  return `/api/imgs/${first.replace(/^\/+/, '')}`
}

/** 卡片封面：有真实图片就叠在渐变上，没有就用渐变兜底 */
export function coverStyle(images: string | undefined, seed: number) {
  const url = imageUrl(images)
  const gradient = GRADIENTS[seed % GRADIENTS.length]
  if (!url) {
    return { backgroundImage: gradient }
  }
  return { backgroundImage: `url("${url}"), ${gradient}` }
}

/** 笔记图片列表（后端多图用逗号分隔） */
export function imageList(images?: string | null) {
  return (images || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => imageUrl(item))
}
