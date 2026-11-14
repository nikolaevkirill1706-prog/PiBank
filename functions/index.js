const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const admin = require("firebase-admin");
const crypto = require("crypto");

admin.initializeApp();
const db = admin.database();

const DAY = 24 * 60 * 60 * 1000;
const GRACE = 3 * DAY;               // сколько дней после срока ждём, потом удаляем карты
const MAX_CARDS = 3;
const TYPES = ["debit", "credit", "child"];
const TERMS = [3, 7, 14, 30];        // доступные сроки кредита, дней
const MIN_LOAN = 10000;              // 100 ₽ (суммы в копейках)
const MAX_LOAN = 10000000;           // 100 000 ₽
const MAX_TRANSFER = 100000000;      // 1 000 000 ₽

// ---------- вспомогательное ----------

function requireAuth(request) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Войдите в аккаунт");
  return request.auth.uid;
}

function requireAmount(v, min, max) {
  if (!Number.isSafeInteger(v) || v < min || v > max) {
    throw new HttpsError("invalid-argument", "Некорректная сумма");
  }
  return v;
}

function money(minor) {
  const abs = Math.abs(minor);
  return Math.floor(abs / 100).toLocaleString("ru-RU") + "," + String(abs % 100).padStart(2, "0") + " ₽";
}

function luhn(payload) {
  let sum = 0;
  let dbl = true;
  for (let i = payload.length - 1; i >= 0; i--) {
    let d = Number(payload[i]);
    if (dbl) {
      d *= 2;
      if (d > 9) d -= 9;
    }
    sum += d;
    dbl = !dbl;
  }
  return (10 - (sum % 10)) % 10;
}

function newNumber() {
  let s = "5400";
  for (let i = 0; i < 11; i++) s += crypto.randomInt(10);
  return s + luhn(s);
}

// Атомарно меняет баланс. Возвращает false, если денег не хватает или карты нет.
async function adjustBalance(ref, delta) {
  const res = await ref.transaction((cur) => {
    if (cur === null) return cur;
    if (typeof cur !== "number") return;
    const next = cur + delta;
    if (next < 0) return;
    return next;
  });
  return res.committed && res.snapshot.val() !== null;
}

async function findCard(number) {
  if (!/^\d{16}$/.test(number)) return null;
  const idx = (await db.ref("cardIndex/" + number).get()).val();
  if (!idx) return null;
  let uid;
  let cardId;
  if (String(idx).includes("/")) {
    [uid, cardId] = String(idx).split("/");
  } else {
    // старый формат индекса (этап 1): в значении только uid
    uid = String(idx);
    const snap = await db.ref("cards/" + uid).get();
    snap.forEach((c) => {
      if (c.child("number").val() === number) cardId = c.key;
    });
    if (!cardId) return null;
  }
  const card = (await db.ref(`cards/${uid}/${cardId}`).get()).val();
  if (!card) return null;
  return { uid, cardId, card };
}

function addOp(updates, uid, type, amount, info) {
  const key = db.ref("operations/" + uid).push().key;
  updates[`operations/${uid}/${key}`] = {
    type,
    amount,
    info,
    createdAt: admin.database.ServerValue.TIMESTAMP,
  };
}

// ---------- создание карты ----------

exports.createCard = onCall(async (request) => {
  const uid = requireAuth(request);
  const { type, amount, termDays } = request.data || {};

  if (!TYPES.includes(type)) throw new HttpsError("invalid-argument", "Неизвестный тип карты");

  const cardsSnap = await db.ref("cards/" + uid).get();
  if (cardsSnap.numChildren() >= MAX_CARDS) {
    throw new HttpsError("failed-precondition", "Можно создать максимум три карты");
  }

  let loanAmount = 0;
  if (type === "credit") {
    loanAmount = requireAmount(amount, MIN_LOAN, MAX_LOAN);
    if (!TERMS.includes(termDays)) throw new HttpsError("invalid-argument", "Выберите срок кредита");
    const loan = (await db.ref("loans/" + uid).get()).val();
    if (loan && (loan.status === "active" || loan.status === "overdue")) {
      throw new HttpsError("failed-precondition", "У вас уже есть непогашенный кредит");
    }
  }

  const name = (await db.ref(`users/${uid}/name`).get()).val();
  const fallback = String((request.auth.token && request.auth.token.email) || "CLIENT").split("@")[0];
  const holder = String(name || fallback).toUpperCase().slice(0, 26);

  const cardId = db.ref("cards/" + uid).push().key;
  const indexValue = `${uid}/${cardId}`;

  // Резервируем уникальный номер
  let number = null;
  for (let i = 0; i < 20 && !number; i++) {
    const cand = newNumber();
    const r = await db.ref("cardIndex/" + cand).transaction((cur) => (cur === null ? indexValue : undefined));
    if (r.committed && r.snapshot.val() === indexValue) number = cand;
  }
  if (!number) throw new HttpsError("internal", "Не удалось создать номер карты. Попробуйте ещё раз");

  const d = new Date();
  d.setFullYear(d.getFullYear() + 4);
  const expiry = String(d.getMonth() + 1).padStart(2, "0") + "/" + String(d.getFullYear() % 100).padStart(2, "0");
  const cvv = String(crypto.randomInt(1000)).padStart(3, "0");
  const now = Date.now();

  const updates = {};
  updates[`cards/${uid}/${cardId}`] = {
    number,
    type,
    holder,
    expiry,
    cvv,
    status: "active",
    balance: loanAmount,
    createdAt: now,
  };
  if (type === "credit") {
    updates[`loans/${uid}`] = {
      cardId,
      principal: loanAmount,
      termDays,
      createdAt: now,
      dueAt: now + termDays * DAY,
      status: "active",
    };
    addOp(updates, uid, "loan_issued", loanAmount, `Кредит на ${termDays} дн. · карта ••••${number.slice(-4)}`);
  }

  try {
    await db.ref().update(updates);
  } catch (e) {
    await db.ref("cardIndex/" + number).remove();
    throw new HttpsError("internal", "Не удалось создать карту. Попробуйте ещё раз");
  }
  return { cardId, number };
});

