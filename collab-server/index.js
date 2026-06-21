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
  async onAuthenticate({ token }) {
    if (!token) {
      throw new Error('Not authorized: Token missing');
    }
    try {
      const ktorAuthUrl = process.env.KTOR_AUTH_URL || 'http://localhost:9051/v1/auth/verify';
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
      return { userId: user.userId }; // 注入上下文
    } catch (e) {
      console.error('Authentication failed:', e.message);
      throw new Error('Unauthorized');
    }
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
  async onStoreDocument({ documentName, state }) {
    await dbPool.query(
      `INSERT INTO yjs_documents (article_id, state, updated_at) 
       VALUES ($1::uuid, $2, NOW())
       ON CONFLICT (article_id) DO UPDATE SET state = $2, updated_at = NOW()`,
      [documentName, state]
    );
  }
});

server.listen();