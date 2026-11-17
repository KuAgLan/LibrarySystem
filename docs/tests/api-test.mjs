/**
 * 接口验收脚本：登录 → 借书 → 续借 → 还书 → 校验库存与额度
 * 用法：node docs/tests/api-test.mjs [BASE_URL]
 */
const B = process.argv[2] || 'http://127.0.0.1:8090'
let pass = 0, fail = 0

const call = async (method, path, body, token) => {
  const res = await fetch(B + path, {
    method,
    headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined
  })
  const json = await res.json().catch(() => null)
  return { status: res.status, body: json }
}
const check = (name, ok, extra = '') => {
  ok ? pass++ : fail++
  console.log(`${ok ? '✅' : '❌'} ${name}${extra ? ' — ' + extra : ''}`)
}

const login = await call('POST', '/auth/login', { username: 'admin', password: 'admin123' })
check('管理员登录', login.status === 200 && login.body.data?.token, `role=${login.body.data?.user?.role}`)
const token = login.body.data.token
const badLogin = await call('POST', '/auth/login', { username: 'admin', password: 'wrong' })
check('错误密码返回 401', badLogin.status === 401)

const noAuth = await call('GET', '/books')
check('未登录访问受保护接口返回 401', noAuth.status === 401)

const books = await call('GET', '/books?page=1&pageSize=50', null, token)
const book = books.body.data.items.find((b) => b.availableCopies > 0)
check('图书列表', books.body.data.total > 0, `共 ${books.body.data.total} 种`)

const readers = await call('GET', '/readers?page=1&pageSize=50', null, token)
const reader = readers.body.data.items.find((r) => r.status === 'ACTIVE' && r.borrowedNow < 5)
check('读者列表', readers.body.data.total > 0, `共 ${readers.body.data.total} 位`)

const before = (await call('GET', `/books/${book.id}`, null, token)).body.data.availableCopies
const borrow = await call('POST', '/loans/borrow', { bookId: book.id, readerId: reader.id, remark: 'api-test' }, token)
check('借书成功', borrow.status === 200 && borrow.body.data.status === 'BORROWED', `应还 ${borrow.body.data?.dueAt}`)
const after = (await call('GET', `/books/${book.id}`, null, token)).body.data.availableCopies
check('库存 -1', after === before - 1, `${before} → ${after}`)

const renew = await call('POST', `/loans/${borrow.body.data.id}/renew`, null, token)
check('续借成功', renew.status === 200 && renew.body.data.renewCount === 1)
const renewAgain = await call('POST', `/loans/${borrow.body.data.id}/renew`, null, token)
check('超出续借次数被拒', renewAgain.status === 400)

const ret = await call('POST', `/loans/${borrow.body.data.id}/return`, null, token)
check('还书成功', ret.status === 200 && ret.body.data.status === 'RETURNED', `罚金 ${ret.body.data?.fineAmount}`)
const restored = (await call('GET', `/books/${book.id}`, null, token)).body.data.availableCopies
check('库存恢复', restored === before, `${after} → ${restored}`)

const returnAgain = await call('POST', `/loans/${borrow.body.data.id}/return`, null, token)
check('重复还书被拒', returnAgain.status === 400)

const badAmount = await call('POST', '/loans/borrow', { bookId: 999999, readerId: reader.id }, token)
check('不存在的图书返回 404', badAmount.status === 404)

const dash = await call('GET', '/reports/dashboard', null, token)
check('看板数据', dash.status === 200 && dash.body.data.stat.titleCount > 0)

const librarian = await call('POST', '/auth/login', { username: 'librarian', password: 'lib123456' })
const libToken = librarian.body.data.token
const libDelete = await call('DELETE', `/books/${book.id}`, null, libToken)
check('馆员删除图书返回 403', libDelete.status === 403)

console.log(`\n通过 ${pass} 项，失败 ${fail} 项`)
process.exit(fail === 0 ? 0 : 1)