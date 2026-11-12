import { after, before, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { arrayUnion, collection, deleteDoc, doc, getDoc, getDocs, query, serverTimestamp, setDoc, updateDoc, where } from 'firebase/firestore';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-listaspesa',
    firestore: { rules: readFileSync('firestore.rules', 'utf8') },
  });
});

after(async () => { await env?.cleanup(); });

test('solo membri e invitati validi accedono alla lista e ai suoi articoli', async () => {
  const owner = env.authenticatedContext('owner').firestore();
  const guest = env.authenticatedContext('guest').firestore();
  const outsider = env.authenticatedContext('outsider').firestore();
  const anonymous = env.unauthenticatedContext().firestore();
  const listId = 'sharedList';
  const secret = '0123456789abcdef0123456789abcdef';
  const ownerList = doc(owner, 'lists', listId);
  const guestList = doc(guest, 'lists', listId);
  const outsiderList = doc(outsider, 'lists', listId);

  await assertFails(setDoc(doc(anonymous, 'lists', 'anonymous'), {
    name: 'Vietata', ownerId: 'anonymous', members: ['anonymous'], inviteCode: secret, createdAt: serverTimestamp(),
  }));
  await assertSucceeds(setDoc(ownerList, {
    name: 'Spesa', ownerId: 'owner', members: ['owner'], inviteCode: secret, createdAt: serverTimestamp(),
  }));
  await assertSucceeds(getDoc(ownerList));
  await assertFails(getDoc(outsiderList));
  await assertFails(getDoc(doc(anonymous, 'lists', listId)));
  await assertSucceeds(getDocs(query(collection(owner, 'lists'), where('members', 'array-contains', 'owner'))));
  await assertFails(updateDoc(outsiderList, { members: arrayUnion('outsider'), joinProof: 'wrong' }));
  await assertFails(updateDoc(outsiderList, { members: arrayUnion('outsider', 'another'), joinProof: secret }));
  await assertSucceeds(updateDoc(guestList, { members: arrayUnion('guest'), joinProof: secret }));
  await assertSucceeds(getDoc(guestList));
  await assertFails(updateDoc(guestList, { name: 'Modifica non autorizzata' }));

  const guestItem = doc(guest, 'lists', listId, 'items', 'milk');
  const outsiderItem = doc(outsider, 'lists', listId, 'items', 'milk');
  await assertSucceeds(setDoc(guestItem, {
    name: 'Latte', checked: false, deleted: false,
    createdBy: 'guest', updatedBy: 'guest',
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
  }));
  await assertFails(getDoc(outsiderItem));
  await assertSucceeds(getDoc(doc(owner, 'lists', listId, 'items', 'milk')));
  await assertFails(setDoc(doc(outsider, 'lists', listId, 'items', 'bread'), {
    name: 'Pane', checked: false, deleted: false,
    createdBy: 'outsider', updatedBy: 'outsider',
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(updateDoc(guestItem, { checked: true, updatedBy: 'guest', updatedAt: serverTimestamp() }));
  await assertFails(deleteDoc(guestItem));
  await assertSucceeds(updateDoc(guestItem, { deleted: true, updatedBy: 'guest', updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(guestItem, { deleted: false, updatedBy: 'guest', updatedAt: serverTimestamp() }));
});
