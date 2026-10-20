/** 极简 hash 路由：#/books、#/loans?readerId=3 */

export function parseHash() {
  const raw = (window.location.hash || '').replace(/^#/, '');
  const [pathPart, queryPart] = raw.split('?');
  const path = pathPart && pathPart.startsWith('/') ? pathPart : '/books';
  const query = {};
  if (queryPart) {
    for (const [key, value] of new URLSearchParams(queryPart).entries()) query[key] = value;
  }
  return { path, query };
}

export function buildHash(path, query) {
  const params = new URLSearchParams();
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined || value === null || value === '') continue;
      params.set(key, String(value));
    }
  }
  const qs = params.toString();
  return `#${path}${qs ? `?${qs}` : ''}`;
}

export function navigate(path, query) {
  const next = buildHash(path, query);
  if (window.location.hash === next) {
    window.dispatchEvent(new HashChangeEvent('hashchange'));
    return;
  }
  window.location.hash = next;
}

export function replace(path, query) {
  const next = buildHash(path, query);
  window.history.replaceState(null, '', next);
}
