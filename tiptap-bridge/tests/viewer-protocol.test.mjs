import { test } from 'node:test';
import assert from 'node:assert/strict';
import { prepareViewerContent } from '../src/webview/viewer-protocol.js';

test('preserves request identity and renders parsed JSON', () => {
  const result = prepareViewerContent('{"type":"doc"}', 'session:2', value => value.type);
  assert.deepEqual(result, { content: 'doc', requestId: 'session:2', status: 'committed' });
});

test('empty content is a successful clear operation', () => {
  const result = prepareViewerContent('', 'clear', () => assert.fail('must not render'));
  assert.deepEqual(result, { content: null, requestId: 'clear', status: 'committed' });
});

test('malformed JSON produces a correlated failure without leaking input', () => {
  const result = prepareViewerContent('private invalid content', 'bad', () => assert.fail());
  assert.deepEqual(result, { content: null, requestId: 'bad', status: 'failed' });
});

test('renderer exceptions become correlated failures', () => {
  const result = prepareViewerContent('{}', 'broken', () => { throw new Error('private'); });
  assert.deepEqual(result, { content: null, requestId: 'broken', status: 'failed' });
});

test('legacy callers need no request identifier', () => {
  const result = prepareViewerContent('{}', null, () => 'legacy');
  assert.deepEqual(result, { content: 'legacy', requestId: null, status: 'committed' });
});
