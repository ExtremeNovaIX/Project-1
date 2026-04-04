import type { ParsedEmotionMessage } from './types';

const EMOTION_PREFIX_PATTERN = /^\[([^\]]+)\]\s*/;
const EXTENDED_EMOTION_PREFIX_PATTERN = /^[\[【(（]([^\]】)）]+)[\]】)）]\s*/;

export const parseEmotionPrefix = (value: string): ParsedEmotionMessage => {
  const trimmedValue = value.trim();
  const matchedPrefix = trimmedValue.match(EXTENDED_EMOTION_PREFIX_PATTERN)
    ?? trimmedValue.match(EMOTION_PREFIX_PATTERN);

  if (!matchedPrefix) {
    return {
      emotion: null,
      content: trimmedValue
    };
  }

  return {
    emotion: matchedPrefix[1].trim() || null,
    content: trimmedValue.replace(EXTENDED_EMOTION_PREFIX_PATTERN, '').replace(EMOTION_PREFIX_PATTERN, '')
  };
};

export const normalizeEmotionToken = (value: string) =>
  value
    .trim()
    .replace(/^[\[【(（\s]+|[\]】)）\s]+$/g, '')
    .replace(/\s+/g, '')
    .toLowerCase();

export const extractSentenceContent = (value: unknown): string => {
  if (typeof value === 'string') {
    return value;
  }

  if (!value || typeof value !== 'object') {
    return '';
  }

  const objectValue = value as Record<string, unknown>;
  const preferredKeys = ['content', 'text', 'message', 'sentence'];

  for (const key of preferredKeys) {
    if (typeof objectValue[key] === 'string') {
      return objectValue[key] as string;
    }
  }

  const firstStringValue = Object.values(objectValue).find((item) => typeof item === 'string');
  return typeof firstStringValue === 'string' ? firstStringValue : '';
};
