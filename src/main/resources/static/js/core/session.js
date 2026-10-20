/**
 * 登录态：JWT 存 localStorage，身份信息从令牌载荷解析（后端登录响应只含 token 与有效期）。
 * 载荷字段：sub(accountId)、username、role、readerId、iat、exp。
 */
const KEY = 'springbook.session.v1';

/** 解析 JWT 载荷（不校验签名，签名与过期由服务端判定） */
export function decodeToken(token) {
  const seg = String(token).split('.')[1];
  if (!seg) throw new Error('令牌格式不正确');
  const b64 = seg.replace(/-/g, '+').replace(/_/g, '/');
  const padded = b64 + '='.repeat((4 - (b64.length % 4)) % 4);
  const bin = atob(padded);
  const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0));
  return JSON.parse(new TextDecoder('utf-8').decode(bytes));
}

/** 写入会话，返回归一化后的会话对象 */
export function startSession(token, expiresInSeconds) {
  const claims = decodeToken(token);
  const session = {
    token,
    username: claims.username || '',
    role: claims.role === 'ADMIN' ? 'ADMIN' : 'READER',
    readerId: claims.readerId ?? null,
    accountId: claims.sub ?? null,
    expiresAt: claims.exp
      ? claims.exp * 1000
      : Date.now() + (Number(expiresInSeconds) || 7200) * 1000,
  };
  localStorage.setItem(KEY, JSON.stringify(session));
  return session;
}

export function getSession() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return null;
    const session = JSON.parse(raw);
    if (!session || !session.token) return null;
    if (session.expiresAt && session.expiresAt <= Date.now()) {
      localStorage.removeItem(KEY);
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

export function clearSession() {
  try {
    localStorage.removeItem(KEY);
  } catch {
    /* 隐私模式下忽略 */
  }
}

export function isAdmin(session = getSession()) {
  return !!session && session.role === 'ADMIN';
}
