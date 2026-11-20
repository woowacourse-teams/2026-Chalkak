// 견본 내려받기와 레시피 쓰기. S3 클라이언트는 send(command)만 있으면 되므로 주입받는다.
import { createWriteStream } from 'node:fs';
import { rm } from 'node:fs/promises';
import { Transform } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { GetObjectCommand, PutObjectCommand } from '@aws-sdk/client-s3';

export const RECIPE_CONTENT_TYPE = 'application/json';
// 레시피 키는 recipeId마다 하나이고 내용이 바뀌지 않는다
export const RECIPE_CACHE_CONTROL = 'public, max-age=31536000, immutable';

class TooLargeError extends Error {}

const isMissing = (error) => error?.name === 'NoSuchKey' || error?.Code === 'NoSuchKey';

/**
 * 본문을 버퍼에 들지 않고 dest 파일로 바로 쓴다.
 * @returns {Promise<{ kind: 'ok', bytes: number } | { kind: 'too-large' } | { kind: 'missing' }>}
 *   그 밖의 오류(AccessDenied, 네트워크, 중단)는 throw한다. too-large·throw이면 부분 파일을 지운다.
 */
export async function downloadProbe(s3, { bucket, key, maxBytes, dest, signal }) {
  let response;
  try {
    response = await s3.send(new GetObjectCommand({ Bucket: bucket, Key: key }), { abortSignal: signal });
  } catch (error) {
    if (isMissing(error)) return { kind: 'missing' };
    throw error;
  }
  const { Body: body, ContentLength: contentLength } = response;
  if (typeof contentLength === 'number' && contentLength > maxBytes) {
    body?.destroy?.();
    return { kind: 'too-large' };
  }

  let bytes = 0;
  const counter = new Transform({
    transform(chunk, _encoding, callback) {
      bytes += chunk.length;
      callback(bytes > maxBytes ? new TooLargeError() : null, chunk);
    },
  });
  try {
    await pipeline(body, counter, createWriteStream(dest), { signal });
  } catch (error) {
    await rm(dest, { force: true });
    if (error instanceof TooLargeError) return { kind: 'too-large' };
    throw error;
  }
  return { kind: 'ok', bytes };
}

// 조건부 쓰기는 하지 않는다. 같은 키를 다시 쓰는 재시도는 같은 내용을 덮어쓴다.
export async function putRecipe(s3, { bucket, key, body, signal }) {
  await s3.send(
    new PutObjectCommand({ Bucket: bucket, Key: key, Body: body, ContentType: RECIPE_CONTENT_TYPE, CacheControl: RECIPE_CACHE_CONTROL }),
    { abortSignal: signal },
  );
}
