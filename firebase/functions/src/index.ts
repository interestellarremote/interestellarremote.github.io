import {createHash, randomUUID, timingSafeEqual} from "node:crypto";
import {initializeApp} from "firebase-admin/app";
import {getAuth} from "firebase-admin/auth";
import {getDatabase} from "firebase-admin/database";
import {FieldValue, getFirestore, type DocumentSnapshot} from "firebase-admin/firestore";
import {HttpsError, onCall, onRequest} from "firebase-functions/v2/https";
import {onValueCreated} from "firebase-functions/v2/database";
import {getMessaging} from "firebase-admin/messaging";
import {GoogleAuth} from "google-auth-library";

initializeApp();

const firestore = getFirestore();
const PAIRING_TTL_MS = 10 * 60 * 1000;
const APP_PACKAGE_NAME = "com.antigravity.remote";
const FREE_DAILY_MESSAGE_LIMIT = 10;
const FREE_QUOTA_TIME_ZONE = "America/Sao_Paulo";
const PLAY_ANDROID_PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher";

type PairingDocument = {
  deviceId: string;
  secretVerifier: string;
  encryptedName: string;
  expiresAt: number;
  ownerUid?: string;
  customToken?: string;
  claimedAt?: FirebaseFirestore.FieldValue;
};

type AccessState = {
  proActive: boolean;
  productId: string | null;
  expiresAtMillis: number | null;
  subscriptionState: string | null;
};

type UsageState = {
  quotaDate: string;
  dailyMessageCount: number;
  dailyMessageLimit: number;
  proActive: boolean;
  productId: string | null;
  expiresAtMillis: number | null;
};

type PlaySubscriptionV2 = {
  subscriptionState?: string;
  lineItems?: Array<{
    productId?: string;
    expiryTime?: string;
  }>;
};

function requireString(value: unknown, name: string, max = 512): string {
  if (typeof value !== "string" || value.length < 1 || value.length > max) {
    throw new HttpsError("invalid-argument", `${name} inválido`);
  }
  return value;
}

function requireNumber(value: unknown, name: string): number {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new HttpsError("invalid-argument", `${name} inválido`);
  }
  return value;
}

function verifier(secret: string): Buffer {
  return createHash("sha256").update(secret, "utf8").digest();
}

function matchesSecret(secret: string, expectedHex: string): boolean {
  const actual = verifier(secret);
  const expected = Buffer.from(expectedHex, "hex");
  return actual.length === expected.length && timingSafeEqual(actual, expected);
}

function accessDoc(uid: string) {
  return firestore.doc(`users/${uid}/billing/access`);
}

function usageDoc(uid: string, quotaDate: string) {
  return firestore.doc(`users/${uid}/usage/${quotaDate}`);
}

function quotaDateKey(now = Date.now()): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: FREE_QUOTA_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date(now));
  const entries = Object.fromEntries(
    parts
      .filter((part) => part.type !== "literal")
      .map((part) => [part.type, part.value]),
  ) as Record<string, string>;
  return `${entries.year}-${entries.month}-${entries.day}`;
}

