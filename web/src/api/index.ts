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

export const listVouchers = (shopId: number | string) =>
  http.get<any, Voucher[]>(`/voucher/list/${shopId}`)

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
