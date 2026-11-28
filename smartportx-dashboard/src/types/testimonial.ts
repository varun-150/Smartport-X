// Testimonial module — hosted by the notification service.

export interface Testimonial {
  id: string
  name: string
  /** Optional; omitted by the API when null. */
  role?: string
  message: string
  rating: number
  createdAt: string
  updatedAt: string
}

export interface TestimonialRequest {
  name: string
  role?: string
  message: string
  rating: number
}