/**
 * 读者选择器：管理员代办借阅/预约时用来指定读者。
 * 关键词走后端 /api/readers?keyword=（姓名或手机号模糊匹配），列表点选。
 */
import { api } from '../core/api.js';
import { esc } from './format.js';
import { icon } from './icons.js';
import { delegate } from './dom.js';
import { toastError } from './toast.js';

export function createReaderPicker({ selected = null, onSelect = null } = {}) {
  const el = document.createElement('div');
  el.className = 'picker';
  el.innerHTML = `
    <div class="search">
      <span class="search__icon">${icon('search', 16)}</span>
      <input class="input" type="search" data-role="kw" placeholder="按姓名或手机号搜索读者" autocomplete="off" />
    </div>
    <div class="picker__list" data-role="list">
      <div class="picker__empty">输入关键词开始搜索…</div>
    </div>
  `;

  const input = el.querySelector('[data-role="kw"]');
  const list = el.querySelector('[data-role="list"]');
  let current = selected;
  let items = [];
  let timer = null;
  let seq = 0;

  function paint() {
    if (!items.length) {
      list.innerHTML = '<div class="picker__empty">没有匹配的读者</div>';
      return;
    }
    list.innerHTML = items
      .map((reader) => {
        const isSelected = current && String(current.id) === String(reader.id);
        return `<button type="button" class="picker__item ${isSelected ? 'is-selected' : ''}"
          data-role="pick" data-id="${reader.id}" data-name="${esc(reader.name)}">
          <span>
            <b>${esc(reader.name)}</b>
            <span class="muted mono" style="margin-left:8px;font-size:11.5px">${esc(reader.phone)}</span>
          </span>
          ${reader.status === 'FROZEN' ? '<span class="pill pill--danger">已冻结</span>' : '<span class="muted" style="font-size:11.5px">#' + reader.id + '</span>'}
        </button>`;
      })
      .join('');
  }

  async function search(keyword) {
    const mySeq = ++seq;
    list.innerHTML = '<div class="picker__empty">搜索中…</div>';
    try {
      const data = await api.listReaders({ keyword: keyword || undefined, page: 1, size: 20 });
      if (mySeq !== seq) return;
      items = data?.records || [];
      paint();
    } catch (error) {
      if (mySeq !== seq) return;
      items = [];
      list.innerHTML = '<div class="picker__empty">读取失败</div>';
      toastError(error);
    }
  }

  input.addEventListener('input', () => {
    clearTimeout(timer);
    const keyword = input.value.trim();
    timer = setTimeout(() => search(keyword), 260);
  });
  input.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      clearTimeout(timer);
      search(input.value.trim());
    }
  });

  delegate(list, 'click', '[data-role="pick"]', (event, button) => {
    const id = Number(button.dataset.id);
    current = { id, name: button.dataset.name };
    paint();
    if (onSelect) onSelect(current);
  });

  search('');

  return {
    el,
    getSelected: () => current,
    focus: () => input.focus(),
  };
}