// ---------- перевод ----------

exports.transfer = onCall(async (request) => {
  const uid = requireAuth(request);
  const { fromCardId, toNumber, amount } = request.data || {};
  const amt = requireAmount(amount, 1, MAX_TRANSFER);
  if (typeof fromCardId !== "string" || !fromCardId) {
    throw new HttpsError("invalid-argument", "Выберите карту списания");
  }

  const fromRef = db.ref(`cards/${uid}/${fromCardId}`);
  const from = (await fromRef.get()).val();
  if (!from) throw new HttpsError("not-found", "Карта списания не найдена");
  if (from.status !== "active") throw new HttpsError("failed-precondition", "Карта списания заблокирована");

  const dest = await findCard(String(toNumber || "").replace(/\s/g, ""));
  if (!dest) throw new HttpsError("not-found", "Карта получателя не найдена");
  if (dest.card.status !== "active") throw new HttpsError("failed-precondition", "Карта получателя недоступна");
  if (dest.uid === uid && dest.cardId === fromCardId) {
    throw new HttpsError("invalid-argument", "Нельзя переводить на ту же карту");
  }

  const toBalance = db.ref(`cards/${dest.uid}/${dest.cardId}/balance`);
  const okDebit = await adjustBalance(fromRef.child("balance"), -amt);
  if (!okDebit) throw new HttpsError("failed-precondition", "Недостаточно средств");

  let okCredit = false;
  try {
    okCredit = await adjustBalance(toBalance, amt);
  } catch (e) {
    okCredit = false;
  }
  if (!okCredit) {
    await adjustBalance(fromRef.child("balance"), amt); // возвращаем деньги
    throw new HttpsError("aborted", "Не удалось выполнить перевод. Деньги возвращены");
  }

  const updates = {};
  addOp(updates, uid, "transfer_out", amt, `На карту ••••${dest.card.number.slice(-4)}`);
  addOp(updates, dest.uid, "transfer_in", amt, `С карты ••••${from.number.slice(-4)}`);
  await db.ref().update(updates);
  return { ok: true };
});

// ---------- погашение кредита ----------

exports.repayLoan = onCall(async (request) => {
  const uid = requireAuth(request);
  const { fromCardId } = request.data || {};
  if (typeof fromCardId !== "string" || !fromCardId) {
    throw new HttpsError("invalid-argument", "Выберите карту для оплаты");
  }

  const loan = (await db.ref("loans/" + uid).get()).val();
  if (!loan || (loan.status !== "active" && loan.status !== "overdue")) {
    throw new HttpsError("failed-precondition", "У вас нет непогашенного кредита");
  }
  const due = Date.now() > loan.dueAt ? loan.principal * 2 : loan.principal;

  const from = (await db.ref(`cards/${uid}/${fromCardId}`).get()).val();
  if (!from) throw new HttpsError("not-found", "Карта не найдена");
  if (from.status !== "active") throw new HttpsError("failed-precondition", "Карта заблокирована");

  const ok = await adjustBalance(db.ref(`cards/${uid}/${fromCardId}/balance`), -due);
  if (!ok) throw new HttpsError("failed-precondition", `Недостаточно средств. Нужно ${money(due)}`);

  const updates = {};
  updates[`loans/${uid}/status`] = "closed";
  updates[`loans/${uid}/closedAt`] = Date.now();
  updates[`loans/${uid}/paid`] = due;
  addOp(updates, uid, "loan_repaid", due, `Погашение кредита · карта ••••${from.number.slice(-4)}`);
  await db.ref().update(updates);
  return { paid: due };
});

// ---------- проверка просроченных кредитов (каждые 30 минут) ----------

async function deleteAllCards(uid) {
  const cardsSnap = await db.ref("cards/" + uid).get();
  const updates = {};
  cardsSnap.forEach((c) => {
    const n = c.child("number").val();
    if (n) updates["cardIndex/" + n] = null;
  });
  updates["cards/" + uid] = null;
  updates[`loans/${uid}/status`] = "defaulted";
  addOp(updates, uid, "cards_deleted", 0, "Кредит не погашен вовремя. Все карты удалены");
  await db.ref().update(updates);
}

exports.checkLoans = onSchedule("every 30 minutes", async () => {
  const snap = await db.ref("loans").get();
  const now = Date.now();
  const tasks = [];
  snap.forEach((child) => {
    const uid = child.key;
    const loan = child.val();
    if (!loan || (loan.status !== "active" && loan.status !== "overdue")) return;
    if (now > loan.dueAt + GRACE) {
      tasks.push(deleteAllCards(uid));
    } else if (now > loan.dueAt && loan.status === "active") {
      tasks.push(db.ref(`loans/${uid}/status`).set("overdue"));
    }
  });
  await Promise.all(tasks);
});
