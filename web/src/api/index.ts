import http from './http'
import type { CurrentUser } from '@/stores/user'

export interface ShopType {
  id: number
  name: string
  icon?: string
  sort?: number
}

export interface Shop {
  id: number
  name: string
  typeId: number
  images?: string
  area?: string
  address?: string
  avgPrice?: number
  score?: number
  sold?: number
  comments?: number
  openHours?: string
  distance?: number
}

export interface Voucher {
  id: number
  shopId: number
  title: string
  subTitle?: string
  rules?: string
  payValue?: number
  actualValue?: number
  type?: number
  status?: number
  stock?: number
  beginTime?: string
  endTime?: string
}

export interface Blog {
  id: number
  shopId?: number
  userId?: number
  title: string
  content?: string
  images?: string
  liked?: number
  comments?: number
  name?: string
  icon?: string
  createTime?: string
  isLike?: boolean
}

export const sendCode = (phone: string) =>
  http.post<any, void>('/user/code', null, { params: { phone } })

export const loginByCode = (phone: string, code: string) =>
  http.post<any, string>('/user/login', { phone, code })

export const fetchMe = () => http.get<any, CurrentUser>('/user/me')

export const listShopTypes = () => http.get<any, ShopType[]>('/shop-type/list')

export const listShopsByType = (params: {
  typeId: number
  current?: number
  x?: number
  y?: number
}) => http.get<any, Shop[]>('/shop/of/type', { params })

export const getShop = (id: number | string) => http.get<any, Shop>(`/shop/${id}`)

export const searchShops = (name: string) =>
  http.get<any, Shop[]>('/shop/of/name', { params: { name } })

export const listVouchers = (shopId: number | string) =>
  http.get<any, Voucher[]>(`/voucher/list/${shopId}`)

export const seckillVoucher = (voucherId: number | string) =>
  http.post<any, number>(`/voucher-order/seckill/${voucherId}`)

export const listHotBlogs = (current = 1) =>
  http.get<any, Blog[]>('/blog/hot', { params: { current } })

export const getBlog = (id: number | string) => http.get<any, Blog>(`/blog/${id}`)

export interface UsageRecord {
  time: string
  sessionId: string
  promptTokens: number
  completionTokens: number
  totalTokens: number
  costMs: number
  answerChars: number
  estimatedYuan: string
}

export interface UsageSummary {
  recent: UsageRecord[]
  today: {
    date: string
    requests: number
    promptTokens: number
    completionTokens: number
    totalTokens: number
    estimatedYuan: string
  }
}

export const getUsage = (limit = 1) =>
  http.get<any, UsageSummary>('/agent/usage', { params: { limit } })

export interface PendingOrder {
  orderId: string
  voucherId: string
  title: string
  payYuan?: number
}

export const getPendingOrder = () => http.get<any, PendingOrder | null>('/agent/order/pending')

export const confirmOrder = (orderId: string) =>
  http.post<any, { orderId: string; status: string; duplicated: boolean }>('/agent/order/confirm', {
    orderId
  })

export const cancelOrder = (orderId: string) =>
  http.delete<any, void>(`/agent/order/pending/${orderId}`)

/* ------------------------------ 用户主页 ------------------------------ */

export interface UserInfo {
  userId: number
  city?: string
  introduce?: string
  fans?: number
  followee?: number
  gender?: boolean
  birthday?: string
  credits?: number
  level?: number
}

export const getUser = (id: number | string) => http.get<any, CurrentUser>(`/user/${id}`)

export const getUserInfo = (id: number | string) =>
  http.get<any, UserInfo | null>(`/user/info/${id}`)

export const signToday = () => http.post<any, void>('/user/sign')

export const getSignCount = () => http.get<any, number>('/user/sign/count')

export const listMyBlogs = (current = 1) =>
  http.get<any, Blog[]>('/blog/of/me', { params: { current } })

export const listBlogsOfUser = (id: number | string, current = 1) =>
  http.get<any, Blog[]>('/blog/of/user', { params: { id, current } })

export const isFollowing = (id: number | string) =>
  http.get<any, boolean>(`/follow/or/not/${id}`)

export const followUser = (id: number | string, follow: boolean) =>
  http.put<any, void>(`/follow/${id}/${follow}`)

/* ------------------------------ 笔记 ------------------------------ */

export const createBlog = (data: {
  title: string
  content: string
  images?: string
  shopId?: number
}) => http.post<any, number>('/blog', data)

export const likeBlog = (id: number | string) => http.put<any, void>(`/blog/like/${id}`)

export const uploadBlogImage = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return http.post<any, string>('/upload/blog', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const deleteBlogImage = (name: string) =>
  http.get<any, void>('/upload/blog/delete', { params: { name } })

/* ------------------------------ 评论 ------------------------------ */

export interface BlogComment {
  id: number
  blogId: number
  userId: number
  content: string
  liked?: number
  createTime?: string
  name?: string
  icon?: string
}

export const listComments = (blogId: number | string, current = 1) =>
  http.get<any, BlogComment[]>(`/blog-comments/of/${blogId}`, { params: { current } })

export const createComment = (blogId: number, content: string) =>
  http.post<any, number>('/blog-comments', { blogId, content })

