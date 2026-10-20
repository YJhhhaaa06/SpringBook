/**
 * 图书检索：卡片网格 + 关键词/分页。
 *
 * 角色差异：
 * - READER：借阅（自身）、预约/取消预约、查看队列；
 * - ADMIN：额外可新增/编辑/调册（入库下架）/删除，借阅与预约需先选读者（代办）。
 *
 * 预约视图受接口所限（后端无「按读者查预约」接口），因此本人的排队状态与 READY 倒计时
 * 直接叠加在图书卡片上：每页并行拉一次 /api/books/{id}/reservations。
 */
import { api } from '../core/api.js';
import {
  esc,
  money,
  datetime,
  percent,
  centsToYuan,
  yuanToCents,
} from '../ui/format.js';
import { icon } from '../ui/icons.js';
import { delegate, setBusy } from '../ui/dom.js';
import { countdownHtml } from '../ui/clock.js';
import { toastOk, toastError } from '../ui/toast.js';
import {
  emptyHtml,
  skeletonCards,
  pagerHtml,
  field,
  applyFieldErrors,
  reservationStatusPill,
} from '../ui/components.js';
import { openDialog, openDrawer, openFormModal, confirmDialog } from '../ui/modal.js';
import { createReaderPicker } from '../ui/reader-picker.js';

const PAGE_SIZE = 9;

