import { Server } from '@hocuspocus/server';
import { TiptapTransformer } from "@hocuspocus/transformer";
import pg from 'pg';
import dotenv from 'dotenv';

import StarterKit from '@tiptap/starter-kit';
import Image from '@tiptap/extension-image';
import { TaskItem, TaskList } from '@tiptap/extension-list';
import TextAlign from '@tiptap/extension-text-align';
import Typography from '@tiptap/extension-typography';
import Highlight from '@tiptap/extension-highlight';
import Subscript from '@tiptap/extension-subscript';
import Superscript from '@tiptap/extension-superscript';
import HorizontalRule from '@tiptap/extension-horizontal-rule';

import * as Y from 'yjs';

const backendSchemaExtensions = [
  StarterKit.configure({
    horizontalRule: false,
  }),
  HorizontalRule,
  TextAlign.configure({ types: ['heading', 'paragraph'] }),
  TaskList,
  TaskItem.configure({ nested: true }),
  Highlight.configure({ multicolor: true }),
  Image,
  Typography,
  Superscript,
  Subscript,
];

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

dotenv.config();

const dbPool = new pg.Pool({
  connectionString: process.env.DATABASE_URL,
});

const server = new Server({
  name: 'chat-collab-server',
  port: process.env.PORT || 1234,
  debounce: 2000,      // 防抖：2 秒内无人打字则落库
  maxDebounce: 10000,  // 强制落库：打字不停时，最长 10 秒强制备份一次

  // 鉴权 Hook
  async onAuthenticate({ token, documentName, connectionConfig }) {
    if (!token) {
      throw new Error('Not authorized: Token missing');
    }

    const ktorAuthUrl = process.env.KTOR_AUTH_URL || 'http://localhost:9051/v1/auth/verify';
    let userId;
    try {
      // 远程调用 Ktor 服务端校验 JWT 合法性
      const response = await fetch(ktorAuthUrl, {
        headers: {
          'Authorization': `Bearer ${token}` ,
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        }
      });
      if (!response.ok) {
        throw new Error('Unauthorized');
      }
      const user = await response.json();
      userId = user.userId;
    } catch (e) {
      console.error('Authentication failed:', e.message);
      throw new Error('Unauthorized');
    }

    if (!UUID_REGEX.test(documentName)) {
      throw new Error('Forbidden: invalid document name');
    }

    // 按房间授权：作者可写；已发布文章的其他登录用户只读。
    // 草稿必须拒绝：只放行写入是不够的，onLoadDocument 会把 articles.content
    // （未发布正文）作为初始文档推给任何连上房间的人。
    let article;
    try {
      const ownership = await dbPool.query(
        'SELECT author_id, status, deleted_at FROM articles WHERE id = $1::uuid',
        [documentName]
      );
      article = ownership.rows[0];
    } catch (e) {
      console.error('[onAuthenticate] Ownership lookup failed:', e.message);
      throw new Error('Authorization unavailable');
    }

    const isAuthor = article?.author_id === userId;
    const isPublished = article?.status === 'PUBLISHED' && article?.deleted_at == null;
    if (!isAuthor && !isPublished) {
      throw new Error('Forbidden');
    }
    if (!isAuthor && connectionConfig) {
      connectionConfig.readOnly = true;
    }

    return { userId }; // 注入上下文
  },

  async onLoadDocument({ documentName }) {

    if (!UUID_REGEX.test(documentName)) {
      console.warn(`[onLoadDocument] Invalid UUID room name: "${documentName}". Skipping DB fetch.`);
      return null;
    }

    try {
      const res = await dbPool.query(
        'SELECT state FROM yjs_documents WHERE article_id = $1::uuid',
        [documentName]
      );
      if (res.rows[0]?.state) {
          return res.rows[0].state; 
      }
  
      const articleRes = await dbPool.query(
          'SELECT content FROM articles WHERE id = $1::uuid',
          [documentName]
      );
  
      const contentJson = articleRes.rows[0]?.content;
  
      if (contentJson && Object.keys(contentJson).length > 0) {
          const ydoc = TiptapTransformer.toYdoc(contentJson, 'default', backendSchemaExtensions);
          return ydoc; 
      }
    } catch (e) {
      console.error('[onLoadDocument] Database query failed:', e.message);
    }

    return null;
  },

  // 存储文档 Hook
  async onStoreDocument({ documentName, document }) {
    const state = Y.encodeStateAsUpdate(document);
    await dbPool.query(
      `INSERT INTO yjs_documents (article_id, state, updated_at) 
       VALUES ($1::uuid, $2, NOW())
       ON CONFLICT (article_id) DO UPDATE SET state = $2, updated_at = NOW()`,
      [documentName, state]
    );
  }
});

server.listen().catch((e) => {
  // EADDRINUSE and hook failures arrive here; without this the container dies on an
  // unhandled rejection with a raw stack and no diagnosis.
  console.error('[collab-server] failed to start:', e.message);
  process.exit(1);
});