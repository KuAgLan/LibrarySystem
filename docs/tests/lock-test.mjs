/**
 * 分布式锁验收：并发触发日结任务，验证「多实例只跑一次」。
 * 用法：node docs/tests/lock-test.mjs [BASE_URL]
 */
const B = process.argv[2] || 'http://127.0.0.1:8090'
let pass = 0, fail = 0
const check = (name, ok, extra = '') => {
  ok ? pass++ : fail++
  console.log(`${ok ? '✅' : '❌'} ${name}${extra ? ' — ' + extra : ''}`)
}
const call = async (method, path, body, token) => {
  const res = await fetch(B + path, {
    method,
    headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined
  })
  return { status: res.status, body: await res.json().catch(() => null) }
}

const login = await call('POST', '/auth/login', { username: 'admin', password: 'admin123' })
const token = login.body.data.token
check('管理员登录', Boolean(token))

const before = await call('GET', '/admin/jobs/locks', null, token)
check('查询锁实现', before.status === 200, `provider=${before.body.data.provider}，占用 ${before.body.data.activeLocks.length} 个`)

// 单次触发
const single = await call('POST', '/admin/jobs/settle', null, token)
check('手动触发日结任务', single.status === 200,
  `逾期 ${single.body.data?.overdueLoans} / 停用 ${single.body.data?.suspendedReaders} / 耗时 ${single.body.data?.costMs}ms`)
check('任务执行后锁自动释放', (await call('GET', '/admin/jobs/locks', null, token)).body.data.activeLocks.length === 0)

// 并发争抢：4 个请求同时打，每个持锁 2500ms
const concurrent = 4
const hold = 2500
const results = await Promise.all(
  Array.from({ length: concurrent }, () => call('POST', `/admin/jobs/settle?holdMs=${hold}`, null, token))
)
const ok = results.filter((r) => r.status === 200).length
const conflict = results.filter((r) => r.status === 409).length
check(`并发 ${concurrent} 个请求只有 1 个执行`, ok === 1, `成功 ${ok} 次，被锁拒绝 ${conflict} 次`)
check('其余请求返回 409 且提示锁被占用', conflict === concurrent - 1,
  results.find((r) => r.status === 409)?.body?.error?.message || '')
check('并发结束后锁已释放', (await call('GET', '/admin/jobs/locks', null, token)).body.data.activeLocks.length === 0)

// 馆员无权触发
const lib = await call('POST', '/auth/login', { username: 'librarian', password: 'lib123456' })
const libTrigger = await call('POST', '/admin/jobs/settle', null, lib.body.data.token)
check('馆员触发任务返回 403', libTrigger.status === 403)

// 未登录
const anon = await fetch(B + '/admin/jobs/locks')
check('未登录访问任务接口返回 401', anon.status === 401)

// 审计留痕
const logs = await call('GET', '/logs?keyword=NIGHTLY_SETTLE&page=1&pageSize=5', null, token)
check('任务执行写入审计日志', logs.status === 200 && logs.body.data.total > 0, `共 ${logs.body.data?.total} 条`)

console.log(`\n通过 ${pass} 项，失败 ${fail} 项`)
process.exit(fail === 0 ? 0 : 1)
