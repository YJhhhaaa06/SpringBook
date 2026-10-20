/**
 * 全局秒级时钟：扫描 [data-countdown]（ISO 时间串）并刷新剩余时长文案。
 * 预约保留期、令牌到期都用它，避免每个视图各起一个 interval。
 */
import { duration, esc } from './format.js';

/** 剩余时间低于该阈值标红（预约保留 48h、令牌 2h，30 分钟以内才算紧急） */
const URGENT_MS = 30 * 60 * 1000;

/** 刷新作用域内所有倒计时（秒级由 setInterval 驱动，局部重渲染后可手动调用一次） */
export function tickCountdowns(scope = document) {
  for (const node of scope.querySelectorAll('[data-countdown]')) {
    const target = new Date(node.dataset.countdown).getTime();
    if (Number.isNaN(target)) continue;
    const left = target - Date.now();
    node.textContent = left <= 0 ? '已过期' : duration(left);
    node.classList.toggle('is-expired', left <= 0);
    node.classList.toggle('is-urgent', left > 0 && left < URGENT_MS);
  }
}

/**
 * 生成带初始文案的倒计时节点：避免插入后到下一个 tick 之前显示占位符。
 */
export function countdownHtml(iso, extraClass = '') {
  const target = iso ? new Date(iso).getTime() : Number.NaN;
  let text = '—';
  if (!Number.isNaN(target)) {
    const left = target - Date.now();
    text = left <= 0 ? '已过期' : duration(left);
  }
  const classes = ['countdown', extraClass].filter(Boolean).join(' ');
  return `<span class="${classes}" data-countdown="${esc(iso || '')}">${text}</span>`;
}

export function startClock() {
  tickCountdowns();
  return setInterval(() => tickCountdowns(), 1000);
}
