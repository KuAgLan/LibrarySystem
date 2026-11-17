import http from './http'

export const authApi = {
  login: (data) => http.post('/auth/login', data),
  me: () => http.get('/auth/me'),
  changePassword: (data) => http.post('/auth/password', data)
}

export const bookApi = {
  page: (params) => http.get('/books', { params }),
  create: (data) => http.post('/books', data),
  update: (id, data) => http.put(`/books/${id}`, data),
  remove: (id) => http.delete(`/books/${id}`)
}

export const readerApi = {
  page: (params) => http.get('/readers', { params }),
  create: (data) => http.post('/readers', data),
  update: (id, data) => http.put(`/readers/${id}`, data),
  remove: (id) => http.delete(`/readers/${id}`),
  setStatus: (id, status) => http.post(`/readers/${id}/status`, { status }),
  payFine: (id) => http.post(`/readers/${id}/pay-fine`),
  resetPassword: (id) => http.post(`/readers/${id}/reset-password`)
}

export const loanApi = {
  page: (params) => http.get('/loans', { params }),
  borrow: (data) => http.post('/loans/borrow', data),
  returnBook: (id) => http.post(`/loans/${id}/return`),
  renew: (id) => http.post(`/loans/${id}/renew`),
  overdue: (limit = 20) => http.get('/loans/overdue', { params: { limit } })
}

export const reservationApi = {
  list: (status) => http.get('/reservations', { params: { status } }),
  create: (data) => http.post('/reservations', data),
  cancel: (id) => http.post(`/reservations/${id}/cancel`)
}

export const reportApi = {
  dashboard: () => http.get('/reports/dashboard'),
  exportUrl: '/reports/loans/export'
}

export const settingApi = {
  get: () => http.get('/settings'),
  save: (data) => http.put('/settings', data)
}

export const portalApi = {
  profile: () => http.get('/reader/profile'),
  books: (params) => http.get('/reader/books', { params }),
  loans: (params) => http.get('/reader/loans', { params }),
  borrow: (bookId) => http.post('/reader/loans/borrow', { bookId }),
  renew: (loanId) => http.post(`/reader/loans/${loanId}/renew`),
  reserve: (bookId) => http.post(`/reader/books/${bookId}/reserve`),
  reservations: () => http.get('/reader/reservations'),
  cancelReservation: (id) => http.post(`/reader/reservations/${id}/cancel`),
  fine: () => http.get('/reader/fine')
}

export const jobApi = {
  settle: (holdMs = 0) => http.post(`/admin/jobs/settle?holdMs=${holdMs}`),
  locks: () => http.get('/admin/jobs/locks')
}

export const logApi = {
  page: (params) => http.get('/logs', { params })
}