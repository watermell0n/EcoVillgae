import { http } from '../../../shared/api/http'
import type { ApiResponse } from '../../../shared/api/types'

export interface ShopProduct {
  productId: string
  name: string
  description: string
  imageUrl: string | null
  originalPrice: number
  price: number
  initialStock: number
  currentStock: number
  status: 'DRAFT' | 'ON_SALE' | 'PAUSED' | 'SOLD_OUT'
  displayOrder: number
}

export interface StudentShopCatalog {
  walletBalance: number
  products: ShopProduct[]
}

export interface ShopPurchase {
  purchaseId: string
  productId: string
  productName: string
  quantity: number
  unitPrice: number
  totalPrice: number
  walletBalance: number
  remainingStock: number
  productStatus: ShopProduct['status']
  purchasedAt: string
  replayed: boolean
}

export interface PurchaseHistoryItem {
  purchaseId: string
  productId: string
  productName: string
  quantity: number
  unitPrice: number
  totalPrice: number
  status: 'SUCCESS' | 'REJECTED' | 'FAILED'
  purchasedAt: string
}

export interface TeacherPurchase {
  purchaseId: string
  participantNumber: number
  nickname: string | null
  productName: string
  quantity: number
  totalPrice: number
  purchasedAt: string
}

export interface TeacherShopSummary {
  roomId: string
  totalPurchaseCount: number
  totalRevenue: number
  products: ShopProduct[]
  recentPurchases: TeacherPurchase[]
}

export async function getStudentShopProducts() {
  const response = await http.get<ApiResponse<StudentShopCatalog>>('/student/shop/products')
  return response.data.data
}

export async function purchaseShopProduct(
  productId: string,
  quantity: number,
  idempotencyKey: string,
) {
  const response = await http.post<ApiResponse<ShopPurchase>>(
    '/student/shop/purchases',
    { productId, quantity },
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return response.data.data
}

export async function getStudentPurchaseHistory() {
  const response = await http.get<ApiResponse<{ purchases: PurchaseHistoryItem[] }>>(
    '/student/shop/purchases',
  )
  return response.data.data
}

export async function getTeacherShopSummary(roomId: string) {
  const response = await http.get<ApiResponse<TeacherShopSummary>>(
    `/teacher/rooms/${roomId}/shop-summary`,
  )
  return response.data.data
}