function parseMillis(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim().length > 0) {
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function isEntitlementStateAllowed(subscriptionState: string | null, expiresAtMillis: number | null, now = Date.now()): boolean {
  if (expiresAtMillis == null || expiresAtMillis <= now) return false;
  if (subscriptionState == null) return true;
  return [
    "SUBSCRIPTION_STATE_ACTIVE",
    "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
    "SUBSCRIPTION_STATE_CANCELED",
  ].includes(subscriptionState);
}

function readAccessState(snapshot: DocumentSnapshot, now = Date.now()): AccessState {
  const data = snapshot.data() ?? {};
  const productId = typeof data.productId === "string" ? data.productId : null;
  const subscriptionState = typeof data.subscriptionState === "string" ? data.subscriptionState : null;
  const expiresAtMillis = parseMillis(data.expiresAtMillis);
  const proActive = data.proActive === true && isEntitlementStateAllowed(subscriptionState, expiresAtMillis, now);
  return {proActive, productId, expiresAtMillis, subscriptionState};
}

async function verifyDeviceOwnership(uid: string, deviceId: string) {
  const owner = (await getDatabase().ref(`deviceOwners/${deviceId}`).get()).val();
  if (owner !== uid) {
    throw new HttpsError("permission-denied", "O dispositivo informado não pertence à sua conta.");
  }
}

function extractPromptEnvelope(data: unknown) {
  if (typeof data !== "object" || data == null) {
    throw new HttpsError("invalid-argument", "Envelope ausente.");
  }
  const envelope = data as Record<string, unknown>;
  const version = requireNumber(envelope.version, "version");
  const messageId = requireString(envelope.messageId, "messageId", 128);
  const deviceId = requireString(envelope.deviceId, "deviceId", 128);
  const conversationId = requireString(envelope.conversationId, "conversationId", 128);
  const sequence = requireNumber(envelope.sequence, "sequence");
  const type = requireString(envelope.type, "type", 64);
  const createdAt = requireNumber(envelope.createdAt, "createdAt");
  const expiresAt = requireNumber(envelope.expiresAt, "expiresAt");
  const keyVersion = requireNumber(envelope.keyVersion, "keyVersion");
  const nonce = requireString(envelope.nonce, "nonce", 4096);
  const ciphertext = requireString(envelope.ciphertext, "ciphertext", 200_000);
  if (version !== 1) throw new HttpsError("failed-precondition", "Versão de envelope não suportada.");
  if (type !== "SEND_PROMPT") throw new HttpsError("invalid-argument", "Somente prompts podem ser despachados por esta rota.");
  if (expiresAt <= Date.now()) throw new HttpsError("failed-precondition", "O envelope do prompt expirou antes do despacho.");
  return {
    version,
    messageId,
    deviceId,
    conversationId,
    sequence,
    type,
    createdAt,
    expiresAt,
    keyVersion,
    nonce,
    ciphertext,
  };
}

async function getPlaySubscription(purchaseToken: string): Promise<PlaySubscriptionV2> {
  const googleAuth = new GoogleAuth({scopes: [PLAY_ANDROID_PUBLISHER_SCOPE]});
  const client = await googleAuth.getClient();
  const url =
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${APP_PACKAGE_NAME}` +
    `/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;
  try {
    const response = await client.request<PlaySubscriptionV2>({url, method: "GET"});
    return response.data;
  } catch (error: unknown) {
    const status = typeof error === "object" && error != null &&
      "response" in error &&
      typeof (error as {response?: {status?: unknown}}).response?.status === "number"
      ? (error as {response: {status: number}}).response.status
      : null;
    if (status === 404) {
      throw new HttpsError("not-found", "A assinatura informada não foi encontrada no Google Play.");
    }
    if (status === 401 || status === 403) {
      throw new HttpsError(
        "permission-denied",
        "O servidor ainda não está autorizado a validar assinaturas no Google Play Developer API.",
      );
    }
    throw new HttpsError("internal", "Falha ao validar a assinatura no Google Play.");
  }
}

function summarizePlaySubscription(subscription: PlaySubscriptionV2, fallbackProductId: string | null) {
  const lineItems = Array.isArray(subscription.lineItems) ? subscription.lineItems : [];
  const expiresAtMillis = lineItems
    .map((item) => parseMillis(item.expiryTime))
    .reduce<number | null>((latest, current) => {
      if (current == null) return latest;
      if (latest == null) return current;
      return Math.max(latest, current);
    }, null);
  const productId = lineItems.find((item) => typeof item.productId === "string")?.productId ?? fallbackProductId;
  const subscriptionState = subscription.subscriptionState ?? null;
  const proActive = isEntitlementStateAllowed(subscriptionState, expiresAtMillis);
  return {proActive, productId, subscriptionState, expiresAtMillis};
}

async function bestEffortRevertUsage(uid: string, quotaDate: string) {
  await firestore.runTransaction(async (transaction) => {
    const reference = usageDoc(uid, quotaDate);
    const snapshot = await transaction.get(reference);
    const current = snapshot.exists ? Number(snapshot.data()?.count ?? 0) : 0;
    const next = Math.max(0, current - 1);
    transaction.set(
      reference,
      {
        date: quotaDate,
        count: next,
        updatedAt: FieldValue.serverTimestamp(),
      },
      {merge: true},
    );
  });
}

export const startPairing = onRequest({cors: false}, async (request, response) => {
  if (request.method !== "POST") {
    response.status(405).send("Method Not Allowed");
    return;
  }
  try {
    const deviceId = requireString(request.body?.deviceId, "deviceId", 128);
    const secretVerifier = requireString(request.body?.secretVerifier, "secretVerifier", 64);
    const encryptedName = requireString(request.body?.encryptedName, "encryptedName", 4096);
    if (!/^[a-f0-9]{64}$/.test(secretVerifier)) {
      throw new HttpsError("invalid-argument", "secretVerifier inválido");
    }
    const pairingId = randomUUID();
    const expiresAt = Date.now() + PAIRING_TTL_MS;
    const document: PairingDocument = {deviceId, secretVerifier, encryptedName, expiresAt};
    await firestore.doc(`pairings/${pairingId}`).create(document);
    response.json({pairingId, expiresAt});
  } catch (error) {
    const message = error instanceof Error ? error.message : "Bad Request";
    response.status(400).json({error: message});
  }
});

export const claimPairing = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Faça login primeiro");
  const pairingId = requireString(request.data?.pairingId, "pairingId", 128);
  const deviceId = requireString(request.data?.deviceId, "deviceId", 128);
  const secret = requireString(request.data?.secret, "secret", 256);
  const reference = firestore.doc(`pairings/${pairingId}`);
  const snapshot = await reference.get();
  if (!snapshot.exists) throw new HttpsError("not-found", "Pareamento não encontrado");
  const pairing = snapshot.data() as PairingDocument;
  if (pairing.deviceId !== deviceId || pairing.expiresAt < Date.now()) {
    throw new HttpsError("failed-precondition", "Pareamento expirado");
  }
  if (!matchesSecret(secret, pairing.secretVerifier)) {
    throw new HttpsError("permission-denied", "Segredo inválido");
  }
  const existing = await firestore.doc(`devices/${deviceId}`).get();
  if (existing.exists && existing.data()?.ownerUid !== request.auth.uid) {
    throw new HttpsError("already-exists", "Dispositivo pertence a outra conta");
  }
  const customToken = await getAuth().createCustomToken(`bridge-${deviceId}`, {
    type: "bridge",
    deviceId,
    ownerUid: request.auth.uid,
  });
  const batch = firestore.batch();
  batch.set(firestore.doc(`devices/${deviceId}`), {
    ownerUid: request.auth.uid,
    encryptedName: pairing.encryptedName,
    createdAt: FieldValue.serverTimestamp(),
    revoked: false,
  }, {merge: true});
  batch.update(reference, {
    ownerUid: request.auth.uid,
    customToken,
    claimedAt: FieldValue.serverTimestamp(),
  });
  await batch.commit();
  await getDatabase().ref(`deviceOwners/${deviceId}`).set(request.auth.uid);
  await getDatabase().ref(`users/${request.auth.uid}/devices/${deviceId}`).set(true);
  return {deviceId};
});

