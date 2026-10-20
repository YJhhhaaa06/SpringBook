/**
 * 应用入口：会话守卫、hash 路由、侧栏与顶栏、主题切换。
 *
 * 视图按需动态加载（import()），路由表是唯一的页面清单：
 * 未登录 → 登录页；已登录访问无权页面 → 回落到图书检索。
 */
import { getSession, clearSession } from './core/session.js';
import { parseHash, navigate, replace } from './core/router.js';
import { UNAUTHORIZED_EVENT } from './core/api.js';
import { startClock, tickCountdowns } from './ui/clock.js';
import { toastWarn } from './ui/toast.js';
import { esc } from './ui/format.js';
import { icon } from './ui/icons.js';
import { emptyHtml } from './ui/components.js';
import * as loginView from './views/login.js';

const ROUTES = [
  {
    path: '/books',
    title: { ADMIN: '图书检索', READER: '图书检索' },
    subtitle: {
      ADMIN: '检索馆藏、维护书目；借阅与预约可代办',
      READER: '按书名或书号检索，卡片上直接借阅或预约',
    },
    load: () => import('./views/books.js'),
  },
  {
    path: '/loans',
    title: { ADMIN: '借阅管理', READER: '我的借阅' },
    subtitle: {
      ADMIN: '全量借还记录；还书即结算罚款，可代办缴款',
      READER: '我的借还记录、逾期罚款与还书入口',
    },
    load: () => import('./views/loans.js'),
  },
  {
    path: '/readers',
    roles: ['ADMIN'],
    title: { ADMIN: '读者管理' },
    subtitle: { ADMIN: '分页检索、资料维护、冻结与解冻' },
    load: () => import('./views/readers.js'),
  },
  {
    path: '/account',
    title: { ADMIN: '我的账户', READER: '我的账户' },
    subtitle: { ADMIN: '账号信息与修改密码', READER: '档案、在借与欠款、修改密码' },
    load: () => import('./views/account.js'),
  },
];

const NAV = [
  { path: '/books', label: { ADMIN: '图书检索', READER: '图书检索' }, iconName: 'book' },
  { path: '/loans', label: { ADMIN: '借阅管理', READER: '我的借阅' }, iconName: 'loan' },
  { path: '/readers', label: { ADMIN: '读者管理' }, iconName: 'users', roles: ['ADMIN'] },
  { path: '/account', label: { ADMIN: '我的账户', READER: '我的账户' }, iconName: 'user' },
];

const root = document.getElementById('root');

let disposeView = null;
let mountedToken = null;
let routeSeq = 0;
let pendingNotice = '';

/* ---------- 主题 ---------- */

function currentTheme() {
  return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';
}

function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  try {
    localStorage.setItem('springbook.theme', theme);
  } catch {
    /* 隐私模式下忽略 */
  }
  const button = document.querySelector('[data-action="theme"]');
  if (button) {
    button.innerHTML = theme === 'dark' ? icon('sun', 16) : icon('moon', 16);
    button.setAttribute('title', theme === 'dark' ? '切换到浅色' : '切换到深色');
  }
}

function initTheme() {
  let saved = null;
  try {
    saved = localStorage.getItem('springbook.theme');
  } catch {
    saved = null;
  }
  if (saved === 'dark' || saved === 'light') {
    applyTheme(saved);
    return;
  }
  const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
  applyTheme(prefersDark ? 'dark' : 'light');
}

/* ---------- 外壳 ---------- */

