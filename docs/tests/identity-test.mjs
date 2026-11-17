/**
 * 双端身份验收：管理员 / 读者 两类用户 + 越权隔离
 * 用法：node docs/tests/identity-test.mjs [BASE_URL] [借书证号]
 */
const B = process.argv[2] || 'http://127.0.0.1:8090'
const cardNo = process.argv[3] || 'R20241001'
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

// ---------- 管理员 ----------
const admin = await call('POST', '/auth/login', { username: 'admin', password: 'admin123' })
const adminToken = admin.body.data.token
check('管理员登录', Boolean(adminToken), `role=${admin.body.data?.user?.role} actor=${admin.body.data?.user?.actor}`)

// ---------- 读者 ----------
const reader = await call('POST', '/auth/login', { username: cardNo, password: '123456' })
const readerToken = reader.body.data?.token
check('读者用借书证登录', Boolean(readerToken), `role=${reader.body.data?.user?.role} actor=${reader.body.data?.user?.actor}`)
check('读者登录后角色为 READER', reader.body.data?.user?.role === 'READER')

const badPwd = await call('POST', '/auth/login', { username: cardNo, password: 'wrong-pwd' })
check('读者错误密码返回 401', badPwd.status === 401)

const profile = await call('GET', '/reader/profile', null, readerToken)
check('读者可读本人资料', profile.status === 200,
  `在借 ${profile.body.data?.borrowing} / 上限 ${profile.body.data?.maxBorrow}`)

const books = await call('GET', '/reader/books?onlyAvailable=true&pageSize=5', null, readerToken)
check('读者可检索馆藏', books.status === 200 && books.body.data.total > 0, `可借 ${books.body.data?.total} 种`)

// 借书用「全新的测试读者」，避免受演示数据已有逾期影响
const fresh = await call('POST', '/readers', {
  name: `测试读者${Date.now().toString().slice(-4)}`, phone: '', type: 'TEACHER', department: '验收'
}, adminToken)
const freshCard = fresh.body.data?.cardNo
const freshLogin = await call('POST', '/auth/login', { username: freshCard, password: '123456' })
const freshToken = freshLogin.body.data?.token
check('管理员新建读者可用默认密码登录门户', freshLogin.status === 200, `借书证 ${freshCard}`)

const borrowBook = books.body.data.items[0]
const borrow = await call('POST', '/reader/loans/borrow', { bookId: borrowBook.id }, freshToken)
check('读者自助借书', borrow.status === 200,
  borrow.status === 200 ? `应还 ${borrow.body.data?.dueAt}` : (borrow.body.error?.message || ''))

if (borrow.status === 200) {
  const renew = await call('POST', `/reader/loans/${borrow.body.data.id}/renew`, null, freshToken)
  check('读者自助续借', renew.status === 200, `新应还 ${renew.body.data?.dueAt}`)

  // 别人的借阅记录不可操作
  const adminLoans = await call('GET', '/loans?pageSize=50', null, adminToken)
  const other = adminLoans.body.data.items.find((l) => l.readerCardNo !== cardNo && l.status === 'BORROWED')
  if (other) {
    const cross = await call('POST', `/reader/loans/${other.id}/renew`, null, readerToken)
    check('读者无法操作他人借阅', cross.status === 404, `目标借阅 ${other.id}`)
  }
}

const myLoans = await call('GET', '/reader/loans?status=ALL&pageSize=50', null, readerToken)
const foreign = (myLoans.body.data.items || []).filter((l) => l.readerCardNo !== cardNo)
check('读者借阅列表只含本人数据', foreign.length === 0, `共 ${myLoans.body.data?.total} 条`)

const reserveTarget = books.body.data.items.find((b) => b.availableCopies === 0)
if (reserveTarget) {
  const reserve = await call('POST', `/reader/books/${reserveTarget.id}/reserve`, null, readerToken)
  check('读者预约无库存图书', reserve.status === 200 || reserve.status === 400, `状态 ${reserve.status}`)
}

// ---------- 越权矩阵 ----------
const staffEndpoints = ['/books', '/readers', '/loans', '/reports/dashboard', '/settings', '/logs', '/reservations', '/admin/jobs/locks']
const blocked = []
for (const ep of staffEndpoints) {
  const r = await call('GET', ep, null, readerToken)
  blocked.push(r.status)
}
check('读者访问全部管理端接口返回 403', blocked.every((s) => s === 403), blocked.join(','))

const adminOnPortal = await call('GET', '/reader/profile', null, adminToken)
check('管理员访问读者门户返回 403', adminOnPortal.status === 403)

const anonPortal = await call('GET', '/reader/profile')
check('未登录访问读者门户返回 401', anonPortal.status === 401)

const anonBooks = await call('GET', '/books')
check('未登录访问管理端返回 401', anonBooks.status === 401)

console.log(`\n通过 ${pass} 项，失败 ${fail} 项`)
process.exit(fail === 0 ? 0 : 1)
