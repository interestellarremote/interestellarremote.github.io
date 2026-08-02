import {createHash, randomUUID, timingSafeEqual} from "node:crypto";
import {initializeApp} from "firebase-admin/app";
import {getAuth} from "firebase-admin/auth";
import {getDatabase} from "firebase-admin/database";
import {FieldValue, getFirestore} from "firebase-admin/firestore";
import {HttpsError, onCall, onRequest} from "firebase-functions/v2/https";
import {onValueCreated} from "firebase-functions/v2/database";
import {getMessaging} from "firebase-admin/messaging";

initializeApp();

const firestore = getFirestore();
const PAIRING_TTL_MS = 10 * 60 * 1000;

type PairingDocument = {
  deviceId: string;
  secretVerifier: string;
  encryptedName: string;
  expiresAt: number;
  ownerUid?: string;
  customToken?: string;
  claimedAt?: FirebaseFirestore.FieldValue;
};

function requireString(value: unknown, name: string, max = 512): string {
  if (typeof value !== "string" || value.length < 1 || value.length > max) {
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
  }
);