function mountShell(session) {
  const admin = session.role === 'ADMIN';
  const navItems = NAV.filter((item) => !item.roles || item.roles.includes(session.role));

  root.innerHTML = `
    <div class="app">
      <aside class="sidebar">
        <div class="brand">
          <span class="brand__mark">藏</span>
          <span class="brand__text">
            <span class="brand__name">藏书阁</span>
            <span class="brand__sub">SpringBook</span>
          </span>
        </div>
        <nav class="nav" data-role="nav">
          <span class="nav__group">工作台</span>
          ${navItems
            .map(
              (item) => `<a class="nav__item" href="#${item.path}" data-path="${item.path}">
                <span class="nav__icon">${icon(item.iconName, 17)}</span>
                <span>${esc(item.label[session.role] || item.label.ADMIN)}</span>
              </a>`,
            )
            .join('')}
        </nav>
        <div class="sidebar__foot">
          <div class="userchip">
            <span class="avatar">${esc((session.username || '?').slice(0, 1).toUpperCase())}</span>
            <span class="userchip__meta">
              <span class="userchip__name">${esc(session.username)}</span>
              <span class="userchip__role">${admin ? '管理员' : '读者'}</span>
            </span>
          </div>
          <button class="btn btn--ghost btn--sm" type="button" data-action="logout">
            ${icon('logout', 14)} 退出
          </button>
        </div>
      </aside>

      <div class="main">
        <header class="topbar" data-role="topbar">
          <div class="topbar__title">
            <h1 data-role="title">图书检索</h1>
            <p class="topbar__sub" data-role="sub"></p>
          </div>
          <div class="topbar__actions">
            <span class="role-badge">${admin ? 'ADMIN' : 'READER'}</span>
            <button class="btn btn--ghost btn--icon" type="button" data-action="reload" title="刷新当前页">
              ${icon('refresh', 16)}
            </button>
            <button class="btn btn--ghost btn--icon" type="button" data-action="theme" title="切换主题"></button>
          </div>
        </header>
        <div class="content" data-role="content"></div>
      </div>
    </div>
  `;

  applyTheme(currentTheme());

  root.querySelector('[data-action="theme"]').addEventListener('click', () => {
    applyTheme(currentTheme() === 'dark' ? 'light' : 'dark');
  });
  root.querySelector('[data-action="reload"]').addEventListener('click', () => handleRoute());
  root.querySelectorAll('[data-action="logout"]').forEach((button) => {
    button.addEventListener('click', () => {
      clearSession();
      mountedToken = null;
      pendingNotice = '';
      navigate('/login');
    });
  });
}

function syncTopbarShadow() {
  const topbar = document.querySelector('.topbar');
  if (topbar) topbar.classList.toggle('is-stuck', window.scrollY > 4);
}

function setRouteMeta(route, session) {
  const title = root.querySelector('[data-role="title"]');
  const sub = root.querySelector('[data-role="sub"]');
  if (title) title.textContent = route.title[session.role] || route.title.ADMIN || '';
  if (sub) sub.textContent = route.subtitle[session.role] || route.subtitle.ADMIN || '';
  root.querySelectorAll('.nav__item').forEach((item) => {
    item.classList.toggle('is-active', item.dataset.path === route.path);
  });
}

/* ---------- 路由 ---------- */

async function handleRoute() {
  const session = getSession();
  const { path, query } = parseHash();
  const seq = ++routeSeq;

  if (!session) {
    if (disposeView) {
      disposeView();
      disposeView = null;
    }
    mountedToken = null;
    loginView.render(root, { notice: pendingNotice });
    pendingNotice = '';
    return;
  }

  if (path === '/login') {
    navigate('/books');
    return;
  }

  if (mountedToken !== session.token || !root.querySelector('[data-role="content"]')) {
    mountShell(session);
    mountedToken = session.token;
  }

  const route = ROUTES.find((item) => item.path === path) || ROUTES[0];
  if (route.roles && !route.roles.includes(session.role)) {
    toastWarn('没有访问该页面的权限', '已返回图书检索');
    navigate('/books');
    return;
  }

  setRouteMeta(route, session);

  if (disposeView) {
    disposeView();
    disposeView = null;
  }

  const content = root.querySelector('[data-role="content"]');
  content.innerHTML = '';
  const wrap = document.createElement('div');
  content.appendChild(wrap);

  try {
    const module = await route.load();
    if (seq !== routeSeq || !wrap.isConnected) return;
    wrap.innerHTML = '';
    disposeView = module.render(wrap, { session, query }) || null;
    tickCountdowns(wrap);
  } catch (error) {
    console.error('[route]', route.path, error);
    wrap.innerHTML = emptyHtml({
      title: '页面加载失败',
      desc: String(error?.message || error),
    });
  }
}

/* ---------- 启动 ---------- */

initTheme();
startClock();

if (!window.location.hash) replace('/books');

window.addEventListener('scroll', syncTopbarShadow, { passive: true });
window.addEventListener('hashchange', () => handleRoute());
window.addEventListener('storage', (event) => {
  if (event.key === 'springbook.session.v1') handleRoute();
});
window.addEventListener(UNAUTHORIZED_EVENT, () => {
  pendingNotice = '登录已过期，请重新登录';
  mountedToken = null;
  if (parseHash().path !== '/login') navigate('/login');
  else handleRoute();
});

handleRoute();
