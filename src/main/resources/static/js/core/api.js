/**
 * REST 客户端。默认同源部署（页面由 Spring Boot 从 static/ 直接托管，无需 CORS）；
 * 若前后端分离，可在 index.html 中设置 window.SPRINGBOOK_API_BASE = 'http://host:8080'。
 *
 * 统一约定：响应体 { code, message, data }，code = 0 为成功；
 * 401 + 已带令牌 → 清理会话并广播 sb:unauthorized（由 app.js 跳登录页）。
 */
import { getSession, clearSession } from './session.js';
import { ERROR_MESSAGES, ERROR_HINTS } from './errors.js';

const API_BASE = (window.SPRINGBOOK_API_BASE || '').replace(/\/+$/, '');
const UNAUTHORIZED_EVENT = 'sb:unauthorized';

export class ApiError extends Error {
  constructor({ code, message, data, status }) {
    const friendly = ERROR_MESSAGES[code] || message || '请求失败';
    super(friendly);
    this.name = 'ApiError';
    this.code = code;
    this.data = data ?? null;
    this.status = status;
    this.friendly = friendly;
    this.hint = ERROR_HINTS[code] || '';
  }

  /** 40001 的 data 是 { 字段: 提示 }；其余返回 null */
  get fieldErrors() {
    if (this.code !== 40001) return null;
    if (!this.data || typeof this.data !== 'object' || Array.isArray(this.data)) return null;
    return this.data;
  }
}

export function isApiError(err) {
  return err instanceof ApiError;
}

async function request(method, path, { body, query, auth = true } = {}) {
  const url = new URL(API_BASE + path, window.location.origin);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined || value === null || value === '') continue;
      url.searchParams.set(key, String(value));
    }
  }

  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  const session = getSession();
  if (auth && session) headers.Authorization = `Bearer ${session.token}`;

  let response;
  try {
    response = await fetch(url.toString(), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError({ code: -1, message: '网络异常，无法连接服务端', status: 0 });
  }

  const text = await response.text();
  let payload = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = null;
    }
  }

  if (response.status === 401 && auth && session) {
    clearSession();
    window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
  }

  if (!response.ok || (payload && payload.code !== 0)) {
    throw new ApiError({
      code: payload?.code ?? response.status,
      message: payload?.message,
      data: payload?.data ?? null,
      status: response.status,
    });
  }
  return payload ? payload.data : null;
}

const get = (path, query) => request('GET', path, { query });
const post = (path, body) => request('POST', path, { body });
const put = (path, body) => request('PUT', path, { body });
const patch = (path, body) => request('PATCH', path, { body });
const del = (path) => request('DELETE', path);

export const api = {
  /* ---- 认证 ---- */
  login: (username, password) =>
    request('POST', '/api/auth/login', { body: { username, password }, auth: false }),
  register: (payload) =>
    request('POST', '/api/auth/register', { body: payload, auth: false }),
  changePassword: (oldPassword, newPassword) => put('/api/auth/password', { oldPassword, newPassword }),

  /* ---- 图书 ---- */
  listBooks: (query) => get('/api/books', query),
  getBook: (id) => get(`/api/books/${id}`),
  createBook: (payload) => post('/api/books', payload),
  updateBook: (id, payload) => put(`/api/books/${id}`, payload),
  adjustCopies: (id, totalCopies) => patch(`/api/books/${id}/copies`, { totalCopies }),
  deleteBook: (id) => del(`/api/books/${id}`),
  listReservations: (bookId) => get(`/api/books/${bookId}/reservations`),

  /* ---- 读者 ---- */
  listReaders: (query) => get('/api/readers', query),
  getReader: (id) => get(`/api/readers/${id}`),
  createReader: (payload) => post('/api/readers', payload),
  updateReader: (id, payload) => put(`/api/readers/${id}`, payload),
  updateReaderStatus: (id, status) => put(`/api/readers/${id}/status`, { status }),

  /* ---- 借还 / 罚款 ---- */
  listLoans: (query) => get('/api/loans', query),
  borrow: (bookId, readerId) => post('/api/loans', { bookId, readerId }),
  returnLoan: (id) => post(`/api/loans/${id}/return`),
  payFine: (id) => post(`/api/loans/${id}/fine/pay`),

  /* ---- 预约 ---- */
  createReservation: (bookId, readerId) => post('/api/reservations', { bookId, readerId }),
  cancelReservation: (id) => del(`/api/reservations/${id}`),
};

export { UNAUTHORIZED_EVENT };
