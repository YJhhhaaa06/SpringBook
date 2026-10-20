/** 展示层格式化：金额、时间、时长、文本转义 */

const HTML_ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

export function esc(value) {
  if (value === null || value === undefined) return '';
  return String(value).replace(/[&<>"']/g, (ch) => HTML_ESCAPES[ch]);
}

/** 分 → ¥ 元（展示用；出参同时有 *Cents 与换算好的元，一律用分做换算避免浮点误差） */
export function money(cents) {
  const n = Number(cents);
  if (!Number.isFinite(n)) return '¥0.00';
  return `¥${(n / 100).toFixed(2)}`;
}

/** 元（输入框字符串）→ 分；非法返回 null */
export function yuanToCents(input) {
  const text = String(input ?? '').trim();
  if (!text) return null;
  if (!/^\d+(\.\d{1,2})?$/.test(text)) return null;
  return Math.round(Number(text) * 100);
}

/** 分 → 输入框里的元字符串（不带符号） */
export function centsToYuan(cents) {
  return (Number(cents || 0) / 100).toFixed(2);
}

const pad = (n) => String(n).padStart(2, '0');

function toDate(value) {
  if (!value) return null;
  // 后端 LocalDateTime 无时区后缀，按本地时间解析
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

/** 2026-10-19 15:30 */
export function datetime(value) {
  const d = toDate(value);
  if (!d) return '—';
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** 2026-10-19 */
export function dateOnly(value) {
  const d = toDate(value);
  if (!d) return '—';
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** 2026-10-19T15:30:00 → 可读相对描述：今天 15:30 / 昨天 / 3 天后 */
export function humanDate(value) {
  const d = toDate(value);
  if (!d) return '—';
  const now = new Date();
  const startOfDay = (x) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const diffDays = Math.round((startOfDay(d) - startOfDay(now)) / 86400000);
  const time = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  if (diffDays === 0) return `今天 ${time}`;
  if (diffDays === 1) return `明天 ${time}`;
  if (diffDays === -1) return `昨天 ${time}`;
  if (diffDays > 1 && diffDays <= 7) return `${diffDays} 天后`;
  if (diffDays < -1 && diffDays >= -7) return `${-diffDays} 天前`;
  return datetime(value);
}

/** 剩余时间 → 47:12:33 / 1天 03:00:12 / 已超时 */
export function duration(ms) {
  if (!Number.isFinite(ms)) return '—';
  if (ms <= 0) return '已超时';
  const totalSeconds = Math.floor(ms / 1000);
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const clock = `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
  return days > 0 ? `${days}天 ${clock}` : clock;
}

/** 距现在还有多久（给「应还时间」这种不需要秒级跳动的地方用） */
export function relativeDays(value) {
  const d = toDate(value);
  if (!d) return '—';
  const diff = d.getTime() - Date.now();
  const days = Math.ceil(diff / 86400000);
  if (days === 0) return '今天到期';
  if (days > 0) return `还有 ${days} 天`;
  return `已逾期 ${-days} 天`;
}

export function initials(name) {
  const text = String(name || '?').trim();
  return text ? text.slice(0, 1) : '?';
}

export function percent(part, total) {
  const t = Number(total) || 0;
  if (t <= 0) return 0;
  return Math.max(0, Math.min(100, Math.round((Number(part) / t) * 100)));
}
