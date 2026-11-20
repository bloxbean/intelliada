#!/usr/bin/env node
// A HOSTILE fake joust-lsp for IntelliAda's tests (ADR-004 M1, I5, I15, I18, I25). No dependencies. It logs everything
// it receives and every answer the client gives to its own requests into ../log.jsonl, and tries every edit, link and
// markup trick the ADR says the client must refuse. ../port.txt and ../targets.json are written by the test.
'use strict';
const fs = require('fs');
const path = require('path');
const dir = path.join(__dirname, '..');
const logFile = path.join(dir, 'log.jsonl');
const read = (f, d) => { try { return fs.readFileSync(path.join(dir, f), 'utf8'); } catch { return d; } };
const port = read('port.txt', '9').trim();
const targets = JSON.parse(read('targets.json', '{}'));
const log = (o) => fs.appendFileSync(logFile, JSON.stringify(o) + '\n');
log({ event: 'started', argv: process.argv.slice(2), cwd: process.cwd(), pid: process.pid });

let buf = Buffer.alloc(0);
let nextId = 1000;
const pending = new Map();
const sendRaw = (text) => {
  const b = Buffer.from(text, 'utf8');
  process.stdout.write('Content-Length: ' + b.length + '\r\n\r\n');
  process.stdout.write(b);
};
let folds = 0;
let opens = 0;
const send = (o) => {
  const b = Buffer.from(JSON.stringify(Object.assign({ jsonrpc: '2.0' }, o)), 'utf8');
  process.stdout.write('Content-Length: ' + b.length + '\r\n\r\n');
  process.stdout.write(b);
};
const request = (method, params, tag) => { const id = nextId++; pending.set(id, tag || method); send({ id, method, params }); };
const notify = (method, params) => send({ method, params });

const img = (p) => '<img src="http://127.0.0.1:' + port + '/' + p + '">';
const range = (l, c, l2, c2) => ({ start: { line: l, character: c }, end: { line: l2, character: c2 } });
const hostileLocations = () => [
  { uri: targets.inside, range: range(0, 0, 0, 3) },
  { uri: targets.markupName, range: range(0, 0, 0, 1) },
  { uri: targets.outside, range: range(0, 0, 0, 1) },
  { uri: targets.symlink, range: range(0, 0, 0, 1) },
  { uri: 'joust-lib:/std/List.jsc', range: range(0, 0, 0, 1) },
  { uri: 'https://evil.example/Counter.jsc', range: range(0, 0, 0, 1) },
  { uri: 'file:////evil-server/share/x.jsc', range: range(0, 0, 0, 1) },
];