export function render(root, ctx) {
  const { session } = ctx;
  const admin = session.role === 'ADMIN';
  const state = {
    keyword: '',
    page: 1,
    size: PAGE_SIZE,
    total: 0,
    records: [],
    queues: new Map(),
    seq: 0,
  };
  let disposed = false;

  root.innerHTML = `
    <div class="toolbar">
      <form class="search" data-role="search">
        <span class="search__icon">${icon('search', 16)}</span>
        <input class="input" name="keyword" type="search" autocomplete="off"
          placeholder="书名或书号，回车检索" aria-label="检索图书" />
      </form>
      <div class="toolbar__spacer"></div>
      ${
        admin
          ? `<button class="btn btn--primary" type="button" data-action="create">
               ${icon('plus', 14)} 新增图书
             </button>`
          : ''
      }
    </div>
    <div data-role="list"></div>
  `;

  const list = root.querySelector('[data-role="list"]');
  const searchForm = root.querySelector('[data-role="search"]');

  /* ---------- 数据 ---------- */

  async function load() {
    const seq = ++state.seq;
    list.innerHTML = skeletonCards(PAGE_SIZE);
    try {
      const page = await api.listBooks({ keyword: state.keyword, page: state.page, size: state.size });
      if (disposed || seq !== state.seq) return;
      state.total = page?.total || 0;
      state.records = page?.records || [];

      const results = await Promise.allSettled(state.records.map((book) => api.listReservations(book.id)));
      if (disposed || seq !== state.seq) return;
      state.queues.clear();
      results.forEach((result, index) => {
        if (result.status === 'fulfilled') {
          state.queues.set(state.records[index].id, result.value || []);
        }
      });
      paint();
    } catch (error) {
      if (disposed || seq !== state.seq) return;
      list.innerHTML = `<div class="callout callout--danger">
        <span>${icon('alert', 16)}</span>
        <div class="callout__body">${esc(error.friendly || '图书记录加载失败')}</div>
      </div>`;
    }
  }

  function queueOf(bookId) {
    return state.queues.get(bookId) || [];
  }

  function mineOf(book) {
    if (session.readerId === null || session.readerId === undefined) return null;
    return (
      queueOf(book.id).find(
        (item) =>
          String(item.readerId) === String(session.readerId) &&
          (item.status === 'WAITING' || item.status === 'READY'),
      ) || null
    );
  }

  /* ---------- 渲染 ---------- */

  function paint() {
    if (!state.records.length) {
      list.innerHTML = emptyHtml({
        title: state.keyword ? `没有匹配「${state.keyword}」的馆藏` : '馆藏还是空的',
        desc: state.keyword
          ? '换一个书名或书号关键词试试，支持模糊匹配。'
          : admin
            ? '点右上角「新增图书」入库第一本书。'
            : '等管理员入库后即可检索。',
        iconName: 'book',
      });
      return;
    }
    list.innerHTML = `
      <div class="book-grid">${state.records.map(cardHtml).join('')}</div>
      <div class="pager">${pagerHtml(
        { page: state.page, size: state.size, total: state.total },
        '本',
      )}</div>
    `;
  }

  function cardHtml(book) {
    const active = queueOf(book.id).filter((r) => r.status === 'WAITING' || r.status === 'READY');
    const mine = mineOf(book);
    const readyMine = mine && mine.status === 'READY';
    const waitingCount = active.filter((r) => r.status === 'WAITING').length;
    const readyCount = active.filter((r) => r.status === 'READY').length;
    const canBorrow = book.availableCopies > 0 || !!readyMine;
    const ratio = percent(book.availableCopies, book.totalCopies);
    const barClass = book.availableCopies === 0 ? 'is-empty' : ratio <= 34 ? 'is-low' : '';

    let note = '';
    if (readyMine) {
      note = `<div class="book-card__note is-ready">
        ${icon('clock', 14)}
        <span>到书待取，保留至 <b>${countdownHtml(mine.holdUntil)}</b></span>
      </div>`;
    } else if (mine) {
      const position = active.findIndex((r) => r.id === mine.id) + 1;
      note = `<div class="book-card__note">
        ${icon('queue', 14)}
        <span>排队中 · 第 <b class="num">${position}</b> 位（前面 ${position - 1} 人）</span>
      </div>`;
    } else if (book.availableCopies === 0) {
      note = `<div class="book-card__note">
        ${icon('clock', 14)}
        <span>已借完${waitingCount ? ` · 队列 ${waitingCount + readyCount} 人` : ' · 可预约排队'}</span>
      </div>`;
    }

    const actions = [];
    actions.push(
      `<button class="btn btn--sm ${canBorrow ? 'btn--primary' : ''}" type="button"
        data-action="borrow" data-id="${book.id}"
        ${!canBorrow && !admin ? 'disabled' : ''}
        ${canBorrow ? '' : 'title="可借数量为 0，可改为预约"'}>借阅</button>`,
    );

    if (!admin) {
      if (mine) {
        actions.push(
          `<button class="btn btn--sm btn--danger" type="button" data-action="cancel-reservation"
            data-id="${book.id}" data-rid="${mine.id}">取消预约</button>`,
        );
      } else if (book.availableCopies === 0) {
        actions.push(
          `<button class="btn btn--sm" type="button" data-action="reserve" data-id="${book.id}">预约</button>`,
        );
      }
    }

    if (admin) {
      actions.push(
        `<button class="btn btn--sm btn--ghost" type="button" data-action="edit" data-id="${book.id}">编辑</button>`,
        `<button class="btn btn--sm btn--ghost" type="button" data-action="copies" data-id="${book.id}">调册</button>`,
        `<button class="btn btn--sm btn--ghost" type="button" data-action="delete" data-id="${book.id}">下架</button>`,
      );
    }

    // 读者端队列为空时不必占位；管理员始终可见（要管理队列）
    if (admin || active.length) {
      actions.push(
        `<button class="btn btn--sm btn--ghost" type="button" data-action="queue" data-id="${book.id}">队列${
          active.length ? `（${active.length}）` : ''
        }</button>`,
      );
    }

    return `<article class="book-card ${readyMine ? 'is-ready' : mine ? 'is-mine' : ''}">
      <div class="book-card__head">
        <h3 class="book-card__title" title="${esc(book.title)}">${esc(book.title)}</h3>
        <div class="book-card__isbn">${esc(book.isbn)}</div>
      </div>
      <div>
        <div class="book-card__meta">
          <span class="book-card__price">${money(book.priceCents)}</span>
          <span class="book-card__stock">可借 <b class="num">${book.availableCopies}</b> / ${book.totalCopies}</span>
        </div>
        <div class="meter" style="margin-top:8px">
          <div class="meter__bar ${barClass}" style="width:${ratio}%"></div>
        </div>
      </div>
      ${note}
      <div class="book-card__actions">${actions.join('')}</div>
    </article>`;
  }

  /* ---------- 借阅 ---------- */

  function openBorrowDialog(book) {
    const pickerHost = document.createElement('div');
    const dialog = openDialog({
      title: '借出图书',
      subtitle: book.title,
      body: `
        <div class="callout callout--info" style="margin-bottom:14px">
          <div class="callout__body">
            可借 <b class="num">${book.availableCopies}</b> / 共 ${book.totalCopies} 册 ·
            书号 ${esc(book.isbn)}
          </div>
        </div>
        ${
          admin
            ? `<div class="field">
                 <span class="field__label">借阅读者<span class="req">*</span></span>
                 <div data-role="picker"></div>
                 <span class="field__err" data-role="picker-err"></span>
               </div>`
            : `<p class="muted" style="margin:0 0 6px;font-size:12.5px">
                 将以读者身份借出，应还时间由系统按借期计算，借出后可在「我的借阅」查看。
               </p>`
        }
      `,
    });

    let picker = null;
    if (admin) {
      picker = createReaderPicker();
      dialog.body.querySelector('[data-role="picker"]').appendChild(picker.el);
    }

    const cancel = document.createElement('button');
    cancel.type = 'button';
    cancel.className = 'btn';
    cancel.textContent = '取消';

    const confirm = document.createElement('button');
    confirm.type = 'button';
    confirm.className = 'btn btn--primary';
    confirm.textContent = '确认借出';

    cancel.addEventListener('click', dialog.close);
    confirm.addEventListener('click', async () => {
      const errEl = dialog.body.querySelector('[data-role="picker-err"]');
      if (errEl) errEl.classList.remove('is-shown');
      const readerId = admin ? picker.getSelected()?.id : session.readerId;
      if (!readerId) {
        if (errEl) {
          errEl.textContent = '请先选择借阅读者';
          errEl.classList.add('is-shown');
        }
        picker?.focus();
        return;
      }
      setBusy(confirm, true, '借出中…');
      try {
        const loan = await api.borrow(book.id, readerId);
        dialog.close();
        toastOk(`《${book.title}》已借出`, `应还时间 ${datetime(loan.dueAt)}`);
        load();
      } catch (error) {
        setBusy(confirm, false);
        toastError(error);
      }
    });

    dialog.foot.append(cancel, confirm);
    setTimeout(() => confirm.focus(), 50);
  }

  /* ---------- 预约 ---------- */

  function openReserveDialog(book) {
    const dialog = openDialog({
      title: '预约排队',
      subtitle: book.title,
      body: `
        <p style="margin:0 0 12px;color:var(--ink-2);line-height:1.7">
          当前可借 0 册。预约后进入 FIFO 队列，有副本归还时队首会转为「到书待取」，
          并保留 48 小时（系统配置），逾期未取则顺延下一位。
        </p>
        ${
          admin
            ? `<div class="field">
                 <span class="field__label">预约读者<span class="req">*</span></span>
                 <div data-role="picker"></div>
                 <span class="field__err" data-role="picker-err"></span>
               </div>`
            : ''
        }
      `,
    });

    let picker = null;
    if (admin) {
      picker = createReaderPicker();
      dialog.body.querySelector('[data-role="picker"]').appendChild(picker.el);
    }

    const cancel = document.createElement('button');
    cancel.type = 'button';
    cancel.className = 'btn';
    cancel.textContent = '取消';

    const confirm = document.createElement('button');
    confirm.type = 'button';
    confirm.className = 'btn btn--primary';
    confirm.textContent = '加入队列';

    cancel.addEventListener('click', dialog.close);
    confirm.addEventListener('click', async () => {
      const errEl = dialog.body.querySelector('[data-role="picker-err"]');
      if (errEl) errEl.classList.remove('is-shown');
      const readerId = admin ? picker.getSelected()?.id : session.readerId;
      if (!readerId) {
        if (errEl) {
          errEl.textContent = '请先选择预约读者';
          errEl.classList.add('is-shown');
        }
        picker?.focus();
        return;
      }
      setBusy(confirm, true, '提交中…');
      try {
        await api.createReservation(book.id, readerId);
        dialog.close();
        toastOk('已加入预约队列', '图书归还后会保留 48 小时，卡片上会显示取书倒计时');
        load();
      } catch (error) {
        setBusy(confirm, false);
        toastError(error);
      }
    });

    dialog.foot.append(cancel, confirm);
    setTimeout(() => confirm.focus(), 50);
  }

  async function cancelReservation(book, reservationId) {
    const ok = await confirmDialog({
      title: '取消预约',
      message: `取消对《${book.title}》的预约？`,
      detail: '若该书已为你预留（到书待取），名额会顺延给下一位排队者。',
    });
    if (!ok) return;
    try {
      await api.cancelReservation(reservationId);
      toastOk('预约已取消');
      load();
    } catch (error) {
      toastError(error);
    }
  }

  /* ---------- 队列抽屉 ---------- */

  async function openQueueDrawer(book) {
    const dialog = openDrawer({
      title: '预约队列',
      subtitle: book.title,
      body: '<div class="muted" style="font-size:12.5px">正在读取队列…</div>',
    });

    const renderBody = async () => {
      try {
        const [fresh, reservations] = await Promise.all([
          api.getBook(book.id),
          api.listReservations(book.id),
        ]);
        const active = (reservations || []).filter((r) => r.status === 'WAITING' || r.status === 'READY');
        const history = (reservations || []).filter((r) => r.status !== 'WAITING' && r.status !== 'READY');

        dialog.body.innerHTML = `
          <section>
            <div class="drawer__section-title">馆藏信息</div>
            <dl class="desc">
              <dt>书名</dt><dd>${esc(fresh.title)}</dd>
              <dt>书号</dt><dd class="mono">${esc(fresh.isbn)}</dd>
              <dt>价格</dt><dd class="mono">${money(fresh.priceCents)}</dd>
              <dt>可借 / 总册</dt><dd><b class="num">${fresh.availableCopies}</b> / ${fresh.totalCopies}</dd>
              <dt>入库时间</dt><dd>${datetime(fresh.createdAt)}</dd>
            </dl>
          </section>
          <section>
            <div class="drawer__section-title">活跃队列（${active.length}）</div>
            ${
              active.length
                ? `<div class="panel"><div class="table-wrap"><table class="table">
                     <thead><tr><th>#</th><th>读者</th><th>状态</th><th>时间</th><th></th></tr></thead>
                     <tbody>${active
                       .map((item, index) => queueRow(item, index + 1))
                       .join('')}</tbody>
                   </table></div></div>`
                : '<div class="callout">当前没有排队中的预约。</div>'
            }
          </section>
          ${
            history.length
              ? `<section>
                   <div class="drawer__section-title">历史记录（${history.length}）</div>
                   <div class="panel"><div class="table-wrap"><table class="table">
                     <thead><tr><th>读者</th><th>状态</th><th>时间</th></tr></thead>
                     <tbody>${history
                       .slice(0, 12)
                       .map(
                         (item) => `<tr>
                           <td>${esc(item.readerName)}</td>
                           <td>${reservationStatusPill(item.status)}</td>
                           <td class="muted num">${datetime(item.createdAt)}</td>
                         </tr>`,
                       )
                       .join('')}</tbody>
                   </table></div></div>
                 </section>`
              : ''
          }
        `;
      } catch (error) {
        dialog.body.innerHTML = `<div class="callout callout--danger">${esc(error.friendly || '队列读取失败')}</div>`;
      }
    };

    function queueRow(item, position) {
      const mine = String(item.readerId) === String(session.readerId);
      const canCancel = item.status === 'WAITING' || item.status === 'READY';
      const timeCell =
        item.status === 'READY' && item.holdUntil
          ? `${countdownHtml(item.holdUntil)}
             <div class="table__sub">保留至 ${datetime(item.holdUntil)}</div>`
          : `<span class="num">${datetime(item.createdAt)}</span>`;
      return `<tr>
        <td class="num">${position}</td>
        <td>${esc(item.readerName)}${mine ? ' <span class="pill pill--info">我</span>' : ''}</td>
        <td>${reservationStatusPill(item.status)}</td>
        <td>${timeCell}</td>
        <td class="t-actions">${
          canCancel
            ? `<button class="btn btn--sm btn--ghost" type="button" data-action="queue-cancel"
                 data-id="${item.id}" data-title="${esc(book.title)}">取消</button>`
            : ''
        }</td>
      </tr>`;
    }

    dialog.panel.addEventListener('click', async (event) => {
      const button = event.target.closest('[data-action="queue-cancel"]');
      if (!button) return;
      const ok = await confirmDialog({
        title: '取消预约',
        message: `取消该预约？`,
        detail: 'READY 状态的名额会顺延给下一位排队者。',
      });
      if (!ok) return;
      try {
        await api.cancelReservation(Number(button.dataset.id));
        toastOk('预约已取消');
        await renderBody();
        load();
      } catch (error) {
        toastError(error);
      }
    });

    renderBody();
  }

  /* ---------- 维护（ADMIN） ---------- */

  function openCreateForm() {
    openFormModal({
      title: '新增图书',
      subtitle: '入库后立即出现在检索结果中',
      submitText: '入库',
      body:
        field({ name: 'isbn', label: '书号（ISBN）', required: true, placeholder: '978-7-111-11111-1', attrs: 'maxlength="20"' }) +
        field({ name: 'title', label: '书名', required: true, placeholder: '深入理解计算机系统', attrs: 'maxlength="200"' }) +
        `<div class="field-row">
           ${field({ name: 'totalCopies', label: '总册数', required: true, type: 'number', value: '1', attrs: 'min="1" step="1"' })}
           ${field({ name: 'price', label: '单价（元）', required: true, type: 'number', value: '0.00', attrs: 'min="0" step="0.01"' })}
         </div>`,
      onSubmit: async ({ form, values }) => {
        const errors = {};
        if (!values.isbn) errors.isbn = '书号不能为空';
        if (!values.title) errors.title = '书名不能为空';
        const totalCopies = Number(values.totalCopies);
        if (!Number.isInteger(totalCopies) || totalCopies < 1) errors.totalCopies = '总册数至少为 1';
        const priceCents = yuanToCents(values.price);
        if (priceCents === null) errors.price = '请输入合法金额，最多两位小数';
        if (Object.keys(errors).length) return void applyFieldErrors(form, errors);

        await api.createBook({ isbn: values.isbn, title: values.title, totalCopies, priceCents });
        toastOk(`《${values.title}》已入库`);
        load();
      },
    });
  }

  function openEditForm(book) {
    openFormModal({
      title: '编辑图书',
      subtitle: `#${book.id} · ${book.isbn}`,
      submitText: '保存修改',
      body:
        field({ name: 'title', label: '书名', required: true, value: book.title, attrs: 'maxlength="200"' }) +
        field({
          name: 'price',
          label: '单价（元）',
          required: true,
          type: 'number',
          value: centsToYuan(book.priceCents),
          attrs: 'min="0" step="0.01"',
          hint: '仅书名与价格可改；总册数请用「调册」',
        }),
      onSubmit: async ({ form, values }) => {
        if (!values.title) return void applyFieldErrors(form, { title: '书名不能为空' });
        const priceCents = yuanToCents(values.price);
        if (priceCents === null) return void applyFieldErrors(form, { price: '请输入合法金额，最多两位小数' });
        const updated = await api.updateBook(book.id, { title: values.title, priceCents });
        toastOk('图书信息已更新', `版本 v${updated.version}`);
        load();
      },
    });
  }

  function openCopiesForm(book) {
    openFormModal({
      title: '调册（入库 / 下架）',
      subtitle: `${book.title} · 当前 ${book.totalCopies} 册`,
      submitText: '确认调整',
      body:
        field({
          name: 'totalCopies',
          label: '目标总册数',
          required: true,
          type: 'number',
          value: String(book.totalCopies),
          attrs: 'min="0" step="1"',
          hint: '直接填写调整后的总册数；可借数量按差额同步增减。',
        }) +
        `<div class="callout callout--warn">
          新总册数不得低于「在借数 + 未过期预约预留数」，否则会被服务端拒绝（40916）。
         </div>`,
      onSubmit: async ({ form, values }) => {
        const totalCopies = Number(values.totalCopies);
        if (!Number.isInteger(totalCopies) || totalCopies < 0)
          return void applyFieldErrors(form, { totalCopies: '请填写不小于 0 的整数' });
        const updated = await api.adjustCopies(book.id, totalCopies);
        toastOk('册数已调整', `现可借 ${updated.availableCopies} / 共 ${updated.totalCopies} 册`);
        load();
      },
    });
  }

  async function deleteBook(book) {
    const ok = await confirmDialog({
      title: '下架并删除图书',
      message: `确认删除《${book.title}》？`,
      detail: '若存在未归还借阅或活跃预约，服务端会拒绝删除。历史借阅记录不受影响。',
      confirmText: '删除',
      danger: true,
    });
    if (!ok) return;
    try {
      await api.deleteBook(book.id);
      toastOk(`《${book.title}》已删除`);
      if (state.records.length === 1 && state.page > 1) state.page -= 1;
      load();
    } catch (error) {
      toastError(error);
    }
  }

  /* ---------- 事件 ---------- */

  searchForm.addEventListener('submit', (event) => {
    event.preventDefault();
    state.keyword = searchForm.elements.namedItem('keyword').value.trim();
    state.page = 1;
    load();
  });

  delegate(root, 'click', '[data-action]', async (event, target) => {
    const action = target.dataset.action;
    const id = Number(target.dataset.id);

    if (action === 'page') {
      const next = Number(target.dataset.page);
      if (!Number.isFinite(next) || next < 1) return;
      state.page = next;
      load();
      return;
    }
    if (action === 'create') return openCreateForm();

    const book = state.records.find((item) => item.id === id);
    if (!book) return;

    switch (action) {
      case 'borrow':
        return openBorrowDialog(book);
      case 'reserve':
        return openReserveDialog(book);
      case 'cancel-reservation':
        return cancelReservation(book, Number(target.dataset.rid));
      case 'queue':
        return openQueueDrawer(book);
      case 'edit':
        return openEditForm(book);
      case 'copies':
        return openCopiesForm(book);
      case 'delete':
        return deleteBook(book);
      default:
        return undefined;
    }
  });

  load();

  return () => {
    disposed = true;
  };
}