export const completePairing = onRequest({cors: false}, async (request, response) => {
  if (request.method !== "POST") {
    response.status(405).send("Method Not Allowed");
    return;
  }
  const pairingId = String(request.body?.pairingId ?? "");
  const deviceId = String(request.body?.deviceId ?? "");
  const secret = String(request.body?.secret ?? "");
  const reference = firestore.doc(`pairings/${pairingId}`);
  const snapshot = await reference.get();
  if (!snapshot.exists) {
    response.status(404).json({error: "Pareamento não encontrado"});
    return;
  }
  const pairing = snapshot.data() as PairingDocument;
  if (
    pairing.deviceId !== deviceId || pairing.expiresAt < Date.now() ||
    !matchesSecret(secret, pairing.secretVerifier)
  ) {
    response.status(403).json({error: "Pareamento inválido"});
    return;
  }
  if (!pairing.customToken) {
    response.status(202).json({status: "pending"});
    return;
  }
  await reference.delete();
  response.json({customToken: pairing.customToken});
});

export const syncSubscriptionPurchase = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Faça login primeiro");
  const purchaseToken = requireString(request.data?.purchaseToken, "purchaseToken", 4096);
  const fallbackProductId =
    typeof request.data?.productId === "string" && request.data.productId.length > 0 ?
      request.data.productId :
      null;
  const subscription = await getPlaySubscription(purchaseToken);
  const summary = summarizePlaySubscription(subscription, fallbackProductId);
  await accessDoc(request.auth.uid).set(
    {
      provider: "google_play",
      packageName: APP_PACKAGE_NAME,
      productId: summary.productId,
      purchaseTokenHash: createHash("sha256").update(purchaseToken, "utf8").digest("hex"),
      subscriptionState: summary.subscriptionState,
      proActive: summary.proActive,
      expiresAtMillis: summary.expiresAtMillis,
      updatedAt: FieldValue.serverTimestamp(),
    },
    {merge: true},
  );
  return {
    proActive: summary.proActive,
    productId: summary.productId,
    subscriptionState: summary.subscriptionState,
    expiresAtMillis: summary.expiresAtMillis,
  };
});