function onMessage(m) {
  if (m.method === undefined && m.id !== undefined) {
    log({ event: 'answer', to: pending.get(m.id), result: m.result, error: m.error });
    pending.delete(m.id);
    return;
  }
  log({ event: 'received', method: m.method, params: m.method === 'textDocument/didOpen' ? { uri: m.params.textDocument.uri } : m.params });
  switch (m.method) {
    case 'initialize':
      send({ id: m.id, result: { serverInfo: { name: '<html><b>hostile-fake</b>', version: '<b>1</b>' }, capabilities: {
        // Full sync as before, but as options asking for willSaveWaitUntil, whose edits LSP4IJ 0.30.1 applies (UP-CDX-1).
        textDocumentSync: { openClose: true, change: 1, willSaveWaitUntil: true, save: { includeText: false } }, hoverProvider: true, foldingRangeProvider: true, selectionRangeProvider: true, documentHighlightProvider: true, definitionProvider: true, referencesProvider: true, documentSymbolProvider: true,
        semanticTokensProvider: { legend: { tokenTypes: ['namespace', 'type', 'enum', 'enumMember', 'property', 'parameter', 'variable',
          'function', 'method', 'class', 'keyword'], tokenModifiers: ['declaration', 'readonly', 'defaultLibrary', 'timeValue'] }, full: true },
        // Everything below is refused or stripped by IntelliAda.
        completionProvider: { triggerCharacters: ['.'] }, renameProvider: true, documentFormattingProvider: true,
        documentRangeFormattingProvider: true, codeActionProvider: true, executeCommandProvider: { commands: ['evil'] },
        codeLensProvider: {}, documentLinkProvider: {},
        // LSP4IJ 0.30.1 paths (ADR-004 §12.2): pull diagnostics, virtual documents, folder changes, inline and linked edits.
        diagnosticProvider: { interFileDependencies: true, workspaceDiagnostics: true },
        inlineValueProvider: true, inlineCompletionProvider: {}, linkedEditingRangeProvider: true,
        workspace: { fileOperations: { willRename: { filters: [{ pattern: { glob: '**/*' } }] } },
          workspaceFolders: { supported: true, changeNotifications: true }, textDocumentContent: { schemes: ['joust-lib'] } },
      } } });
      break;
    case 'initialized':
      request('client/registerCapability', { registrations: [{ id: 'f', method: 'textDocument/formatting' }] }, 'register-formatting');
      request('client/registerCapability', { registrations: [{ id: 'w', method: 'workspace/willRenameFiles' }] }, 'register-willRename');
      request('workspace/applyEdit', { label: 'evil', edit: { changes: { [targets.inside]: [{ range: range(0, 0, 0, 0), newText: 'EVIL' }] } } }, 'applyEdit');
      request('window/showDocument', { uri: 'https://evil.example/x', external: true }, 'showDocument-external');
      request('window/showDocument', { uri: targets.outside }, 'showDocument-outside');
      request('window/showDocument', { uri: targets.symlink }, 'showDocument-symlink');
      request('workspace/workspaceFolders', null, 'workspaceFolders');
      request('workspace/configuration', { items: [{ section: '' }, { section: 'joust' }] }, 'configuration');
      request('workspace/inlineValue/refresh', null, 'inlineValue-refresh');
      request('workspace/diagnostic/refresh', null, 'diagnostic-refresh');
      request('client/registerCapability', { registrations: [{ id: 'd', method: 'textDocument/diagnostic',
        registerOptions: { interFileDependencies: true, workspaceDiagnostics: true } }] }, 'register-diagnostic');
      request('client/registerCapability', { registrations: [{ id: 'c', method: 'workspace/textDocumentContent',
        registerOptions: { schemes: ['joust-lib'] } }] }, 'register-textDocumentContent');
      notify('window/showMessage', { type: 1, message: 'boom ' + img('message') + ' <a href="file:///etc/passwd">open</a>' });
      request('window/workDoneProgress/create', { token: 'p1' }, 'progress-create');
      notify('$/progress', { token: 'p1', value: { kind: 'begin', title: '\u001b[31m<html><b>T</b>' + 'x'.repeat(5000), message: '<html>' + img('progress') } });
      notify('window/logMessage', { type: 3, message: '\u001b[31m<html><b>log</b>' + img('log') });
      notify('$/progress', { token: 'p1', value: { kind: 'end' } });
      log({ event: 'initialized-done' });
      break;
    case 'textDocument/didOpen':
      opens++;
      // A fresh folder request on every open, so module changes during a session are seen (I25).
      request('workspace/workspaceFolders', null, 'workspaceFolders-' + opens);
      notify('joust/status', { uri: m.params.textDocument.uri, compiler: '0.0.0-hostile', source: 'bundled', mode: 'advisory',
        state: 'ok', server: '<b>x</b>' });
      // Unsolicited diagnostics for a document the client never opened (M1-CDX-24), once.
      if (opens === 1) notify('textDocument/publishDiagnostics', { uri: targets.unopened, diagnostics: [{ range: range(0, 0, 0, 1), severity: 1,
        message: 'UNSOLICITED ' + img('unsolicited') }] });
      if (opens === 1) notify('textDocument/publishDiagnostics', { uri: 'file:////evil-server/share/x.jsc', diagnostics: [{ range: range(0, 0, 0, 1),
        severity: 1, message: 'REMOTE' }] });
      notify('textDocument/publishDiagnostics', { uri: m.params.textDocument.uri, diagnostics: [{
        range: range(0, 0, 0, 3), severity: 1, code: 'JSC0502', source: '<b>src</b>' + img('source'),
        message: 'List<T> is bad ' + img('diag'),
        codeDescription: { href: 'https://evil.example/"><img src="http://127.0.0.1:' + port + '/href">' },
        relatedInformation: [{ location: hostileLocations()[1], message: '<a href="file:///etc/passwd">see</a>' }],
      }] });
      break;
    case 'textDocument/hover':
      send({ id: m.id, result: { contents: { kind: 'markdown', value: '```jsc\ncount: Int\n```\n' + img('hover') + ' [l](file:///etc/passwd)' } } });
      break;
    case 'textDocument/definition':
    case 'textDocument/references':
      send({ id: m.id, result: hostileLocations() });
      break;
    case 'textDocument/documentSymbol':
      // Flat symbols for Flat.jsc, one located in a server-named virtual document (UP-CDX-5).
      if (m.params.textDocument.uri.endsWith('/Flat.jsc')) {
        send({ id: m.id, result: [
          { name: 'FlatLocal', kind: 12, location: { uri: m.params.textDocument.uri, range: range(0, 0, 0, 3) } },
          { name: 'FlatVirtual', kind: 12, location: { uri: 'joust-lib:/std/List.jsc', range: range(0, 0, 0, 1) } }] });
        break;
      }
      send({ id: m.id, result: [{ name: '\u001b[31m<html>Counter' + 'x'.repeat(5000), detail: '<b>d</b>' + img('symbol'), kind: 5,
        range: range(0, 0, 1, 0), selectionRange: range(0, 0, 0, 3) }] });
      break;
    case 'textDocument/foldingRange':
      // First an error (written with an escaped member name, M1-SEC-1), then a folding with a markup placeholder.
      if (folds++ % 2 === 1) {
        send({ id: m.id, result: [{ startLine: 0, endLine: 2, collapsedText: '<html><img src="http://127.0.0.1:' + port + '/fold">' + 'z'.repeat(500) }] });
        break;
      }
      sendRaw('{"jsonrpc":"2.0","id":' + JSON.stringify(m.id) + ',"\\u0065rror":{"code":-32603,"message":"fold ' + img('error').replace(/"/g, '\\"')
        + ' <a href=\\"file:///etc/passwd\\">x</a>"}}');
      break;
    case 'textDocument/selectionRange':
    case 'textDocument/documentHighlight':
      send({ id: m.id, error: { code: -32603, message: 'fold ' + img('error') + ' <a href="file:///etc/passwd">x</a>' + 'y'.repeat(5000), data: img('data') } });
      break;
    case 'textDocument/willSaveWaitUntil':
      send({ id: m.id, result: [{ range: range(0, 0, 0, 0), newText: 'EVIL-SAVE ' }] });
      break;
    case 'textDocument/diagnostic':
      send({ id: m.id, result: { kind: 'full', items: [], relatedDocuments: {
        [targets.unopened]: { kind: 'full', items: [{ range: range(0, 0, 0, 1), message: 'RELATED' }] },
        'file:////evil-server/share/r.jsc': { kind: 'full', items: [] } } } });
      break;
    case 'workspace/textDocumentContent':
      send({ id: m.id, result: { text: 'VIRTUAL' } });
      break;
    case 'textDocument/semanticTokens/full':
      send({ id: m.id, result: { data: [0, 0, 3, 4, 0] } });
      break;
    case 'shutdown':
      send({ id: m.id, result: null });
      break;
    case 'exit':
      process.exit(0);
      break;
    default:
      if (m.id !== undefined) send({ id: m.id, result: null });
  }
}

process.stdin.on('data', (chunk) => {
  buf = Buffer.concat([buf, chunk]);
  for (;;) {
    const sep = buf.indexOf('\r\n\r\n');
    if (sep < 0) return;
    const header = buf.slice(0, sep).toString('ascii');
    const len = Number((/Content-Length: *(\d+)/i.exec(header) || [])[1]);
    if (!(len >= 0) || buf.length < sep + 4 + len) return;
    const body = buf.slice(sep + 4, sep + 4 + len).toString('utf8');
    buf = buf.slice(sep + 4 + len);
    try { onMessage(JSON.parse(body)); } catch (e) { log({ event: 'error', error: String(e) }); }
  }
});
process.stdin.on('end', () => process.exit(0));
