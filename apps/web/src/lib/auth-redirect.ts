/** 未登录时跳转登录页，登录后回到指定路径 */
export function loginPath(redirectTo = "/") {
  const safe = redirectTo.startsWith("/") ? redirectTo : "/";
  return `/login?redirect=${encodeURIComponent(safe)}`;
}

export function registerPath(redirectTo = "/") {
  const safe = redirectTo.startsWith("/") ? redirectTo : "/";
  return `/register?redirect=${encodeURIComponent(safe)}`;
}
