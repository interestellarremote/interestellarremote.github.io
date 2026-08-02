import {after, before, test} from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {initializeTestEnvironment, assertFails, assertSucceeds} from "@firebase/rules-unit-testing";
import {get, ref, set} from "firebase/database";

const projectId = "antigravity-remote-test";
const deviceId = "dddddddddddddddddddddddddddddddd";
let environment;

before(async () => {
  environment = await initializeTestEnvironment({
    projectId,
    database: {rules: readFileSync(new URL("../../database.rules.json", import.meta.url), "utf8")},
  });
  await environment.withSecurityRulesDisabled(async (context) => {
    await set(ref(context.database(), `deviceOwners/${deviceId}`), "owner");
  });
});

after(async () => environment?.cleanup());

function command(messageId = "11111111-1111-4111-8111-111111111111") {
  return {
    version: 1, messageId, deviceId, conversationId: "conversation-1", sequence: 1,
    type: "SEND_PROMPT", createdAt: Date.now(), expiresAt: Date.now() + 60_000,
    keyVersion: 1, nonce: "AAECAwQFBgcICQoL", ciphertext: "opaque-ciphertext-value",
  };
}

test("owner can enqueue an encrypted command", async () => {
  const database = environment.authenticatedContext("owner").database();
  await assertSucceeds(set(ref(database, `mailboxes/${deviceId}/commands/${command().messageId}`), command()));
});

test("another user cannot read or enqueue commands", async () => {
  const database = environment.authenticatedContext("intruder").database();
  await assertFails(get(ref(database, `mailboxes/${deviceId}/events`)));
  await assertFails(set(ref(database, `mailboxes/${deviceId}/commands/bad`), command("bad")));
});

test("bridge can read its mailbox and publish encrypted events", async () => {
  const database = environment.authenticatedContext("bridge-device", {type: "bridge", deviceId}).database();
  await assertSucceeds(get(ref(database, `mailboxes/${deviceId}/commands`)));
  const event = {...command("22222222-2222-4222-8222-222222222222"), type: "TURN_COMPLETE"};
  await assertSucceeds(set(ref(database, `mailboxes/${deviceId}/events/${event.messageId}`), event));
});

test("expired commands are rejected", async () => {
  const database = environment.authenticatedContext("owner").database();
  const expired = {...command("33333333-3333-4333-8333-333333333333"), expiresAt: Date.now() - 1};
  await assertFails(set(ref(database, `mailboxes/${deviceId}/commands/${expired.messageId}`), expired));
});