export const getAccessStatus = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Faça login primeiro");
  const uid = request.auth.uid;
  const quotaDate = quotaDateKey();
  const [accessSnapshot, usageSnapshot] = await Promise.all([
    accessDoc(uid).get(),
    usageDoc(uid, quotaDate).get(),
  ]);
  const access = readAccessState(accessSnapshot);
  const dailyMessageCount = usageSnapshot.exists ? Number(usageSnapshot.data()?.count ?? 0) : 0;
  return {
    proActive: access.proActive,
    productId: access.productId,
    expiresAtMillis: access.expiresAtMillis,
    dailyMessageCount,
    dailyMessageLimit: FREE_DAILY_MESSAGE_LIMIT,
    quotaDate,
  };
});

export const dispatchPrompt = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Faça login primeiro");
  const uid = request.auth.uid;
  const envelope = extractPromptEnvelope(request.data?.envelope);
  await verifyDeviceOwnership(uid, envelope.deviceId);

  const quotaDate = quotaDateKey();
  const reservation = await firestore.runTransaction(async (transaction): Promise<UsageState> => {
    const [accessSnapshot, usageSnapshot] = await Promise.all([
      transaction.get(accessDoc(uid)),
      transaction.get(usageDoc(uid, quotaDate)),
    ]);
    const access = readAccessState(accessSnapshot);
    const currentCount = usageSnapshot.exists ? Number(usageSnapshot.data()?.count ?? 0) : 0;
    if (!access.proActive && currentCount >= FREE_DAILY_MESSAGE_LIMIT) {
      throw new HttpsError(
        "resource-exhausted",
        `Limite diário de mensagens gratuito atingido (${FREE_DAILY_MESSAGE_LIMIT}/${FREE_DAILY_MESSAGE_LIMIT}). Assine o Interestellar Pro para uso ilimitado!`,
      );
    }
    const nextCount = access.proActive ? currentCount : currentCount + 1;
    if (!access.proActive) {
      transaction.set(
        usageDoc(uid, quotaDate),
        {
          date: quotaDate,
          count: nextCount,
          updatedAt: FieldValue.serverTimestamp(),
        },
        {merge: true},
      );
    }
    return {
      quotaDate,
      dailyMessageCount: nextCount,
      dailyMessageLimit: FREE_DAILY_MESSAGE_LIMIT,
      proActive: access.proActive,
      productId: access.productId,
      expiresAtMillis: access.expiresAtMillis,
    };
  });

  try {
    await getDatabase()
      .ref(`mailboxes/${envelope.deviceId}/commands/${envelope.messageId}`)
      .set(envelope);
  } catch (error) {
    if (!reservation.proActive) {
      await bestEffortRevertUsage(uid, quotaDate).catch(() => undefined);
    }
    throw new HttpsError("internal", "Não foi possível encaminhar o prompt para o computador.");
  }

  return reservation;
});

export const revokeDevice = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Faça login primeiro");
  const deviceId = requireString(request.data?.deviceId, "deviceId", 128);
  const device = await firestore.doc(`devices/${deviceId}`).get();
  if (!device.exists || device.data()?.ownerUid !== request.auth.uid) {
    throw new HttpsError("permission-denied", "Dispositivo não pertence à conta");
  }
  await getAuth().revokeRefreshTokens(`bridge-${deviceId}`);
  await firestore.doc(`devices/${deviceId}`).set({revoked: true}, {merge: true});
  await getDatabase().ref(`deviceOwners/${deviceId}`).remove();
  await getDatabase().ref(`users/${request.auth.uid}/devices/${deviceId}`).remove();
  return {revoked: true};
});

export const notifyRemoteEvent = onValueCreated(
  "/notifications/{deviceId}/{eventId}",
  async (event) => {
    const envelope = event.data.val() as {type?: string};
    if (!["APPROVAL_REQUEST", "TURN_COMPLETE", "BUILD_RESULT", "ERROR"].includes(envelope.type ?? "")) return;
    const deviceId = event.params.deviceId;
    const owner = (await getDatabase().ref(`deviceOwners/${deviceId}`).get()).val() as string | null;
    if (!owner) return;
    const tokensSnapshot = await getDatabase().ref(`users/${owner}/fcmTokens`).get();
    const tokens = Object.values(tokensSnapshot.val() ?? {}).filter((value): value is string => typeof value === "string");
    if (!tokens.length) return;
    const kind = envelope.type === "APPROVAL_REQUEST" ? "approval" : envelope.type === "ERROR" ? "error" : "complete";
    await getMessaging().sendEachForMulticast({
      tokens,
      data: {kind, deviceId},
      android: {priority: "high"},
    });
    await event.data.ref.remove();
  },
);
