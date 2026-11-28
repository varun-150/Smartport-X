// Testimonial API client — paths verified against the notification-service controller.

import { del, get, post, put } from '../lib/http'
import type { Testimonial, TestimonialRequest } from '../types'

export const testimonialApi = {
  list: () => get<Testimonial[]>('/v1/testimonials'),

  get: (id: string) => get<Testimonial>(`/v1/testimonials/${id}`),

  create: (req: TestimonialRequest) => post<Testimonial>('/v1/testimonials', req),

  update: (id: string, req: TestimonialRequest) => put<Testimonial>(`/v1/testimonials/${id}`, req),

  remove: (id: string) => del<void>(`/v1/testimonials/${id}`),
}